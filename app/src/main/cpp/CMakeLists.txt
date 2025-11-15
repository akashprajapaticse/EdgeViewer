# Sets the minimum CMake version required for this project.
cmake_minimum_required(VERSION 3.22.1)

# Declares the project name.
project("edgeviewer")

# --- START OF OPENCV MANUAL LINKING ---

# 1. Define the path to our new libs folder
#    (This is relative to the CMakeLists.txt file)
set(OPENCV_LIBS_DIR ../jniLibs)

# 2. Add the OpenCV .so file as an imported library
#    We use ${CMAKE_SOURCE_DIR} to create an absolute path
add_library(libopencv SHARED IMPORTED)
set_target_properties(libopencv PROPERTIES IMPORTED_LOCATION
        "${CMAKE_SOURCE_DIR}/${OPENCV_LIBS_DIR}/${ANDROID_ABI}/libopencv_java4.so")

# 3. Tell CMake where to find the header files (.hpp)
#    (This path is relative to CMakeLists.txt)
include_directories(./include)

# --- END OF OPENCV MANUAL LINKING ---

# Creates and names your native library
add_library(${CMAKE_PROJECT_NAME} SHARED
        native-lib.cpp)

# Specifies libraries CMake should link to your target library.
target_link_libraries(${CMAKE_PROJECT_NAME}

        # Link our manually defined libopencv
        libopencv

        # Default Android libraries
        android
        log)