cmake_minimum_required(VERSION 3.22.1)
project("kittenTTS")

# espeak-ng pre-built shared library
add_library(espeak-ng SHARED IMPORTED)
set_target_properties(espeak-ng PROPERTIES
    IMPORTED_LOCATION ${CMAKE_SOURCE_DIR}/../jniLibs/${ANDROID_ABI}/libespeak-ng.so
)

# Our JNI library
add_library(espeak-jni SHARED
    espeak-jni.c
    espeak-bridge.c
)

target_include_directories(espeak-jni PRIVATE
    ${CMAKE_SOURCE_DIR}/include
    ${CMAKE_SOURCE_DIR}
)

target_link_libraries(espeak-jni
    espeak-ng
    log
)
