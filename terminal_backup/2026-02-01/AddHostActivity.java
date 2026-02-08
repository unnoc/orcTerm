package com.orcterm.ui;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.RadioGroup;
import android.widget.Toast;
import android.content.Intent;
import android.net.Uri;
import android.content.SharedPreferences;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.orcterm.R;
import com.orcterm.data.HostEntity;
import com.orcterm.core.ssh.SshNative;
import com.orcterm.util.CommandConstants;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

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
    
    private MainViewModel mViewModel;
    private ActivityResultLauncher<Intent> filePickerLauncher;
    private int currentAuthType = 0; // 0: password, 1: key
    private String currentContainerEngine = CommandConstants.CMD_ENGINE_DOCKER;
    private boolean isEdit = false;
    private long editHostId = -1;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_host);

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
        Button buttonSelectKey = findViewById(R.id.button_select_key);
        Button buttonSave = findViewById(R.id.button_save);

        setupAuthTypeListener();
        setupContainerEngineListener();
        setupFilePicker();

        buttonSelectKey.setOnClickListener(v -> openFilePicker());
        buttonSave.setOnClickListener(v -> saveHost());
        
        initViewFromIntent();
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
        
        if (currentAuthType == 0) {
            host.password = password;
        } else {
            if (TextUtils.isEmpty(keyPath)) {
                Toast.makeText(this, "请选择或输入密钥路径", Toast.LENGTH_SHORT).show();
                return;
            }
            host.keyPath = keyPath;
        }

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

    private void fetchMacForWol(HostEntity host) {
        executor.execute(() -> {
            SshNative ssh = new SshNative();
            long handle = 0;
            try {
                handle = ssh.connect(host.hostname, host.port);
                if (handle == 0) return;
                int auth;
                if (host.authType == 1 && host.keyPath != null) {
                    auth = ssh.authKey(handle, host.username, host.keyPath);
                } else {
                    auth = ssh.authPassword(handle, host.username, host.password);
                }
                if (auth != 0) return;
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
