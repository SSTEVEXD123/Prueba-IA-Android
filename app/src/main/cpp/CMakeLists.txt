cmake_minimum_required(VERSION 3.22.1)
project(jarvis_llm)

# =====================================================================
# llama.cpp Integration para Android
# Compilar llama.cpp como librería compartida (.so)
# =====================================================================

set(LLAMA_CPP_DIR ${CMAKE_SOURCE_DIR}/llama.cpp)

# Flags de optimización para ARM64
if(ANDROID_ABI STREQUAL "arm64-v8a")
    set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} -O3 -DNDEBUG -march=armv8-a+dotprod+fp16")
    set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -O3 -DNDEBUG -march=armv8-a+dotprod+fp16")
    add_definitions(-DGGML_USE_LLAMAFILE)
endif()

# Incluir headers de llama.cpp
include_directories(
    ${LLAMA_CPP_DIR}
    ${LLAMA_CPP_DIR}/ggml/include
    ${LLAMA_CPP_DIR}/include
    ${CMAKE_SOURCE_DIR}/include
)

# Archivos fuente de llama.cpp (núcleo)
file(GLOB LLAMA_SOURCES
    ${LLAMA_CPP_DIR}/src/*.cpp
    ${LLAMA_CPP_DIR}/ggml/src/*.c
    ${LLAMA_CPP_DIR}/ggml/src/*.cpp
)

# Librería principal de llama
add_library(llama SHARED ${LLAMA_SOURCES})

target_link_libraries(llama
    android
    log
)

# =====================================================================
# JNI Bridge - Comunicación Java/Kotlin <-> C++
# =====================================================================
add_library(
    jarvis_jni
    SHARED
    src/main/cpp/jarvis_jni.cpp
    src/main/cpp/model_manager.cpp
    src/main/cpp/inference_engine.cpp
)

target_include_directories(jarvis_jni PRIVATE
    ${LLAMA_CPP_DIR}
    ${LLAMA_CPP_DIR}/include
    ${LLAMA_CPP_DIR}/ggml/include
    ${CMAKE_SOURCE_DIR}/include
)

target_link_libraries(jarvis_jni
    llama
    android
    log
    z
)
