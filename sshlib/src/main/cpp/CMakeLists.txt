cmake_minimum_required(VERSION 3.22.1)

project("orcterm-core")

include_directories(${CMAKE_SOURCE_DIR}/include)

set(OPENSSL_ROOT_DIR ${CMAKE_SOURCE_DIR})
set(OPENSSL_USE_STATIC_LIBS TRUE)
set(OPENSSL_INCLUDE_DIR ${CMAKE_SOURCE_DIR}/include)
set(OPENSSL_SSL_LIBRARY ${CMAKE_SOURCE_DIR}/libs/${ANDROID_ABI}/libssl.a)
set(OPENSSL_CRYPTO_LIBRARY ${CMAKE_SOURCE_DIR}/libs/${ANDROID_ABI}/libcrypto.a)
find_package(OpenSSL REQUIRED)

set(LIBSSH2_SRC_DIR ${CMAKE_SOURCE_DIR}/libssh2_official)
include_directories(${LIBSSH2_SRC_DIR}/include)

set(BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)
set(BUILD_STATIC_LIBS ON CACHE BOOL "" FORCE)
set(BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(BUILD_TESTING OFF CACHE BOOL "" FORCE)
set(LIBSSH2_BUILD_DOCS OFF CACHE BOOL "" FORCE)
set(LIBSSH2_DISABLE_INSTALL ON CACHE BOOL "" FORCE)
set(LIBSSH2_USE_PKGCONFIG OFF CACHE BOOL "" FORCE)
set(ENABLE_ZLIB_COMPRESSION OFF CACHE BOOL "" FORCE)
set(CRYPTO_BACKEND "OpenSSL" CACHE STRING "" FORCE)

add_subdirectory(${LIBSSH2_SRC_DIR} ${CMAKE_BINARY_DIR}/libssh2_build)

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
    libssh2_static
    OpenSSL::SSL
    OpenSSL::Crypto
    ${log-lib}
    z
)
