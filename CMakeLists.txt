cmake_minimum_required(VERSION 3.20)
project(owncut_engine VERSION 0.1.0 LANGUAGES CXX)

option(OWNCUT_BUILD_TESTS "Build portable engine unit tests" ON)

add_library(owncut_engine
    engine/core/src/time.cpp
    engine/timeline/src/timeline.cpp
    engine/commands/src/command_history.cpp
    engine/project/src/project.cpp)
target_include_directories(owncut_engine PUBLIC
    engine/core/include
    engine/timeline/include
    engine/commands/include
    engine/project/include)
target_compile_features(owncut_engine PUBLIC cxx_std_20)
set_target_properties(owncut_engine PROPERTIES POSITION_INDEPENDENT_CODE ON)
if(CMAKE_CXX_COMPILER_ID MATCHES "Clang|GNU")
    target_compile_options(owncut_engine PRIVATE -Wall -Wextra -Wpedantic -Wconversion -Wsign-conversion)
endif()

if(ANDROID)
    add_library(owncut_android SHARED platform/android/jni/editor_jni.cpp)
    target_link_libraries(owncut_android PRIVATE owncut_engine)
    target_compile_features(owncut_android PRIVATE cxx_std_20)
endif()

if(OWNCUT_BUILD_TESTS)
    enable_testing()
    add_executable(owncut_core_tests tests/unit/core_tests.cpp)
    target_link_libraries(owncut_core_tests PRIVATE owncut_engine)
    add_test(NAME owncut_core_tests COMMAND owncut_core_tests)
endif()
