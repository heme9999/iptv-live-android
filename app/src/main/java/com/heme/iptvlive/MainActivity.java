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
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;
import androidx.media3.common.AudioAttributes;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.datasource.DefaultDataSource;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;
import androidx.media3.ui.PlayerView;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import java.util.ArrayList;
import java.util.Collections;
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
    private ChannelAdapter homeAdapter;
    private ChannelAdapter mobileDrawerAdapter;
    private SharedPreferences preferences;
    private boolean mobilePlayerFullscreen;
    private boolean mobileDrawerVisible;
    private boolean enteringPictureInPicture;
    private boolean tvPlayerFullscreen;
    private Channel currentChannel;
    private int currentPlayingIndex = 0;
    private TextListAdapter categoryAdapter;
    private final List<String> orderedCategoryGroups = new ArrayList<>();
    private Toast channelToast;
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
        initAdapters();
        configureDeviceLayout();
        configureNavigation();
        configureSettings();
        loadChannels();
        String start = preferences.getString("startup_page", "主页");
        showPage("直播".equals(start) ? 1 : "分类".equals(start) ? 2 : 0);
        UpdateChecker.check(this, false);
    }

    private void initAdapters() {
        RecyclerView live = findViewById(R.id.channels);
        live.setLayoutManager(new LinearLayoutManager(this));
        liveAdapter = new ChannelAdapter(new ArrayList<>(), this::play);
        live.setAdapter(liveAdapter);
        RecyclerView homeChannels = findViewById(R.id.home_channels);
        if (homeChannels != null) {
            homeChannels.setLayoutManager(new GridLayoutManager(this, BuildConfig.TV_UI ? 4 : 2));
            homeAdapter = new ChannelAdapter(new ArrayList<>(), channel -> { showPage(1); play(channel); });
            homeChannels.setAdapter(homeAdapter);
        }
        RecyclerView mobileDrawer = findViewById(R.id.mobile_drawer_channels);
        if (mobileDrawer != null) {
            mobileDrawer.setLayoutManager(new LinearLayoutManager(this));
            mobileDrawerAdapter = new ChannelAdapter(new ArrayList<>(), channel -> { play(channel); hideMobileChannelDrawer(); });
            mobileDrawer.setAdapter(mobileDrawerAdapter);
        }
        RecyclerView categoryChannels = findViewById(R.id.category_channels);
        if (categoryChannels != null) {
            categoryChannels.setLayoutManager(new LinearLayoutManager(this));
            categoryChannelAdapter = new ChannelAdapter(new ArrayList<>(), channel -> { showPage(1); play(channel); });
            categoryChannels.setAdapter(categoryChannelAdapter);
        }
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
            sidebar.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1.4f));
            playerView.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

            LinearLayout categoryPage = findViewById(R.id.page_categories);
            categoryPage.setOrientation(LinearLayout.VERTICAL);
            View groupPane = categoryPage.getChildAt(0);
            View channelPane = categoryPage.getChildAt(1);
            groupPane.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            LinearLayout.LayoutParams channelParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
            channelParams.topMargin = dp(8);
            channelPane.setLayoutParams(channelParams);
            playerView.setOnTouchListener((view, event) -> {
                if (event.getAction() == MotionEvent.ACTION_UP && mobilePlayerFullscreen) {
                    boolean landscape = getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;
                    if (landscape) toggleMobileChannelDrawer();
                }
                return false;
            });
        }
    }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }

    private static final String DEFAULT_REMOTE_M3U = "https://raw.githubusercontent.com/heme9999/iptv-live/main/live.m3u";
    private static final String DEFAULT_CDN_M3U = "https://fastly.jsdelivr.net/gh/heme9999/iptv-live@main/live.m3u";

    private void configureNavigation() {
        findViewById(R.id.nav_home).setOnClickListener(v -> showPage(0));
        findViewById(R.id.nav_live).setOnClickListener(v -> showPage(1));
        findViewById(R.id.nav_categories).setOnClickListener(v -> showPage(2));
        findViewById(R.id.nav_settings).setOnClickListener(v -> showPage(3));
        findViewById(R.id.home_live).setOnClickListener(v -> showPage(1));
        findViewById(R.id.home_categories).setOnClickListener(v -> showPage(2));
        findViewById(R.id.home_continue).setOnClickListener(v -> continueWatching());
        View homeRefresh = findViewById(R.id.home_refresh);
        if (homeRefresh != null) homeRefresh.setOnClickListener(v -> refreshPlaylist(true));
    }

    private void configureSettings() {
        Spinner startup = findViewById(R.id.startup_page);
        ArrayAdapter<String> startupAdapter = new ArrayAdapter<>(this, R.layout.item_spinner_selected, START_PAGES);
        startupAdapter.setDropDownViewResource(R.layout.item_spinner_dropdown);
        startup.setAdapter(startupAdapter);
        String saved = preferences.getString("startup_page", "主页");
        for (int i = 0; i < START_PAGES.length; i++) if (START_PAGES[i].equals(saved)) startup.setSelection(i);
        startup.setOnItemSelectedListener(new SimpleItemSelectedListener(position -> preferences.edit().putString("startup_page", START_PAGES[position]).apply()));
        SwitchCompat autoplay = findViewById(R.id.autoplay);
        if (autoplay != null) {
            autoplay.setChecked(preferences.getBoolean("autoplay", false));
            autoplay.setOnCheckedChangeListener((button, checked) -> preferences.edit().putBoolean("autoplay", checked).apply());
        }
        View btnRefreshPlaylist = findViewById(R.id.btn_refresh_playlist);
        if (btnRefreshPlaylist != null) btnRefreshPlaylist.setOnClickListener(v -> refreshPlaylist(true));
        View btnEditSource = findViewById(R.id.btn_edit_source);
        if (btnEditSource != null) btnEditSource.setOnClickListener(v -> showEditSourceDialog());
        View btnResetSource = findViewById(R.id.btn_reset_source);
        if (btnResetSource != null) btnResetSource.setOnClickListener(v -> resetToDefaultSource());
        findViewById(R.id.check_update).setOnClickListener(v -> UpdateChecker.check(this, true));
        updateSourceStatusDisplay();
    }

    private void updateSourceStatusDisplay() {
        TextView status = findViewById(R.id.source_status);
        if (status != null) {
            String customUrl = preferences.getString("custom_m3u_url", null);
            if (customUrl != null && !customUrl.trim().isEmpty()) {
                status.setText("当前播放源：自定义订阅链接（" + allChannels.size() + " 个频道）\n" + customUrl);
            } else {
                status.setText("当前播放源：官方内置高质量源（" + allChannels.size() + " 个频道）");
            }
        }
    }

    public void refreshPlaylist(boolean userInitiated) {
        if (userInitiated) {
            Toast.makeText(this, "正在刷新并同步最新播放清单...", Toast.LENGTH_SHORT).show();
        }
        new Thread(() -> {
            String customUrl = preferences.getString("custom_m3u_url", null);
            List<Channel> fetched = null;
            String sourceName = "";
            if (customUrl != null && !customUrl.trim().isEmpty()) {
                try {
                    String urlNoCache = customUrl.contains("?") ? customUrl + "&_t=" + System.currentTimeMillis() : customUrl + "?_t=" + System.currentTimeMillis();
                    fetched = M3uParser.fromUrl(urlNoCache);
                    sourceName = "自定义源";
                } catch (Exception e) {
                    try {
                        fetched = M3uParser.fromUrl(customUrl);
                        sourceName = "自定义源";
                    } catch (Exception ignore) {}
                }
            }
            if (fetched == null || fetched.isEmpty()) {
                try {
                    fetched = M3uParser.fromUrl(DEFAULT_REMOTE_M3U + "?_t=" + System.currentTimeMillis());
                    sourceName = "官方云端源";
                } catch (Exception e1) {
                    try {
                        fetched = M3uParser.fromUrl(DEFAULT_CDN_M3U + "?_t=" + System.currentTimeMillis());
                        sourceName = "全球 CDN 加速源";
                    } catch (Exception ignore) {}
                }
            }
            if (fetched != null && !fetched.isEmpty()) {
                final List<Channel> finalChannels = fetched;
                final String finalSource = sourceName;
                runOnUiThread(() -> {
                    applyNewChannels(finalChannels);
                    if (userInitiated) {
                        Toast.makeText(this, "播放清单已更新！共载入 " + finalChannels.size() + " 个频道（" + finalSource + "）", Toast.LENGTH_LONG).show();
                    }
                });
            } else {
                if (userInitiated) {
                    runOnUiThread(() -> {
                        Toast.makeText(this, "刷新失败，网络超时或源不可用（已保留当前列表）", Toast.LENGTH_SHORT).show();
                    });
                }
            }
        }).start();
    }

    private void showEditSourceDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("自定义播放源");
        builder.setMessage("请输入 M3U 播放源订阅链接（HTTP / HTTPS）：");
        final EditText input = new EditText(this);
        input.setHint("https://raw.githubusercontent.com/.../live.m3u");
        input.setTextColor(0xFFF5F7FB);
        input.setHintTextColor(0xFF8490A3);
        input.setBackgroundResource(R.drawable.setting_control_background);
        int pad = dp(14);
        input.setPadding(pad, pad, pad, pad);
        String currentUrl = preferences.getString("custom_m3u_url", "");
        if (currentUrl != null && !currentUrl.isEmpty()) {
            input.setText(currentUrl);
            input.setSelection(currentUrl.length());
        }
        FrameLayout container = new FrameLayout(this);
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.leftMargin = dp(20);
        params.rightMargin = dp(20);
        params.topMargin = dp(10);
        params.bottomMargin = dp(10);
        input.setLayoutParams(params);
        container.addView(input);
        builder.setView(container);

        builder.setPositiveButton("测试并应用", (dialog, which) -> {
            String newUrl = input.getText().toString().trim();
            if (newUrl.isEmpty()) {
                Toast.makeText(this, "链接不能为空", Toast.LENGTH_SHORT).show();
                return;
            }
            if (!newUrl.startsWith("http://") && !newUrl.startsWith("https://")) {
                Toast.makeText(this, "请输入以 http:// 或 https:// 开头的有效链接", Toast.LENGTH_SHORT).show();
                return;
            }
            Toast.makeText(this, "正在拉取并解析播放源...", Toast.LENGTH_SHORT).show();
            new Thread(() -> {
                try {
                    List<Channel> fetched = M3uParser.fromUrl(newUrl);
                    runOnUiThread(() -> {
                        preferences.edit().putString("custom_m3u_url", newUrl).apply();
                        applyNewChannels(fetched);
                        Toast.makeText(this, "播放源更新成功！共加载 " + fetched.size() + " 个频道", Toast.LENGTH_LONG).show();
                    });
                } catch (Exception err) {
                    runOnUiThread(() -> {
                        Toast.makeText(this, "解析失败：" + err.getMessage(), Toast.LENGTH_LONG).show();
                    });
                }
            }).start();
        });

        builder.setNegativeButton("取消", null);
        builder.setNeutralButton("恢复默认源", (dialog, which) -> resetToDefaultSource());
        builder.show();
    }

    private void resetToDefaultSource() {
        preferences.edit().remove("custom_m3u_url").apply();
        Toast.makeText(this, "正在重新加载官方内置源...", Toast.LENGTH_SHORT).show();
        try {
            List<Channel> defaultChannels = M3uParser.fromAssets(this);
            applyNewChannels(defaultChannels);
            Toast.makeText(this, "已恢复为官方内置播放源（" + defaultChannels.size() + " 个频道）", Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Toast.makeText(this, "恢复默认源失败：" + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void applyNewChannels(List<Channel> channels) {
        Collections.sort(channels, (left, right) -> {
            int pLeft = categoryPriority(left.group);
            int pRight = categoryPriority(right.group);
            if (pLeft != pRight) return Integer.compare(pLeft, pRight);
            return 0;
        });
        allChannels = channels;
        if (liveAdapter != null) liveAdapter.replace(allChannels);
        if (homeAdapter != null) homeAdapter.replace(allChannels);
        if (mobileDrawerAdapter != null) mobileDrawerAdapter.replace(allChannels);
        Set<String> groups = new LinkedHashSet<>();
        for (Channel channel : allChannels) groups.add(channel.group);
        List<String> orderedGroups = new ArrayList<>(groups);
        Collections.sort(orderedGroups, (left, right) -> {
            int priority = Integer.compare(categoryPriority(left), categoryPriority(right));
            return priority != 0 ? priority : left.compareTo(right);
        });
        orderedCategoryGroups.clear();
        orderedCategoryGroups.addAll(orderedGroups);
        RecyclerView categories = findViewById(R.id.category_list);
        if (categories != null) {
            if (BuildConfig.TV_UI) {
                categories.setLayoutManager(new LinearLayoutManager(this));
            } else {
                categories.setLayoutManager(new GridLayoutManager(this, 3));
            }
            categoryAdapter = new TextListAdapter(orderedGroups, this::selectCategory);
            categories.setAdapter(categoryAdapter);
            if (!orderedGroups.isEmpty()) {
                categoryAdapter.setSelected(0);
                selectCategory(orderedGroups.get(0));
            }
        }
        updateSourceStatusDisplay();
        latencyTester.measureAll(allChannels, this::refreshChannelLatency);
    }

    private void loadChannels() {
        try {
            applyNewChannels(M3uParser.fromAssets(this));
        } catch (Exception e) {
            // fallback
        }
        refreshPlaylist(false);
    }

    private int categoryPriority(String group) {
        String[] orderedKeywords = {"国际", "日本", "台湾", "港澳", "粤语", "自然", "纪实", "体育", "影视", "央视", "卫视"};
        int[] priorities = {0, 1, 2, 3, 3, 4, 4, 5, 6, 7, 8};
        for (int i = 0; i < orderedKeywords.length; i++) if (group.contains(orderedKeywords[i])) return priorities[i];
        return 100;
    }

    private void refreshChannelLatency(Channel channel) {
        if (liveAdapter != null) liveAdapter.refresh(channel);
        if (homeAdapter != null) homeAdapter.refresh(channel);
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
        if (BuildConfig.TV_UI && index != 1) exitTvPlayerFullscreen();
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

    @androidx.annotation.OptIn(markerClass = androidx.media3.common.util.UnstableApi.class)
    private void ensurePlayer() {
        if (player == null) {
            DefaultHttpDataSource.Factory httpDataSourceFactory = new DefaultHttpDataSource.Factory()
                .setUserAgent("Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Mobile/15E148 APTV/1.0")
                .setAllowCrossProtocolRedirects(true)
                .setConnectTimeoutMs(15000)
                .setReadTimeoutMs(15000)
                .setKeepPostFor302Redirects(true);

            DefaultDataSource.Factory dataSourceFactory = new DefaultDataSource.Factory(this, httpDataSourceFactory);

            DefaultMediaSourceFactory mediaSourceFactory = new DefaultMediaSourceFactory(this)
                .setDataSourceFactory(dataSourceFactory);

            AudioAttributes audioAttributes = new AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                .build();

            player = new ExoPlayer.Builder(this)
                .setMediaSourceFactory(mediaSourceFactory)
                .setAudioAttributes(audioAttributes, true)
                .setHandleAudioBecomingNoisy(true)
                .build();

            player.addListener(new Player.Listener() {
                @Override public void onPlayerError(PlaybackException error) {
                    Toast.makeText(MainActivity.this, "正在重新连接直播源...", Toast.LENGTH_SHORT).show();
                    if (player != null) {
                        player.prepare();
                        player.play();
                    }
                }
            });

            playerView.setPlayer(player);
        }
    }

    private void play(Channel channel) {
        ensurePlayer();
        currentChannel = channel;
        for (int i = 0; i < allChannels.size(); i++) {
            if (allChannels.get(i).url.equals(channel.url)) {
                currentPlayingIndex = i;
                break;
            }
        }
        preferences.edit().putString("last_url", channel.url).putString("last_name", channel.name).apply();

        MediaItem.Builder mediaItemBuilder = new MediaItem.Builder().setUri(Uri.parse(channel.url));
        if (!channel.url.contains(".flv") && !channel.url.contains(".mp4")) {
            mediaItemBuilder.setMimeType(MimeTypes.APPLICATION_M3U8);
        }
        player.setMediaItem(mediaItemBuilder.build());
        player.prepare();
        player.play();

        TextView nowPlaying = findViewById(R.id.now_playing);
        if (nowPlaying != null) {
            String latency = channel.latencyMs >= 0 ? " · " + channel.latencyMs + " ms" : "";
            nowPlaying.setText("LIVE  " + channel.name + "\n" + channel.group + latency);
        }
        if (BuildConfig.TV_UI) {
            enterTvPlayerFullscreen();
        } else if (getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE) {
            enterMobilePlayerFullscreen();
        }
    }

    private void showChannelToast(Channel channel) {
        if (channelToast != null) {
            channelToast.cancel();
        }
        channelToast = Toast.makeText(this, "▶ " + channel.name + " (" + channel.group + ")", Toast.LENGTH_SHORT);
        channelToast.show();
    }

    private void playNextChannel() {
        if (allChannels.isEmpty()) return;
        currentPlayingIndex = (currentPlayingIndex + 1) % allChannels.size();
        Channel next = allChannels.get(currentPlayingIndex);
        play(next);
        showChannelToast(next);
    }

    private void playPreviousChannel() {
        if (allChannels.isEmpty()) return;
        currentPlayingIndex = (currentPlayingIndex - 1 + allChannels.size()) % allChannels.size();
        Channel prev = allChannels.get(currentPlayingIndex);
        play(prev);
        showChannelToast(prev);
    }

    private void showCategoryForCurrentChannel() {
        showPage(2);
        if (currentChannel != null && !orderedCategoryGroups.isEmpty()) {
            int idx = orderedCategoryGroups.indexOf(currentChannel.group);
            if (idx >= 0 && categoryAdapter != null) {
                categoryAdapter.setSelected(idx);
                selectCategory(currentChannel.group);
            }
        }
    }

    private void enterTvPlayerFullscreen() {
        tvPlayerFullscreen = true;
        findViewById(R.id.nav_rail).setVisibility(View.GONE);
        findViewById(R.id.sidebar).setVisibility(View.GONE);
        playerView.requestFocus();
    }

    private void exitTvPlayerFullscreen() {
        tvPlayerFullscreen = false;
        findViewById(R.id.nav_rail).setVisibility(View.VISIBLE);
        findViewById(R.id.sidebar).setVisibility(View.VISIBLE);
    }

    private void toggleTvPlayerMenu() {
        if (tvPlayerFullscreen) {
            exitTvPlayerFullscreen();
        } else {
            enterTvPlayerFullscreen();
        }
    }

    private void enterMobilePlayerFullscreen() {
        mobilePlayerFullscreen = true;
        findViewById(R.id.nav_rail).setVisibility(View.GONE);
        findViewById(R.id.sidebar).setVisibility(View.GONE);
    }

    private void exitMobilePlayerFullscreen() {
        mobilePlayerFullscreen = false;
        hideMobileChannelDrawer();
        findViewById(R.id.nav_rail).setVisibility(View.VISIBLE);
        findViewById(R.id.sidebar).setVisibility(View.VISIBLE);
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

    @Override public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            if (BuildConfig.TV_UI) {
                if (pages.size() > 1 && pages.get(1).getVisibility() == View.VISIBLE) {
                    if (tvPlayerFullscreen) {
                        if (event.getKeyCode() == KeyEvent.KEYCODE_DPAD_UP || event.getKeyCode() == KeyEvent.KEYCODE_CHANNEL_UP || event.getKeyCode() == KeyEvent.KEYCODE_PAGE_UP) {
                            playPreviousChannel();
                            return true;
                        }
                        if (event.getKeyCode() == KeyEvent.KEYCODE_DPAD_DOWN || event.getKeyCode() == KeyEvent.KEYCODE_CHANNEL_DOWN || event.getKeyCode() == KeyEvent.KEYCODE_PAGE_DOWN) {
                            playNextChannel();
                            return true;
                        }
                        if (event.getKeyCode() == KeyEvent.KEYCODE_MENU || event.getKeyCode() == KeyEvent.KEYCODE_DPAD_CENTER || event.getKeyCode() == KeyEvent.KEYCODE_ENTER) {
                            exitTvPlayerFullscreen();
                            return true;
                        }
                        if (event.getKeyCode() == KeyEvent.KEYCODE_BACK) {
                            exitTvPlayerFullscreen();
                            showCategoryForCurrentChannel();
                            return true;
                        }
                    } else {
                        if (event.getKeyCode() == KeyEvent.KEYCODE_BACK) {
                            showCategoryForCurrentChannel();
                            return true;
                        }
                    }
                } else if (pages.size() > 2 && pages.get(2).getVisibility() == View.VISIBLE) {
                    if (event.getKeyCode() == KeyEvent.KEYCODE_BACK) {
                        showPage(0);
                        return true;
                    }
                } else if (pages.size() > 3 && pages.get(3).getVisibility() == View.VISIBLE) {
                    if (event.getKeyCode() == KeyEvent.KEYCODE_BACK) {
                        showPage(0);
                        return true;
                    }
                }
            }
            if (!BuildConfig.TV_UI && mobilePlayerFullscreen) {
                if (event.getKeyCode() == KeyEvent.KEYCODE_BACK) {
                    if (mobileDrawerVisible) {
                        hideMobileChannelDrawer();
                        return true;
                    }
                    exitMobilePlayerFullscreen();
                    showCategoryForCurrentChannel();
                    return true;
                }
                if (event.getKeyCode() == KeyEvent.KEYCODE_MENU) {
                    toggleMobileChannelDrawer();
                    return true;
                }
            }
        }
        return super.dispatchKeyEvent(event);
    }

    @Override public boolean dispatchTouchEvent(MotionEvent ev) {
        if (!BuildConfig.TV_UI && mobilePlayerFullscreen && ev.getAction() == MotionEvent.ACTION_UP) {
            boolean landscape = getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;
            if (landscape) {
                int drawerWidth = dp(310);
                if (mobileDrawerVisible && ev.getX() > drawerWidth) {
                    hideMobileChannelDrawer();
                    return true;
                }
                if (!mobileDrawerVisible && ev.getX() <= dp(48)) {
                    showMobileChannelDrawer();
                    return true;
                }
            }
        }
        return super.dispatchTouchEvent(ev);
    }

    @Override public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if (!BuildConfig.TV_UI) {
            if (newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE) {
                if (pages.get(1).getVisibility() == View.VISIBLE) enterMobilePlayerFullscreen();
            } else {
                exitMobilePlayerFullscreen();
            }
        }
    }

    private void releasePlayer() {
        if (player != null) {
            try {
                player.setPlayWhenReady(false);
                player.stop();
                player.clearMediaItems();
                player.release();
            } catch (Exception ignored) {
            } finally {
                player = null;
                if (playerView != null) {
                    playerView.setPlayer(null);
                }
            }
        }
    }

    @Override protected void onUserLeaveHint() {
        super.onUserLeaveHint();
        if (!BuildConfig.TV_UI && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && player != null && player.isPlaying() && pages.size() > 1 && pages.get(1).getVisibility() == View.VISIBLE) {
            try {
                enteringPictureInPicture = true;
                enterPictureInPictureMode(new PictureInPictureParams.Builder().setAspectRatio(new Rational(16, 9)).build());
            } catch (Exception ignored) {
                enteringPictureInPicture = false;
            }
        }
    }

    @Override public void onPictureInPictureModeChanged(boolean isInPictureInPictureMode, Configuration newConfig) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig);
        enteringPictureInPicture = false;
        if (!isInPictureInPictureMode) {
            // When leaving PiP (whether user closed PiP or dismissed it), kill player instantly!
            releasePlayer();
        }
    }

    @Override protected void onResume() {
        super.onResume();
        enteringPictureInPicture = false;
        // If returning to foreground and on live tab with no player, restore playback cleanly
        if (!BuildConfig.TV_UI && player == null && pages.size() > 1 && pages.get(1).getVisibility() == View.VISIBLE) {
            continueWatching();
        }
    }

    @Override protected void onPause() {
        super.onPause();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            if (!isInPictureInPictureMode() && !enteringPictureInPicture) {
                if (player != null) {
                    player.setPlayWhenReady(false);
                    player.pause();
                }
            }
        } else {
            if (player != null) {
                player.setPlayWhenReady(false);
                player.pause();
            }
        }
    }

    @Override protected void onStop() {
        super.onStop();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && isInPictureInPictureMode()) {
            return;
        }
        releasePlayer();
    }

    @Override protected void onDestroy() {
        super.onDestroy();
        releasePlayer();
        latencyTester.close();
    }
}
