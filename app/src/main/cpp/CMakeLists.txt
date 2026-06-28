cmake_minimum_required(VERSION 3.22.1)

project("accessmanager-core")

# Add root directory so #include "common/Logger.h" works correctly
include_directories(${CMAKE_CURRENT_SOURCE_DIR})

# Gather source files
set(BRIDGE_SRC bridge/bridge.cpp)
set(BACKEND_SRC backend/LwipBackend.cpp)

# Create the shared library
add_library(${CMAKE_PROJECT_NAME} SHARED
    ${BRIDGE_SRC}
    ${BACKEND_SRC}
)

# Link against Android standard libraries (log for __android_log_print)
find_library(log-lib log)
target_link_libraries(${CMAKE_PROJECT_NAME} ${log-lib})
