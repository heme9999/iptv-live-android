package com.heme.iptvlive;

import android.app.PictureInPictureParams;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.Rational;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;
import androidx.media3.common.MediaItem;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.ui.PlayerView;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class MainActivity extends AppCompatActivity {
    private static final String PREFS = "iptv_settings";
    private static final String[] START_PAGES = {"主页", "直播", "分类"};
    private final List<View> pages = new ArrayList<>();
    private ExoPlayer player;
    private PlayerView playerView;
    private List<Channel> allChannels = new ArrayList<>();
    private ChannelAdapter categoryChannelAdapter;
    private ChannelAdapter liveAdapter;
    private ChannelAdapter mobileDrawerAdapter;
    private SharedPreferences preferences;
    private boolean mobilePlayerFullscreen;
    private boolean mobileDrawerVisible;
    private boolean enteringPictureInPicture;
    private final LatencyTester latencyTester = new LatencyTester();

    @Override protected void onCreate(@Nullable Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_main);
        preferences = getSharedPreferences(PREFS, MODE_PRIVATE);
        playerView = findViewById(R.id.player_view);
        pages.add(findViewById(R.id.page_home));
        pages.add(findViewById(R.id.page_live));
        pages.add(findViewById(R.id.page_categories));
        pages.add(findViewById(R.id.page_settings));
        configureDeviceLayout();
        configureNavigation();
        configureSettings();
        loadChannels();
        String start = preferences.getString("startup_page", "主页");
        showPage("直播".equals(start) ? 1 : "分类".equals(start) ? 2 : 0);
        UpdateChecker.check(this, false);
    }

    private void configureDeviceLayout() {
        String version = "v" + BuildConfig.VERSION_NAME;
        ((TextView) findViewById(R.id.nav_version)).setText(version);
        ((TextView) findViewById(R.id.settings_version)).setText("当前版本 " + version + "\n更新源：" + BuildConfig.GITHUB_REPO);
        if (BuildConfig.TV_UI) {
            getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        } else {
            LinearLayout root = findViewById(R.id.root);
            LinearLayout rail = findViewById(R.id.nav_rail);
            root.setOrientation(LinearLayout.VERTICAL);
            rail.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            findViewById(R.id.page_container).setLayoutParams(
                new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
            rail.setOrientation(LinearLayout.HORIZONTAL);
            rail.getChildAt(0).setVisibility(View.GONE);
            rail.getChildAt(5).setVisibility(View.GONE);
            rail.getChildAt(6).setVisibility(View.GONE);
            int[] mobileNavIds = {R.id.nav_home, R.id.nav_live, R.id.nav_categories, R.id.nav_settings};
            for (int id : mobileNavIds) findViewById(id).setLayoutParams(new LinearLayout.LayoutParams(0, dp(56), 1f));

            LinearLayout livePage = findViewById(R.id.page_live);
            LinearLayout sidebar = findViewById(R.id.sidebar);
            livePage.setOrientation(LinearLayout.VERTICAL);
            sidebar.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
            playerView.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1.2f));

            LinearLayout categoryPage = findViewById(R.id.page_categories);
            categoryPage.setOrientation(LinearLayout.VERTICAL);
            View groupPane = categoryPage.getChildAt(0);
            View channelPane = categoryPage.getChildAt(1);
            groupPane.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 0.8f));
            LinearLayout.LayoutParams channelParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1.2f);
            channelParams.topMargin = dp(16);
            channelPane.setLayoutParams(channelParams);
            playerView.setOnTouchListener((view, event) -> {
                if (event.getAction() == MotionEvent.ACTION_UP && mobilePlayerFullscreen) toggleMobileChannelDrawer();
                return false;
            });
        }
    }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }

    private void configureNavigation() {
        findViewById(R.id.nav_home).setOnClickListener(v -> showPage(0));
        findViewById(R.id.nav_live).setOnClickListener(v -> showPage(1));
        findViewById(R.id.nav_categories).setOnClickListener(v -> showPage(2));
        findViewById(R.id.nav_settings).setOnClickListener(v -> showPage(3));
        findViewById(R.id.home_live).setOnClickListener(v -> showPage(1));
        findViewById(R.id.home_categories).setOnClickListener(v -> showPage(2));
        findViewById(R.id.home_continue).setOnClickListener(v -> continueWatching());
    }

    private void configureSettings() {
        Spinner startup = findViewById(R.id.startup_page);
        startup.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, START_PAGES));
        String saved = preferences.getString("startup_page", "主页");
        for (int i = 0; i < START_PAGES.length; i++) if (START_PAGES[i].equals(saved)) startup.setSelection(i);
        startup.setOnItemSelectedListener(new SimpleItemSelectedListener(position -> preferences.edit().putString("startup_page", START_PAGES[position]).apply()));
        SwitchCompat autoplay = findViewById(R.id.autoplay);
        autoplay.setChecked(preferences.getBoolean("autoplay", false));
        autoplay.setOnCheckedChangeListener((button, checked) -> preferences.edit().putBoolean("autoplay", checked).apply());
        findViewById(R.id.check_update).setOnClickListener(v -> UpdateChecker.check(this, true));
    }

    private void loadChannels() {
        try {
            allChannels = M3uParser.fromAssets(this);
            RecyclerView live = findViewById(R.id.channels);
            live.setLayoutManager(new LinearLayoutManager(this));
            liveAdapter = new ChannelAdapter(allChannels, this::play);
            live.setAdapter(liveAdapter);
            RecyclerView mobileDrawer = findViewById(R.id.mobile_drawer_channels);
            mobileDrawer.setLayoutManager(new LinearLayoutManager(this));
            mobileDrawerAdapter = new ChannelAdapter(allChannels, channel -> { play(channel); hideMobileChannelDrawer(); });
            mobileDrawer.setAdapter(mobileDrawerAdapter);
            RecyclerView categoryChannels = findViewById(R.id.category_channels);
            categoryChannels.setLayoutManager(new LinearLayoutManager(this));
            categoryChannelAdapter = new ChannelAdapter(new ArrayList<>(), channel -> { showPage(1); play(channel); });
            categoryChannels.setAdapter(categoryChannelAdapter);
            Set<String> groups = new LinkedHashSet<>();
            for (Channel channel : allChannels) groups.add(channel.group);
            RecyclerView categories = findViewById(R.id.category_list);
            categories.setLayoutManager(new LinearLayoutManager(this));
            categories.setAdapter(new TextListAdapter(new ArrayList<>(groups), this::selectCategory));
            latencyTester.measureAll(allChannels, this::refreshChannelLatency);
        } catch (Exception error) {
            Toast.makeText(this, "频道载入失败：" + error.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void refreshChannelLatency(Channel channel) {
        if (liveAdapter != null) liveAdapter.refresh(channel);
        if (mobileDrawerAdapter != null) mobileDrawerAdapter.refresh(channel);
        if (categoryChannelAdapter != null) categoryChannelAdapter.refresh(channel);
    }

    private void selectCategory(String group) {
        List<Channel> filtered = new ArrayList<>();
        for (Channel channel : allChannels) if (group.equals(channel.group)) filtered.add(channel);
        ((TextView) findViewById(R.id.category_title)).setText(group + "（" + filtered.size() + "）");
        categoryChannelAdapter.replace(filtered);
        RecyclerView list = findViewById(R.id.category_channels);
        list.scrollToPosition(0);
        list.post(() -> { RecyclerView.ViewHolder holder = list.findViewHolderForAdapterPosition(0); if (holder != null) holder.itemView.requestFocus(); });
    }

    private void showPage(int index) {
        if (!BuildConfig.TV_UI && index != 1) exitMobilePlayerFullscreen();
        for (int i = 0; i < pages.size(); i++) pages.get(i).setVisibility(i == index ? View.VISIBLE : View.GONE);
        int[] navIds = {R.id.nav_home, R.id.nav_live, R.id.nav_categories, R.id.nav_settings};
        for (int i = 0; i < navIds.length; i++) findViewById(navIds[i]).setSelected(i == index);
        if (index == 1 && preferences.getBoolean("autoplay", false) && player == null && !allChannels.isEmpty()) play(allChannels.get(0));
    }

    private void continueWatching() {
        String url = preferences.getString("last_url", null);
        String name = preferences.getString("last_name", "继续观看");
        showPage(1);
        if (url != null) play(new Channel(name, "最近观看", url)); else if (!allChannels.isEmpty()) play(allChannels.get(0));
    }

    private void ensurePlayer() { if (player == null) { player = new ExoPlayer.Builder(this).build(); playerView.setPlayer(player); } }
    private void play(Channel channel) {
        ensurePlayer();
        preferences.edit().putString("last_url", channel.url).putString("last_name", channel.name).apply();
        player.setMediaItem(MediaItem.fromUri(Uri.parse(channel.url))); player.prepare(); player.play();
        if (!BuildConfig.TV_UI) enterMobilePlayerFullscreen();
    }

    private void enterMobilePlayerFullscreen() {
        mobilePlayerFullscreen = true;
        hideMobileChannelDrawer();
        findViewById(R.id.nav_rail).setVisibility(View.GONE);
        findViewById(R.id.sidebar).setVisibility(View.GONE);
        findViewById(R.id.page_container).setLayoutParams(
            new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        playerView.setLayoutParams(new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        getWindow().getDecorView().setSystemUiVisibility(
            View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
    }

    private void exitMobilePlayerFullscreen() {
        if (!mobilePlayerFullscreen) return;
        hideMobileChannelDrawer();
        mobilePlayerFullscreen = false;
        findViewById(R.id.nav_rail).setVisibility(View.VISIBLE);
        findViewById(R.id.sidebar).setVisibility(View.VISIBLE);
        playerView.setLayoutParams(new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1.2f));
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_VISIBLE);
    }

    private void toggleMobileChannelDrawer() {
        if (mobileDrawerVisible) hideMobileChannelDrawer(); else showMobileChannelDrawer();
    }

    private void showMobileChannelDrawer() {
        mobileDrawerVisible = true;
        findViewById(R.id.mobile_channel_drawer).setVisibility(View.VISIBLE);
    }

    private void hideMobileChannelDrawer() {
        mobileDrawerVisible = false;
        findViewById(R.id.mobile_channel_drawer).setVisibility(View.GONE);
    }
    private void releasePlayer() { if (player != null) { player.stop(); player.clearMediaItems(); player.release(); player = null; playerView.setPlayer(null); } }
    @Override public void onUserLeaveHint() {
        super.onUserLeaveHint();
        if (!BuildConfig.TV_UI && player != null && player.getMediaItemCount() > 0 && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            hideMobileChannelDrawer();
            enteringPictureInPicture = true;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                PictureInPictureParams params = new PictureInPictureParams.Builder()
                    .setAspectRatio(new Rational(16, 9)).build();
                if (!enterPictureInPictureMode(params)) enteringPictureInPicture = false;
            } else {
                enterPictureInPictureMode();
            }
        }
    }

    @Override public void onPictureInPictureModeChanged(boolean inPictureInPictureMode, Configuration newConfig) {
        super.onPictureInPictureModeChanged(inPictureInPictureMode, newConfig);
        enteringPictureInPicture = inPictureInPictureMode;
    }

    @Override protected void onStop() {
        super.onStop();
        boolean pip = !BuildConfig.TV_UI && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && (isInPictureInPictureMode() || enteringPictureInPicture);
        if (!isChangingConfigurations() && !pip) releasePlayer();
    }
    @Override protected void onDestroy() { latencyTester.close(); releasePlayer(); super.onDestroy(); }
    @SuppressWarnings("deprecation")
    @Override public void onBackPressed() {
        if (!BuildConfig.TV_UI && mobileDrawerVisible) {
            hideMobileChannelDrawer();
            return;
        }
        if (!BuildConfig.TV_UI && mobilePlayerFullscreen) {
            exitMobilePlayerFullscreen();
            return;
        }
        super.onBackPressed();
    }
    @Override public boolean onKeyLongPress(int keyCode, KeyEvent event) { if (keyCode == KeyEvent.KEYCODE_MENU) { UpdateChecker.check(this, true); return true; } return super.onKeyLongPress(keyCode, event); }
    @Override public void onConfigurationChanged(Configuration newConfig) { super.onConfigurationChanged(newConfig); }
}
