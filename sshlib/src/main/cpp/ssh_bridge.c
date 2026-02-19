#include <jni.h>
#include <string.h>
#include <android/log.h>
#include <stdlib.h>
#include <stdio.h>
#include <sys/socket.h>
#include <netinet/in.h>
#include <arpa/inet.h>
#include <unistd.h>
#include <fcntl.h>
#include <netdb.h>
#include <openssl/evp.h>
#include <openssl/pem.h>
#include <openssl/err.h>
#include <sys/stat.h>

// Include libssh2 headers
#include "libssh2.h"
#include "libssh2_sftp.h"

#define TAG "SshNative"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

static void build_perm_string(unsigned long perm, int is_dir, char *out) {
    out[0] = is_dir ? 'd' : '-';
    out[1] = (perm & S_IRUSR) ? 'r' : '-';
    out[2] = (perm & S_IWUSR) ? 'w' : '-';
    out[3] = (perm & S_IXUSR) ? 'x' : '-';
    out[4] = (perm & S_IRGRP) ? 'r' : '-';
    out[5] = (perm & S_IWGRP) ? 'w' : '-';
    out[6] = (perm & S_IXGRP) ? 'x' : '-';
    out[7] = (perm & S_IROTH) ? 'r' : '-';
    out[8] = (perm & S_IWOTH) ? 'w' : '-';
    out[9] = (perm & S_IXOTH) ? 'x' : '-';
    out[10] = '\0';
}

/**
 * 上下文结构体，用于保存会话数据
 * 在 Java 层以 long (指针) 形式持有。
 */
typedef struct {
    int socket_fd;
    LIBSSH2_SESSION *session;
    LIBSSH2_CHANNEL *channel; // 当前活动的 Shell 通道
} SshContext;

typedef struct {
    char *data;
    size_t len;
    size_t cap;
} StrBuf;

static void sb_init(StrBuf *sb, size_t initial_cap) {
    sb->cap = initial_cap < 256 ? 256 : initial_cap;
    sb->len = 0;
    sb->data = (char *)malloc(sb->cap);
    if (sb->data) sb->data[0] = 0;
}

static void sb_free(StrBuf *sb) {
    if (sb->data) free(sb->data);
    sb->data = NULL;
    sb->len = 0;
    sb->cap = 0;
}

static int sb_ensure(StrBuf *sb, size_t add_len) {
    if (!sb->data) return -1;
    size_t need = sb->len + add_len + 1;
    if (need <= sb->cap) return 0;
    size_t new_cap = sb->cap * 2;
    while (new_cap < need) new_cap *= 2;
    char *p = (char *)realloc(sb->data, new_cap);
    if (!p) return -1;
    sb->data = p;
    sb->cap = new_cap;
    return 0;
}

static int sb_append(StrBuf *sb, const char *s) {
    if (!s) return 0;
    size_t l = strlen(s);
    if (sb_ensure(sb, l) != 0) return -1;
    memcpy(sb->data + sb->len, s, l);
    sb->len += l;
    sb->data[sb->len] = 0;
    return 0;
}

static int sb_append_char(StrBuf *sb, char c) {
    if (sb_ensure(sb, 1) != 0) return -1;
    sb->data[sb->len++] = c;
    sb->data[sb->len] = 0;
    return 0;
}

static int sb_append_escaped(StrBuf *sb, const char *s) {
    if (!s) return 0;
    const unsigned char *p = (const unsigned char *)s;
    while (*p) {
        unsigned char c = *p++;
        switch (c) {
            case '\"': if (sb_append(sb, "\\\"") != 0) return -1; break;
            case '\\': if (sb_append(sb, "\\\\") != 0) return -1; break;
            case '\b': if (sb_append(sb, "\\b") != 0) return -1; break;
            case '\f': if (sb_append(sb, "\\f") != 0) return -1; break;
            case '\n': if (sb_append(sb, "\\n") != 0) return -1; break;
            case '\r': if (sb_append(sb, "\\r") != 0) return -1; break;
            case '\t': if (sb_append(sb, "\\t") != 0) return -1; break;
            default:
                if (c < 0x20) {
                    char buf[7];
                    snprintf(buf, sizeof(buf), "\\u%04x", c);
                    if (sb_append(sb, buf) != 0) return -1;
                } else {
                    if (sb_append_char(sb, (char)c) != 0) return -1;
                }
        }
    }
    return 0;
}

static int map_knownhost_key_type(int type) {
    switch (type) {
        case LIBSSH2_HOSTKEY_TYPE_RSA:
            return LIBSSH2_KNOWNHOST_KEY_SSHRSA;
        case LIBSSH2_HOSTKEY_TYPE_DSS:
            return LIBSSH2_KNOWNHOST_KEY_SSHDSS;
        case LIBSSH2_HOSTKEY_TYPE_ECDSA_256:
            return LIBSSH2_KNOWNHOST_KEY_ECDSA_256;
        case LIBSSH2_HOSTKEY_TYPE_ECDSA_384:
            return LIBSSH2_KNOWNHOST_KEY_ECDSA_384;
        case LIBSSH2_HOSTKEY_TYPE_ECDSA_521:
            return LIBSSH2_KNOWNHOST_KEY_ECDSA_521;
        case LIBSSH2_HOSTKEY_TYPE_ED25519:
            return LIBSSH2_KNOWNHOST_KEY_ED25519;
        default:
            return LIBSSH2_KNOWNHOST_KEY_UNKNOWN;
    }
}

static const char *hostkey_type_name(int type) {
    switch (type) {
        case LIBSSH2_HOSTKEY_TYPE_RSA:
            return "RSA";
        case LIBSSH2_HOSTKEY_TYPE_DSS:
            return "DSA";
        case LIBSSH2_HOSTKEY_TYPE_ECDSA_256:
            return "ECDSA-256";
        case LIBSSH2_HOSTKEY_TYPE_ECDSA_384:
            return "ECDSA-384";
        case LIBSSH2_HOSTKEY_TYPE_ECDSA_521:
            return "ECDSA-521";
        case LIBSSH2_HOSTKEY_TYPE_ED25519:
            return "ED25519";
        default:
            return "UNKNOWN";
    }
}

// --- 辅助函数 ---

/**
 * 建立 TCP 连接
 *
 * @param hostname 主机名或 IP
 * @param port 端口
 * @return Socket 文件描述符，失败返回 -1
 */
int connect_socket(const char *hostname, int port) {
    int sock = socket(AF_INET, SOCK_STREAM, 0);
    struct sockaddr_in sin;
    struct hostent *he;
    
    if (sock == -1) return -1;
    
    he = gethostbyname(hostname);
    if (!he) {
        sin.sin_addr.s_addr = inet_addr(hostname);
        if (sin.sin_addr.s_addr == INADDR_NONE) return -1;
    } else {
        memcpy(&sin.sin_addr, he->h_addr, he->h_length);
    }
    
    sin.sin_family = AF_INET;
    sin.sin_port = htons(port);
    
    if (connect(sock, (struct sockaddr*)(&sin), sizeof(struct sockaddr_in)) != 0) {
        close(sock);
        return -1;
    }
    return sock;
}

// --- JNI 实现 ---

/**
 * 连接 SSH 服务器
 */
JNIEXPORT jlong JNICALL
Java_com_orcterm_core_ssh_SshNative_connect(JNIEnv *env, jobject thiz, jstring host, jint port) {
    const char *nativeHost = (*env)->GetStringUTFChars(env, host, 0);
    
    // 初始化 libssh2 (每个进程只需一次，但多次调用是安全的)
    libssh2_init(0);
    
    int sock = connect_socket(nativeHost, port);
    if (sock == -1) {
        LOGE("Socket connect failed");
        (*env)->ReleaseStringUTFChars(env, host, nativeHost);
        return 0;
    }
    
    LIBSSH2_SESSION *session = libssh2_session_init();
    if (!session) {
        close(sock);
        (*env)->ReleaseStringUTFChars(env, host, nativeHost);
        return 0;
    }
    
    // 执行握手
    if (libssh2_session_handshake(session, sock) != 0) {
        LOGE("SSH Handshake failed");
        libssh2_session_free(session);
        close(sock);
        (*env)->ReleaseStringUTFChars(env, host, nativeHost);
        return 0;
    }
    
    (*env)->ReleaseStringUTFChars(env, host, nativeHost);
    
    SshContext *ctx = malloc(sizeof(SshContext));
    ctx->socket_fd = sock;
    ctx->session = session;
    ctx->channel = NULL;
    
    return (jlong)ctx;
}

JNIEXPORT void JNICALL
Java_com_orcterm_core_ssh_SshNative_setSessionTimeout(JNIEnv *env, jobject thiz, jlong handle, jint timeoutMs) {
    SshContext *ctx = (SshContext *)handle;
    if (!ctx) return;
    libssh2_session_set_timeout(ctx->session, timeoutMs);
}

JNIEXPORT void JNICALL
Java_com_orcterm_core_ssh_SshNative_setSessionReadTimeout(JNIEnv *env, jobject thiz, jlong handle, jint timeoutSec) {
    SshContext *ctx = (SshContext *)handle;
    if (!ctx) return;
    libssh2_session_set_read_timeout(ctx->session, timeoutSec);
}

JNIEXPORT void JNICALL
Java_com_orcterm_core_ssh_SshNative_setKeepaliveConfig(JNIEnv *env, jobject thiz, jlong handle, jboolean wantReply, jint intervalSec) {
    SshContext *ctx = (SshContext *)handle;
    if (!ctx) return;
    libssh2_keepalive_config(ctx->session, wantReply ? 1 : 0, intervalSec < 0 ? 0 : (unsigned int)intervalSec);
}

JNIEXPORT jint JNICALL
Java_com_orcterm_core_ssh_SshNative_sendKeepalive(JNIEnv *env, jobject thiz, jlong handle) {
    SshContext *ctx = (SshContext *)handle;
    if (!ctx) return -1;
    int secondsToNext = 0;
    int rc = libssh2_keepalive_send(ctx->session, &secondsToNext);
    if (rc != 0) {
        return -1;
    }
    return secondsToNext;
}

JNIEXPORT jint JNICALL
Java_com_orcterm_core_ssh_SshNative_knownHostsCheck(JNIEnv *env, jobject thiz, jlong handle, jstring host, jint port, jstring knownHostsPath) {
    SshContext *ctx = (SshContext *)handle;
    if (!ctx) return LIBSSH2_KNOWNHOST_CHECK_FAILURE;

    const char *hostStr = (*env)->GetStringUTFChars(env, host, 0);
    const char *pathStr = (*env)->GetStringUTFChars(env, knownHostsPath, 0);

    size_t keyLen = 0;
    int keyType = 0;
    const char *key = libssh2_session_hostkey(ctx->session, &keyLen, &keyType);
    if (!key) {
        (*env)->ReleaseStringUTFChars(env, host, hostStr);
        (*env)->ReleaseStringUTFChars(env, knownHostsPath, pathStr);
        return LIBSSH2_KNOWNHOST_CHECK_FAILURE;
    }

    LIBSSH2_KNOWNHOSTS *hosts = libssh2_knownhost_init(ctx->session);
    if (!hosts) {
        (*env)->ReleaseStringUTFChars(env, host, hostStr);
        (*env)->ReleaseStringUTFChars(env, knownHostsPath, pathStr);
        return LIBSSH2_KNOWNHOST_CHECK_FAILURE;
    }

    struct stat st;
    if (stat(pathStr, &st) == 0) {
        libssh2_knownhost_readfile(hosts, pathStr, LIBSSH2_KNOWNHOST_FILE_OPENSSH);
    }

    int typemask = LIBSSH2_KNOWNHOST_TYPE_PLAIN | LIBSSH2_KNOWNHOST_KEYENC_RAW | map_knownhost_key_type(keyType);
    struct libssh2_knownhost *known = NULL;
    int rc = libssh2_knownhost_checkp(hosts, hostStr, port, key, keyLen, typemask, &known);
    libssh2_knownhost_free(hosts);

    (*env)->ReleaseStringUTFChars(env, host, hostStr);
    (*env)->ReleaseStringUTFChars(env, knownHostsPath, pathStr);
    return rc;
}

JNIEXPORT jint JNICALL
Java_com_orcterm_core_ssh_SshNative_knownHostsAdd(JNIEnv *env, jobject thiz, jlong handle, jstring host, jint port, jstring knownHostsPath, jstring comment) {
    SshContext *ctx = (SshContext *)handle;
    if (!ctx) return -1;

    const char *hostStr = (*env)->GetStringUTFChars(env, host, 0);
    const char *pathStr = (*env)->GetStringUTFChars(env, knownHostsPath, 0);
    const char *commentStr = comment ? (*env)->GetStringUTFChars(env, comment, 0) : NULL;
    size_t commentLen = commentStr ? strlen(commentStr) : 0;

    size_t keyLen = 0;
    int keyType = 0;
    const char *key = libssh2_session_hostkey(ctx->session, &keyLen, &keyType);
    if (!key) {
        (*env)->ReleaseStringUTFChars(env, host, hostStr);
        (*env)->ReleaseStringUTFChars(env, knownHostsPath, pathStr);
        if (commentStr) (*env)->ReleaseStringUTFChars(env, comment, commentStr);
        return -1;
    }

    LIBSSH2_KNOWNHOSTS *hosts = libssh2_knownhost_init(ctx->session);
    if (!hosts) {
        (*env)->ReleaseStringUTFChars(env, host, hostStr);
        (*env)->ReleaseStringUTFChars(env, knownHostsPath, pathStr);
        if (commentStr) (*env)->ReleaseStringUTFChars(env, comment, commentStr);
        return -1;
    }

    struct stat st;
    if (stat(pathStr, &st) == 0) {
        libssh2_knownhost_readfile(hosts, pathStr, LIBSSH2_KNOWNHOST_FILE_OPENSSH);
    }

    int typemask = LIBSSH2_KNOWNHOST_TYPE_PLAIN | LIBSSH2_KNOWNHOST_KEYENC_RAW | map_knownhost_key_type(keyType);
    int rc = libssh2_knownhost_addc(hosts, hostStr, NULL, key, keyLen, commentStr, commentLen, typemask, NULL);
    if (rc == 0) {
        rc = libssh2_knownhost_writefile(hosts, pathStr, LIBSSH2_KNOWNHOST_FILE_OPENSSH);
    }
    libssh2_knownhost_free(hosts);

    (*env)->ReleaseStringUTFChars(env, host, hostStr);
    (*env)->ReleaseStringUTFChars(env, knownHostsPath, pathStr);
    if (commentStr) (*env)->ReleaseStringUTFChars(env, comment, commentStr);
    return rc == 0 ? 0 : -1;
}

JNIEXPORT jstring JNICALL
Java_com_orcterm_core_ssh_SshNative_getHostKeyInfo(JNIEnv *env, jobject thiz, jlong handle) {
    SshContext *ctx = (SshContext *)handle;
    if (!ctx) return (*env)->NewStringUTF(env, "");

    int keyType = 0;
    size_t keyLen = 0;
    const char *key = libssh2_session_hostkey(ctx->session, &keyLen, &keyType);
    if (!key) return (*env)->NewStringUTF(env, "");

    const unsigned char *hash = (const unsigned char *)libssh2_hostkey_hash(ctx->session, LIBSSH2_HOSTKEY_HASH_SHA256);
    if (!hash) return (*env)->NewStringUTF(env, "");

    unsigned char b64[128];
    int outLen = EVP_EncodeBlock(b64, hash, 32);
    b64[outLen] = 0;

    char info[192];
    snprintf(info, sizeof(info), "%s|SHA256:%s", hostkey_type_name(keyType), b64);
    return (*env)->NewStringUTF(env, info);
}

/**
 * 密码认证
 */
JNIEXPORT jint JNICALL
Java_com_orcterm_core_ssh_SshNative_authPassword(JNIEnv *env, jobject thiz, jlong handle, jstring user, jstring pwd) {
    SshContext *ctx = (SshContext *)handle;
    if (!ctx) return -1;
    
    const char *u = (*env)->GetStringUTFChars(env, user, 0);
    const char *p = (*env)->GetStringUTFChars(env, pwd, 0);
    
    int rc = libssh2_userauth_password(ctx->session, u, p);
    
    (*env)->ReleaseStringUTFChars(env, user, u);
    (*env)->ReleaseStringUTFChars(env, pwd, p);
    
    if (rc != 0) {
        LOGE("Auth failed: %d", rc);
        return -1;
    }
    return 0;
}

/**
 * 密钥认证
 */
JNIEXPORT jint JNICALL
Java_com_orcterm_core_ssh_SshNative_authKey(JNIEnv *env, jobject thiz, jlong handle, jstring user, jstring keyPath) {
    SshContext *ctx = (SshContext *)handle;
    if (!ctx) return -1;
    
    const char *u = (*env)->GetStringUTFChars(env, user, 0);
    const char *k = (*env)->GetStringUTFChars(env, keyPath, 0);
    
    int rc = libssh2_userauth_publickey_fromfile(ctx->session, u, NULL, k, NULL);
    
    (*env)->ReleaseStringUTFChars(env, user, u);
    (*env)->ReleaseStringUTFChars(env, keyPath, k);
    
    if (rc != 0) {
        LOGE("Auth Key failed: %d", rc);
        return -1;
    }
    return 0;
}

JNIEXPORT jint JNICALL
Java_com_orcterm_core_ssh_SshNative_authKeyWithPassphrase(JNIEnv *env, jobject thiz, jlong handle, jstring user, jstring keyPath, jstring passphrase) {
    SshContext *ctx = (SshContext *)handle;
    if (!ctx) return -1;

    const char *u = (*env)->GetStringUTFChars(env, user, 0);
    const char *k = (*env)->GetStringUTFChars(env, keyPath, 0);
    const char *p = passphrase ? (*env)->GetStringUTFChars(env, passphrase, 0) : NULL;

    int rc = libssh2_userauth_publickey_fromfile(ctx->session, u, NULL, k, p);

    (*env)->ReleaseStringUTFChars(env, user, u);
    (*env)->ReleaseStringUTFChars(env, keyPath, k);
    if (p) (*env)->ReleaseStringUTFChars(env, passphrase, p);

    if (rc != 0) {
        LOGE("Auth Key(passphrase) failed: %d", rc);
        return -1;
    }
    return 0;
}

/**
 * 打开 Shell 通道
 */
JNIEXPORT jint JNICALL
Java_com_orcterm_core_ssh_SshNative_openShell(JNIEnv *env, jobject thiz, jlong handle, jint cols, jint rows) {
    SshContext *ctx = (SshContext *)handle;
    if (!ctx) return -1;
    
    LIBSSH2_CHANNEL *channel = libssh2_channel_open_session(ctx->session);
    if (!channel) return -1;
    
    libssh2_channel_request_pty(channel, "xterm");
    libssh2_channel_request_pty_size(channel, cols, rows);
    
    if (libssh2_channel_shell(channel) != 0) {
        LOGE("Open shell failed");
        libssh2_channel_free(channel);
        return -1;
    }
    
    // 设置非阻塞模式，以便 UI 线程可以流畅读取
    // 暂时保持阻塞，但在 Java 层使用短读取
    libssh2_session_set_blocking(ctx->session, 0); 
    
    ctx->channel = channel;
    return 0;
}

/**
 * 写入数据到 Shell
 */
JNIEXPORT jint JNICALL
Java_com_orcterm_core_ssh_SshNative_write(JNIEnv *env, jobject thiz, jlong handle, jbyteArray data) {
    SshContext *ctx = (SshContext *)handle;
    if (!ctx || !ctx->channel) return -1;
    
    jsize len = (*env)->GetArrayLength(env, data);
    jbyte *body = (*env)->GetByteArrayElements(env, data, 0);
    
    libssh2_session_set_blocking(ctx->session, 1); // 写入时暂时设为阻塞以确保数据发送
    ssize_t written_total = 0;
    while (written_total < len) {
        ssize_t written = libssh2_channel_write(ctx->channel, (const char*)body + written_total, len - written_total);
        if (written == LIBSSH2_ERROR_EAGAIN) continue;
        if (written <= 0) break;
        written_total += written;
    }
    libssh2_session_set_blocking(ctx->session, 0); // 恢复非阻塞
    
    (*env)->ReleaseByteArrayElements(env, data, body, 0);
    return (int)written_total;
}

/**
 * 从 Shell 读取数据
 */
JNIEXPORT jbyteArray JNICALL
Java_com_orcterm_core_ssh_SshNative_read(JNIEnv *env, jobject thiz, jlong handle) {
    SshContext *ctx = (SshContext *)handle;
    if (!ctx || !ctx->channel) return (*env)->NewByteArray(env, 0);
    
    char buffer[4096];
    // 非阻塞读取
    ssize_t rc = libssh2_channel_read(ctx->channel, buffer, sizeof(buffer));
    
    if (rc > 0) {
        jbyteArray result = (*env)->NewByteArray(env, (jsize)rc);
        (*env)->SetByteArrayRegion(env, result, 0, (jsize)rc, (const jbyte*)buffer);
        return result;
    }
    
    return (*env)->NewByteArray(env, 0);
}

/**
 * 执行单条命令
 */
JNIEXPORT jstring JNICALL
Java_com_orcterm_core_ssh_SshNative_exec(JNIEnv *env, jobject thiz, jlong handle, jstring command) {
    SshContext *ctx = (SshContext *)handle;
    if (!ctx) return (*env)->NewStringUTF(env, "");
    
    const char *cmd = (*env)->GetStringUTFChars(env, command, 0);
    
    libssh2_session_set_blocking(ctx->session, 1); // Exec 需要阻塞等待
    LIBSSH2_CHANNEL *channel = libssh2_channel_open_session(ctx->session);
    if (!channel) {
        (*env)->ReleaseStringUTFChars(env, command, cmd);
        libssh2_session_set_blocking(ctx->session, 0);
        return (*env)->NewStringUTF(env, "Error: Open Channel");
    }
    
    libssh2_channel_exec(channel, cmd);
    
    // 读取输出
    char *result = malloc(1); result[0] = 0;
    size_t total = 0;
    char buffer[1024];
    ssize_t rc;
    
    while((rc = libssh2_channel_read(channel, buffer, sizeof(buffer)-1)) > 0) {
        buffer[rc] = 0;
        result = realloc(result, total + rc + 1);
        memcpy(result + total, buffer, rc);
        result[total + rc] = 0;
        total += rc;
    }
    
    libssh2_channel_close(channel);
    libssh2_channel_free(channel);
    libssh2_session_set_blocking(ctx->session, 0); // 恢复
    
    (*env)->ReleaseStringUTFChars(env, command, cmd);
    jstring jstr = (*env)->NewStringUTF(env, result);
    free(result);
    return jstr;
}

JNIEXPORT jstring JNICALL
Java_com_orcterm_core_ssh_SshNative_execWithResult(JNIEnv *env, jobject thiz, jlong handle, jstring command) {
    SshContext *ctx = (SshContext *)handle;
    if (!ctx) return (*env)->NewStringUTF(env, "{\"exitCode\":-1,\"stdout\":\"\",\"stderr\":\"No Context\"}");

    const char *cmd = (*env)->GetStringUTFChars(env, command, 0);

    libssh2_session_set_blocking(ctx->session, 1);
    LIBSSH2_CHANNEL *channel = libssh2_channel_open_session(ctx->session);
    if (!channel) {
        (*env)->ReleaseStringUTFChars(env, command, cmd);
        libssh2_session_set_blocking(ctx->session, 0);
        return (*env)->NewStringUTF(env, "{\"exitCode\":-1,\"stdout\":\"\",\"stderr\":\"Open Channel\"}");
    }

    int exec_rc = libssh2_channel_exec(channel, cmd);
    (*env)->ReleaseStringUTFChars(env, command, cmd);
    if (exec_rc != 0) {
        libssh2_channel_close(channel);
        libssh2_channel_free(channel);
        libssh2_session_set_blocking(ctx->session, 0);
        return (*env)->NewStringUTF(env, "{\"exitCode\":-1,\"stdout\":\"\",\"stderr\":\"Exec Failed\"}");
    }

    StrBuf out;
    StrBuf err;
    sb_init(&out, 1024);
    sb_init(&err, 256);

    char buffer[2048];
    while (1) {
        ssize_t rc_out = libssh2_channel_read_ex(channel, 0, buffer, sizeof(buffer));
        if (rc_out > 0) {
            if (sb_ensure(&out, (size_t)rc_out) == 0) {
                memcpy(out.data + out.len, buffer, (size_t)rc_out);
                out.len += (size_t)rc_out;
                out.data[out.len] = 0;
            }
        }
        ssize_t rc_err = libssh2_channel_read_ex(channel, 1, buffer, sizeof(buffer));
        if (rc_err > 0) {
            if (sb_ensure(&err, (size_t)rc_err) == 0) {
                memcpy(err.data + err.len, buffer, (size_t)rc_err);
                err.len += (size_t)rc_err;
                err.data[err.len] = 0;
            }
        }
        if (rc_out == LIBSSH2_ERROR_EAGAIN && rc_err == LIBSSH2_ERROR_EAGAIN) {
            if (libssh2_channel_eof(channel)) break;
        }
        if (rc_out <= 0 && rc_err <= 0) {
            if (libssh2_channel_eof(channel)) break;
        }
    }

    int exit_code = libssh2_channel_get_exit_status(channel);

    libssh2_channel_close(channel);
    libssh2_channel_free(channel);
    libssh2_session_set_blocking(ctx->session, 0);

    StrBuf json;
    sb_init(&json, out.len + err.len + 64);
    sb_append(&json, "{\"exitCode\":");
    char code_buf[32];
    snprintf(code_buf, sizeof(code_buf), "%d", exit_code);
    sb_append(&json, code_buf);
    sb_append(&json, ",\"stdout\":\"");
    sb_append_escaped(&json, out.data ? out.data : "");
    sb_append(&json, "\",\"stderr\":\"");
    sb_append_escaped(&json, err.data ? err.data : "");
    sb_append(&json, "\"}");

    jstring result = (*env)->NewStringUTF(env, json.data ? json.data : "{\"exitCode\":-1,\"stdout\":\"\",\"stderr\":\"\"}");

    sb_free(&out);
    sb_free(&err);
    sb_free(&json);
    return result;
}

/**
 * SFTP 列出目录
 */
JNIEXPORT jstring JNICALL
Java_com_orcterm_core_ssh_SshNative_sftpList(JNIEnv *env, jobject thiz, jlong handle, jstring path) {
    SshContext *ctx = (SshContext *)handle;
    if (!ctx) return (*env)->NewStringUTF(env, "[]");
    
    libssh2_session_set_blocking(ctx->session, 1);
    
    const char *p = (*env)->GetStringUTFChars(env, path, 0);
    LIBSSH2_SFTP *sftp = libssh2_sftp_init(ctx->session);
    
    if (!sftp) {
        (*env)->ReleaseStringUTFChars(env, path, p);
        libssh2_session_set_blocking(ctx->session, 0);
        return (*env)->NewStringUTF(env, "[]");
    }
    
    LIBSSH2_SFTP_HANDLE *h = libssh2_sftp_opendir(sftp, p);
    if (!h) {
        libssh2_sftp_shutdown(sftp);
        (*env)->ReleaseStringUTFChars(env, path, p);
        libssh2_session_set_blocking(ctx->session, 0);
        return (*env)->NewStringUTF(env, "[]");
    }
    
    StrBuf json;
    sb_init(&json, 4096);
    sb_append(&json, "[");
    
    char mem[512];
    LIBSSH2_SFTP_ATTRIBUTES attrs;
    int first = 1;
    
    while(libssh2_sftp_readdir(h, mem, sizeof(mem), &attrs) > 0) {
        if(strcmp(mem, ".") == 0 || strcmp(mem, "..") == 0) continue;
        
        if(!first) sb_append(&json, ",");
        first = 0;
        
        int is_dir = LIBSSH2_SFTP_S_ISDIR(attrs.permissions);
        char perm_str[11];
        build_perm_string(attrs.permissions, is_dir, perm_str);
        long mtime = (attrs.flags & LIBSSH2_SFTP_ATTR_ACMODTIME) ? (long)attrs.mtime : 0;
        char num_buf[64];
        sb_append(&json, "{\"name\":\"");
        sb_append_escaped(&json, mem);
        sb_append(&json, "\",\"isDir\":");
        sb_append(&json, is_dir ? "true" : "false");
        sb_append(&json, ",\"size\":");
        snprintf(num_buf, sizeof(num_buf), "%ld", (long)attrs.filesize);
        sb_append(&json, num_buf);
        sb_append(&json, ",\"perm\":\"");
        sb_append_escaped(&json, perm_str);
        sb_append(&json, "\",\"mtime\":");
        snprintf(num_buf, sizeof(num_buf), "%ld", mtime);
        sb_append(&json, num_buf);
        sb_append(&json, "}");
    }
    sb_append(&json, "]");
    
    libssh2_sftp_closedir(h);
    libssh2_sftp_shutdown(sftp);
    libssh2_session_set_blocking(ctx->session, 0);
    (*env)->ReleaseStringUTFChars(env, path, p);
    
    jstring jstr = (*env)->NewStringUTF(env, json.data ? json.data : "[]");
    sb_free(&json);
    return jstr;
}

JNIEXPORT jint JNICALL
Java_com_orcterm_core_ssh_SshNative_sftpUpload(JNIEnv *env, jobject thiz, jlong handle, jstring localPath, jstring remotePath) {
    SshContext *ctx = (SshContext *)handle;
    if (!ctx) return -1;

    const char *local = (*env)->GetStringUTFChars(env, localPath, 0);
    const char *remote = (*env)->GetStringUTFChars(env, remotePath, 0);

    libssh2_session_set_blocking(ctx->session, 1);

    LIBSSH2_SFTP *sftp = libssh2_sftp_init(ctx->session);
    if (!sftp) {
        (*env)->ReleaseStringUTFChars(env, localPath, local);
        (*env)->ReleaseStringUTFChars(env, remotePath, remote);
        libssh2_session_set_blocking(ctx->session, 0);
        return -1;
    }

    LIBSSH2_SFTP_HANDLE *h = libssh2_sftp_open(sftp, remote,
        LIBSSH2_FXF_WRITE | LIBSSH2_FXF_CREAT | LIBSSH2_FXF_TRUNC,
        LIBSSH2_SFTP_S_IRUSR | LIBSSH2_SFTP_S_IWUSR | LIBSSH2_SFTP_S_IRGRP | LIBSSH2_SFTP_S_IROTH);
    if (!h) {
        libssh2_sftp_shutdown(sftp);
        (*env)->ReleaseStringUTFChars(env, localPath, local);
        (*env)->ReleaseStringUTFChars(env, remotePath, remote);
        libssh2_session_set_blocking(ctx->session, 0);
        return -1;
    }

    FILE *fp = fopen(local, "rb");
    if (!fp) {
        libssh2_sftp_close(h);
        libssh2_sftp_shutdown(sftp);
        (*env)->ReleaseStringUTFChars(env, localPath, local);
        (*env)->ReleaseStringUTFChars(env, remotePath, remote);
        libssh2_session_set_blocking(ctx->session, 0);
        return -1;
    }

    char buffer[16384];
    size_t nread;
    int rc = 0;
    while ((nread = fread(buffer, 1, sizeof(buffer), fp)) > 0) {
        char *ptr = buffer;
        size_t left = nread;
        while (left > 0) {
            ssize_t nw = libssh2_sftp_write(h, ptr, left);
            if (nw == LIBSSH2_ERROR_EAGAIN) continue;
            if (nw < 0) { rc = -1; break; }
            ptr += nw;
            left -= (size_t)nw;
        }
        if (rc != 0) break;
    }

    fclose(fp);
    libssh2_sftp_close(h);
    libssh2_sftp_shutdown(sftp);
    libssh2_session_set_blocking(ctx->session, 0);

    (*env)->ReleaseStringUTFChars(env, localPath, local);
    (*env)->ReleaseStringUTFChars(env, remotePath, remote);
    return rc;
}

JNIEXPORT jint JNICALL
Java_com_orcterm_core_ssh_SshNative_sftpDownload(JNIEnv *env, jobject thiz, jlong handle, jstring remotePath, jstring localPath) {
    SshContext *ctx = (SshContext *)handle;
    if (!ctx) return -1;

    const char *remote = (*env)->GetStringUTFChars(env, remotePath, 0);
    const char *local = (*env)->GetStringUTFChars(env, localPath, 0);

    libssh2_session_set_blocking(ctx->session, 1);

    LIBSSH2_SFTP *sftp = libssh2_sftp_init(ctx->session);
    if (!sftp) {
        (*env)->ReleaseStringUTFChars(env, remotePath, remote);
        (*env)->ReleaseStringUTFChars(env, localPath, local);
        libssh2_session_set_blocking(ctx->session, 0);
        return -1;
    }

    LIBSSH2_SFTP_HANDLE *h = libssh2_sftp_open(sftp, remote, LIBSSH2_FXF_READ, 0);
    if (!h) {
        libssh2_sftp_shutdown(sftp);
        (*env)->ReleaseStringUTFChars(env, remotePath, remote);
        (*env)->ReleaseStringUTFChars(env, localPath, local);
        libssh2_session_set_blocking(ctx->session, 0);
        return -1;
    }

    FILE *fp = fopen(local, "wb");
    if (!fp) {
        libssh2_sftp_close(h);
        libssh2_sftp_shutdown(sftp);
        (*env)->ReleaseStringUTFChars(env, remotePath, remote);
        (*env)->ReleaseStringUTFChars(env, localPath, local);
        libssh2_session_set_blocking(ctx->session, 0);
        return -1;
    }

    char buffer[16384];
    int rc = 0;
    while (1) {
        ssize_t nread = libssh2_sftp_read(h, buffer, sizeof(buffer));
        if (nread == 0) break;
        if (nread == LIBSSH2_ERROR_EAGAIN) continue;
        if (nread < 0) { rc = -1; break; }
        size_t nw = fwrite(buffer, 1, (size_t)nread, fp);
        if (nw != (size_t)nread) { rc = -1; break; }
    }

    fclose(fp);
    libssh2_sftp_close(h);
    libssh2_sftp_shutdown(sftp);
    libssh2_session_set_blocking(ctx->session, 0);

    (*env)->ReleaseStringUTFChars(env, remotePath, remote);
    (*env)->ReleaseStringUTFChars(env, localPath, local);
    return rc;
}

/**
 * 调整窗口大小
 */
JNIEXPORT jint JNICALL
Java_com_orcterm_core_ssh_SshNative_resize(JNIEnv *env, jobject thiz, jlong handle, jint cols, jint rows) {
    SshContext *ctx = (SshContext *)handle;
    if (!ctx || !ctx->channel) return -1;
    
    int rc = libssh2_channel_request_pty_size(ctx->channel, cols, rows);
    return rc;
}

/**
 * 断开连接
 */
JNIEXPORT void JNICALL
Java_com_orcterm_core_ssh_SshNative_disconnect(JNIEnv *env, jobject thiz, jlong handle) {
    SshContext *ctx = (SshContext *)handle;
    if (!ctx) return;
    
    if (ctx->channel) libssh2_channel_free(ctx->channel);
    if (ctx->session) {
        libssh2_session_disconnect(ctx->session, "Normal Shutdown");
        libssh2_session_free(ctx->session);
    }
    close(ctx->socket_fd);
    free(ctx);
    
    libssh2_exit();
}

/**
 * 生成 Ed25519 密钥对
 */
JNIEXPORT jint JNICALL
Java_com_orcterm_core_ssh_SshNative_generateKeyPair(JNIEnv *env, jobject thiz, jstring path) {
    const char *privPath = (*env)->GetStringUTFChars(env, path, 0);
    char pubPath[512];
    snprintf(pubPath, sizeof(pubPath), "%s.pub", privPath);

    EVP_PKEY *pkey = NULL;
    EVP_PKEY_CTX *pctx = EVP_PKEY_CTX_new_id(EVP_PKEY_ED25519, NULL);
    
    if (!pctx) {
        LOGE("EVP_PKEY_CTX_new_id failed");
        (*env)->ReleaseStringUTFChars(env, path, privPath);
        return -1;
    }

    if (EVP_PKEY_keygen_init(pctx) <= 0) {
        LOGE("EVP_PKEY_keygen_init failed");
        EVP_PKEY_CTX_free(pctx);
        (*env)->ReleaseStringUTFChars(env, path, privPath);
        return -1;
    }

    if (EVP_PKEY_keygen(pctx, &pkey) <= 0) {
        LOGE("EVP_PKEY_keygen failed");
        EVP_PKEY_CTX_free(pctx);
        (*env)->ReleaseStringUTFChars(env, path, privPath);
        return -1;
    }

    // 写入私钥
    FILE *fp = fopen(privPath, "wb");
    if (!fp) {
        LOGE("Could not open %s for writing", privPath);
        EVP_PKEY_free(pkey);
        EVP_PKEY_CTX_free(pctx);
        (*env)->ReleaseStringUTFChars(env, path, privPath);
        return -1;
    }
    
    // 使用 PKCS8 格式写入私钥
    if (!PEM_write_PrivateKey(fp, pkey, NULL, NULL, 0, NULL, NULL)) {
        LOGE("PEM_write_PrivateKey failed");
        fclose(fp);
        EVP_PKEY_free(pkey);
        EVP_PKEY_CTX_free(pctx);
        (*env)->ReleaseStringUTFChars(env, path, privPath);
        return -1;
    }
    fclose(fp);
    chmod(privPath, 0600); // 设置安全权限

    // 生成 OpenSSH 格式公钥
    size_t len = 32;
    unsigned char pub[32];
    if (EVP_PKEY_get_raw_public_key(pkey, pub, &len) == 1) {
        // 构造 OpenSSH Blob: 
        // string "ssh-ed25519" (11 chars) -> 00 00 00 0B 73 73 68 2D 65 64 32 35 35 31 39
        // string key (32 bytes) -> 00 00 00 20 <32 bytes>
        
        unsigned char blob[128];
        unsigned char *p = blob;
        
        // Type length
        *p++ = 0; *p++ = 0; *p++ = 0; *p++ = 11;
        memcpy(p, "ssh-ed25519", 11); p += 11;
        
        // Key length
        *p++ = 0; *p++ = 0; *p++ = 0; *p++ = 32;
        memcpy(p, pub, 32); p += 32;
        
        size_t blob_len = p - blob;
        
        // Base64 编码
        BIO *b64 = BIO_new(BIO_f_base64());
        BIO_set_flags(b64, BIO_FLAGS_BASE64_NO_NL);
        BIO *mem = BIO_new(BIO_s_mem());
        BIO_push(b64, mem);
        BIO_write(b64, blob, blob_len);
        BIO_flush(b64);
        
        char *b64_data;
        long b64_len = BIO_get_mem_data(mem, &b64_data);
        
        FILE *fp_pub = fopen(pubPath, "w");
        fprintf(fp_pub, "ssh-ed25519 %.*s orcterm-android\n", (int)b64_len, b64_data);
        fclose(fp_pub);
        
        BIO_free_all(b64);
    } else {
        LOGE("Failed to get raw public key");
    }

    EVP_PKEY_free(pkey);
    EVP_PKEY_CTX_free(pctx);
    (*env)->ReleaseStringUTFChars(env, path, privPath);
    return 0;
}

/**
 * 发送绑定请求 (通过 SSH 隧道)
 */
JNIEXPORT jstring JNICALL
Java_com_orcterm_core_ssh_SshNative_sendBindRequest(JNIEnv *env, jobject thiz, jlong handle, jint apiPort, jstring token, jstring pubKey) {
    SshContext *ctx = (SshContext *)handle;
    if (!ctx) return (*env)->NewStringUTF(env, "Error: No Context");

    const char *tok = (*env)->GetStringUTFChars(env, token, 0);
    const char *key = (*env)->GetStringUTFChars(env, pubKey, 0);
    
    // 打开到 localhost:apiPort 的 Direct TCP/IP 通道
    libssh2_session_set_blocking(ctx->session, 1);
    LIBSSH2_CHANNEL *channel = libssh2_channel_direct_tcpip(ctx->session, "127.0.0.1", apiPort);
    
    if (!channel) {
        LOGE("Direct TCPIP failed");
        (*env)->ReleaseStringUTFChars(env, token, tok);
        (*env)->ReleaseStringUTFChars(env, pubKey, key);
        libssh2_session_set_blocking(ctx->session, 0);
        return (*env)->NewStringUTF(env, "Error: Connection Failed");
    }

    // 构造 JSON Body
    char body[2048];
    snprintf(body, sizeof(body), "{\"token\":\"%s\",\"pub_key\":\"%s\"}", tok, key);
    
    // 构造 HTTP 请求
    char request[4096];
    snprintf(request, sizeof(request), 
        "POST /bind HTTP/1.0\r\n"
        "Content-Type: application/json\r\n"
        "Content-Length: %zu\r\n"
        "\r\n%s", strlen(body), body);

    // 发送请求
    libssh2_channel_write(channel, request, strlen(request));
    
    // 读取响应
    char buffer[1024];
    char response[4096] = {0};
    ssize_t rc;
    size_t total = 0;
    
    while ((rc = libssh2_channel_read(channel, buffer, sizeof(buffer)-1)) > 0) {
        if (total + rc < sizeof(response) - 1) {
            memcpy(response + total, buffer, rc);
            total += rc;
            response[total] = 0;
        }
    }
    
    libssh2_channel_close(channel);
    libssh2_channel_free(channel);
    libssh2_session_set_blocking(ctx->session, 0);
    
    (*env)->ReleaseStringUTFChars(env, token, tok);
    (*env)->ReleaseStringUTFChars(env, pubKey, key);
    
    // 检查响应是否包含 "200 OK"
    if (strstr(response, "200 OK")) {
        return (*env)->NewStringUTF(env, "OK");
    } else {
        return (*env)->NewStringUTF(env, response);
    }
}

// --- 端口转发支持 (Port Forwarding) ---

/**
 * 打开直接 TCP/IP 通道
 */
JNIEXPORT jlong JNICALL
Java_com_orcterm_core_ssh_SshNative_openDirectTcpIp(JNIEnv *env, jobject thiz, jlong handle, jstring targetHost, jint targetPort) {
    SshContext *ctx = (SshContext *)handle;
    if (!ctx) return 0;
    
    const char *host = (*env)->GetStringUTFChars(env, targetHost, 0);
    
    libssh2_session_set_blocking(ctx->session, 1);
    LIBSSH2_CHANNEL *channel = libssh2_channel_direct_tcpip(ctx->session, host, targetPort);
    libssh2_session_set_blocking(ctx->session, 0);
    
    (*env)->ReleaseStringUTFChars(env, targetHost, host);
    
    if (!channel) {
        LOGE("Direct TCPIP failed to %s:%d", host, targetPort);
        return 0;
    }
    
    return (jlong)channel;
}

/**
 * 写入数据到通道
 */
JNIEXPORT jint JNICALL
Java_com_orcterm_core_ssh_SshNative_writeChannel(JNIEnv *env, jobject thiz, jlong handle, jlong channelHandle, jbyteArray data) {
    SshContext *ctx = (SshContext *)handle;
    LIBSSH2_CHANNEL *channel = (LIBSSH2_CHANNEL *)channelHandle;
    
    if (!ctx || !channel) return -1;
    
    jsize len = (*env)->GetArrayLength(env, data);
    jbyte *body = (*env)->GetByteArrayElements(env, data, 0);
    
    // 使用阻塞模式写入以保证可靠性
    libssh2_session_set_blocking(ctx->session, 1);
    ssize_t written_total = 0;
    while (written_total < len) {
        ssize_t written = libssh2_channel_write(channel, (const char*)body + written_total, len - written_total);
        if (written == LIBSSH2_ERROR_EAGAIN) continue;
        if (written <= 0) break;
        written_total += written;
    }
    libssh2_session_set_blocking(ctx->session, 0);
    
    (*env)->ReleaseByteArrayElements(env, data, body, 0);
    return (int)written_total;
}

/**
 * 从通道读取数据
 */
JNIEXPORT jbyteArray JNICALL
Java_com_orcterm_core_ssh_SshNative_readChannel(JNIEnv *env, jobject thiz, jlong handle, jlong channelHandle) {
    SshContext *ctx = (SshContext *)handle;
    LIBSSH2_CHANNEL *channel = (LIBSSH2_CHANNEL *)channelHandle;
    
    if (!ctx || !channel) return NULL;
    
    char buffer[8192];
    ssize_t rc = libssh2_channel_read(channel, buffer, sizeof(buffer));
    
    if (rc > 0) {
        jbyteArray result = (*env)->NewByteArray(env, (jsize)rc);
        (*env)->SetByteArrayRegion(env, result, 0, (jsize)rc, (const jbyte*)buffer);
        return result;
    }
    
    if (rc == LIBSSH2_ERROR_EAGAIN) {
        return (*env)->NewByteArray(env, 0);
    }
    
    // EOF (0) 或 错误 (<0 且非 EAGAIN)
    return NULL;
}

/**
 * 关闭通道
 */
JNIEXPORT void JNICALL
Java_com_orcterm_core_ssh_SshNative_closeChannel(JNIEnv *env, jobject thiz, jlong channelHandle) {
    LIBSSH2_CHANNEL *channel = (LIBSSH2_CHANNEL *)channelHandle;
    if (channel) {
        libssh2_channel_close(channel);
        libssh2_channel_free(channel);
    }
}
