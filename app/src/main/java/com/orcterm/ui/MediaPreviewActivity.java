
package com.orcterm.ui;

import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.VideoView;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import com.orcterm.R;

/**
 * 媒体预览界面（图片/视频）
 */
public class MediaPreviewActivity extends AppCompatActivity {

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_media_preview);

        ImageView imageView = findViewById(R.id.image_preview);
        VideoView videoView = findViewById(R.id.video_preview);

        Uri fileUri = getIntent().getData();
        String mimeType = getIntent().getType();

        if (mimeType != null && fileUri != null) {
            if (mimeType.startsWith("image/")) {
                imageView.setVisibility(View.VISIBLE);
                videoView.setVisibility(View.GONE);
                imageView.setImageURI(fileUri);
            } else if (mimeType.startsWith("video/")) {
                imageView.setVisibility(View.GONE);
                videoView.setVisibility(View.VISIBLE);
                videoView.setVideoURI(fileUri);
                videoView.start();
            }
        }
    }
}
