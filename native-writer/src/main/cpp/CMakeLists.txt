cmake_minimum_required(VERSION 3.22.1)
project(native_writer)

add_library(
    native_writer
    SHARED
    native_writer.cpp
)

find_library(log-lib log)

target_link_libraries(
    native_writer
    ${log-lib}
)
