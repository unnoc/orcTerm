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
    ssize_t written = libssh2_channel_write(ctx->channel, (const char*)body, len);
    libssh2_session_set_blocking(ctx->session, 0); // 恢复非阻塞
    
    (*env)->ReleaseByteArrayElements(env, data, body, 0);
    return (int)written;
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
    
    // 简单的 JSON 构造
    // 实际项目中建议使用 cJSON 或类似库，这里手动拼接仅作为示例
    char *json = malloc(1024 * 1024); // 1MB 缓冲区
    strcpy(json, "[");
    
    char mem[512];
    LIBSSH2_SFTP_ATTRIBUTES attrs;
    int first = 1;
    
    while(libssh2_sftp_readdir(h, mem, sizeof(mem), &attrs) > 0) {
        if(strcmp(mem, ".") == 0 || strcmp(mem, "..") == 0) continue;
        
        if(!first) strcat(json, ",");
        first = 0;
        
        int is_dir = LIBSSH2_SFTP_S_ISDIR(attrs.permissions);
        char entry[1024];
        char perm_str[11];
        build_perm_string(attrs.permissions, is_dir, perm_str);
        long mtime = (attrs.flags & LIBSSH2_SFTP_ATTR_ACMODTIME) ? (long)attrs.mtime : 0;
        sprintf(entry, "{\"name\":\"%s\", \"isDir\":%s, \"size\":%ld, \"perm\":\"%s\", \"mtime\":%ld}",
            mem, is_dir ? "true" : "false", (long)attrs.filesize, perm_str, mtime);
            
        if(strlen(json) + strlen(entry) < 1024*1024 - 10) {
            strcat(json, entry);
        }
    }
    strcat(json, "]");
    
    libssh2_sftp_closedir(h);
    libssh2_sftp_shutdown(sftp);
    libssh2_session_set_blocking(ctx->session, 0);
    (*env)->ReleaseStringUTFChars(env, path, p);
    
    jstring jstr = (*env)->NewStringUTF(env, json);
    free(json);
    return jstr;
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
    ssize_t written = libssh2_channel_write(channel, (const char*)body, len);
    libssh2_session_set_blocking(ctx->session, 0);
    
    (*env)->ReleaseByteArrayElements(env, data, body, 0);
    return (int)written;
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
