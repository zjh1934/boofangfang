// JNI 桥：把 qwen3_tts.cpp 的推理管线暴露给 Kotlin
// mode: 0=base（标准朗读） 1=customvoice（参考音频克隆） 2=voicedesign（文字描述音色）
#include <jni.h>
#include <android/log.h>
#include <string>
#include <cstring>
#include <unistd.h>
#include <vector>
#include <atomic>
#include <mutex>
#include <thread>
#include <chrono>
#include "qwen3_tts.h"

#define TAG "BFF"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)

static std::unique_ptr<qwen3_tts::Qwen3TTS> g_tts;
static std::mutex g_mtx;
static std::atomic<bool> g_cancel{false};
static std::atomic<int> g_done{0}, g_max{0};
static std::string g_last_error;

static std::string j2s(JNIEnv* env, jstring s) {
    if (!s) return {};
    const char* c = env->GetStringUTFChars(s, nullptr);
    std::string r(c ? c : "");
    env->ReleaseStringUTFChars(s, c);
    return r;
}

extern "C" {

JNIEXPORT jboolean JNICALL Java_com_boofangfang_engine_TtsEngine_nativeLoad(
        JNIEnv* env, jclass, jstring dir, jint threads) {
    std::string path = j2s(env, dir);
    // 上游 load_models() 固定查找 qwen3-tts-0.6b-q8_0.gguf / qwen3-tts-tokenizer-f16.gguf，
    // 这里按 manifest.json（talker/tokenizer 字段）把实际模型软链为上游期望的文件名。
    {
        std::string mf = path + "/manifest.json";
        std::string talker, tok;
        if (FILE* f = fopen(mf.c_str(), "r")) {
            char buf[4096]; size_t n = fread(buf, 1, sizeof(buf) - 1, f); buf[n] = 0; fclose(f);
            auto pick = [&](const char* key, std::string& out) {
                std::string k = std::string(""") + key + """;
                const char* p = strstr(buf, k.c_str());
                if (!p) return;
                p = strchr(p + k.size(), ':'); if (!p) return;
                while (*p == ' ' || *p == '"') p++;
                const char* e = strchr(p, '"'); if (!e) return;
                out = path + "/" + std::string(p, e);
            };
            pick("talker", talker); pick("tokenizer", tok);
        }
        auto link = [&](const std::string& src, const std::string& dst) {
            if (src.empty()) return;
            unlink(dst.c_str());
            if (symlink(src.c_str(), dst.c_str()) != 0) {  // 软链不可用时退化为复制
                if (FILE* a = fopen(src.c_str(), "rb")) { if (FILE* b = fopen(dst.c_str(), "wb")) { char b2[1 << 16]; size_t r; while ((r = fread(b2, 1, sizeof(b2), a)) > 0) fwrite(b2, 1, r, b); fclose(b); } fclose(a); }
            }
        };
        link(talker, path + "/qwen3-tts-0.6b-q8_0.gguf");
        link(tok, path + "/qwen3-tts-tokenizer-f16.gguf");
    }
    std::lock_guard<std::mutex> lk(g_mtx);
    if (g_tts) return JNI_TRUE;
    auto t = std::make_unique<qwen3_tts::Qwen3TTS>();
    bool ok = t->load_models(path);
    if (!ok || !t->is_loaded()) {
        g_last_error = t->get_error();
        LOGI("load failed: %s", g_last_error.c_str());
        return JNI_FALSE;
    }
    g_tts = std::move(t);
    g_tts->set_progress_callback([](int done, int max) {
        g_done.store(done);
        g_max.store(max);
        if (done % 50 == 0) LOGI("progress %d/%d", done, max);
    });
    LOGI("models loaded from %s (threads=%d)", path.c_str(), threads);
    return JNI_TRUE;
}

JNIEXPORT jboolean JNICALL Java_com_boofangfang_engine_TtsEngine_nativeIsLoaded(JNIEnv*, jclass) {
    return g_tts && g_tts->is_loaded();
}

JNIEXPORT jstring JNICALL Java_com_boofangfang_engine_TtsEngine_nativeError(JNIEnv* env, jclass) {
    std::lock_guard<std::mutex> lk(g_mtx);
    return env->NewStringUTF(g_last_error.c_str());
}

JNIEXPORT jint JNICALL Java_com_boofangfang_engine_TtsEngine_nativeProgress(JNIEnv*, jclass) {
    int max = g_max.load();
    if (max <= 0) return -1;
    return (int)(100LL * g_done.load() / max);
}

// 合成：返回 24kHz 单声道 float PCM；失败返回 null
// instruct：voicedesign 的音色描述；customvoice 也可附带风格描述（可为空）
JNIEXPORT jfloatArray JNICALL Java_com_boofangfang_engine_TtsEngine_nativeSpeak(
        JNIEnv* env, jclass, jint mode, jstring text, jstring refPath, jstring instruct,
        jstring language, jfloat temperature, jint topK, jint maxTokens) {
    if (!g_tts) { g_last_error = "engine not loaded"; return nullptr; }
    std::string txt = j2s(env, text);
    std::string ref = j2s(env, refPath);
    std::string ins = j2s(env, instruct);
    std::string lang = j2s(env, language);

    // 三种模式共用同一套推理，差别只在输入：
    // base = 纯文本；customvoice = 文本 + 参考音频（克隆音色）；voicedesign = 音色描述 + 文本
    std::string prompt = txt;
    if (mode == 2 && !ins.empty()) prompt = ins + "\n" + txt;
    if (mode == 1 && ref.empty()) { g_last_error = "customvoice 模式需要先选择参考音频"; return nullptr; }

    qwen3_tts::tts_params prm;
    prm.temperature = temperature;
    prm.top_k = topK;
    prm.max_audio_tokens = maxTokens > 0 ? maxTokens : 4096;
    prm.n_threads = std::max(1, (int)std::thread::hardware_concurrency() - 1);
    prm.repetition_penalty = 1.05f;
    prm.print_timing = true;
    if (lang == "zh") prm.language_id = 2055;
    else if (lang == "en") prm.language_id = 2050;
    else if (lang == "ja") prm.language_id = 2058;
    else if (lang == "ko") prm.language_id = 2064;

    g_cancel.store(false);
    g_done.store(0);
    g_max.store(0);

    qwen3_tts::tts_result res;
    std::thread work([&] {
        if (mode == 1) res = g_tts->synthesize_with_voice(prompt, ref, prm);
        else res = g_tts->synthesize(prompt, prm);
    });
    // 每 200ms 检查一次取消请求
    while (!work.joinable() || work.wait_for(std::chrono::milliseconds(200)) != std::future_status::ready) {
        if (g_cancel.load()) {
            LOGI("cancel requested, aborting process");
            work.detach();
            std::abort();  // ggml 图计算无法安全中断，直接结束进程由系统回收内存
        }
        break;
    }
    if (work.joinable()) work.join();

    if (!res.success || res.audio.empty()) {
        g_last_error = res.error_msg.empty() ? "synthesis failed" : res.error_msg;
        LOGI("synth failed: %s", g_last_error.c_str());
        return nullptr;
    }
    LOGI("synth ok: %.1fs audio in %lld ms", res.audio.size() / 24000.0, (long long)res.t_total_ms);
    jfloatArray out = env->NewFloatArray((jsize)res.audio.size());
    env->SetFloatArrayRegion(out, 0, (jsize)res.audio.size(), res.audio.data());
    return out;
}

JNIEXPORT void JNICALL Java_com_boofangfang_engine_TtsEngine_nativeCancel(JNIEnv*, jclass) {
    g_cancel.store(true);
}

JNIEXPORT void JNICALL Java_com_boofangfang_engine_TtsEngine_nativeDestroy(JNIEnv*, jclass) {
    std::lock_guard<std::mutex> lk(g_mtx);
    g_tts.reset();
}
}
