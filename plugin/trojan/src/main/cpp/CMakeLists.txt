cmake_minimum_required(VERSION 3.6)
project(trojan)
add_definitions(-DENABLE_TLS13_CIPHERSUITES)

add_library(trojan_shared
        trojan/src/core/authenticator.cpp
        trojan/src/core/config.cpp
        trojan/src/core/log.cpp
        trojan/src/core/service.cpp
        trojan/src/core/version.cpp
        trojan/src/proto/socks5address.cpp
        trojan/src/proto/trojanrequest.cpp
        trojan/src/proto/udppacket.cpp
        trojan/src/session/clientsession.cpp
        trojan/src/session/forwardsession.cpp
        trojan/src/session/natsession.cpp
        trojan/src/session/serversession.cpp
        trojan/src/session/session.cpp
        trojan/src/session/udpforwardsession.cpp
        trojan/src/ssl/ssldefaults.cpp
        trojan/src/ssl/sslsession.cpp)
target_include_directories(trojan_shared PRIVATE
        ${CMAKE_SOURCE_DIR}/igniter-libs/include
        ${CMAKE_SOURCE_DIR}/igniter-libs/include/${ANDROID_ABI}
        ${CMAKE_SOURCE_DIR}/trojan/src)
target_link_libraries(trojan_shared
        ${CMAKE_SOURCE_DIR}/igniter-libs/lib/${ANDROID_ABI}/libboost_program_options.a
        ${CMAKE_SOURCE_DIR}/igniter-libs/lib/${ANDROID_ABI}/libboost_system.a
        ${CMAKE_SOURCE_DIR}/igniter-libs/lib/${ANDROID_ABI}/libssl.a
        ${CMAKE_SOURCE_DIR}/igniter-libs/lib/${ANDROID_ABI}/libcrypto.a)

add_executable(libtrojan.so
        trojan/src/main.cpp)
target_include_directories(libtrojan.so PRIVATE
        ${CMAKE_SOURCE_DIR}/igniter-libs/include
        ${CMAKE_SOURCE_DIR}/igniter-libs/include/${ANDROID_ABI}
        ${CMAKE_SOURCE_DIR}/trojan/src)
target_link_libraries(libtrojan.so trojan_shared)