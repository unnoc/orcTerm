package com.orcterm.core.session;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import com.orcterm.core.terminal.TerminalSession;

public class SessionManager {
    private static SessionManager instance;
    private final List<SessionInfo> sessions = new CopyOnWriteArrayList<>();
    private final Map<Long, TerminalSession> sessionMap = new ConcurrentHashMap<>();
    private final Map<Long, Long> sharedHandleMap = new ConcurrentHashMap<>();
    private final List<SessionListener> listeners = new CopyOnWriteArrayList<>();

    public interface SessionListener {
        void onSessionsChanged();
    }

    private SessionManager() {}

    public static synchronized SessionManager getInstance() {
        if (instance == null) {
            instance = new SessionManager();
        }
        return instance;
    }

    public void addSession(SessionInfo info, TerminalSession session) {
        upsertSession(info, session);
    }
    
    // Legacy method support if needed, or update callers
    public void addSession(SessionInfo info) {
        addSession(info, null);
    }

    // 确保会话信息存在且可更新，避免列表缺失导致底部列表为空
    public synchronized void upsertSession(SessionInfo info, TerminalSession session) {
        int index = -1;
        for (int i = 0; i < sessions.size(); i++) {
            if (sessions.get(i).id == info.id) {
                index = i;
                break;
            }
        }
        if (index >= 0) {
            SessionInfo existing = sessions.get(index);
            existing.name = info.name;
            existing.hostname = info.hostname;
            existing.port = info.port;
            existing.username = info.username;
            existing.password = info.password;
            existing.authType = info.authType;
            existing.keyPath = info.keyPath;
            existing.connected = info.connected;
            existing.timestamp = info.timestamp;
        } else {
            sessions.add(info);
        }
        if (session != null) {
            sessionMap.put(info.id, session);
        }
        notifyListeners();
    }

    public void updateSession(long id, boolean connected) {
        for (SessionInfo session : sessions) {
            if (session.id == id) {
                session.connected = connected;
                notifyListeners();
                break;
            }
        }
    }
    
    public TerminalSession getTerminalSession(long id) {
        return sessionMap.get(id);
    }

    public TerminalSession findConnectedSession(String host, int port, String username) {
        if (host == null || username == null) {
            return null;
        }
        for (SessionInfo info : sessions) {
            if (info == null) {
                continue;
            }
            if (info.port != port) {
                continue;
            }
            if (!host.equals(info.hostname)) {
                continue;
            }
            if (!username.equals(info.username)) {
                continue;
            }
            TerminalSession session = sessionMap.get(info.id);
            if (session != null && session.isConnected() && session.getHandle() != 0) {
                return session;
            }
        }
        return null;
    }
    
    // 临时保存来自主机详情的共享句柄，供终端会话接管
    public void putSharedHandle(long sessionId, long handle) {
        if (handle != 0) {
            sharedHandleMap.put(sessionId, handle);
        }
    }
    
    // 获取并移除共享句柄，避免重复接管同一连接
    public long getAndRemoveSharedHandle(long sessionId) {
        Long handle = sharedHandleMap.remove(sessionId);
        return handle == null ? 0 : handle;
    }

    public void removeSession(long id) {
        TerminalSession session = sessionMap.remove(id);
        if (session != null) {
            // We might want to disconnect here, or let the caller handle it.
            // Usually if we remove it from manager, we want to kill it.
            // But let's check if it's connected.
            // session.disconnect(); // Assuming disconnect exists and is safe
            // For now, let caller handle disconnect to avoid side effects if reused? 
            // No, manager owns the lifecycle now.
            // But TerminalSession disconnect method needs verification.
        }
        sessions.removeIf(s -> s.id == id);
        notifyListeners();
    }

    public void clearSessions() {
        sessionMap.clear(); // Should we disconnect all?
        sessions.clear();
        notifyListeners();
    }

    public List<SessionInfo> getSessions() {
        return new ArrayList<>(sessions);
    }

    public void addListener(SessionListener listener) {
        listeners.add(listener);
    }

    public void removeListener(SessionListener listener) {
        listeners.remove(listener);
    }

    private void notifyListeners() {
        for (SessionListener listener : listeners) {
            listener.onSessionsChanged();
        }
    }
}
