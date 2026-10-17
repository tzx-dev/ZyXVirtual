cmake_minimum_required(VERSION 3.22.1)
project(zyx_qemu_jni C)

add_library(zyx-qemu-jni SHARED zyx_qemu_jni.c)
target_include_directories(zyx-qemu-jni PRIVATE ${CMAKE_CURRENT_SOURCE_DIR})
target_compile_features(zyx-qemu-jni PRIVATE c_std_11)
target_compile_options(zyx-qemu-jni PRIVATE -Wall -Wextra -Werror)
target_link_libraries(zyx-qemu-jni PRIVATE dl)
