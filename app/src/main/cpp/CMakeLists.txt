cmake_minimum_required(VERSION 3.22.1)
project("kittytune_audio_dsp")

set(CMAKE_C_STANDARD 11)
set(CMAKE_CXX_STANDARD 17)

add_library(ebur128 STATIC
    ebur128/ebur128.c
)

target_include_directories(ebur128 PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}/ebur128
    ${CMAKE_CURRENT_SOURCE_DIR}/ebur128/queue
)

target_compile_options(ebur128 PRIVATE
    -O3
)

if(ANDROID)
    target_compile_definitions(ebur128 PRIVATE ANDROID)
endif()

add_library(kittytune_audio_dsp SHARED
    KittyTuneAudioDSP.cpp
)

target_include_directories(kittytune_audio_dsp PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}/ebur128
)

target_compile_options(kittytune_audio_dsp PRIVATE
    -O3
)

target_link_options(kittytune_audio_dsp PRIVATE
    "-Wl,-z,max-page-size=16384"
)

target_link_libraries(kittytune_audio_dsp
    ebur128
    android
    log
)
