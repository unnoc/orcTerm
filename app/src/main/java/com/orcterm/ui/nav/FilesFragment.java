package com.orcterm.ui.nav;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.orcterm.R;
import com.orcterm.core.session.SessionInfo;
import com.orcterm.core.session.SessionManager;
import com.orcterm.ui.SftpActivity;
import com.orcterm.ui.adapter.SessionAdapter;
import com.orcterm.util.AppBackgroundHelper;
import java.util.ArrayList;
import java.util.List;

/**
 * 文件管理页面 - 显示活动SSH会话以进行SFTP管理
 */
public class FilesFragment extends Fragment implements SessionManager.SessionListener {

    private RecyclerView recyclerView;
    private TextView textNoSessions;
    private SessionAdapter adapter;
    private SharedPreferences prefs;
    private String currentHost;
    private String currentUser;
    private int currentPort;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_files, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        AppBackgroundHelper.applyFromPrefs(requireContext(), view);
        
        recyclerView = view.findViewById(R.id.recycler_sessions);
        textNoSessions = view.findViewById(R.id.text_no_sessions);
        
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        adapter = new SessionAdapter();
        recyclerView.setAdapter(adapter);
        
        adapter.setOnSessionClickListener(session -> {
            Intent intent = new Intent(getContext(), SftpActivity.class);
            intent.putExtra("session_id", session.id);
            intent.putExtra("hostname", session.hostname);
            intent.putExtra("port", session.port);
            intent.putExtra("username", session.username);
            intent.putExtra("password", session.password);
            intent.putExtra("auth_type", session.authType);
            intent.putExtra("key_path", session.keyPath);
            startActivity(intent);
        });
        
        updateSessions();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (getView() != null) {
            AppBackgroundHelper.applyFromPrefs(requireContext(), getView());
        }
        SessionManager.getInstance().addListener(this);
        updateSessions();
    }

    @Override
    public void onPause() {
        super.onPause();
        SessionManager.getInstance().removeListener(this);
    }

    @Override
    public void onSessionsChanged() {
        if (getActivity() != null) {
            getActivity().runOnUiThread(this::updateSessions);
        }
    }

    private void updateSessions() {
        loadCurrentHost();
        if (TextUtils.isEmpty(currentHost) || currentPort <= 0) {
            recyclerView.setVisibility(View.GONE);
            textNoSessions.setVisibility(View.VISIBLE);
            adapter.setSessions(new ArrayList<>());
            return;
        }
        List<SessionInfo> sessions = SessionManager.getInstance().getSftpSessions();
        SessionInfo selected = null;
        for (SessionInfo session : sessions) {
            if (matchesCurrentHost(session)) {
                if (!session.connected) {
                    continue;
                }
                if (selected == null || session.timestamp > selected.timestamp) {
                    selected = session;
                }
            }
        }
        if (selected == null) {
            recyclerView.setVisibility(View.GONE);
            textNoSessions.setVisibility(View.VISIBLE);
            adapter.setSessions(new ArrayList<>());
        } else {
            recyclerView.setVisibility(View.VISIBLE);
            textNoSessions.setVisibility(View.GONE);
            List<SessionInfo> one = new ArrayList<>();
            one.add(selected);
            adapter.setSessions(one);
        }
    }

    private void loadCurrentHost() {
        if (prefs == null) {
            prefs = requireContext().getSharedPreferences("orcterm_prefs", Context.MODE_PRIVATE);
        }
        currentHost = prefs.getString("current_host_hostname", null);
        currentUser = prefs.getString("current_host_username", null);
        currentPort = prefs.getInt("current_host_port", -1);
    }

    private boolean matchesCurrentHost(SessionInfo session) {
        if (session == null) return false;
        if (currentPort > 0 && session.port != currentPort) return false;
        if (!TextUtils.equals(currentHost, session.hostname)) return false;
        if (TextUtils.isEmpty(currentUser)) return true;
        return TextUtils.equals(currentUser, session.username);
    }
}
