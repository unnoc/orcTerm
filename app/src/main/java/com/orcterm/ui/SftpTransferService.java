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
import com.orcterm.core.ssh.SshNative;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

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

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Object transferLock = new Object();
    private final List<TransferTask> transferQueueList = new ArrayList<>();
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
        synchronized (transferLock) {
            if (activeTransfer != null) return;
            if (transferQueueList.isEmpty()) {
                sendUiUpdate(false, null, 0, 0, null, null);
                if (isForeground) {
                    stopForeground(true);
                    isForeground = false;
                }
                stopSelf();
                return;
            }
            activeTransfer = transferQueueList.remove(0);
            transferStartTime = System.currentTimeMillis();
            sendUiUpdate(true, getTitleForTask(activeTransfer), 0, 1000, "", getQueueText());
            updateNotification(getTitleForTask(activeTransfer), 0, 1000, "", getQueueText());
        }
        executor.execute(() -> {
            final TransferTask task = activeTransfer;
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
                if (task != null && task.retriesRemaining > 0) {
                    task.retriesRemaining--;
                    synchronized (transferLock) {
                        transferQueueList.add(0, task);
                    }
                    requeued = true;
                    sendEvent("retrying", String.format(getString(R.string.msg_transfer_retrying_fmt), task.name, task.retriesRemaining), null);
                } else {
                    sendEvent("failed", String.format(getString(R.string.msg_error), e.getMessage()), null);
                }
            } finally {
                synchronized (transferLock) {
                    if (!requeued) {
                        completedTransfers++;
                    }
                    activeTransfer = null;
                }
                processNextTransfer();
            }
        });
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
        File destFile = task.localFile;
        try (FileOutputStream fos = new FileOutputStream(destFile)) {
            long pos = 0;
            while (true) {
                checkTransferCanceled(task);
                int chunk = (total > 0) ? (int) Math.min(TRANSFER_BLOCK_SIZE, total - pos) : TRANSFER_BLOCK_SIZE;
                if (total > 0 && chunk <= 0) break;
                long skipBlocks = pos / TRANSFER_BLOCK_SIZE;
                String cmd = "dd if=" + escapePath(task.remotePath) + " bs=" + TRANSFER_BLOCK_SIZE + " skip=" + skipBlocks + " count=1 status=none | base64";
                String base64Content = sshNative.exec(handle, cmd);
                if (base64Content == null) base64Content = "";
                byte[] content = Base64.decode(base64Content.replace("\n", ""), Base64.NO_WRAP);
                if (content.length == 0) {
                    break;
                }
                fos.write(content);
                pos += content.length;
                updateTransferUI(task, pos, total);
                if (content.length < TRANSFER_BLOCK_SIZE) break;
            }
        } finally {
            sshNative.disconnect(handle);
        }
    }

    private void performUploadUri(TransferTask task) throws Exception {
        SshNative sshNative = new SshNative();
        long handle = connect(task, sshNative);
        checkTransferCanceled(task);
        sshNative.exec(handle, "> " + escapePath(task.remotePath));
        try (InputStream is = getContentResolver().openInputStream(task.uri)) {
            if (is == null) throw new IllegalStateException("InputStream is null");
            byte[] buffer = new byte[16384];
            long sent = 0;
            int bytesRead;
            while ((bytesRead = is.read(buffer)) != -1) {
                checkTransferCanceled(task);
                byte[] chunk = new byte[bytesRead];
                System.arraycopy(buffer, 0, chunk, 0, bytesRead);
                String base64 = Base64.encodeToString(chunk, Base64.NO_WRAP);
                String cmd = "echo \"" + base64 + "\" | base64 -d >> " + escapePath(task.remotePath);
                sshNative.exec(handle, cmd);
                sent += bytesRead;
                updateTransferUI(task, sent, Math.max(task.totalBytes, 0));
            }
        } finally {
            sshNative.disconnect(handle);
        }
    }

    private void performUploadFile(TransferTask task) throws Exception {
        SshNative sshNative = new SshNative();
        long handle = connect(task, sshNative);
        checkTransferCanceled(task);
        sshNative.exec(handle, "> " + escapePath(task.remotePath));
        try (InputStream is = new java.io.FileInputStream(task.localFile)) {
            byte[] buffer = new byte[16384];
            long sent = 0;
            int bytesRead;
            while ((bytesRead = is.read(buffer)) != -1) {
                checkTransferCanceled(task);
                byte[] chunk = new byte[bytesRead];
                System.arraycopy(buffer, 0, chunk, 0, bytesRead);
                String base64 = Base64.encodeToString(chunk, Base64.NO_WRAP);
                String cmd = "echo \"" + base64 + "\" | base64 -d >> " + escapePath(task.remotePath);
                sshNative.exec(handle, cmd);
                sent += bytesRead;
                updateTransferUI(task, sent, Math.max(task.totalBytes, 0));
            }
        } finally {
            sshNative.disconnect(handle);
        }
    }

    private long connect(TransferTask task, SshNative sshNative) throws Exception {
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
        return handle;
    }

    private void checkTransferCanceled(TransferTask task) {
        if (task != null && task.isCanceled()) {
            throw new CancellationException(getString(R.string.msg_transfer_canceled));
        }
    }

    private void updateTransferUI(TransferTask task, long done, long total) {
        long now = System.currentTimeMillis();
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
        int progress = (total > 0) ? (int) (done * 1000 / total) : 0;
        String subtitle = getString(R.string.transfer_speed_eta_fmt, speedStr, etaStr);
        String title = getTitleForTask(task);
        String queue = getQueueText();
        sendUiUpdate(true, title, Math.max(0, Math.min(1000, progress)), 1000, subtitle, queue);
        updateNotification(title, Math.max(0, Math.min(1000, progress)), 1000, subtitle, queue);
    }

    private String getQueueText() {
        int totalCount;
        int current;
        synchronized (transferLock) {
            totalCount = completedTransfers + (activeTransfer != null ? 1 : 0) + transferQueueList.size();
            current = completedTransfers + (activeTransfer != null ? 1 : 0);
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
