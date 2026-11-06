# 上游推理源码（构建前必须放入本目录）

本工程的 JNI 桥（`jni_bridge.cpp`）调用 `qwen3_tts::Qwen3TTS` 类，接口来自
**ServeurpersoCom/qwentts.cpp**（qwentts.cpp，C++17/GGML，MIT）。由于该仓库在当前网络
环境无法自动拉取（GitHub 403），源码没有打进本仓库，请按下面任一方式补齐后再执行
`scripts/build_engine_jni.sh`：

1. 在能访问 GitHub 的机器上：
   ```bash
   git clone --recurse-submodules https://github.com/ServeurpersoCom/qwentts.cpp.git
   cp qwentts.cpp/src/*.cpp qwentts.cpp/src/*.h  engine/src/main/cpp/
   cp -r qwentts.cpp/ggml     engine/src/main/cpp/ggml
   ```
2. 或从 Hugging Face 模型卡 `Serveurperso/Qwen3-TTS-GGUF` 的 README 找到仓库链接下载。

需要的文件（与本目录 jni_bridge.cpp / CMakeLists.txt 对应）：
`qwen3_tts.h`、`qwen3_tts.cpp`、`text_tokenizer.h/.cpp`、`audio_tokenizer_decoder.h/.cpp`、
`audio_tokenizer_encoder.h/.cpp`、`tts_transformer.h/.cpp`、`gguf_loader.h/.cpp`，以及 `ggml/` 子模块。

## 已验证的接口（来自上游 CLI 文档）
- 加载：目录内 **talker GGUF + qwen-tokenizer-12hz GGUF** 成对存在，自动识别
- 普通/参考音频合成：`synthesize(text)` / `synthesize_with_voice(text, refWavPath, params)`
- 参数：language(2055=zh)、temperature、top_k、repetition_penalty、max_audio_tokens、threads
- 输出：24kHz 单声道 float PCM
- CustomVoice 模式：CLI 用 `--speaker <id>` + `--instruct`；VoiceDesign 模式用 `--instruct`。
  若你拿到的上游版本没有把 speaker/instruct 暴露给库接口，请在 `jni_bridge.cpp` 里按
  `Qwen3TTS` 的公开方法补上（上游 CLI 支持 `--speaker vivian/serena/uncle_fu/dylan/eric/ryan/aiden/ono_anna/sohee`）。
