package com.orcterm.ui;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.orcterm.R;
import com.orcterm.core.ssh.SshNative;
import com.orcterm.util.CommandConstants;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Docker 容器日志 Activity
 */
public class DockerLogsActivity extends AppCompatActivity {

    private TextView textLogs;
    private ScrollView scrollView;
    
    private SshNative sshNative;
    private long sshHandle = 0;
    
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private boolean isReading = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_docker_logs);
        
        setSupportActionBar(findViewById(R.id.toolbar));
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        
        String containerName = getIntent().getStringExtra("container_name");
        getSupportActionBar().setTitle("日志: " + containerName);

        textLogs = findViewById(R.id.text_logs);
        scrollView = findViewById(R.id.scroll_view);

        String hostname = getIntent().getStringExtra("hostname");
        int port = getIntent().getIntExtra("port", 22);
        String username = getIntent().getStringExtra("username");
        String password = getIntent().getStringExtra("password");
        int authType = getIntent().getIntExtra("auth_type", 0);
        String keyPath = getIntent().getStringExtra("key_path");
        String containerId = getIntent().getStringExtra("container_id");

        sshNative = new SshNative();
        startLogStream(hostname, port, username, password, authType, keyPath, containerId);
    }

    private void startLogStream(String host, int port, String user, String pass, int authType, String keyPath, String containerId) {
        executor.execute(() -> {
            try {
                // 连接新会话以获取日志
                sshHandle = sshNative.connect(host, port);
                
                int ret;
                if (authType == 1 && keyPath != null) {
                    ret = sshNative.authKey(sshHandle, user, keyPath);
                } else {
                    ret = sshNative.authPassword(sshHandle, user, pass);
                }

                if (sshHandle != 0 && ret == 0) {
                    
                    // 暂时只获取最后100行
                    // "docker logs --tail 100 <id>"
                    String logs = sshNative.exec(sshHandle, String.format(CommandConstants.CMD_DOCKER_LOGS_TAIL, containerId));
                    
                    mainHandler.post(() -> {
                        textLogs.setText(logs);
                        scrollView.fullScroll(ScrollView.FOCUS_DOWN);
                    });
                    
                } else {
                    mainHandler.post(() -> Toast.makeText(this, "连接失败", Toast.LENGTH_SHORT).show());
                }
            } catch (Exception e) {
                 mainHandler.post(() -> textLogs.setText("错误: " + e.getMessage()));
            }
        });
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        isReading = false;
        new Thread(() -> {
            if (sshHandle != 0) {
                sshNative.disconnect(sshHandle);
                sshHandle = 0;
            }
        }).start();
        executor.shutdownNow();
    }
}
