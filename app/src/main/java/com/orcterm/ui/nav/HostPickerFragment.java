package com.orcterm.ui.nav;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.button.MaterialButton;
import com.orcterm.R;
import com.orcterm.data.HostEntity;
import com.orcterm.ui.AddHostActivity;
import com.orcterm.ui.MainActivity;
import com.orcterm.ui.MainViewModel;
import com.orcterm.ui.DockerActivity;
import com.orcterm.ui.HostDetailActivity;
import com.orcterm.ui.SftpActivity;

import java.util.ArrayList;
import java.util.List;

public class HostPickerFragment extends Fragment {

    public static final String ACTION_TERMINAL = "terminal";
    public static final String ACTION_FILES = "files";
    public static final String ACTION_MONITOR = "monitor";
    private static final String ARG_ACTION = "action";

    private MainViewModel mHostViewModel;
    private String action;
    private List<HostEntity> mAllHosts = new ArrayList<>();
    private HostEntity currentHost;
    private NavViewModel navViewModel;
    private TextView textCurrentServer;
    private TextView textCurrentDetail;
    private MaterialButton btnPrimaryAction;
    private MaterialButton btnSelectServer;
    private MaterialButton btnAddServer;

    public static HostPickerFragment newInstance(String action) {
        HostPickerFragment fragment = new HostPickerFragment();
        Bundle args = new Bundle();
        args.putString(ARG_ACTION, action);
        fragment.setArguments(args);
        return fragment;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_host_picker, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        action = getArguments() != null ? getArguments().getString(ARG_ACTION, ACTION_TERMINAL) : ACTION_TERMINAL;

        textCurrentServer = view.findViewById(R.id.text_current_server);
        textCurrentDetail = view.findViewById(R.id.text_current_detail);
        btnPrimaryAction = view.findViewById(R.id.btn_primary_action);
        btnSelectServer = view.findViewById(R.id.btn_select_server);
        btnAddServer = view.findViewById(R.id.btn_add_server);

        mHostViewModel = new ViewModelProvider(requireActivity()).get(MainViewModel.class);
        mHostViewModel.getAllHosts().observe(getViewLifecycleOwner(), hosts -> {
            mAllHosts = new ArrayList<>(hosts);
            updateCurrentHost();
        });

        navViewModel = new ViewModelProvider(requireActivity()).get(NavViewModel.class);
        navViewModel.getCurrentHostId().observe(getViewLifecycleOwner(), id -> {
            updateCurrentHost();
        });

        btnPrimaryAction.setText(getPrimaryActionLabel());
        btnPrimaryAction.setOnClickListener(v -> {
            if (currentHost == null) {
                Toast.makeText(requireContext(), "请选择服务器", Toast.LENGTH_SHORT).show();
                return;
            }
            openTarget(currentHost);
        });

        btnSelectServer.setOnClickListener(v -> {
            if (getActivity() instanceof MainActivity) {
                // ((MainActivity) getActivity()).showServerPicker();
            }
        });

        btnAddServer.setOnClickListener(v -> {
            Intent intent = new Intent(requireContext(), AddHostActivity.class);
            startActivity(intent);
        });
    }

    private void updateCurrentHost() {
        if (navViewModel == null) return;
        Long id = navViewModel.getCurrentHostId().getValue();
        currentHost = null;
        if (id != null) {
            for (HostEntity host : mAllHosts) {
                if (host.id == id) {
                    currentHost = host;
                    break;
                }
            }
        }
        updateUi();
    }

    private void updateUi() {
        if (currentHost == null) {
            if (mAllHosts.isEmpty()) {
                textCurrentServer.setText("尚未添加服务器");
                textCurrentDetail.setText("添加服务器后即可使用当前功能");
                btnPrimaryAction.setEnabled(false);
                btnSelectServer.setVisibility(View.GONE);
                btnAddServer.setVisibility(View.VISIBLE);
            } else {
                textCurrentServer.setText("未选择服务器");
                textCurrentDetail.setText("请选择服务器以继续");
                btnPrimaryAction.setEnabled(false);
                btnSelectServer.setVisibility(View.VISIBLE);
                btnAddServer.setVisibility(View.GONE);
            }
        } else {
            textCurrentServer.setText(currentHost.alias);
            String status = currentHost.status != null ? currentHost.status : "unknown";
            textCurrentDetail.setText(currentHost.username + "@" + currentHost.hostname + ":" + currentHost.port + " · " + status);
            btnPrimaryAction.setEnabled(true);
            btnSelectServer.setVisibility(View.VISIBLE);
            btnAddServer.setVisibility(View.GONE);
        }
    }

    private String getPrimaryActionLabel() {
        if (ACTION_FILES.equals(action)) return "打开文件";
        if (ACTION_MONITOR.equals(action)) return "查看监控";
        return "进入容器管理";
    }

    private void openTarget(HostEntity host) {
        Class<?> target;
        if (ACTION_FILES.equals(action)) {
            target = SftpActivity.class;
        } else if (ACTION_MONITOR.equals(action)) {
            target = HostDetailActivity.class;
        } else {
            target = DockerActivity.class;
        }
        Intent intent = new Intent(requireContext(), target);
        intent.putExtra("host_id", host.id);
        intent.putExtra("hostname", host.hostname);
        intent.putExtra("username", host.username);
        intent.putExtra("port", host.port);
        intent.putExtra("password", host.password);
        intent.putExtra("auth_type", host.authType);
        intent.putExtra("key_path", host.keyPath);
        intent.putExtra("container_engine", host.containerEngine);
        startActivity(intent);
    }
}
