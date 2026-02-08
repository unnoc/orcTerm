package com.orcterm.ui.nav;

import android.content.Intent;
import android.os.Bundle;
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
import com.orcterm.ui.TerminalActivity;
import com.orcterm.ui.adapter.SessionAdapter;
import java.util.ArrayList;
import java.util.List;

/**
 * 终端页面 - 显示活动会话列表
 */
public class TerminalFragment extends Fragment implements SessionManager.SessionListener {

    private RecyclerView recyclerView;
    private TextView textNoSessions;
    private SessionAdapter adapter;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_terminal, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        
        recyclerView = view.findViewById(R.id.recycler_sessions);
        textNoSessions = view.findViewById(R.id.text_no_sessions);
        
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        adapter = new SessionAdapter();
        recyclerView.setAdapter(adapter);
        
        adapter.setOnSessionClickListener(session -> {
            Intent intent = new Intent(getContext(), TerminalActivity.class);
            intent.putExtra("focus_container_id", session.id);
            startActivity(intent);
        });
        
        updateSessions();
    }

    @Override
    public void onResume() {
        super.onResume();
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
        List<SessionInfo> sessions = SessionManager.getInstance().getSessions();
        List<SessionInfo> activeSessions = new ArrayList<>();
        for (SessionInfo session : sessions) {
            if (session.connected) {
                activeSessions.add(session);
            }
        }
        if (activeSessions.isEmpty()) {
            recyclerView.setVisibility(View.GONE);
            textNoSessions.setVisibility(View.VISIBLE);
        } else {
            recyclerView.setVisibility(View.VISIBLE);
            textNoSessions.setVisibility(View.GONE);
            adapter.setSessions(activeSessions);
        }
    }
}
