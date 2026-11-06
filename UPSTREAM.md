# 上游推理源码（已内置）

本仓库 `engine/src/main/cpp/qwen3_tts/` 已包含 qwentts.cpp 风格的
Qwen3-TTS C++17/GGML 推理管线（来自 predict-woo/qwen3-tts.cpp，移植/适配见各文件头）：
qwen3_tts / text_tokenizer / tts_transformer / audio_tokenizer_encoder /
audio_tokenizer_decoder / gguf_loader。ggml 依赖由 CMake 自动拉取，见 ggml/README.md。

注意：上游 load_models() 里写死了 0.6B 的文件名，安卓端 jni_bridge.cpp 在加载前会把
talker/tokenizer 的 GGUF 软链/复制为上游期望的名称，或通过 manifest.json 指向实际文件。
