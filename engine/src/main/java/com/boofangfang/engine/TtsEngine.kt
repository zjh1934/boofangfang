package com.boofangfang.engine

import android.util.Log
import java.io.File
import java.io.FileOutputStream

/** 本地 GGUF 推理引擎封装：JNI -> qwen3-tts.cpp（ggml/CPU） */
object TtsEngine {
    private const val TAG = "BFF"
    private var loaded = false

    init { System.loadLibrary("boofangfang_jni") }

    private external fun nativeLoad(dir: String, threads: Int): Boolean
    private external fun nativeIsLoaded(): Boolean
    private external fun nativeError(): String
    private external fun nativeProgress(): Int
    private external fun nativeSpeak(mode: Int, text: String, refPath: String?, instruct: String?,
                                     language: String, temperature: Float, topK: Int,
                                     maxTokens: Int): FloatArray?
    private external fun nativeCancel()
    private external fun nativeDestroy()

    val isLoaded get() = loaded && nativeIsLoaded()

    /** 当前合成进度 0~100，-1 表示未知 */
    val progress: Int get() = nativeProgress()

    /** modelDir 内需含 manifest.json，指向 talker + tokenizer 两个 GGUF */
    fun load(modelDir: File, threads: Int): Result<Unit> {
        if (loaded) return Result.success(Unit)
        val manifest = File(modelDir, "manifest.json")
        if (!manifest.exists()) return Result.failure(IllegalStateException("缺少 manifest.json"))
        return try {
            if (nativeLoad(modelDir.absolutePath, threads)) { loaded = true; Result.success(Unit) }
            else Result.failure(IllegalStateException(nativeError()))
        } catch (t: Throwable) { Result.failure(t) }
    }

    /** mode: 0=base 标准朗读, 1=customvoice 音色克隆, 2=voicedesign 声音设计 */
    fun speak(mode: Int, text: String, ref: File?, instruct: String?, language: String = "zh",
              temperature: Float = 0.9f, topK: Int = 50,
              maxTokens: Int = 4096): FloatArray? =
        nativeSpeak(mode, text, ref?.absolutePath, instruct, language, temperature, topK, maxTokens)

    fun cancel() = nativeCancel()

    fun saveWav(pcm: FloatArray, out: File, sampleRate: Int = 24000) {
        FileOutputStream(out).use { fos ->
            val dataBytes = pcm.size * 2
            val buf = ByteArray(44)
            fun s(off: Int, v: String) = v.forEachIndexed { i, c -> buf[off + i] = c.code.toByte() }
            fun i32(off: Int, v: Int) { buf[off] = (v and 255).toByte(); buf[off+1] = (v shr 8 and 255).toByte(); buf[off+2] = (v shr 16 and 255).toByte(); buf[off+3] = (v shr 24 and 255).toByte() }
            s(0, "RIFF"); i32(4, 36 + dataBytes); s(8, "WAVE"); s(12, "fmt "); i32(16, 16)
            buf[20] = 1; buf[21] = 0; buf[22] = 1; buf[23] = 0
            i32(24, sampleRate); i32(28, sampleRate * 2); buf[32] = 2; buf[33] = 0; buf[34] = 16; buf[35] = 0
            s(36, "data"); i32(40, dataBytes)
            fos.write(buf)
            val shortBuf = ByteArray(8192)
            var idx = 0
            while (idx < pcm.size) {
                var n = 0
                while (idx < pcm.size && n + 2 <= shortBuf.size) {
                    var v = (pcm[idx].coerceIn(-1f, 1f) * 32767).toInt()
                    if (v < 0) v += 65536
                    shortBuf[n++] = (v and 255).toByte(); shortBuf[n++] = (v shr 8 and 255).toByte()
                    idx++
                }
                fos.write(shortBuf, 0, n)
            }
        }
        Log.i(TAG, "wav saved: ${out.absolutePath}")
    }

    fun destroy() { nativeDestroy(); loaded = false }
}
