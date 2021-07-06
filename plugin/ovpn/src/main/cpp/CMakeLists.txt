cmake_minimum_required(VERSION 3.4.1)
# Git version string

include(GetGitRevisionDescription.cmake)
git_describe(OPENVPN2_GIT "${CMAKE_CURRENT_SOURCE_DIR}/openvpn" "--tags" "--always" "--long")
git_describe(OPENVPN3_GIT "${CMAKE_CURRENT_SOURCE_DIR}/openvpn3" "--tags" "--always" "--long")
message("OpenVPN 2.x version ${OPENVPN2_GIT}")
message("OpenVPN 3.x version ${OPENVPN3_GIT}")


# Set mbedtls options
OPTION(ENABLE_PROGRAMS "" OFF)
OPTION(USE_SHARED_MBEDTLS_LIBRARY "" OFF)
OPTION(ENABLE_TESTING "" OFF)

# Own options
OPTION(OPENVPN2MBED "Use mbed TLS for OpenVPN2" OFF)
OPTION(OPENVPN3OSSL "Use OpenSSL for OpenVPN3" ON)
SET(OPENVPN3OSSL ON)

# STATIC or SHARED
SET(SSLLIBTYPE STATIC)
SET(OPENSSL_PATH "openssl")

#add_subdirectory(lzo)
include(tools.cmake)
include(lzo.cmake)
include(lz4.cmake)
include(openssl/openssl.cmake)

if(NOT ${OPENVPN3OSSL} OR ${OPENVPN2MBED})
    add_subdirectory(mbedtls)
endif()

    # Super hacky way to determine flavour
#-DCMAKE_LIBRARY_OUTPUT_DIRECTORY=/Users/arne/software/icsopenvpn/main/build/intermediates/cmake/normal/debug/obj/arm64-v8a
if (NOT ${CMAKE_LIBRARY_OUTPUT_DIRECTORY} MATCHES "build/intermediates/cmake/.*skeleton.*/")
    FIND_PACKAGE(SWIG 3.0 REQUIRED)

    add_custom_command(OUTPUT "ovpncli_wrap.cxx"
            COMMAND ${CMAKE_COMMAND} -E make_directory ovpn3
            COMMAND ${SWIG_EXECUTABLE} -outdir ovpn3
            -c++
            -java -package net.openvpn.ovpn3
            -outcurrentdir
            -I${CMAKE_SOURCE_DIR}/openvpn3/client
            -I${CMAKE_SOURCE_DIR}/openvpn3
            ${CMAKE_SOURCE_DIR}/openvpn3/javacli/ovpncli.i)


    # proper way bunt unfinished
    #INCLUDE(${SWIG_USE_FILE})
    #SET_SOURCE_FILES_PROPERTIES(openvpn3/javacli/ovpncli.i PROPERTIES SWIG_FLAGS "-package net.openvpn.ovpn3")
    #SET_SOURCE_FILES_PROPERTIES(openvpn3/javacli/ovpncli.i PROPERTIES CPLUSPLUS ON)
    #SWIG_ADD_MODULE(ovpen3cli java openvpn3/javacli/ovpncli.i)


    set(ovpn3_SRCS
            openvpn3/client/ovpncli.cpp
            ovpncli_wrap.cxx)

    add_library(ovpn3 SHARED ${ovpn3_SRCS})

    target_include_directories(ovpn3 PUBLIC
            ${CMAKE_CURRENT_SOURCE_DIR}/lzo/include
            ${CMAKE_CURRENT_SOURCE_DIR}/openvpn3
            ${CMAKE_CURRENT_SOURCE_DIR}/asio/asio/include
            ${CMAKE_CURRENT_SOURCE_DIR}/openvpn3/client
            ${CMAKE_CURRENT_SOURCE_DIR}/mbedtls/include
            )

    if (${OPENVPN3OSSL})
        target_compile_definitions(ovpn3 PRIVATE
                -DUSE_OPENSSL
                )
        target_link_libraries(ovpn3 crypto ssl lzo lz4)
    else ()
        target_compile_definitions(ovpn3 PRIVATE
                -DUSE_MBEDTLS
                )
        target_link_libraries(ovpn3 mbedtls mbedx509 mbedcrypto lzo lz4)
    endif ()

    target_compile_options(ovpn3 PRIVATE -std=c++1y)
    target_compile_definitions(ovpn3 PRIVATE
            -DHAVE_CONFIG_H
            -DHAVE_LZO
            -DHAVE_LZ4
            -DASIO_STANDALONE
            -DUSE_ASIO
            -DGIT_VERSION_STRING=\"${OPENVPN3_GIT}\"
            -DNO_ROUTE_EXCLUDE_EMULATION
            -DOPENVPN_SHOW_SESSION_TOKEN
            -DOPENSSL_API_COMPAT=0x10200000L

            )
else ()
    message("Not budiling OpenVPN for output dir ${CMAKE_LIBRARY_OUTPUT_DIRECTORY}")
endif ()

add_library(opvpnutil SHARED opvpnutil/jniglue.c opvpnutil/scan_ifs.c)
target_compile_definitions(opvpnutil PRIVATE -DTARGET_ARCH_ABI=\"${ANDROID_ABI}\"
        -DOPENVPN2_GIT_REVISION=\"${OPENVPN2_GIT}\"
        -DOPENVPN3_GIT_REVISION=\"${OPENVPN3_GIT}\"
        )
target_link_libraries(opvpnutil log)


if (NOT ${CMAKE_LIBRARY_OUTPUT_DIRECTORY} MATCHES "build/intermediates/cmake/.*skeleton.*/")
    add_library(osslspeedtest SHARED opvpnutil/sslspeed.c)
    target_link_libraries(osslspeedtest log crypto ssl)
else ()
    message("Not budiling SSLSpeedTest for output dir ${CMAKE_LIBRARY_OUTPUT_DIRECTORY}")
endif ()

# The magic Jellybean keystore signing hack. Beware dragons and dlsyms magic ahead
add_library(jbcrypto SHARED jbcrypto/jbcrypto.cpp)
target_link_libraries(jbcrypto log dl)
target_compile_options(jbcrypto PRIVATE)

set(openvpn_srcs
        src/compat/compat-basename.c
        src/compat/compat-daemon.c
        src/compat/compat-dirname.c
        src/compat/compat-gettimeofday.c
        src/compat/compat-inet_ntop.c
        src/compat/compat-inet_pton.c
        src/compat/compat-lz4.c
        src/openvpn/argv.c
        src/openvpn/auth_token.c
        src/openvpn/base64.c
        src/openvpn/buffer.c
        src/openvpn/clinat.c
        src/openvpn/console.c
        src/openvpn/console_builtin.c
        src/openvpn/crypto.c
        src/openvpn/crypto_openssl.c
        src/openvpn/crypto_mbedtls.c
        src/openvpn/cryptoapi.c
        src/openvpn/dhcp.c
        src/openvpn/error.c
        src/openvpn/event.c
        src/openvpn/env_set.c
        src/openvpn/fdmisc.c
        src/openvpn/forward.c
        src/openvpn/fragment.c
        src/openvpn/gremlin.c
        src/openvpn/helper.c
        src/openvpn/httpdigest.c
        src/openvpn/init.c
        src/openvpn/interval.c
        src/openvpn/list.c
        src/openvpn/lladdr.c
        src/openvpn/lzo.c
        src/openvpn/manage.c
        src/openvpn/mbuf.c
        src/openvpn/misc.c
        src/openvpn/mroute.c
        src/openvpn/mss.c
        src/openvpn/mstats.c
        src/openvpn/mtcp.c
        src/openvpn/mtu.c
        src/openvpn/mudp.c
        src/openvpn/multi.c
        src/openvpn/ntlm.c
        src/openvpn/occ.c
        src/openvpn/openvpn.c
        src/openvpn/options.c
        src/openvpn/otime.c
        src/openvpn/packet_id.c
        src/openvpn/perf.c
        src/openvpn/pf.c
        src/openvpn/ping.c
        src/openvpn/pkcs11.c
        src/openvpn/pkcs11_openssl.c
        src/openvpn/platform.c
        src/openvpn/plugin.c
        src/openvpn/pool.c
        src/openvpn/proto.c
        src/openvpn/proxy.c
        src/openvpn/ps.c
        src/openvpn/push.c
        src/openvpn/reliable.c
        src/openvpn/route.c
        src/openvpn/run_command.c
        src/openvpn/schedule.c
        src/openvpn/session_id.c
        src/openvpn/shaper.c
        src/openvpn/sig.c
        src/openvpn/socket.c
        src/openvpn/socks.c
        src/openvpn/ssl.c
        src/openvpn/ssl_openssl.c
        src/openvpn/ssl_mbedtls.c
        src/openvpn/ssl_ncp.c
        src/openvpn/ssl_util.c
        src/openvpn/ssl_verify.c
        src/openvpn/ssl_verify_openssl.c
        src/openvpn/ssl_verify_mbedtls.c
        src/openvpn/status.c
        src/openvpn/tls_crypt.c
        src/openvpn/tun.c
        src/openvpn/vlan.c
        src/openvpn/comp-lz4.c
        src/openvpn/comp.c
        src/openvpn/compstub.c
        )

PREPEND(openvpn_srcs_with_path "openvpn" ${openvpn_srcs})

add_library(openvpn SHARED ${openvpn_srcs_with_path})

target_include_directories(openvpn PRIVATE
        openvpn-config
        openvpn/src/compat
        openvpn/include
        mbedtls/include
        lzo/include
        openvpn
        )
target_compile_definitions(openvpn PRIVATE
        -DHAVE_CONFIG_H
        -DCONFIGURE_GIT_REVISION=\"${OPENVPN2_GIT}\"
        -DCONFIGURE_GIT_FLAGS=\"\"
        -DTARGET_ABI=\"${ANDROID_ABI}\"
        -DOPENSSL_API_COMPAT=0x11000000L
        )

if (${OPENVPN2MBED})
    target_compile_definitions(openvpn PRIVATE
            -DENABLE_CRYPTO_MBEDTLS=1
            )
    target_link_libraries(openvpn mbedtls mbedx509 mbedcrypto lzo)
else ()
    target_compile_definitions(openvpn PRIVATE
            -DENABLE_CRYPTO_OPENSSL=1
            )
    target_link_libraries(openvpn crypto ssl lzo)
endif ()

add_executable(libovpnexec.so minivpn/minivpn.c)
target_compile_options(libovpnexec.so PRIVATE -fPIE)
target_link_libraries(libovpnexec.so PRIVATE openvpn -fPIE -pie)

add_executable(pie_openvpn.${ANDROID_ABI} minivpn/minivpn.c)
target_compile_options(pie_openvpn.${ANDROID_ABI} PRIVATE -fPIE)
target_link_libraries(pie_openvpn.${ANDROID_ABI} PRIVATE openvpn -fPIE -pie)

add_executable(nopie_openvpn.${ANDROID_ABI} minivpn/minivpn.c)
target_link_libraries(nopie_openvpn.${ANDROID_ABI} PRIVATE openvpn)

# Hack to copy OpenVPN binaries to assets directory
SET(OVPN_ASSET_DIR ${CMAKE_SOURCE_DIR}/../../../build/ovpnassets)

add_custom_target(makeassetdir ALL
        COMMAND ${CMAKE_COMMAND} -E make_directory ${OVPN_ASSET_DIR})

add_custom_command(TARGET nopie_openvpn.${ANDROID_ABI} POST_BUILD
        COMMAND
        ${CMAKE_COMMAND} -E copy
        ${CMAKE_CURRENT_BINARY_DIR}/nopie_openvpn.${ANDROID_ABI}
        ${OVPN_ASSET_DIR}
        )

add_custom_command(TARGET pie_openvpn.${ANDROID_ABI} POST_BUILD
        COMMAND
        ${CMAKE_COMMAND} -E copy
        ${CMAKE_CURRENT_BINARY_DIR}/pie_openvpn.${ANDROID_ABI}
        ${OVPN_ASSET_DIR}
        )

# Hack that these targets are really executed
add_dependencies(opvpnutil pie_openvpn.${ANDROID_ABI} nopie_openvpn.${ANDROID_ABI})
add_dependencies(pie_openvpn.${ANDROID_ABI} makeassetdir)
add_dependencies(nopie_openvpn.${ANDROID_ABI} makeassetdir)
