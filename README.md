# 播放播放（Boofangfang）

基于 **Qwen3-Talker 1.7B（GGUF Q8_0，本地离线）** 的安卓有声书 / 朗读 App：
**一个模型，三种模式**，上方可喂电子书或自定义输入，下方产出并播放音频。

## 功能
- 🔀 **三种模式切换**（共用 `qwen-tokenizer-12hz-Q8_0.gguf`，只换 talker）
  | 模式 | 模型文件 | 说明 |
  |---|---|---|
  | 标准朗读 base | `qwen-talker-1.7b-base-Q8_0.gguf` | 内置音色/方言零样本朗读 |
  | 音色克隆 customvoice | `qwen-talker-1.7b-customvoice-Q8_0.gguf` | 上传参考音频克隆声音 |
  | 声音设计 voicedesign | `qwen-talker-1.7b-voicedesign-Q8_0.gguf` | 一句话描述生成新音色 |
- 📖 **电子书**：导入 `.txt / .md / .epub`（epub 按 spine 顺序抽取正文），分页、翻页、进度条
- ✍️ **自定义输入框**：直接输入文字；voicedesign 模式下输入音色描述
- 🔊 **产出音频**：24kHz WAV 保存到 `Android/data/com.boofangfang.app/files/outputs/`，支持播放/暂停、停止

## 模型放置（手机）
把 `Download/千问模型库` 里 4 个 gguf 整理为：

```
Android/data/com.boofangfang.app/files/models/
├── base/{qwen-talker-1.7b-base-Q8_0.gguf, qwen-tokenizer-12hz-Q8_0.gguf, manifest.json}
├── customvoice/{qwen-talker-1.7b-customvoice-Q8_0.gguf, tokenizer..., manifest.json}
└── voicedesign/{qwen-talker-1.7b-voicedesign-Q8_0.gguf, tokenizer..., manifest.json}
```

一键整理（电脑上运行）：
```bash
python3 scripts/prepare_models.py "/sdcard/Download/千问模型库" ./models
```

## 构建
```bash
# 1) 安装 Android SDK (API 35) + NDK 26+，设置 ANDROID_HOME / ANDROID_NDK
# 2) 编译原生引擎（首次配置会自动下载 ggml）
ANDROID_NDK=/path/to/ndk ./scripts/build_engine_jni.sh
# 3) 打 APK
./gradlew :app:assembleRelease
# app/build/outputs/apk/release/app-release-unsigned.apk
```
签名：`apksigner sign --ks my.keystore app-release-unsigned.apk`

## 项目结构
```
app/       Kotlin UI（模式切换 / 电子书 / 输入 / 播放 / 输出列表）
engine/    JNI 封装 TtsEngine.kt + jni_bridge.cpp（调用 qwen3_tts.cpp）
scripts/   模型整理、原生编译脚本
```

## 已知限制
- 1.7B Q8_0 约 2.4GB，需 8GB+ 内存的中高端手机，纯 CPU 推理，长文本按段合成
- APK 需在本机用 Android SDK + NDK 构建（见上），本仓库不含编译产物与模型权重；上游推理源码已随仓库提供
- 上游模型：Qwen3-TTS（Apache 2.0）；GGUF 工具：qwentts.cpp（MIT）；ggml（MIT）
