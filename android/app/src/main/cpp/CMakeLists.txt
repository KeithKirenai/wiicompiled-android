cmake_minimum_required(VERSION 3.22.1)
project(mkw_android LANGUAGES C CXX ASM)

set(CMAKE_CXX_STANDARD 20)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

get_filename_component(MKW_ROOT "${CMAKE_CURRENT_SOURCE_DIR}/../../../../../Wiicompiled" ABSOLUTE)
set(MKW_RUNTIME_DIR "${MKW_ROOT}/runtime")

include_directories(
    "${MKW_RUNTIME_DIR}/include"
    "${MKW_RUNTIME_DIR}/src"
    "${MKW_ROOT}"
)

# Core platform, coroutines, touch input, and disc extraction sources
set(ANDROID_SOURCES
    android_main.cpp
    "${MKW_RUNTIME_DIR}/src/android_touch_input.cpp"
    "${MKW_RUNTIME_DIR}/src/android_disc_extractor.cpp"
    "${MKW_RUNTIME_DIR}/src/platform/host_platform.cpp"
    "${MKW_RUNTIME_DIR}/src/host_context.cpp"
    "${MKW_RUNTIME_DIR}/src/platform/android/co_switch_android.S"
    "${MKW_RUNTIME_DIR}/third_party/pugixml/pugixml.cpp"
)

add_library(mkw_android SHARED
    ${ANDROID_SOURCES}
)

target_compile_definitions(mkw_android PRIVATE
    ANDROID
    NOMINMAX
    TARGET_PC
)

target_link_libraries(mkw_android PRIVATE
    android
    log
    vulkan
)