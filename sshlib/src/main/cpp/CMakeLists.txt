cmake_minimum_required(VERSION 3.22.1)

project("orcterm-core")

# ==============================================================================
# 1. OpenSSL Setup (Prebuilt)
# ==============================================================================
include_directories(${CMAKE_SOURCE_DIR}/include)

add_library(ssl STATIC IMPORTED)
set_target_properties(ssl PROPERTIES IMPORTED_LOCATION "${CMAKE_SOURCE_DIR}/libs/${ANDROID_ABI}/libssl.a")

add_library(crypto STATIC IMPORTED)
set_target_properties(crypto PROPERTIES IMPORTED_LOCATION "${CMAKE_SOURCE_DIR}/libs/${ANDROID_ABI}/libcrypto.a")

# ==============================================================================
# 2. Libssh2 Setup (Build from Source)
# ==============================================================================
# We compile libssh2 directly here instead of using add_subdirectory to avoid
# configuration complexity of the full libssh2 project.

set(LIBSSH2_SRC_DIR ${CMAKE_SOURCE_DIR}/libssh2_official)

include_directories(${LIBSSH2_SRC_DIR}/include)

# List of source files for libssh2 (Core + Packet + SFTP + etc.)
file(GLOB LIBSSH2_SOURCES 
    "${LIBSSH2_SRC_DIR}/src/*.c"
)

# Configuration definitions for libssh2
add_definitions(
    -DLIBSSH2_OPENSSL  # Use OpenSSL backend
    -DHAVE_UNISTD_H
    -DHAVE_INTTYPES_H
    -DHAVE_STDLIB_H
    -DHAVE_SYS_SELECT_H
    -DHAVE_SYS_SOCKET_H
    -DHAVE_SYS_IOCTL_H
    -DHAVE_SYS_TIME_H
    -DHAVE_SYS_UN_H
    -DHAVE_O_NONBLOCK
    -DHAVE_SELECT
    -DHAVE_SOCKET
    -DHAVE_INET_ADDR
    -DHAVE_GETTIMEOFDAY
    -DHAVE_SNPRINTF
)

add_library(ssh2_static STATIC ${LIBSSH2_SOURCES})

# Link OpenSSL to Libssh2
target_link_libraries(ssh2_static crypto ssl)

# ==============================================================================
# 3. OrcTerm JNI Bridge
# ==============================================================================
add_library(orcterm-jni SHARED
    ssh_bridge.c
)

# Enable Real SSH implementation in ssh_bridge.c
add_definitions(-DORCTERM_REAL_SSH)

find_library(log-lib log)

target_link_libraries(orcterm-jni
    ssh2_static
    ssl
    crypto
    ${log-lib}
    z
)
