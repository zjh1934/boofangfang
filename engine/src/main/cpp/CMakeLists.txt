# 在安卓上构建 qwentts.cpp（Qwen3-TTS C++17/GGML 移植）并封装为 JNI 库
cmake_minimum_required(VERSION 3.22)
project(boofangfang_engine LANGUAGES C CXX)
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_POSITION_INDEPENDENT_CODE ON)
set(CMAKE_CXX_FLAGS_RELEASE "${CMAKE_CXX_FLAGS_RELEASE} -O3")
if(CMAKE_SYSTEM_PROCESSOR MATCHES "aarch64|armv8|arm64")
    set(CMAKE_CXX_FLAGS_RELEASE "${CMAKE_CXX_FLAGS_RELEASE} -mcpu=generic+fp16+simd")
endif()

set(QWEN_DIR ${CMAKE_CURRENT_SOURCE_DIR}/qwen3_tts)
set(GGML_SRC ${CMAKE_CURRENT_SOURCE_DIR}/ggml)
set(GGML_TAG "master" CACHE STRING "ggml 版本(tag/branch)")
# 若 ggml 目录为空，则联网拉取（首次构建需要网络；离线环境请把 ggml 源码放到该目录）
if(NOT EXISTS ${GGML_SRC}/CMakeLists.txt)
    message(STATUS "ggml 源码缺失，正在从 https://github.com/ggml-org/ggml.git 拉取（${GGML_TAG}）…")
    find_program(GIT_EXE NAMES git)
    if(NOT GIT_EXE)
        message(FATAL_ERROR "未找到 git，且 ${GGML_SRC} 为空。请手动下载 ggml 源码放入该目录后重试。")
    endif()
    execute_process(COMMAND ${GIT_EXE} clone --depth 1 --branch ${GGML_TAG} https://github.com/ggml-org/ggml.git ${GGML_SRC}
        RESULT_VARIABLE GGML_CLONE)
    if(NOT GGML_CLONE EQUAL 0)
        file(REMOVE_RECURSE ${GGML_SRC})
        message(FATAL_ERROR "ggml 下载失败。请在联网环境执行：git clone --depth 1 https://github.com/ggml-org/ggml.git ${GGML_SRC}")
    endif()
endif()
set(GGML_TAG "master" CACHE STRING "ggml 版本(tag/branch)")
# 若 ggml 目录为空，则联网拉取（首次构建需要网络；离线环境请把 ggml 源码放到该目录）
if(NOT EXISTS ${GGML_SRC}/CMakeLists.txt)
    message(STATUS "ggml 源码缺失，正在从 https://github.com/ggml-org/ggml.git 拉取（${GGML_TAG}）…")
    find_program(GIT_EXE NAMES git)
    if(NOT GIT_EXE)
        message(FATAL_ERROR "未找到 git，且 ${GGML_SRC} 为空。请手动下载 ggml 源码放入该目录后重试。")
    endif()
    execute_process(COMMAND ${GIT_EXE} clone --depth 1 --branch ${GGML_TAG} https://github.com/ggml-org/ggml.git ${GGML_SRC}
        RESULT_VARIABLE GGML_CLONE)
    if(NOT GGML_CLONE EQUAL 0)
        file(REMOVE_RECURSE ${GGML_SRC})
        message(FATAL_ERROR "ggml 下载失败。请在联网环境执行：git clone --depth 1 https://github.com/ggml-org/ggml.git ${GGML_SRC}")
    endif()
endif()

# ---- ggml：只编 CPU 后端 ----
set(GGML_CCACHE OFF CACHE BOOL "" FORCE)
set(GGML_CPU ON CACHE BOOL "" FORCE)
set(GGML_GPROF OFF CACHE BOOL "" FORCE)
set(GGML_LTO OFF CACHE BOOL "" FORCE)
set(GGML_OPENMP OFF CACHE BOOL "" FORCE)
set(GGML_LLAMAFILE OFF CACHE BOOL "" FORCE)
set(GGML_CUDA OFF CACHE BOOL "" FORCE)
set(GGML_METAL OFF CACHE BOOL "" FORCE)
set(GGML_VULKAN OFF CACHE BOOL "" FORCE)
set(GGML_RPC OFF CACHE BOOL "" FORCE)
set(GGML_BLAS OFF CACHE BOOL "" FORCE)
set(GGML_TESTS OFF CACHE BOOL "" FORCE)
set(GGML_TESTS_LINK OFF CACHE BOOL "" FORCE)
set(GGML_EXAMPLES OFF CACHE BOOL "" FORCE)
set(GGML_TOOLS OFF CACHE BOOL "" FORCE)
set(GGML_BACKEND_DL OFF CACHE BOOL "" FORCE)
set(GGML_SHARED OFF CACHE BOOL "" FORCE)
set(GGML_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(GGML_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
add_subdirectory(${GGML_SRC} ${CMAKE_BINARY_DIR}/ggml_build)

# ---- 上游推理源码 ----
set(QWEN_SRCS
    ${QWEN_DIR}/qwen3_tts.cpp
    ${QWEN_DIR}/text_tokenizer.cpp
    ${QWEN_DIR}/tts_transformer.cpp
    ${QWEN_DIR}/audio_tokenizer_encoder.cpp
    ${QWEN_DIR}/audio_tokenizer_decoder.cpp
    ${QWEN_DIR}/gguf_loader.cpp
    ${QWEN_DIR}/coreml_code_predictor_stub.cpp)
add_library(qwen3_tts STATIC ${QWEN_SRCS})
target_include_directories(qwen3_tts PUBLIC ${QWEN_DIR} ${GGML_SRC}/include)
target_compile_definitions(qwen3_tts PUBLIC GGML_USE_CPU)
target_link_libraries(qwen3_tts PUBLIC ggml Threads::Threads)
if(TARGET ggml-cpu)
    target_link_libraries(qwen3_tts PUBLIC ggml-cpu)
endif()

# ---- JNI 桥 ----
add_library(boofangfang_jni SHARED jni_bridge.cpp)
target_link_libraries(boofangfang_jni PRIVATE qwen3_tts android log)
