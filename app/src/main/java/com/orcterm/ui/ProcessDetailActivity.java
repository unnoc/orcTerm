package com.orcterm.ui;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.MenuItem;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.orcterm.R;
import com.orcterm.core.session.SessionConnector;
import com.orcterm.core.ssh.SshNative;
import com.orcterm.data.AppDatabase;
import com.orcterm.data.HostEntity;
import com.orcterm.util.CommandConstants;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 进程详情界面
 */
public class ProcessDetailActivity extends AppCompatActivity {

    private TextView tvBasic;
    private TextView tvMem;
    private TextView tvFiles;
    private TextView tvEnv;
    private TextView tvNet;
    private MaterialButton btnDone;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler handler = new Handler(Looper.getMainLooper());

    private long hostId;
    private int pid;
    private HostEntity currentHost;
    private SshNative ssh;
    private long sshHandle = 0;
    private boolean isSharedSession = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_process_detail);

        hostId = getIntent().getLongExtra("host_id", -1);
        pid = getIntent().getIntExtra("pid", -1);
        if (hostId == -1 || pid <= 0) {
            Toast.makeText(this, "Invalid process", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        setSupportActionBar(findViewById(R.id.toolbar));
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        tvBasic = findViewById(R.id.tv_basic);
        tvMem = findViewById(R.id.tv_mem);
        tvFiles = findViewById(R.id.tv_files);
        tvEnv = findViewById(R.id.tv_env);
        tvNet = findViewById(R.id.tv_net);
        btnDone = findViewById(R.id.btn_done);
        btnDone.setOnClickListener(v -> finish());

        ssh = new SshNative();
        loadHostAndFetch();
    }

    private void loadHostAndFetch() {
        executor.execute(() -> {
            currentHost = AppDatabase.getDatabase(this).hostDao().findById(hostId);
            if (currentHost == null) {
                handler.post(() -> {
                    Toast.makeText(this, "Host not found", Toast.LENGTH_SHORT).show();
                    finish();
                });
                return;
            }
            try {
                connectSsh();
                fetchDetail();
            } catch (Exception e) {
                handler.post(() -> Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show());
            }
        });
    }

    private void connectSsh() throws Exception {
        if (sshHandle != 0) return;
        SessionConnector.Connection connection = SessionConnector.acquire(
                ssh,
                currentHost.hostname,
                currentHost.port,
                currentHost.username,
                currentHost.password,
                currentHost.authType,
                currentHost.keyPath,
                "Connect failed",
                "Auth failed"
        );
        sshHandle = connection.getHandle();
        isSharedSession = connection.isShared();
    }

    private void fetchDetail() {
        String pidStr = String.valueOf(pid);
        String output = ssh.exec(sshHandle, String.format(CommandConstants.CMD_PROCESS_DETAIL_TEMPLATE, pidStr, pidStr, pidStr, pidStr, pidStr));
        parseDetail(output);
    }

    private void parseDetail(String output) {
        String[] sections = output.split("SECTION_");
        String basic = extractSection(sections, 1);
        String mem = extractSection(sections, 2);
        String files = extractSection(sections, 3);
        String env = extractSection(sections, 4);
        String net = extractSection(sections, 5);
        handler.post(() -> {
            tvBasic.setText(fallbackText(basic));
            tvMem.setText(fallbackText(mem));
            tvFiles.setText(fallbackText(files));
            tvEnv.setText(fallbackText(env));
            tvNet.setText(fallbackText(net));
        });
    }

    private String extractSection(String[] sections, int index) {
        if (sections.length <= index) return "";
        String s = sections[index];
        int pos = s.indexOf('\n');
        if (pos >= 0 && pos + 1 < s.length()) {
            s = s.substring(pos + 1);
        } else {
            s = "";
        }
        return s.trim();
    }

    private String fallbackText(String text) {
        if (text == null || text.isEmpty()) return "无";
        return text;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        final long handle = sshHandle;
        final boolean shared = isSharedSession;
        sshHandle = 0;
        isSharedSession = false;
        executor.shutdownNow();
        if (handle != 0 && !shared) {
            new Thread(() -> ssh.disconnect(handle)).start();
        }
    }
}
