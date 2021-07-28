cmake_minimum_required(VERSION 3.4.1)

set(PROJECT_PATH "${CMAKE_SOURCE_DIR}/../../../../..")
set(UTILS_PATH "${PROJECT_PATH}/external/netty-jni-util/src/main/c")
file(GLOB UTILS_SOURCES "${UTILS_PATH}/*.c")
file(GLOB UTILS_HEADERS "${UTILS_PATH}/*.h")
add_library(netty-jni-utils
        STATIC
        ${UTILS_SOURCES}
        ${UTILS_HEADERS})

# common
set(COMMON_PATH "${PROJECT_PATH}/external/netty/transport-native-unix-common/src/main/c/")
file(GLOB COMMON_SOURCES "${COMMON_PATH}/*.c")
file(GLOB COMMON_HEADERS "${COMMON_PATH}/*.h")
add_library(netty-common
        STATIC
        ${COMMON_SOURCES}
        ${COMMON_HEADERS})
target_include_directories(netty-common
        PRIVATE
        ${UTILS_PATH})
target_link_libraries(netty-common netty-jni-utils)

# epoll
set(EPOLL_PATH "${PROJECT_PATH}/external/netty/transport-native-epoll/src/main/c/")
file(GLOB EPOLL_SOURCES "${EPOLL_PATH}/*.c")
file(GLOB EPOLL_HEADERS "${EPOLL_PATH}/*.h")
add_library(netty_transport_native_epoll
        SHARED
        ${EPOLL_SOURCES}
        ${COMMON_HEADERS})
target_include_directories(netty_transport_native_epoll
        PRIVATE
        ${UTILS_PATH}
        ${COMMON_PATH})
target_link_libraries(netty_transport_native_epoll netty-common)