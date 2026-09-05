cmake_minimum_required(VERSION 3.22.1)
project(mkw_shards LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 20)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

get_filename_component(MKW_ROOT "${CMAKE_CURRENT_SOURCE_DIR}/../.." ABSOLUTE)
set(MKW_RUNTIME_DIR "${MKW_ROOT}/runtime")
set(MKW_AURORA_DIR "${MKW_ROOT}/aurora-main")
set(MKW_GENERATED_DIR "${MKW_ROOT}/generated")

include("${MKW_GENERATED_DIR}/build_shards/shards.cmake")

add_library(mkw_base_shared STATIC ${MKW_BASE_COMMON_SHARDS})
target_include_directories(mkw_base_shared PRIVATE
    "${MKW_RUNTIME_DIR}/include"
    "${MKW_RUNTIME_DIR}/src"
    "${MKW_ROOT}"
    "${MKW_AURORA_DIR}/include"
)
target_compile_definitions(mkw_base_shared PRIVATE TARGET_PC NOMINMAX ANDROID NDEBUG)
target_compile_features(mkw_base_shared PRIVATE cxx_std_20)
target_compile_options(mkw_base_shared PRIVATE -O3 -fno-fast-math -ffp-contract=off -w -pipe)
