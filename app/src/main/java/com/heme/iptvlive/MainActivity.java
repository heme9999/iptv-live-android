package com.heme.iptvlive;

import android.content.res.Configuration;
import android.net.Uri;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.Toast;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.media3.common.MediaItem;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.ui.PlayerView;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import java.util.List;

public final class MainActivity extends AppCompatActivity {
    private ExoPlayer player;
    private PlayerView playerView;
    private boolean intentionallyInBackground;

    @Override protected void onCreate(@Nullable Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_main);
        playerView = findViewById(R.id.player_view);
        configureLayout();
        try {
            List<Channel> channels = M3uParser.fromAssets(this);
            RecyclerView list = findViewById(R.id.channels);
            list.setLayoutManager(new LinearLayoutManager(this));
            list.setAdapter(new ChannelAdapter(channels, this::play));
            if (!channels.isEmpty()) play(channels.get(0));
        } catch (Exception error) {
            Toast.makeText(this, "频道载入失败：" + error.getMessage(), Toast.LENGTH_LONG).show();
        }
        UpdateChecker.check(this, false);
    }

    private void configureLayout() {
        if (BuildConfig.TV_UI) {
            getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
            return;
        }
        LinearLayout root = findViewById(R.id.root);
        LinearLayout sidebar = findViewById(R.id.sidebar);
        root.setOrientation(LinearLayout.VERTICAL);
        sidebar.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        playerView.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1.25f));
    }

    private void ensurePlayer() {
        if (player == null) {
            player = new ExoPlayer.Builder(this).build();
            playerView.setPlayer(player);
        }
    }

    private void play(Channel channel) {
        ensurePlayer();
        player.setMediaItem(MediaItem.fromUri(Uri.parse(channel.url)));
        player.prepare();
        player.play();
    }

    private void releasePlayer() {
        if (player != null) {
            player.stop();
            player.clearMediaItems();
            player.release();
            player = null;
            playerView.setPlayer(null);
        }
    }

    @Override protected void onStop() {
        super.onStop();
        if (!isChangingConfigurations() && !intentionallyInBackground) releasePlayer();
    }

    @Override protected void onDestroy() {
        releasePlayer();
        super.onDestroy();
    }

    @Override public boolean onKeyLongPress(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_MENU) {
            UpdateChecker.check(this, true);
            return true;
        }
        return super.onKeyLongPress(keyCode, event);
    }

    @Override public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
    }
}

