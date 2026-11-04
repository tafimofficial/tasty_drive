# For more information about using CMake with Android Studio, read the
# documentation: https://d.android.com/studio/projects/add-native-code.html

cmake_minimum_required(VERSION 3.22.1)

project("tastydrive")

add_library(
        tastydrive
        SHARED
        src/main/cpp/tastydrive.c
)

find_library(
        log-lib
        log
)

target_link_libraries(
        tastydrive
        ${log-lib}
)

