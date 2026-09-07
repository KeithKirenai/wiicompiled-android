cmake_minimum_required(VERSION 3.22)
project(mkw_base C CXX)

set(CMAKE_CXX_STANDARD 20)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_EXPORT_COMPILE_COMMANDS ON)

set(CMAKE_CXX_FLAGS_RELEASE "-O3 -DNDEBUG -fPIC")

include(generated/build_shards/shards.cmake)

# Strictly the 72 base function shards to prevent duplicate symbols in Android CMake
add_library(mkw_base_shared STATIC ${MKW_BASE_COMMON_SHARDS})

target_include_directories(mkw_base_shared PRIVATE
    runtime/include
    runtime/src
    runtime/src/core
    runtime/src/hle
    runtime/src/hle/gx
    generated/build_shards/base_common
    generated
)

target_compile_definitions(mkw_base_shared PRIVATE
    MKW_STANDALONE=1
    MKW_RESTRICT=__restrict__
)
