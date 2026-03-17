cmake_minimum_required(VERSION 3.22.1)
project(medicell_native)

add_library(medicell_native SHARED
        native-lib.cpp)

# OpenCV headers
set(OPENCV_INCLUDE_DIR
        ${CMAKE_SOURCE_DIR}/opencv/OpenCV-android-sdk/sdk/native/jni/include)

target_include_directories(medicell_native PRIVATE
        ${OPENCV_INCLUDE_DIR})

# Link OpenCV .so from jniLibs
set(OPENCV_SO
        ${CMAKE_SOURCE_DIR}/app/src/main/jniLibs/${ANDROID_ABI}/libopencv_java4.so)

add_library(opencv_java4 SHARED IMPORTED)
set_target_properties(opencv_java4 PROPERTIES
        IMPORTED_LOCATION ${OPENCV_SO})

find_library(log-lib log)

target_link_libraries(medicell_native
        opencv_java4
        ${log-lib})