package com.orcterm.ui.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.orcterm.R;
import com.orcterm.util.CommandHistoryManager;

import java.util.ArrayList;
import java.util.List;

/**
 * 命令自动补全适配器
 * 显示命令建议列表，支持中文说明
 */
public class AutocompleteAdapter extends RecyclerView.Adapter<AutocompleteAdapter.ViewHolder> {
    
    private List<CommandHistoryManager.CommandSuggestion> suggestions = new ArrayList<>();
    private OnSuggestionClickListener listener;
    
    public interface OnSuggestionClickListener {
        void onSuggestionClick(CommandHistoryManager.CommandSuggestion suggestion);
    }
    
    public void setOnSuggestionClickListener(OnSuggestionClickListener listener) {
        this.listener = listener;
    }
    
    public void setSuggestions(List<CommandHistoryManager.CommandSuggestion> suggestions) {
        this.suggestions = suggestions != null ? suggestions : new ArrayList<>();
        notifyDataSetChanged();
    }
    
    public void clear() {
        suggestions.clear();
        notifyDataSetChanged();
    }
    
    public CommandHistoryManager.CommandSuggestion getItem(int position) {
        if (position >= 0 && position < suggestions.size()) {
            return suggestions.get(position);
        }
        return null;
    }
    
    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
            .inflate(R.layout.item_autocomplete_suggestion, parent, false);
        return new ViewHolder(view);
    }
    
    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        CommandHistoryManager.CommandSuggestion suggestion = suggestions.get(position);
        boolean isLastItem = position == getItemCount() - 1;
        holder.bind(suggestion, isLastItem);
        
        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onSuggestionClick(suggestion);
            }
        });
    }
    
    @Override
    public int getItemCount() {
        return suggestions.size();
    }
    
    static class ViewHolder extends RecyclerView.ViewHolder {
        private final TextView tvCommand;
        private final TextView tvDescription;
        private final TextView tvType;
        private final View divider;
        
        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvCommand = itemView.findViewById(R.id.tv_command);
            tvDescription = itemView.findViewById(R.id.tv_description);
            tvType = itemView.findViewById(R.id.tv_type);
            divider = itemView.findViewById(R.id.divider);
        }
        
        public void bind(CommandHistoryManager.CommandSuggestion suggestion, boolean isLastItem) {
            tvCommand.setText(suggestion.command);
            tvDescription.setText(suggestion.description);
            
            // 设置类型标签
            if ("history".equals(suggestion.type)) {
                tvType.setText("历史");
                tvType.setBackgroundResource(R.drawable.bg_type_history);
            } else {
                tvType.setText("常用");
                tvType.setBackgroundResource(R.drawable.bg_type_builtin);
            }
            
            // 最后一个不显示分割线
            divider.setVisibility(isLastItem ? View.GONE : View.VISIBLE);
        }
    }
}
