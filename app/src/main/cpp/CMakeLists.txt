cmake_minimum_required(VERSION 3.22.1)
project("uinputpad")

add_library(
    uinputpad
    SHARED
    uinput_gamepad.cpp
)

find_library(log-lib log)

target_link_libraries(
    uinputpad
    ${log-lib}
)
