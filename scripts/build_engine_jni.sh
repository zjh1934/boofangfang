#!/usr/bin/env bash
# 在装有 Android NDK + CMake 的机器上编译原生引擎（Android/arm64-v8a）
# 用法: ANDROID_NDK=/path/to/ndk ./scripts/build_engine_jni.sh
set -e
: "${ANDROID_NDK:?请设置 ANDROID_NDK}"
cd "$(dirname "$0")/.."
ENGINE=engine/src/main/cpp
[ -d "$ENGINE/ggml/src" ] || git clone --depth 1 https://github.com/ggml-org/ggml.git "$ENGINE/ggml"
# 上游推理源码（三模式 CLI 版）放到 engine/src/main/cpp/ 下：
#   qwen3_tts.h qwen3_tts.cpp text_tokenizer.cpp/h audio_tokenizer_decoder.cpp/h
#   audio_tokenizer_encoder.cpp/h tts_transformer.cpp/h gguf_loader.cpp/h
# 获取方式见 engine/src/main/cpp/UPSTREAM.md
ls "$ENGINE"/qwen3_tts.cpp >/dev/null || { echo "缺少上游源码，见 engine/src/main/cpp/UPSTREAM.md"; exit 1; }
cmake -S "$ENGINE" -B build-engine \
  -DCMAKE_TOOLCHAIN_FILE="$ANDROID_NDK/build/cmake/android.toolchain.cmake" \
  -DANDROID_ABI=arm64-v8a -DANDROID_PLATFORM=android-26 -DCMAKE_BUILD_TYPE=Release
cmake --build build-engine -j"$(nproc)"
mkdir -p app/src/main/jniLibs/arm64-v8a
cp build-engine/libboofangfang_jni.so app/src/main/jniLibs/arm64-v8a/
cp "$ANDROID_NDK/toolchains/llvm/prebuilt/linux-x86_64/sysroot/usr/lib/aarch64-linux-android/libc++_shared.so" app/src/main/jniLibs/arm64-v8a/ 2>/dev/null || true
echo "产物: app/src/main/jniLibs/arm64-v8a/libboofangfang_jni.so"
