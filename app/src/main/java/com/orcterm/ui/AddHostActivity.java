package com.orcterm.ui;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.View;
import android.widget.Button;
import android.widget.RadioGroup;
import android.widget.Toast;
import android.content.Intent;
import android.net.Uri;
import android.content.SharedPreferences;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.graphics.Color;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.card.MaterialCardView;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.orcterm.core.session.HostKeyVerifier;
import com.orcterm.R;
import com.orcterm.core.session.SessionConnector;
import com.orcterm.data.HostEntity;
import com.orcterm.core.ssh.SshNative;
import com.orcterm.util.CommandConstants;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 添加主机界面
 */
public class AddHostActivity extends AppCompatActivity {

    private TextInputEditText editAlias;
    private TextInputEditText editHostname;
    private TextInputEditText editPort;
    private TextInputEditText editUsername;
    private TextInputEditText editPassword;
    private TextInputEditText editKeyPath;
    private RadioGroup radioGroupAuth;
    private RadioGroup radioGroupContainer;
    private TextInputLayout layoutPassword;
    private View layoutKey;
    private TextInputEditText editTimeout;
    private TextInputEditText editKeepalive;
    private RadioGroup radioGroupHostKey;
    private RadioGroup radioGroupEnv;
    private RadioGroup radioGroupTheme;
    private View layoutAdvanced;
    private Button buttonToggleAdvanced;
    private Button buttonTest;
    private Button buttonSave;
    private Button buttonSelectKey;
    private ProgressBar progressTesting;
    private TextView textSecurityStatus;
    private TextView textHostKeyType;
    private TextView textHostKeyFingerprint;
    private MaterialCardView cardEnvBadge;
    private TextView textEnvBadge;
    
    private MainViewModel mViewModel;
    private ActivityResultLauncher<Intent> filePickerLauncher;
    private int currentAuthType = 0; // 0: password, 1: key
    private String currentContainerEngine = CommandConstants.CMD_ENGINE_DOCKER;
    private int currentHostKeyPolicy = 1;
    private int currentEnvironmentType = 2;
    private String currentThemePreset = "default";
    private boolean isEdit = false;
    private long editHostId = -1;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private boolean isTesting = false;
    private boolean testPassed = false;
    private boolean hostKeyConfirmed = false;
    private String lastHostKeyType = "-";
    private String lastHostKeyFingerprint = "-";
    private boolean suppressChangeEvents = false;
    private String initialHostname;
    private int initialPort;
    private String initialUsername;
    private int initialAuthType;
    private String initialPassword;
    private String initialKeyPath;
    private int initialTimeout;
    private int initialKeepalive;
    private int initialHostKeyPolicy;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_host);

        suppressChangeEvents = true;
        mViewModel = new ViewModelProvider(this).get(MainViewModel.class);

        editAlias = findViewById(R.id.edit_alias);
        editHostname = findViewById(R.id.edit_hostname);
        editPort = findViewById(R.id.edit_port);
        editUsername = findViewById(R.id.edit_username);
        editPassword = findViewById(R.id.edit_password);
        editKeyPath = findViewById(R.id.edit_key_path);
        
        radioGroupAuth = findViewById(R.id.radio_group_auth);
        radioGroupContainer = findViewById(R.id.radio_group_container);
        layoutPassword = findViewById(R.id.layout_password);
        layoutKey = findViewById(R.id.layout_key);
        buttonSelectKey = findViewById(R.id.button_select_key);
        buttonSave = findViewById(R.id.button_save);
        buttonToggleAdvanced = findViewById(R.id.button_toggle_advanced);
        layoutAdvanced = findViewById(R.id.layout_advanced);
        editTimeout = findViewById(R.id.edit_timeout);
        editKeepalive = findViewById(R.id.edit_keepalive);
        radioGroupHostKey = findViewById(R.id.radio_group_hostkey);
        radioGroupEnv = findViewById(R.id.radio_group_env);
        radioGroupTheme = findViewById(R.id.radio_group_theme);
        buttonTest = findViewById(R.id.button_test);
        progressTesting = findViewById(R.id.progress_testing);
        textSecurityStatus = findViewById(R.id.text_security_status);
        textHostKeyType = findViewById(R.id.text_hostkey_type);
        textHostKeyFingerprint = findViewById(R.id.text_hostkey_fingerprint);
        cardEnvBadge = findViewById(R.id.card_env_badge);
        textEnvBadge = findViewById(R.id.text_env_badge);

        setupAuthTypeListener();
        setupContainerEngineListener();
        setupFilePicker();

        buttonSelectKey.setOnClickListener(v -> openFilePicker());
        buttonSave.setOnClickListener(v -> saveHost());
        buttonToggleAdvanced.setOnClickListener(v -> toggleAdvanced());
        buttonTest.setOnClickListener(v -> testConnection());
        
        initViewFromIntent();
        setupAdvancedListeners();
        setupChangeListeners();
        captureInitialConnectionState();
        suppressChangeEvents = false;
        updateEnvBadge(currentEnvironmentType);
        updateSecurityStatus("未测试", 0xFFB0BEC5);
    }

    private void initViewFromIntent() {
        Intent intent = getIntent();
        isEdit = intent.getBooleanExtra("is_edit", false);
        if (isEdit) {
            editHostId = intent.getLongExtra("host_id", -1);
            editAlias.setText(intent.getStringExtra("alias"));
            editHostname.setText(intent.getStringExtra("hostname"));
            editPort.setText(String.valueOf(intent.getIntExtra("port", 22)));
            editUsername.setText(intent.getStringExtra("username"));
            
            currentAuthType = intent.getIntExtra("auth_type", 0);
            if (currentAuthType == 0) {
                radioGroupAuth.check(R.id.radio_auth_password);
                editPassword.setText(intent.getStringExtra("password"));
                layoutPassword.setVisibility(View.VISIBLE);
                layoutKey.setVisibility(View.GONE);
            } else {
                radioGroupAuth.check(R.id.radio_auth_key);
                editKeyPath.setText(intent.getStringExtra("key_path"));
                layoutPassword.setVisibility(View.GONE);
                layoutKey.setVisibility(View.VISIBLE);
            }
            
            currentContainerEngine = intent.getStringExtra("container_engine");
            if (CommandConstants.CMD_ENGINE_PODMAN.equals(currentContainerEngine)) {
                radioGroupContainer.check(R.id.radio_container_podman);
            } else {
                radioGroupContainer.check(R.id.radio_container_docker);
                currentContainerEngine = CommandConstants.CMD_ENGINE_DOCKER;
            }

            int timeout = intent.getIntExtra("connect_timeout_sec", 10);
            int keepalive = intent.getIntExtra("keepalive_interval_sec", 0);
            currentHostKeyPolicy = intent.getIntExtra("hostkey_policy", 1);
            currentEnvironmentType = intent.getIntExtra("environment_type", 2);
            currentThemePreset = intent.getStringExtra("terminal_theme_preset");
            if (currentThemePreset == null || currentThemePreset.isEmpty()) {
                currentThemePreset = "default";
            }

            editTimeout.setText(String.valueOf(timeout));
            editKeepalive.setText(String.valueOf(keepalive));
            applyHostKeyPolicySelection(currentHostKeyPolicy);
            applyEnvironmentSelection(currentEnvironmentType);
            applyThemeSelection(currentThemePreset);

            if (getSupportActionBar() != null) {
                getSupportActionBar().setTitle("编辑主机");
            }
        }
    }

    private void setupAuthTypeListener() {
        radioGroupAuth.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId == R.id.radio_auth_password) {
                currentAuthType = 0;
                layoutPassword.setVisibility(View.VISIBLE);
                layoutKey.setVisibility(View.GONE);
            } else if (checkedId == R.id.radio_auth_key) {
                currentAuthType = 1;
                layoutPassword.setVisibility(View.GONE);
                layoutKey.setVisibility(View.VISIBLE);
            }
            markTestRequired();
        });
    }

    private void setupContainerEngineListener() {
        radioGroupContainer.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId == R.id.radio_container_docker) {
                currentContainerEngine = CommandConstants.CMD_ENGINE_DOCKER;
            } else if (checkedId == R.id.radio_container_podman) {
                currentContainerEngine = CommandConstants.CMD_ENGINE_PODMAN;
            }
        });
    }

    private void setupAdvancedListeners() {
        radioGroupHostKey.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId == R.id.radio_hostkey_strict) {
                currentHostKeyPolicy = 0;
            } else if (checkedId == R.id.radio_hostkey_accept_once) {
                currentHostKeyPolicy = 2;
            } else {
                currentHostKeyPolicy = 1;
            }
            markTestRequired();
        });

        radioGroupEnv.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId == R.id.radio_env_prod) {
                currentEnvironmentType = 0;
            } else if (checkedId == R.id.radio_env_staging) {
                currentEnvironmentType = 1;
            } else {
                currentEnvironmentType = 2;
            }
            updateEnvBadge(currentEnvironmentType);
        });

        radioGroupTheme.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId == R.id.radio_theme_light) {
                currentThemePreset = "light";
            } else if (checkedId == R.id.radio_theme_contrast) {
                currentThemePreset = "high_contrast";
            } else {
                currentThemePreset = "default";
            }
        });
    }

    private void setupFilePicker() {
        filePickerLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    Uri uri = result.getData().getData();
                    if (uri != null) {
                        handleSelectedFile(uri);
                    }
                }
            }
        );
    }

    private void openFilePicker() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("*/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        filePickerLauncher.launch(intent);
    }

    private void handleSelectedFile(Uri uri) {
        try (InputStream is = getContentResolver().openInputStream(uri)) {
            if (is == null) return;
            File targetDir = new File(getFilesDir(), "keys");
            if (!targetDir.exists()) targetDir.mkdirs();
            
            String fileName = "key_" + System.currentTimeMillis();
            File dest = new File(targetDir, fileName);
            
            try (FileOutputStream fos = new FileOutputStream(dest)) {
                byte[] buffer = new byte[4096];
                int read;
                while ((read = is.read(buffer)) != -1) {
                    fos.write(buffer, 0, read);
                }
            }
            editKeyPath.setText(dest.getAbsolutePath());
        } catch (IOException e) {
            e.printStackTrace();
            Toast.makeText(this, "读取文件失败", Toast.LENGTH_SHORT).show();
        }
    }

    private void saveHost() {
        if (!isEdit || isConnectionConfigChanged()) {
            if (!testPassed || !hostKeyConfirmed) {
                Toast.makeText(this, "请先测试连接并确认 Host Key", Toast.LENGTH_SHORT).show();
                return;
            }
        }

        String hostname = editHostname.getText().toString().trim();
        String username = editUsername.getText().toString().trim();
        String portStr = editPort.getText().toString().trim();
        String password = editPassword.getText().toString().trim();
        String keyPath = editKeyPath.getText().toString().trim();
        String alias = editAlias.getText().toString().trim();

        if (TextUtils.isEmpty(hostname) || TextUtils.isEmpty(username)) {
            Toast.makeText(this, "主机名和用户名不能为空", Toast.LENGTH_SHORT).show();
            return;
        }

        int port = 22;
        try {
            port = Integer.parseInt(portStr);
        } catch (NumberFormatException e) {
            // ignore
        }

        if (TextUtils.isEmpty(alias)) {
            alias = hostname;
        }

        HostEntity host = new HostEntity(alias, hostname, username);
        if (isEdit && editHostId != -1) {
            host.id = editHostId;
        }
        
        host.port = port;
        host.authType = currentAuthType;
        host.containerEngine = currentContainerEngine;
        host.connectTimeoutSec = parseInt(editTimeout.getText().toString().trim(), 10);
        host.keepAliveIntervalSec = parseInt(editKeepalive.getText().toString().trim(), 0);
        host.keepAliveReply = true;
        host.hostKeyPolicy = currentHostKeyPolicy;
        host.environmentType = currentEnvironmentType;
        host.terminalThemePreset = currentThemePreset;
        
        if (currentAuthType == 0) {
            host.password = password;
        } else {
            if (TextUtils.isEmpty(keyPath)) {
                Toast.makeText(this, "请选择或输入密钥路径", Toast.LENGTH_SHORT).show();
                return;
            }
            host.keyPath = keyPath;
        }

        // Restore other fields if editing
        if (isEdit) {
            host.osName = getIntent().getStringExtra("os_name");
            host.osVersion = getIntent().getStringExtra("os_version");
            host.status = getIntent().getStringExtra("status");
            host.lastConnected = getIntent().getLongExtra("last_connected", 0);
            
            mViewModel.update(host);
            Toast.makeText(this, "更新成功", Toast.LENGTH_SHORT).show();
        } else {
            mViewModel.insert(host);
        }
        fetchMacForWol(host);
        finish();
    }

    private void testConnection() {
        if (isTesting) return;

        String hostname = editHostname.getText().toString().trim();
        String username = editUsername.getText().toString().trim();
        String portStr = editPort.getText().toString().trim();
        String password = editPassword.getText().toString().trim();
        String keyPath = editKeyPath.getText().toString().trim();

        if (TextUtils.isEmpty(hostname) || TextUtils.isEmpty(username)) {
            Toast.makeText(this, "主机名和用户名不能为空", Toast.LENGTH_SHORT).show();
            return;
        }

        if (currentAuthType == 0) {
            if (TextUtils.isEmpty(password)) {
                Toast.makeText(this, "请输入密码", Toast.LENGTH_SHORT).show();
                return;
            }
        } else {
            if (TextUtils.isEmpty(keyPath)) {
                Toast.makeText(this, "请选择或输入密钥路径", Toast.LENGTH_SHORT).show();
                return;
            }
        }

        int port = parseInt(portStr, 22);
        int timeoutSec = parseInt(editTimeout.getText().toString().trim(), 10);
        int keepaliveSec = parseInt(editKeepalive.getText().toString().trim(), 0);

        setTestingState(true);
        updateSecurityStatus("测试中...", 0xFF90A4AE);
        textHostKeyType.setText("Host Key: -");
        textHostKeyFingerprint.setText("指纹: -");
        testPassed = false;
        hostKeyConfirmed = false;

        executor.execute(() -> {
            SshNative ssh = new SshNative();
            long handle = 0;
            try {
                handle = SessionConnector.connectOnly(ssh, hostname, port, "网络不可达或握手失败");

                ssh.setSessionTimeout(handle, Math.max(1, timeoutSec) * 1000);
                ssh.setSessionReadTimeout(handle, 60);
                ssh.setKeepaliveConfig(handle, true, Math.max(0, keepaliveSec));

                HostKeyVerifier.HostKeyInfo hostKeyInfo = HostKeyVerifier.parseHostKeyInfo(ssh.getHostKeyInfo(handle));
                lastHostKeyType = hostKeyInfo.getKeyType();
                lastHostKeyFingerprint = hostKeyInfo.getFingerprint();

                String knownHostsPath = ensureKnownHostsPath();
                HostKeyVerifier.VerifyResult verifyResult = HostKeyVerifier.verify(
                        ssh,
                        handle,
                        hostname,
                        port,
                        username,
                        knownHostsPath,
                        currentHostKeyPolicy,
                        hostKeyInfo,
                        challenge -> {
                            runOnUiThread(() -> updateSecurityStatus(
                                    challenge.getReason() == HostKeyVerifier.CHECK_CHANGED ? "Host Key 发生变化" : "首次连接需要确认",
                                    0xFFFFA000
                            ));
                            CountDownLatch latch = new CountDownLatch(1);
                            AtomicInteger decision = new AtomicInteger(0);
                            runOnUiThread(() -> showHostKeyConfirmDialog(
                                    challenge.getHostname(),
                                    challenge.getPort(),
                                    challenge.getUsername(),
                                    challenge.getKeyType(),
                                    challenge.getFingerprint(),
                                    challenge.getReason(),
                                    decision,
                                    latch
                            ));
                            latch.await();
                            return decision.get();
                        }
                );
                hostKeyConfirmed = verifyResult.isTrusted();

                SessionConnector.authenticate(
                        ssh,
                        handle,
                        username,
                        password,
                        currentAuthType,
                        keyPath,
                        "认证失败"
                );

                postSuccess();
            } catch (Exception e) {
                String message = e.getMessage();
                postFailure(TextUtils.isEmpty(message) ? "测试异常" : message);
            } finally {
                if (handle != 0) {
                    ssh.disconnect(handle);
                }
            }
        });
    }

    private void postSuccess() {
        runOnUiThread(() -> {
            testPassed = true;
            updateSecurityStatus("连接成功，主机可用", 0xFF4CAF50);
            textHostKeyType.setText("Host Key: " + lastHostKeyType);
            textHostKeyFingerprint.setText("指纹: " + lastHostKeyFingerprint);
            setTestingState(false);
            buttonSave.setEnabled(true);
        });
    }

    private void postFailure(String message) {
        runOnUiThread(() -> {
            testPassed = false;
            hostKeyConfirmed = false;
            updateSecurityStatus(message, 0xFFE53935);
            textHostKeyType.setText("Host Key: " + lastHostKeyType);
            textHostKeyFingerprint.setText("指纹: " + lastHostKeyFingerprint);
            setTestingState(false);
            buttonSave.setEnabled(false);
        });
    }

    private void setTestingState(boolean testing) {
        isTesting = testing;
        progressTesting.setVisibility(testing ? View.VISIBLE : View.GONE);
        buttonTest.setEnabled(!testing);
        buttonSave.setEnabled(testPassed && !testing);
        buttonSelectKey.setEnabled(!testing);
        editAlias.setEnabled(!testing);
        editHostname.setEnabled(!testing);
        editPort.setEnabled(!testing);
        editUsername.setEnabled(!testing);
        editPassword.setEnabled(!testing);
        editKeyPath.setEnabled(!testing);
        radioGroupAuth.setEnabled(!testing);
        for (int i = 0; i < radioGroupAuth.getChildCount(); i++) {
            radioGroupAuth.getChildAt(i).setEnabled(!testing);
        }
        radioGroupContainer.setEnabled(!testing);
        for (int i = 0; i < radioGroupContainer.getChildCount(); i++) {
            radioGroupContainer.getChildAt(i).setEnabled(!testing);
        }
        buttonToggleAdvanced.setEnabled(!testing);
        editTimeout.setEnabled(!testing);
        editKeepalive.setEnabled(!testing);
        for (int i = 0; i < radioGroupHostKey.getChildCount(); i++) {
            radioGroupHostKey.getChildAt(i).setEnabled(!testing);
        }
        for (int i = 0; i < radioGroupEnv.getChildCount(); i++) {
            radioGroupEnv.getChildAt(i).setEnabled(!testing);
        }
        for (int i = 0; i < radioGroupTheme.getChildCount(); i++) {
            radioGroupTheme.getChildAt(i).setEnabled(!testing);
        }
    }

    private void setupChangeListeners() {
        TextWatcher watcher = new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                markTestRequired();
            }
        };
        editHostname.addTextChangedListener(watcher);
        editPort.addTextChangedListener(watcher);
        editUsername.addTextChangedListener(watcher);
        editPassword.addTextChangedListener(watcher);
        editKeyPath.addTextChangedListener(watcher);
        editTimeout.addTextChangedListener(watcher);
        editKeepalive.addTextChangedListener(watcher);
    }

    private void markTestRequired() {
        if (suppressChangeEvents || isTesting) return;
        testPassed = false;
        hostKeyConfirmed = false;
        updateSecurityStatus("未测试", 0xFFB0BEC5);
        textHostKeyType.setText("Host Key: -");
        textHostKeyFingerprint.setText("指纹: -");
        buttonSave.setEnabled(false);
    }

    private void captureInitialConnectionState() {
        initialHostname = editHostname.getText().toString().trim();
        initialPort = parseInt(editPort.getText().toString().trim(), 22);
        initialUsername = editUsername.getText().toString().trim();
        initialAuthType = currentAuthType;
        initialPassword = editPassword.getText().toString();
        initialKeyPath = editKeyPath.getText().toString();
        initialTimeout = parseInt(editTimeout.getText().toString().trim(), 10);
        initialKeepalive = parseInt(editKeepalive.getText().toString().trim(), 0);
        initialHostKeyPolicy = currentHostKeyPolicy;
    }

    private boolean isConnectionConfigChanged() {
        String hostname = editHostname.getText().toString().trim();
        int port = parseInt(editPort.getText().toString().trim(), 22);
        String username = editUsername.getText().toString().trim();
        int timeout = parseInt(editTimeout.getText().toString().trim(), 10);
        int keepalive = parseInt(editKeepalive.getText().toString().trim(), 0);
        if (!TextUtils.equals(initialHostname, hostname)) return true;
        if (initialPort != port) return true;
        if (!TextUtils.equals(initialUsername, username)) return true;
        if (initialAuthType != currentAuthType) return true;
        if (initialHostKeyPolicy != currentHostKeyPolicy) return true;
        if (initialTimeout != timeout) return true;
        if (initialKeepalive != keepalive) return true;
        if (currentAuthType == 0) {
            String password = editPassword.getText().toString();
            return !TextUtils.equals(initialPassword, password);
        }
        String keyPath = editKeyPath.getText().toString();
        return !TextUtils.equals(initialKeyPath, keyPath);
    }

    private void updateSecurityStatus(String message, int color) {
        textSecurityStatus.setText(message);
        textSecurityStatus.setTextColor(color);
    }

    private void showHostKeyConfirmDialog(String hostname, int port, String username, String keyType, String fingerprint, int reason, AtomicInteger decision, CountDownLatch latch) {
        android.widget.LinearLayout layout = new android.widget.LinearLayout(this);
        layout.setOrientation(android.widget.LinearLayout.VERTICAL);
        layout.setPadding(32, 32, 32, 16);

        TextView title = new TextView(this);
        title.setText((reason == 1 ? "Host Key 发生变化" : "首次连接 Host Key 确认"));
        title.setTextSize(16);
        layout.addView(title);

        TextView hostInfo = new TextView(this);
        hostInfo.setText("主机: " + hostname + ":" + port + "\n用户: " + username + "\n环境: " + envLabel(currentEnvironmentType));
        layout.addView(hostInfo);

        TextView keyInfo = new TextView(this);
        keyInfo.setText("类型: " + keyType + "\n指纹: " + fingerprint);
        keyInfo.setPadding(0, 16, 0, 0);
        keyInfo.setOnClickListener(v -> copyToClipboard(fingerprint));
        layout.addView(keyInfo);

        TextView risk = new TextView(this);
        risk.setText(reason == 1 ? "服务器身份发生变化，可能存在安全风险。" : "这是你第一次连接此服务器，请确认指纹是否可信。");
        risk.setPadding(0, 16, 0, 0);
        layout.addView(risk);

        AlertDialog dialog = new AlertDialog.Builder(this)
            .setTitle("Host Key 安全确认")
            .setView(layout)
            .setPositiveButton("信任并保存", (d, w) -> {
                decision.set(HostKeyVerifier.DECISION_TRUST_AND_SAVE);
                latch.countDown();
            })
            .setNeutralButton("仅本次连接", (d, w) -> {
                decision.set(HostKeyVerifier.DECISION_TRUST_ONCE);
                latch.countDown();
            })
            .setNegativeButton("拒绝连接", (d, w) -> {
                decision.set(HostKeyVerifier.DECISION_REJECT);
                latch.countDown();
            })
            .setCancelable(false)
            .create();

        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(Color.parseColor("#F44336")));
        dialog.show();
    }

    private void toggleAdvanced() {
        layoutAdvanced.setVisibility(layoutAdvanced.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE);
    }

    private void applyHostKeyPolicySelection(int policy) {
        if (policy == 0) {
            radioGroupHostKey.check(R.id.radio_hostkey_strict);
        } else if (policy == 2) {
            radioGroupHostKey.check(R.id.radio_hostkey_accept_once);
        } else {
            radioGroupHostKey.check(R.id.radio_hostkey_ask);
        }
    }

    private void applyEnvironmentSelection(int envType) {
        if (envType == 0) {
            radioGroupEnv.check(R.id.radio_env_prod);
        } else if (envType == 1) {
            radioGroupEnv.check(R.id.radio_env_staging);
        } else {
            radioGroupEnv.check(R.id.radio_env_dev);
        }
        updateEnvBadge(envType);
    }

    private void applyThemeSelection(String preset) {
        if ("light".equals(preset)) {
            radioGroupTheme.check(R.id.radio_theme_light);
        } else if ("high_contrast".equals(preset)) {
            radioGroupTheme.check(R.id.radio_theme_contrast);
        } else {
            radioGroupTheme.check(R.id.radio_theme_default);
        }
    }

    private void updateEnvBadge(int envType) {
        int color;
        String label;
        if (envType == 0) {
            color = 0xFFD32F2F;
            label = "生产";
        } else if (envType == 1) {
            color = 0xFFFBC02D;
            label = "预发";
        } else {
            color = 0xFF42A5F5;
            label = "开发";
        }
        cardEnvBadge.setCardBackgroundColor(color);
        textEnvBadge.setText(label);
        textEnvBadge.setTextColor(Color.WHITE);
    }

    private String envLabel(int envType) {
        if (envType == 0) return "生产";
        if (envType == 1) return "预发";
        return "开发";
    }

    private int parseInt(String value, int def) {
        if (TextUtils.isEmpty(value)) return def;
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return def;
        }
    }

    private void copyToClipboard(String text) {
        ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        if (cm != null) {
            cm.setPrimaryClip(ClipData.newPlainText("fingerprint", text));
            Toast.makeText(this, "指纹已复制", Toast.LENGTH_SHORT).show();
        }
    }

    private String ensureKnownHostsPath() {
        File file = new File(getFilesDir(), "known_hosts");
        if (!file.exists()) {
            try {
                file.createNewFile();
            } catch (IOException ignored) {
            }
        }
        return file.getAbsolutePath();
    }

    private void fetchMacForWol(HostEntity host) {
        executor.execute(() -> {
            SshNative ssh = new SshNative();
            long handle = 0;
            try {
                SessionConnector.Connection connection = SessionConnector.connectFresh(
                        ssh,
                        host.hostname,
                        host.port,
                        host.username,
                        host.password,
                        host.authType,
                        host.keyPath,
                        "Connect failed",
                        "Auth failed"
                );
                handle = connection.getHandle();
                String mac = ssh.exec(handle, CommandConstants.CMD_MAC_FROM_IP_LINK).trim();
                if (mac.isEmpty()) {
                    mac = ssh.exec(handle, CommandConstants.CMD_MAC_FROM_SYS_CLASS).trim();
                }
                if (!mac.isEmpty()) {
                    String finalMac = mac;
                    SharedPreferences prefs = getSharedPreferences("orcterm_prefs", MODE_PRIVATE);
                    String identityKey = buildWolIdentityKey(host.hostname, host.port, host.username);
                    SharedPreferences.Editor editor = prefs.edit();
                    editor.putString(identityKey, finalMac);
                    if (host.id > 0) {
                        editor.putString("wol_mac_" + host.id, finalMac);
                    }
                    editor.apply();
                    runOnUiThread(() -> Toast.makeText(this, "已获取 MAC: " + finalMac, Toast.LENGTH_SHORT).show());
                }
            } catch (Exception ignored) {
            } finally {
                if (handle != 0) {
                    ssh.disconnect(handle);
                }
            }
        });
    }

    private String buildWolIdentityKey(String hostname, int port, String username) {
        return "wol_mac_identity_" + hostname + "|" + port + "|" + username;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executor.shutdownNow();
    }
}
