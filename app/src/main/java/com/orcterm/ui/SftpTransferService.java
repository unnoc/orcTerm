package com.orcterm.ui;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.IBinder;
import android.util.Base64;

import androidx.core.app.NotificationCompat;

import com.orcterm.R;
import com.orcterm.core.session.SessionManager;
import com.orcterm.core.ssh.SshNative;
import com.orcterm.core.terminal.TerminalSession;
import com.orcterm.util.CommandConstants;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * SFTP 上传/下载后台服务
 */
public class SftpTransferService extends Service {

    public static final String ACTION_ENQUEUE = "com.orcterm.action.SFTP_TRANSFER_ENQUEUE";
    public static final String ACTION_CANCEL = "com.orcterm.action.SFTP_TRANSFER_CANCEL";
    public static final String ACTION_STOP = "com.orcterm.action.SFTP_TRANSFER_STOP";
    public static final String ACTION_UI_UPDATE = "com.orcterm.action.SFTP_TRANSFER_UI_UPDATE";
    public static final String ACTION_UI_EVENT = "com.orcterm.action.SFTP_TRANSFER_UI_EVENT";

    public static final String EXTRA_TITLE = "extra_title";
    public static final String EXTRA_PROGRESS = "extra_progress";
    public static final String EXTRA_MAX = "extra_max";
    public static final String EXTRA_SUBTITLE = "extra_subtitle";
    public static final String EXTRA_QUEUE = "extra_queue";
    public static final String EXTRA_VISIBLE = "extra_visible";
    public static final String EXTRA_EVENT = "extra_event";
    public static final String EXTRA_MESSAGE = "extra_message";
    public static final String EXTRA_TYPE = "extra_type";
    public static final String EXTRA_NAME = "extra_name";
    public static final String EXTRA_REMOTE_PATH = "extra_remote_path";
    public static final String EXTRA_TOTAL_BYTES = "extra_total_bytes";
    public static final String EXTRA_URI = "extra_uri";
    public static final String EXTRA_LOCAL_PATH = "extra_local_path";
    public static final String EXTRA_SHOW_TOAST = "extra_show_toast";
    public static final String EXTRA_SHOW_RELOAD = "extra_show_reload";
    public static final String EXTRA_RETRIES = "extra_retries";
    public static final String EXTRA_HOST = "extra_host";
    public static final String EXTRA_PORT = "extra_port";
    public static final String EXTRA_USER = "extra_user";
    public static final String EXTRA_PASSWORD = "extra_password";
    public static final String EXTRA_AUTH_TYPE = "extra_auth_type";
    public static final String EXTRA_KEY_PATH = "extra_key_path";

    public static final int TYPE_DOWNLOAD = 1;
    public static final int TYPE_UPLOAD_URI = 2;
    public static final int TYPE_UPLOAD_FILE = 3;

    private static final String CHANNEL_ID = "sftp_transfer";
    private static final int NOTIFICATION_ID = 10021;
    private static final int TRANSFER_BLOCK_SIZE = 65536;
    private static final int DEFAULT_RETRIES = 2;
    private static final int MAX_PARALLEL_TRANSFERS = 2;
    private static final long RETRY_BASE_DELAY_MS = 1000;
    private static final long UI_UPDATE_MIN_INTERVAL_MS = 200;
    private static final int UI_UPDATE_MIN_PROGRESS_STEP = 5;
    private static final int MAX_UPLOAD_CMD_LENGTH = 60000;
    private static final int MAX_UPLOAD_BATCH_CHUNKS = 6;
    private static final int MAX_DOWNLOAD_CMD_LENGTH = 60000;
    private static final int MAX_DOWNLOAD_BATCH_CHUNKS = 4;
    private static final String DOWNLOAD_SPLIT_MARKER = "__ORCTERM_SPLIT__";
    private static final int RESUME_HASH_TAIL_SIZE = 65536;
    private static final String CMD_MD5_TAIL_TEMPLATE = "dd if=%s bs=1 skip=%d count=%d status=none | md5sum | awk '{print $1}'";

    private final ExecutorService executor = Executors.newFixedThreadPool(MAX_PARALLEL_TRANSFERS);
    private final Object transferLock = new Object();
    private final List<TransferTask> transferQueueList = new ArrayList<>();
    private final List<TransferTask> activeTransfers = new ArrayList<>();
    // 共享连接占用标记，避免并发复用同一会话
    private final Map<String, Long> sharedHandleInUse = new ConcurrentHashMap<>();
    private TransferTask activeTransfer = null;
    private int completedTransfers = 0;
    private long transferStartTime = 0;
    private boolean isForeground = false;

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) return START_NOT_STICKY;
        String action = intent.getAction();
        if (ACTION_STOP.equals(action)) {
            stopForeground(true);
            stopSelf();
            isForeground = false;
            return START_NOT_STICKY;
        }
        if (ACTION_CANCEL.equals(action)) {
            requestCancelActiveTransfer();
            return START_STICKY;
        }
        if (ACTION_ENQUEUE.equals(action)) {
            TransferTask task = buildTask(intent);
            if (task != null) {
                synchronized (transferLock) {
                    transferQueueList.add(task);
                }
                processNextTransfer();
            }
            return START_STICKY;
        }
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        executor.shutdownNow();
    }

    public static void enqueueTransfer(Context context, int type, String name, String remotePath, long totalBytes, Uri uri, File localFile, boolean showToastOnDone, boolean showReloadPrompt, String host, int port, String username, String password, int authType, String keyPath) {
        Intent intent = new Intent(context, SftpTransferService.class);
        intent.setAction(ACTION_ENQUEUE);
        intent.putExtra(EXTRA_TYPE, type);
        intent.putExtra(EXTRA_NAME, name);
        intent.putExtra(EXTRA_REMOTE_PATH, remotePath);
        intent.putExtra(EXTRA_TOTAL_BYTES, totalBytes);
        intent.putExtra(EXTRA_URI, uri != null ? uri.toString() : null);
        intent.putExtra(EXTRA_LOCAL_PATH, localFile != null ? localFile.getAbsolutePath() : null);
        intent.putExtra(EXTRA_SHOW_TOAST, showToastOnDone);
        intent.putExtra(EXTRA_SHOW_RELOAD, showReloadPrompt);
        intent.putExtra(EXTRA_RETRIES, DEFAULT_RETRIES);
        intent.putExtra(EXTRA_HOST, host);
        intent.putExtra(EXTRA_PORT, port);
        intent.putExtra(EXTRA_USER, username);
        intent.putExtra(EXTRA_PASSWORD, password);
        intent.putExtra(EXTRA_AUTH_TYPE, authType);
        intent.putExtra(EXTRA_KEY_PATH, keyPath);
        startServiceCompat(context, intent);
    }

    public static void cancelActive(Context context) {
        Intent intent = new Intent(context, SftpTransferService.class);
        intent.setAction(ACTION_CANCEL);
        startServiceCompat(context, intent);
    }

    private static void startServiceCompat(Context context, Intent intent) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent);
        } else {
            context.startService(intent);
        }
    }

    private String buildContentText(String subtitle, String queue) {
        String s = subtitle == null ? "" : subtitle.trim();
        String q = queue == null ? "" : queue.trim();
        if (!s.isEmpty() && !q.isEmpty()) return s + "  " + q;
        if (!s.isEmpty()) return s;
        if (!q.isEmpty()) return q;
        return getString(R.string.notification_transfer_content);
    }

    private void ensureChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm == null) return;
            NotificationChannel channel = nm.getNotificationChannel(CHANNEL_ID);
            if (channel == null) {
                channel = new NotificationChannel(
                        CHANNEL_ID,
                        getString(R.string.notification_transfer_channel_name),
                        NotificationManager.IMPORTANCE_LOW
                );
                channel.setDescription(getString(R.string.notification_transfer_channel_description));
                nm.createNotificationChannel(channel);
            }
        }
    }

    private TransferTask buildTask(Intent intent) {
        int type = intent.getIntExtra(EXTRA_TYPE, 0);
        String name = intent.getStringExtra(EXTRA_NAME);
        String remotePath = intent.getStringExtra(EXTRA_REMOTE_PATH);
        long totalBytes = intent.getLongExtra(EXTRA_TOTAL_BYTES, 0);
        String uriStr = intent.getStringExtra(EXTRA_URI);
        String localPath = intent.getStringExtra(EXTRA_LOCAL_PATH);
        boolean showToast = intent.getBooleanExtra(EXTRA_SHOW_TOAST, false);
        boolean showReload = intent.getBooleanExtra(EXTRA_SHOW_RELOAD, false);
        int retries = intent.getIntExtra(EXTRA_RETRIES, DEFAULT_RETRIES);
        String host = intent.getStringExtra(EXTRA_HOST);
        int port = intent.getIntExtra(EXTRA_PORT, 22);
        String user = intent.getStringExtra(EXTRA_USER);
        String password = intent.getStringExtra(EXTRA_PASSWORD);
        int authType = intent.getIntExtra(EXTRA_AUTH_TYPE, 0);
        String keyPath = intent.getStringExtra(EXTRA_KEY_PATH);
        if (name == null || remotePath == null || host == null || user == null) return null;
        Uri uri = (uriStr == null || uriStr.isEmpty()) ? null : Uri.parse(uriStr);
        File localFile = (localPath == null || localPath.isEmpty()) ? null : new File(localPath);
        return new TransferTask(type, name, remotePath, totalBytes, uri, localFile, showToast, showReload, retries, host, port, user, password, authType, keyPath);
    }

    private void processNextTransfer() {
        List<TransferTask> toStart = new ArrayList<>();
        TransferTask primary = null;
        synchronized (transferLock) {
            if (activeTransfers.isEmpty() && transferQueueList.isEmpty()) {
                sendUiUpdate(false, null, 0, 0, null, null);
                if (isForeground) {
                    stopForeground(true);
                    isForeground = false;
                }
                stopSelf();
                return;
            }
            while (activeTransfers.size() < MAX_PARALLEL_TRANSFERS && !transferQueueList.isEmpty()) {
                TransferTask next = transferQueueList.remove(0);
                activeTransfers.add(next);
                toStart.add(next);
                if (activeTransfer == null) {
                    activeTransfer = next;
                    transferStartTime = System.currentTimeMillis();
                    primary = next;
                }
            }
        }
        if (primary != null) {
            sendUiUpdate(true, getTitleForTask(primary), 0, 1000, "", getQueueText());
            updateNotification(getTitleForTask(primary), 0, 1000, "", getQueueText());
        } else {
            TransferTask current;
            synchronized (transferLock) {
                current = activeTransfer;
            }
            if (current != null) {
                sendUiUpdate(true, getTitleForTask(current), getCurrentProgress(current), 1000, null, getQueueText());
                updateNotification(getTitleForTask(current), getCurrentProgress(current), 1000, null, getQueueText());
            }
        }
        for (TransferTask task : toStart) {
            executor.execute(() -> runTransferTask(task));
        }
    }

    private void runTransferTask(TransferTask task) {
        boolean requeued = false;
        try {
            checkTransferCanceled(task);
            if (task.type == TYPE_DOWNLOAD) {
                performDownload(task);
                sendEvent("download_done", task.showToastOnDone ? String.format(getString(R.string.msg_download_success), task.localFile != null ? task.localFile.getAbsolutePath() : "") : null, null);
            } else if (task.type == TYPE_UPLOAD_URI) {
                performUploadUri(task);
                sendEvent("upload_done", task.showToastOnDone ? String.format(getString(R.string.msg_upload_success_fmt), task.name) : null, null);
            } else if (task.type == TYPE_UPLOAD_FILE) {
                performUploadFile(task);
                sendEvent("upload_file_done", task.showToastOnDone ? getString(R.string.msg_save_success) : null, task.showReloadPrompt ? task.remotePath : null);
            }
        } catch (CancellationException e) {
            sendEvent("canceled", getString(R.string.msg_transfer_canceled), null);
        } catch (Exception e) {
            String reason = getErrorReason(e);
            if (task.retriesRemaining > 0 && isRetryableReason(reason)) {
                task.retriesRemaining--;
                long delay = RETRY_BASE_DELAY_MS * (DEFAULT_RETRIES - task.retriesRemaining);
                if (delay > 0) {
                    String retryMessage = String.format(getString(R.string.msg_transfer_retrying_reason_fmt), task.name, reason, task.retriesRemaining);
                    updateTransferStatusMessage(task, retryMessage);
                    sendEvent("retrying", retryMessage, null);
                    try {
                        Thread.sleep(delay);
                    } catch (InterruptedException ignored) {
                        Thread.currentThread().interrupt();
                    }
                }
                synchronized (transferLock) {
                    transferQueueList.add(0, task);
                }
                requeued = true;
            } else {
                String failedMessage = buildFailedMessage(e, reason);
                updateTransferStatusMessage(task, failedMessage);
                sendEvent("failed", failedMessage, null);
            }
        } finally {
            releaseSharedHandle(task);
            TransferTask newPrimary = null;
            synchronized (transferLock) {
                activeTransfers.remove(task);
                if (!requeued) {
                    completedTransfers++;
                }
                if (task == activeTransfer) {
                    activeTransfer = activeTransfers.isEmpty() ? null : activeTransfers.get(0);
                    if (activeTransfer != null) {
                        transferStartTime = System.currentTimeMillis();
                        newPrimary = activeTransfer;
                    }
                }
            }
            if (newPrimary != null) {
                sendUiUpdate(true, getTitleForTask(newPrimary), 0, 1000, "", getQueueText());
                updateNotification(getTitleForTask(newPrimary), 0, 1000, "", getQueueText());
            }
            processNextTransfer();
        }
    }

    private void requestCancelActiveTransfer() {
        TransferTask task;
        synchronized (transferLock) {
            task = activeTransfer;
        }
        if (task == null) {
            sendEvent("no_active", getString(R.string.msg_transfer_no_active), null);
            return;
        }
        task.requestCancel();
        sendUiUpdate(true, getTitleForTask(task), getCurrentProgress(task), 1000, getString(R.string.msg_transfer_cancel_requested), getQueueText());
        updateNotification(getTitleForTask(task), getCurrentProgress(task), 1000, getString(R.string.msg_transfer_cancel_requested), getQueueText());
    }

    private void performDownload(TransferTask task) throws Exception {
        SshNative sshNative = new SshNative();
        long handle = connect(task, sshNative);
        long total = task.totalBytes;
        int blockSize = getTransferBlockSize(total);
        String escapedPath = escapePath(task.remotePath);
        File destFile = task.localFile;
        try (RandomAccessFile raf = new RandomAccessFile(destFile, "rw")) {
            long pos = 0;
            if (total > 0 && destFile.exists()) {
                long localSize = destFile.length();
                if (localSize >= total) {
                    updateTransferUI(task, total, total);
                    return;
                }
                if (localSize > 0 && localSize < total) {
                    long aligned = (localSize / blockSize) * blockSize;
                    if (aligned != localSize) {
                        raf.setLength(aligned);
                    }
                    localSize = aligned;
                    if (localSize > 0) {
                        int tailSize = (int) Math.min(RESUME_HASH_TAIL_SIZE, localSize);
                        long tailOffset = localSize - tailSize;
                        String localHash = getLocalFileTailHash(destFile, tailOffset, tailSize);
                        String remoteHash = getRemoteTailHash(sshNative, handle, escapedPath, tailOffset, tailSize);
                        if (localHash == null || remoteHash == null || !localHash.equalsIgnoreCase(remoteHash)) {
                            raf.setLength(0);
                            localSize = 0;
                        }
                    }
                    pos = localSize;
                }
            }
            if (pos > 0) {
                raf.seek(pos);
                updateTransferUI(task, pos, total);
            }
            boolean endOfFile = false;
            while (true) {
                checkTransferCanceled(task);
                if (total > 0 && pos >= total) break;
                long skipBlocksBase = pos / blockSize;
                StringBuilder batchCmd = new StringBuilder();
                int batchCount = 0;
                while (batchCount < MAX_DOWNLOAD_BATCH_CHUNKS) {
                    if (total > 0) {
                        long remaining = total - pos - (long) batchCount * blockSize;
                        if (remaining <= 0) break;
                    }
                    String cmdPart = String.format(CommandConstants.CMD_DD_BASE64, escapedPath, blockSize, skipBlocksBase + batchCount);
                    String combined = (batchCmd.length() == 0 ? "" : " ; ") + cmdPart + " ; echo '" + DOWNLOAD_SPLIT_MARKER + "'";
                    if (batchCmd.length() + combined.length() > MAX_DOWNLOAD_CMD_LENGTH && batchCount > 0) {
                        break;
                    }
                    batchCmd.append(combined);
                    batchCount++;
                }
                if (batchCount == 0) {
                    String cmdPart = String.format(CommandConstants.CMD_DD_BASE64, escapedPath, blockSize, skipBlocksBase);
                    batchCmd.append(cmdPart).append(" ; echo '").append(DOWNLOAD_SPLIT_MARKER).append("'");
                    batchCount = 1;
                }
                String response = sshNative.exec(handle, batchCmd.toString());
                if (response == null) response = "";
                int start = 0;
                int processed = 0;
                while (processed < batchCount) {
                    int markerIndex = response.indexOf(DOWNLOAD_SPLIT_MARKER, start);
                    String part = markerIndex == -1 ? response.substring(start) : response.substring(start, markerIndex);
                    byte[] content = decodeBase64Content(part);
                    if (content.length == 0) {
                        endOfFile = true;
                        break;
                    }
                    raf.write(content);
                    pos += content.length;
                    updateTransferUI(task, pos, total);
                    if (content.length < blockSize) {
                        endOfFile = true;
                        break;
                    }
                    processed++;
                    if (markerIndex == -1) break;
                    start = markerIndex + DOWNLOAD_SPLIT_MARKER.length();
                }
                if (endOfFile) break;
            }
        } finally {
            if (!task.isSharedHandle) {
                sshNative.disconnect(handle);
            }
        }
    }

    private void performUploadUri(TransferTask task) throws Exception {
        SshNative sshNative = new SshNative();
        long handle = connect(task, sshNative);
        checkTransferCanceled(task);
        String escapedPath = escapePath(task.remotePath);
        long remoteSize = getRemoteFileSize(sshNative, handle, escapedPath);
        if (remoteSize >= task.totalBytes && task.totalBytes > 0) {
            updateTransferUI(task, task.totalBytes, task.totalBytes);
            return;
        }
        boolean resume = remoteSize > 0 && remoteSize < task.totalBytes;
        if (resume) {
            int tailSize = (int) Math.min(RESUME_HASH_TAIL_SIZE, remoteSize);
            long tailOffset = remoteSize - tailSize;
            String localHash = getLocalUriTailHash(task.uri, tailOffset, tailSize);
            String remoteHash = getRemoteTailHash(sshNative, handle, escapedPath, tailOffset, tailSize);
            if (localHash == null || remoteHash == null || !localHash.equalsIgnoreCase(remoteHash)) {
                resume = false;
                remoteSize = 0;
            }
        }
        if (!resume) {
            sshNative.exec(handle, String.format(CommandConstants.CMD_TRUNCATE, escapedPath));
        }
        try (InputStream is = getContentResolver().openInputStream(task.uri)) {
            if (is == null) throw new IllegalStateException("InputStream is null");
            byte[] buffer = new byte[getTransferBlockSize(task.totalBytes)];
            long sent = 0;
            if (resume) {
                skipFully(is, remoteSize);
                sent = remoteSize;
                updateTransferUI(task, sent, Math.max(task.totalBytes, 0));
            }
            int bytesRead;
            StringBuilder batchCmd = new StringBuilder();
            int batchChunks = 0;
            while ((bytesRead = is.read(buffer)) != -1) {
                checkTransferCanceled(task);
                byte[] chunk = new byte[bytesRead];
                System.arraycopy(buffer, 0, chunk, 0, bytesRead);
                String base64 = Base64.encodeToString(chunk, Base64.NO_WRAP);
                if (batchCmd.length() > 0) batchCmd.append(" ; ");
                batchCmd.append(String.format(CommandConstants.CMD_ECHO_BASE64_APPEND, base64, escapedPath));
                batchChunks++;
                if (batchCmd.length() >= MAX_UPLOAD_CMD_LENGTH || batchChunks >= MAX_UPLOAD_BATCH_CHUNKS) {
                    sshNative.exec(handle, batchCmd.toString());
                    batchCmd.setLength(0);
                    batchChunks = 0;
                }
                sent += bytesRead;
                updateTransferUI(task, sent, Math.max(task.totalBytes, 0));
            }
            if (batchCmd.length() > 0) {
                sshNative.exec(handle, batchCmd.toString());
            }
        } finally {
            if (!task.isSharedHandle) {
                sshNative.disconnect(handle);
            }
        }
    }

    private void performUploadFile(TransferTask task) throws Exception {
        SshNative sshNative = new SshNative();
        long handle = connect(task, sshNative);
        checkTransferCanceled(task);
        String escapedPath = escapePath(task.remotePath);
        long remoteSize = getRemoteFileSize(sshNative, handle, escapedPath);
        if (remoteSize >= task.totalBytes && task.totalBytes > 0) {
            updateTransferUI(task, task.totalBytes, task.totalBytes);
            return;
        }
        boolean resume = remoteSize > 0 && remoteSize < task.totalBytes;
        if (resume) {
            int tailSize = (int) Math.min(RESUME_HASH_TAIL_SIZE, remoteSize);
            long tailOffset = remoteSize - tailSize;
            String localHash = getLocalFileTailHash(task.localFile, tailOffset, tailSize);
            String remoteHash = getRemoteTailHash(sshNative, handle, escapedPath, tailOffset, tailSize);
            if (localHash == null || remoteHash == null || !localHash.equalsIgnoreCase(remoteHash)) {
                resume = false;
                remoteSize = 0;
            }
        }
        if (!resume) {
            sshNative.exec(handle, String.format(CommandConstants.CMD_TRUNCATE, escapedPath));
        }
        try (InputStream is = new java.io.FileInputStream(task.localFile)) {
            byte[] buffer = new byte[getTransferBlockSize(task.totalBytes)];
            long sent = 0;
            if (resume) {
                skipFully(is, remoteSize);
                sent = remoteSize;
                updateTransferUI(task, sent, Math.max(task.totalBytes, 0));
            }
            int bytesRead;
            StringBuilder batchCmd = new StringBuilder();
            int batchChunks = 0;
            while ((bytesRead = is.read(buffer)) != -1) {
                checkTransferCanceled(task);
                byte[] chunk = new byte[bytesRead];
                System.arraycopy(buffer, 0, chunk, 0, bytesRead);
                String base64 = Base64.encodeToString(chunk, Base64.NO_WRAP);
                if (batchCmd.length() > 0) batchCmd.append(" ; ");
                batchCmd.append(String.format(CommandConstants.CMD_ECHO_BASE64_APPEND, base64, escapedPath));
                batchChunks++;
                if (batchCmd.length() >= MAX_UPLOAD_CMD_LENGTH || batchChunks >= MAX_UPLOAD_BATCH_CHUNKS) {
                    sshNative.exec(handle, batchCmd.toString());
                    batchCmd.setLength(0);
                    batchChunks = 0;
                }
                sent += bytesRead;
                updateTransferUI(task, sent, Math.max(task.totalBytes, 0));
            }
            if (batchCmd.length() > 0) {
                sshNative.exec(handle, batchCmd.toString());
            }
        } finally {
            if (!task.isSharedHandle) {
                sshNative.disconnect(handle);
            }
        }
    }

    private long connect(TransferTask task, SshNative sshNative) throws Exception {
        // 优先复用已连接的终端会话，减少重复认证
        String sharedKey = buildSharedKey(task.hostname, task.port, task.username);
        if (sharedKey != null && !sharedHandleInUse.containsKey(sharedKey)) {
            TerminalSession session = SessionManager.getInstance().findConnectedSession(task.hostname, task.port, task.username);
            if (session != null) {
                long handle = session.getHandle();
                if (handle != 0) {
                    Long existing = sharedHandleInUse.putIfAbsent(sharedKey, handle);
                    if (existing == null) {
                        task.isSharedHandle = true;
                        task.sharedHandleKey = sharedKey;
                        return handle;
                    }
                }
            }
        }
        long handle = sshNative.connect(task.hostname, task.port);
        if (handle == 0) throw new Exception(getString(R.string.err_connect_fail));
        int ret;
        if (task.authType == 1 && task.keyPath != null) {
            ret = sshNative.authKey(handle, task.username, task.keyPath);
        } else {
            ret = sshNative.authPassword(handle, task.username, task.password);
        }
        if (ret != 0) {
            sshNative.disconnect(handle);
            throw new Exception(getString(R.string.err_auth_fail));
        }
        task.isSharedHandle = false;
        task.sharedHandleKey = null;
        return handle;
    }

    private void releaseSharedHandle(TransferTask task) {
        if (task == null || !task.isSharedHandle) return;
        if (task.sharedHandleKey != null) {
            sharedHandleInUse.remove(task.sharedHandleKey);
        }
        task.isSharedHandle = false;
        task.sharedHandleKey = null;
    }

    private String buildSharedKey(String host, int port, String user) {
        if (host == null || user == null) return null;
        return host + ":" + port + ":" + user;
    }

    private void checkTransferCanceled(TransferTask task) {
        if (task != null && task.isCanceled()) {
            throw new CancellationException(getString(R.string.msg_transfer_canceled));
        }
    }

    private void updateTransferUI(TransferTask task, long done, long total) {
        if (task != activeTransfer) return;
        long now = System.currentTimeMillis();
        int progress = (total > 0) ? (int) (done * 1000 / total) : 0;
        if (total > 0 && done >= total) {
            task.lastUiUpdateTime = now;
            task.lastUiProgress = progress;
        } else {
            if (now - task.lastUiUpdateTime < UI_UPDATE_MIN_INTERVAL_MS && Math.abs(progress - task.lastUiProgress) < UI_UPDATE_MIN_PROGRESS_STEP) {
                return;
            }
            task.lastUiUpdateTime = now;
            task.lastUiProgress = progress;
        }
        long elapsed = Math.max(1, now - transferStartTime);
        double speed = done * 1000.0 / elapsed;
        String speedStr = formatBytes((long) speed) + "/s";
        String etaStr;
        if (total > 0 && done < total && speed > 0) {
            long remain = total - done;
            long secs = (long) Math.ceil(remain / speed);
            etaStr = formatDuration(secs);
        } else {
            etaStr = "--:--";
        }
        String subtitle = getString(R.string.transfer_speed_eta_fmt, speedStr, etaStr);
        String title = getTitleForTask(task);
        String queue = getQueueText();
        sendUiUpdate(true, title, Math.max(0, Math.min(1000, progress)), 1000, subtitle, queue);
        updateNotification(title, Math.max(0, Math.min(1000, progress)), 1000, subtitle, queue);
    }

    private void updateTransferStatusMessage(TransferTask task, String message) {
        if (task != activeTransfer) return;
        int progress = getCurrentProgress(task);
        String title = getTitleForTask(task);
        String queue = getQueueText();
        sendUiUpdate(true, title, progress, 1000, message, queue);
        updateNotification(title, progress, 1000, message, queue);
    }

    private String getRemoteTailHash(SshNative sshNative, long handle, String escapedPath, long offset, int length) {
        if (length <= 0 || offset < 0) return null;
        String cmd = String.format(Locale.ROOT, CMD_MD5_TAIL_TEMPLATE, escapedPath, offset, length);
        String result = sshNative.exec(handle, cmd);
        if (result == null) return null;
        String trimmed = result.trim();
        if (trimmed.isEmpty()) return null;
        int space = trimmed.indexOf(' ');
        return space > 0 ? trimmed.substring(0, space) : trimmed;
    }

    private String getLocalFileTailHash(File file, long offset, int length) {
        if (file == null || length <= 0 || offset < 0) return null;
        try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
            if (raf.length() < offset + length) return null;
            raf.seek(offset);
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] buffer = new byte[8192];
            int remaining = length;
            while (remaining > 0) {
                int read = raf.read(buffer, 0, Math.min(buffer.length, remaining));
                if (read <= 0) break;
                md.update(buffer, 0, read);
                remaining -= read;
            }
            if (remaining > 0) return null;
            return toHex(md.digest());
        } catch (Exception e) {
            return null;
        }
    }

    private String getLocalUriTailHash(Uri uri, long offset, int length) {
        if (uri == null || length <= 0 || offset < 0) return null;
        try (InputStream is = getContentResolver().openInputStream(uri)) {
            if (is == null) return null;
            if (offset > 0) {
                skipFully(is, offset);
            }
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] buffer = new byte[8192];
            int remaining = length;
            while (remaining > 0) {
                int read = is.read(buffer, 0, Math.min(buffer.length, remaining));
                if (read <= 0) break;
                md.update(buffer, 0, read);
                remaining -= read;
            }
            if (remaining > 0) return null;
            return toHex(md.digest());
        } catch (Exception e) {
            return null;
        }
    }

    private String toHex(byte[] data) {
        if (data == null || data.length == 0) return null;
        StringBuilder sb = new StringBuilder(data.length * 2);
        for (byte b : data) {
            sb.append(String.format(Locale.ROOT, "%02x", b));
        }
        return sb.toString();
    }

    private int getTransferBlockSize(long totalBytes) {
        if (totalBytes <= 0) return TRANSFER_BLOCK_SIZE;
        if (totalBytes >= 256L * 1024 * 1024) return 262144;
        if (totalBytes >= 64L * 1024 * 1024) return 131072;
        return TRANSFER_BLOCK_SIZE;
    }

    private String buildFailedMessage(Exception e, String reason) {
        String detail = getSafeErrorMessage(e);
        return getString(R.string.msg_transfer_failed_reason_fmt, reason, detail);
    }

    private String getErrorReason(Exception e) {
        String msg = e.getMessage();
        if (msg == null) return getString(R.string.msg_transfer_reason_unknown);
        if (msg.equals(getString(R.string.err_auth_fail))) return getString(R.string.msg_transfer_reason_auth);
        if (msg.equals(getString(R.string.err_connect_fail))) return getString(R.string.msg_transfer_reason_network);
        String lower = msg.toLowerCase(Locale.ROOT);
        if (lower.contains("permission denied") || lower.contains("access denied")) {
            return getString(R.string.msg_transfer_reason_permission);
        }
        if (lower.contains("no such file") || lower.contains("not found") || lower.contains("does not exist")) {
            return getString(R.string.msg_transfer_reason_not_found);
        }
        if (lower.contains("no space left") || lower.contains("disk full") || lower.contains("not enough space")) {
            return getString(R.string.msg_transfer_reason_storage);
        }
        if (lower.contains("timed out") || lower.contains("timeout") || lower.contains("connection")
                || lower.contains("network") || lower.contains("reset") || lower.contains("broken pipe")
                || lower.contains("eof")) {
            return getString(R.string.msg_transfer_reason_network);
        }
        return getString(R.string.msg_transfer_reason_unknown);
    }

    private boolean isRetryableReason(String reason) {
        return reason.equals(getString(R.string.msg_transfer_reason_network))
                || reason.equals(getString(R.string.msg_transfer_reason_unknown));
    }

    private String getSafeErrorMessage(Exception e) {
        String msg = e.getMessage();
        if (msg == null || msg.trim().isEmpty()) {
            return getString(R.string.msg_transfer_reason_unknown);
        }
        return msg.trim();
    }

    private long getRemoteFileSize(SshNative sshNative, long handle, String escapedPath) {
        String result = sshNative.exec(handle, "stat -c %s " + escapedPath);
        if (result == null) return -1;
        String trimmed = result.trim();
        if (trimmed.isEmpty()) return -1;
        try {
            return Long.parseLong(trimmed);
        } catch (Exception e) {
            return -1;
        }
    }

    private void skipFully(InputStream is, long bytes) throws Exception {
        long remaining = bytes;
        while (remaining > 0) {
            long skipped = is.skip(remaining);
            if (skipped <= 0) {
                int b = is.read();
                if (b == -1) break;
                skipped = 1;
            }
            remaining -= skipped;
        }
    }

    private byte[] decodeBase64Content(String content) {
        if (content == null) return new byte[0];
        String cleaned = content.replace("\n", "").replace("\r", "").trim();
        if (cleaned.isEmpty()) return new byte[0];
        return Base64.decode(cleaned, Base64.NO_WRAP);
    }

    private String getQueueText() {
        int totalCount;
        int current;
        synchronized (transferLock) {
            totalCount = completedTransfers + activeTransfers.size() + transferQueueList.size();
            current = completedTransfers + activeTransfers.size();
        }
        return getString(R.string.transfer_queue_fmt, current, totalCount);
    }

    private String getTitleForTask(TransferTask task) {
        if (task == null) return "";
        if (task.type == TYPE_DOWNLOAD) {
            return getString(R.string.action_download) + ": " + task.name;
        }
        return getString(R.string.action_upload) + ": " + task.name;
    }

    private int getCurrentProgress(TransferTask task) {
        return 0;
    }

    private void updateNotification(String title, int progress, int max, String subtitle, String queue) {
        ensureChannel();
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_action_cloud_upload)
                .setContentTitle(title == null || title.isEmpty() ? getString(R.string.notification_transfer_title) : title)
                .setContentText(buildContentText(subtitle, queue))
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setProgress(max, progress, max <= 0);

        Intent openIntent = new Intent(this, SftpActivity.class);
        openIntent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pi = PendingIntent.getActivity(
                this, 0, openIntent,
                Build.VERSION.SDK_INT >= 31
                        ? PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE
                        : PendingIntent.FLAG_UPDATE_CURRENT
        );
        builder.setContentIntent(pi);

        if (!isForeground) {
            startForeground(NOTIFICATION_ID, builder.build());
            isForeground = true;
        } else {
            NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm != null) nm.notify(NOTIFICATION_ID, builder.build());
        }
    }

    private void sendUiUpdate(boolean visible, String title, int progress, int max, String subtitle, String queue) {
        Intent intent = new Intent(ACTION_UI_UPDATE);
        intent.putExtra(EXTRA_VISIBLE, visible);
        intent.putExtra(EXTRA_TITLE, title);
        intent.putExtra(EXTRA_PROGRESS, progress);
        intent.putExtra(EXTRA_MAX, max);
        intent.putExtra(EXTRA_SUBTITLE, subtitle);
        intent.putExtra(EXTRA_QUEUE, queue);
        sendBroadcast(intent);
    }

    private void sendEvent(String event, String message, String remotePath) {
        Intent intent = new Intent(ACTION_UI_EVENT);
        intent.putExtra(EXTRA_EVENT, event);
        intent.putExtra(EXTRA_MESSAGE, message);
        if (remotePath != null) {
            intent.putExtra(EXTRA_REMOTE_PATH, remotePath);
        }
        sendBroadcast(intent);
    }

    private String escapePath(String path) {
        return "'" + path.replace("'", "'\\''") + "'";
    }

    private String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + "B";
        double kb = bytes / 1024.0;
        if (kb < 1024) return String.format(java.util.Locale.getDefault(), "%.1fKB", kb);
        double mb = kb / 1024.0;
        if (mb < 1024) return String.format(java.util.Locale.getDefault(), "%.1fMB", mb);
        double gb = mb / 1024.0;
        return String.format(java.util.Locale.getDefault(), "%.1fGB", gb);
    }

    private String formatDuration(long seconds) {
        long m = seconds / 60;
        long s = seconds % 60;
        return String.format(java.util.Locale.getDefault(), "%02d:%02d", m, s);
    }

    private static class TransferTask {
        final int type;
        final String name;
        final String remotePath;
        final long totalBytes;
        final Uri uri;
        final File localFile;
        final boolean showToastOnDone;
        final boolean showReloadPrompt;
        final AtomicBoolean cancelRequested = new AtomicBoolean(false);
        int retriesRemaining;
        final String hostname;
        final int port;
        final String username;
        final String password;
        final int authType;
        final String keyPath;
        boolean isSharedHandle = false;
        String sharedHandleKey;
        long lastUiUpdateTime = 0;
        int lastUiProgress = 0;

        TransferTask(int type, String name, String remotePath, long totalBytes, Uri uri, File localFile, boolean showToastOnDone, boolean showReloadPrompt, int retriesRemaining, String hostname, int port, String username, String password, int authType, String keyPath) {
            this.type = type;
            this.name = name;
            this.remotePath = remotePath;
            this.totalBytes = totalBytes;
            this.uri = uri;
            this.localFile = localFile;
            this.showToastOnDone = showToastOnDone;
            this.showReloadPrompt = showReloadPrompt;
            this.retriesRemaining = retriesRemaining;
            this.hostname = hostname;
            this.port = port;
            this.username = username;
            this.password = password;
            this.authType = authType;
            this.keyPath = keyPath;
        }

        void requestCancel() {
            cancelRequested.set(true);
        }

        boolean isCanceled() {
            return cancelRequested.get();
        }
    }
}
