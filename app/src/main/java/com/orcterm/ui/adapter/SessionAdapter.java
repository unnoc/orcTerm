package com.orcterm.ui.adapter;

import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.color.MaterialColors;
import com.orcterm.R;
import com.orcterm.core.session.SessionInfo;
import java.util.ArrayList;
import java.util.List;

/**
 * 会话列表适配器
 */
public class SessionAdapter extends RecyclerView.Adapter<SessionAdapter.SessionViewHolder> {

    private List<SessionInfo> sessions = new ArrayList<>();
    private OnSessionClickListener listener;

    public interface OnSessionClickListener {
        void onSessionClick(SessionInfo session);
    }

    public void setSessions(List<SessionInfo> sessions) {
        this.sessions = sessions;
        notifyDataSetChanged();
    }

    public void setOnSessionClickListener(OnSessionClickListener listener) {
        this.listener = listener;
    }

    @NonNull
    @Override
    public SessionViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        // We can reuse a simple list item layout or create a new one. 
        // For now, let's use android.R.layout.simple_list_item_2 but customized if possible.
        // Or better, let's use a custom layout. Since we can't easily create a new layout file without Write, 
        // and we want to look good, I should probably create a layout file item_session.xml.
        // But for speed, I'll use a simple existing layout or create one.
        // Let's create item_session.xml first.
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_session, parent, false);
        return new SessionViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull SessionViewHolder holder, int position) {
        SessionInfo session = sessions.get(position);
        holder.bind(session);
    }

    @Override
    public int getItemCount() {
        return sessions.size();
    }

    class SessionViewHolder extends RecyclerView.ViewHolder {
        TextView textName;
        TextView textHost;
        TextView textStatus;

        SessionViewHolder(View itemView) {
            super(itemView);
            textName = itemView.findViewById(R.id.text_name);
            textHost = itemView.findViewById(R.id.text_host);
            textStatus = itemView.findViewById(R.id.text_status);

            itemView.setOnClickListener(v -> {
                if (listener != null && getAdapterPosition() != RecyclerView.NO_POSITION) {
                    listener.onSessionClick(sessions.get(getAdapterPosition()));
                }
            });
        }

        void bind(SessionInfo session) {
            String hostLine;
            if (TextUtils.isEmpty(session.username)) {
                hostLine = itemView.getContext().getString(R.string.session_host_no_user_format, session.hostname, session.port);
            } else {
                hostLine = itemView.getContext().getString(R.string.session_host_format, session.username, session.hostname, session.port);
            }
            String name = TextUtils.isEmpty(session.name) ? hostLine : session.name;
            textName.setText(name);
            textHost.setText(hostLine);
            boolean connected = session.connected;
            textStatus.setText(connected ? itemView.getContext().getString(R.string.session_status_connected)
                : itemView.getContext().getString(R.string.session_status_disconnected));
            int bgColor = MaterialColors.getColor(textStatus, connected ? com.google.android.material.R.attr.colorPrimary
                : com.google.android.material.R.attr.colorError);
            int fgColor = MaterialColors.getColor(textStatus, connected ? com.google.android.material.R.attr.colorOnPrimary
                : com.google.android.material.R.attr.colorOnError);
            textStatus.setBackgroundColor(bgColor);
            textStatus.setTextColor(fgColor);
            int padH = (int) (6 * itemView.getResources().getDisplayMetrics().density);
            int padV = (int) (2 * itemView.getResources().getDisplayMetrics().density);
            textStatus.setPadding(padH, padV, padH, padV);
        }
    }
}
