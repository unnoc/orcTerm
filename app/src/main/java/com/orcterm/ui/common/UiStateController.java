package com.orcterm.ui.common;

import android.app.Activity;
import android.text.TextUtils;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.Nullable;

import com.orcterm.R;

/**
 * Shared controller for loading/empty/error/content state overlay.
 */
public final class UiStateController {

    private final Activity activity;
    @Nullable
    private final View stateContainer;
    @Nullable
    private final TextView stateTitle;
    @Nullable
    private final TextView stateMessage;
    @Nullable
    private final View retryButton;
    @Nullable
    private Runnable retryAction;

    public UiStateController(Activity activity) {
        this.activity = activity;
        stateContainer = activity.findViewById(R.id.state_container);
        stateTitle = activity.findViewById(R.id.state_title);
        stateMessage = activity.findViewById(R.id.state_message);
        retryButton = activity.findViewById(R.id.btn_state_retry);
        if (retryButton != null) {
            retryButton.setOnClickListener(v -> {
                if (retryAction != null) {
                    retryAction.run();
                }
            });
        }
    }

    public void setRetryAction(@Nullable Runnable retryAction) {
        this.retryAction = retryAction;
    }

    public void showContent() {
        if (stateContainer != null) {
            stateContainer.setVisibility(View.GONE);
        }
    }

    public void showLoading(@Nullable CharSequence message) {
        if (stateContainer == null) return;
        stateContainer.setVisibility(View.VISIBLE);
        if (stateTitle != null) {
            stateTitle.setText(activity.getString(R.string.ui_state_loading));
        }
        if (stateMessage != null) {
            CharSequence value = TextUtils.isEmpty(message)
                    ? activity.getString(R.string.ui_state_loading_message)
                    : message;
            stateMessage.setText(value);
        }
        if (retryButton != null) {
            retryButton.setVisibility(View.GONE);
        }
    }

    public void showEmpty(@Nullable CharSequence message) {
        if (stateContainer == null) return;
        stateContainer.setVisibility(View.VISIBLE);
        if (stateTitle != null) {
            stateTitle.setText(activity.getString(R.string.ui_state_empty));
        }
        if (stateMessage != null) {
            stateMessage.setText(message);
        }
        if (retryButton != null) {
            retryButton.setVisibility(View.GONE);
        }
    }

    public void showError(@Nullable CharSequence errorMessage) {
        if (stateContainer == null) return;
        stateContainer.setVisibility(View.VISIBLE);
        if (stateTitle != null) {
            stateTitle.setText(activity.getString(R.string.ui_state_error));
        }
        if (stateMessage != null) {
            CharSequence value = TextUtils.isEmpty(errorMessage)
                    ? activity.getString(R.string.err_connect_fail)
                    : errorMessage;
            stateMessage.setText(value);
        }
        if (retryButton != null) {
            retryButton.setVisibility(retryAction == null ? View.GONE : View.VISIBLE);
        }
    }
}
