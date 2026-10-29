package com.aether.launcher;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.GestureDetector;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.VideoView;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.aether.launcher.apps.AppDeckRepository;
import com.aether.launcher.apps.AppModel;
import com.aether.launcher.apps.HolographicDeckAdapter;
import com.aether.launcher.arcade.HoloArcadeView;
import com.aether.launcher.audio.CyberSoundSynth;
import com.aether.launcher.hud.ArcReactorView;
import com.aether.launcher.hud.GyroParallaxManager;
import com.aether.launcher.hud.HologramTheme;
import com.aether.launcher.hud.SystemTelemetry;
import com.aether.launcher.iptv.CyberStreamDeck;
import com.aether.launcher.iptv.StreamChannel;
import com.aether.launcher.terminal.CyberCommandEngine;
import com.aether.launcher.widgets.CyberWidgetHostManager;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

public class AetherHomeActivity extends Activity {

    // HUD & Sensors
    private ArcReactorView arcReactorView;
    private SystemTelemetry telemetry;
    private GyroParallaxManager gyroManager;
    private HologramTheme theme;

    // Repositories & Engines
    private AppDeckRepository appDeckRepo;
    private HolographicDeckAdapter horizontalShelfAdapter;
    private HolographicDeckAdapter fallbackMatrixAdapter;
    private CyberCommandEngine commandEngine;
    private CyberWidgetHostManager widgetHostManager;
    private CyberStreamDeck streamDeck;

    // 16:9 Stage Views
    private TextView txtTacticalTitle;
    private TextView txtChronoStardate;
    private TextView txtTelemetryTop;
    private TextView btnThemeCycle;

    // Hero Stage (Mutual Exclusion Engine)
    private FrameLayout containerHeroStage;
    private FrameLayout heroVideoContainer;
    private VideoView heroVideoPlayer;
    private TextView txtHeroMode;
    private TextView btnToggleHero;
    private TextView heroChannelName;
    private TextView heroStreamStatus;
    private TextView btnHeroPrevCh;
    private TextView btnHeroNextCh;
    private boolean isHeroVideoActive = false;
    private final List<StreamChannel> heroChannels = new ArrayList<>();
    private int currentHeroChannelIdx = 0;

    // Right Stage: Dynamic Dossier & Hardware Radar
    private ImageView dossierAppIcon;
    private TextView dossierAppName;
    private TextView dossierAppCategory;
    private TextView dossierAppPackage;
    private TextView radarCpuGauge;
    private TextView radarRamGauge;
    private TextView radarPowerGauge;
    private TextView radarNetGauge;

    // Lower Shelf: Category Tabs & Horizontal Carousel
    private LinearLayout shelfCategoryTabs;
    private RecyclerView recyclerHorizontalShelf;
    private AppModel.Category activeCategory = AppModel.Category.ALL;

    // Overlays & Fallbacks (Strictly Preserved)
    private View drawerAppMatrix;
    private View overlayConsole;
    private View overlayStreamDeck;
    private HoloArcadeView overlayArcade;
    private FrameLayout widgetContainer;
    private RecyclerView recyclerApps;
    private LinearLayout categoriesChipContainer;

    // Terminal views
    private TextView consoleOutput;
    private EditText inputCommand;
    private ScrollView consoleScroll;

    private GestureDetector gestureDetector;
    private final Handler chronoHandler = new Handler(Looper.getMainLooper());
    private final SimpleDateFormat utcFormat = new SimpleDateFormat("HH:mm:ss", Locale.US);

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_aether_home);

        utcFormat.setTimeZone(TimeZone.getTimeZone("UTC"));

        theme = HologramTheme.get();
        telemetry = new SystemTelemetry(this);
        gyroManager = new GyroParallaxManager(this);
        appDeckRepo = new AppDeckRepository(this);
        commandEngine = new CyberCommandEngine(this, appDeckRepo, telemetry, gyroManager);
        widgetHostManager = new CyberWidgetHostManager(this);

        initHeroChannels();
        initViews();
        setupGestureDetector();
        setupTelemetry();
        setupHorizontalShelf();
        setupFallbackAppMatrix();
        setupTerminal();
        setupStreamDeck();
        setupArcade();
        startChronoLoop();

        CyberSoundSynth.get().playPowerUp();
    }

    private void initHeroChannels() {
        heroChannels.add(new StreamChannel("NASA TV LIVE", "https://ntv1.akamaized.net/hls/live/2014075/NASA-NTV1-HLS/master.m3u8", "SPACE"));
        heroChannels.add(new StreamChannel("FRANCE 24 NEWS", "https://stream.france24.com/hls/live/2037989/f24_en/master.m3u8", "NEWS"));
        heroChannels.add(new StreamChannel("RED BULL TV", "https://rbmn-live.akamaized.net/hls/live/590964/BoRB-AT/master.m3u8", "SPORTS"));
        heroChannels.add(new StreamChannel("EURONEWS GLOBAL", "https://euronews-euronews-world-1-au.samsung.wurl.tv/playlist.m3u8", "GLOBAL"));
    }

    private void initViews() {
        // Top Diagnostics Bar
        txtTacticalTitle = findViewById(R.id.txt_tactical_title);
        txtChronoStardate = findViewById(R.id.txt_chrono_stardate);
        txtTelemetryTop = findViewById(R.id.txt_telemetry_top);
        btnThemeCycle = findViewById(R.id.btn_theme_cycle);

        // Left Stage: Hero Core & Video
        containerHeroStage = findViewById(R.id.container_hero_stage);
        arcReactorView = findViewById(R.id.arc_reactor_view);
        heroVideoContainer = findViewById(R.id.hero_video_container);
        heroVideoPlayer = findViewById(R.id.hero_video_player);
        txtHeroMode = findViewById(R.id.txt_hero_mode);
        btnToggleHero = findViewById(R.id.btn_toggle_hero);
        heroChannelName = findViewById(R.id.hero_channel_name);
        heroStreamStatus = findViewById(R.id.hero_stream_status);
        btnHeroPrevCh = findViewById(R.id.btn_hero_prev_ch);
        btnHeroNextCh = findViewById(R.id.btn_hero_next_ch);

        // Right Stage: Active Dossier & Radar
        dossierAppIcon = findViewById(R.id.dossier_app_icon);
        dossierAppName = findViewById(R.id.dossier_app_name);
        dossierAppCategory = findViewById(R.id.dossier_app_category);
        dossierAppPackage = findViewById(R.id.dossier_app_package);
        radarCpuGauge = findViewById(R.id.radar_cpu_gauge);
        radarRamGauge = findViewById(R.id.radar_ram_gauge);
        radarPowerGauge = findViewById(R.id.radar_power_gauge);
        radarNetGauge = findViewById(R.id.radar_net_gauge);

        // Lower Shelf
        shelfCategoryTabs = findViewById(R.id.shelf_category_tabs);
        recyclerHorizontalShelf = findViewById(R.id.recycler_horizontal_shelf);

        // Overlays & Fallbacks
        drawerAppMatrix = findViewById(R.id.drawer_app_matrix);
        overlayConsole = findViewById(R.id.overlay_console);
        overlayStreamDeck = findViewById(R.id.overlay_stream_deck);
        overlayArcade = findViewById(R.id.overlay_arcade);
        widgetContainer = findViewById(R.id.widget_container);

        // Reactor center tap
        arcReactorView.setOnCoreClickListener(this::openTerminal);

        // Theme Switcher Button
        btnThemeCycle.setOnClickListener(v -> cycleTheme());

        // Hero Stage Mutual Exclusion Toggle
        btnToggleHero.setOnClickListener(v -> {
            CyberSoundSynth.get().playChirp();
            toggleHeroStage();
        });

        // Hero Video Channel Controls
        btnHeroPrevCh.setOnClickListener(v -> {
            CyberSoundSynth.get().playChirp();
            prevHeroChannel();
        });

        btnHeroNextCh.setOnClickListener(v -> {
            CyberSoundSynth.get().playChirp();
            nextHeroChannel();
        });

        heroVideoPlayer.setOnPreparedListener(mp -> {
            heroStreamStatus.setText("SIGNAL // 1080p ONLINE");
            heroStreamStatus.setTextColor(0xFF00FF66);
            heroVideoPlayer.start();
        });

        heroVideoPlayer.setOnErrorListener((mp, what, extra) -> {
            heroStreamStatus.setText("SIGNAL // BUFFER FAULT");
            heroStreamStatus.setTextColor(0xFFFF2A6D);
            return true;
        });

        // Bottom Orbital Buttons
        findViewById(R.id.btn_open_terminal).setOnClickListener(v -> {
            CyberSoundSynth.get().playChirp();
            openTerminal();
        });

        findViewById(R.id.btn_open_apps).setOnClickListener(v -> {
            CyberSoundSynth.get().playChirp();
            openAppMatrix();
        });

        findViewById(R.id.btn_open_iptv).setOnClickListener(v -> {
            CyberSoundSynth.get().playChirp();
            openStreamDeck();
        });

        findViewById(R.id.btn_open_arcade).setOnClickListener(v -> {
            CyberSoundSynth.get().playChirp();
            openArcade("vector");
        });

        findViewById(R.id.btn_add_widget).setOnClickListener(v -> {
            CyberSoundSynth.get().playChirp();
            widgetHostManager.pickWidget();
        });

        findViewById(R.id.btn_close_apps).setOnClickListener(v -> {
            CyberSoundSynth.get().playChirp();
            closeAllOverlays();
        });
    }

    private void cycleTheme() {
        CyberSoundSynth.get().playChirp();
        theme.cycleNext();
        btnThemeCycle.setText("[ " + theme.getThemeType().name() + " ]");
        updateThemeAccents();
    }

    private void updateThemeAccents() {
        int primary = theme.getPrimary();
        txtTacticalTitle.setTextColor(primary);
        txtHeroMode.setTextColor(primary);
        if (dossierAppCategory != null) {
            dossierAppCategory.setTextColor(primary);
        }
    }

    private void toggleHeroStage() {
        if (!isHeroVideoActive) {
            // Switch to Video Monitor: Pause Kinetic Arc Reactor to preserve 1GB RAM budget
            isHeroVideoActive = true;
            arcReactorView.pauseAnimation();
            arcReactorView.setVisibility(View.GONE);
            heroVideoContainer.setVisibility(View.VISIBLE);
            txtHeroMode.setText("// HERO STAGE: LIVE AV MONITOR //");
            btnToggleHero.setText("[ SWITCH TO CORE ]");
            tuneHeroChannel();
        } else {
            // Switch to Kinetic Core: Stop video decoder playback to preserve RAM
            isHeroVideoActive = false;
            heroVideoPlayer.stopPlayback();
            heroVideoContainer.setVisibility(View.GONE);
            arcReactorView.setVisibility(View.VISIBLE);
            arcReactorView.resumeAnimation();
            txtHeroMode.setText("// HERO STAGE: TACTICAL CORE //");
            btnToggleHero.setText("[ SWITCH TO AV MONITOR ]");
        }
    }

    private void tuneHeroChannel() {
        if (heroChannels.isEmpty()) return;
        StreamChannel ch = heroChannels.get(currentHeroChannelIdx);
        heroChannelName.setText(String.format("CH %02d // %s", currentHeroChannelIdx + 1, ch.getName()));
        heroStreamStatus.setText("TUNING // CONNECTING...");
        heroStreamStatus.setTextColor(0xFF00F0FF);
        heroVideoPlayer.setVideoURI(Uri.parse(ch.getStreamUrl()));
    }

    private void nextHeroChannel() {
        if (heroChannels.isEmpty()) return;
        currentHeroChannelIdx = (currentHeroChannelIdx + 1) % heroChannels.size();
        tuneHeroChannel();
    }

    private void prevHeroChannel() {
        if (heroChannels.isEmpty()) return;
        currentHeroChannelIdx = (currentHeroChannelIdx - 1 + heroChannels.size()) % heroChannels.size();
        tuneHeroChannel();
    }

    private void setupGestureDetector() {
        gestureDetector = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            private static final int SWIPE_THRESHOLD = 80;
            private static final int SWIPE_VELOCITY_THRESHOLD = 80;

            @Override
            public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
                if (e1 == null || e2 == null) return false;
                float diffY = e2.getY() - e1.getY();
                if (Math.abs(diffY) > SWIPE_THRESHOLD && Math.abs(velocityY) > SWIPE_VELOCITY_THRESHOLD) {
                    if (diffY < 0) {
                        openAppMatrix();
                        return true;
                    } else {
                        openTerminal();
                        return true;
                    }
                }
                return false;
            }
        });
    }

    private void setupTelemetry() {
        arcReactorView.setTelemetry(telemetry);
        telemetry.start(() -> runOnUiThread(this::refreshTelemetryDisplay));

        gyroManager.start((pitch, roll) -> {
            if (!isHeroVideoActive) {
                arcReactorView.setParallax(pitch, roll);
            }
            overlayArcade.applyGyroInput(roll);
        });
    }

    private void refreshTelemetryDisplay() {
        // Top Status Bar
        if (telemetry.isAcPowered()) {
            txtTelemetryTop.setText(String.format(Locale.US, "NET %s // %dM · RAM %d%% · AC ONLINE",
                    telemetry.getWifiSsid(), telemetry.getWifiSpeedMbps(), telemetry.getRamUsagePercent()));
        } else {
            txtTelemetryTop.setText(String.format(Locale.US, "NET %s // %dM · RAM %d%% · BAT %.1fV",
                    telemetry.getWifiSsid(), telemetry.getWifiSpeedMbps(), telemetry.getRamUsagePercent(), telemetry.getBatteryVoltageV()));
        }

        // Right Stage Hardware Radar
        int ramPercent = telemetry.getRamUsagePercent();
        long totalMb = telemetry.getTotalRamBytes() / (1024 * 1024);
        long availMb = telemetry.getAvailRamBytes() / (1024 * 1024);
        long usedMb = totalMb - availMb;

        radarRamGauge.setText(String.format(Locale.US, "RAM LOAD: %s %d%% (%dMB / %dMB)",
                buildGaugeBar(ramPercent), ramPercent, usedMb, totalMb));

        // Simulated CPU load proportional to RAM & animations
        int cpuPercent = Math.min(95, Math.max(15, (int) (ramPercent * 0.75f + (isHeroVideoActive ? 22 : 8))));
        radarCpuGauge.setText(String.format(Locale.US, "CPU LOAD: %s %d%% (4 CORES)",
                buildGaugeBar(cpuPercent), cpuPercent));

        if (telemetry.isAcPowered()) {
            radarPowerGauge.setText(String.format(Locale.US, "TV POWER: AC LINE ACTIVE // %.1f°C", telemetry.getBatteryTempC()));
        } else {
            radarPowerGauge.setText(String.format(Locale.US, "TV POWER: BATTERY %.1fV // %.1f°C",
                    telemetry.getBatteryVoltageV(), telemetry.getBatteryTempC()));
        }

        radarNetGauge.setText(String.format(Locale.US, "NETWORK : %s // %d Mbps ONLINE",
                telemetry.getWifiSsid(), telemetry.getWifiSpeedMbps()));
    }

    private String buildGaugeBar(int percent) {
        int blocks = Math.max(0, Math.min(10, percent / 10));
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < 10; i++) {
            if (i < blocks) {
                sb.append("■");
            } else {
                sb.append("□");
            }
        }
        sb.append("]");
        return sb.toString();
    }

    private void startChronoLoop() {
        chronoHandler.post(new Runnable() {
            @Override
            public void run() {
                String timeUtc = utcFormat.format(new Date());
                txtChronoStardate.setText(String.format("[ %s UTC // %s ]", timeUtc, telemetry.getStardateString()));
                chronoHandler.postDelayed(this, 1000);
            }
        });
    }

    private void setupHorizontalShelf() {
        LinearLayoutManager layoutManager = new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false);
        recyclerHorizontalShelf.setLayoutManager(layoutManager);

        horizontalShelfAdapter = new HolographicDeckAdapter(this, new HolographicDeckAdapter.OnAppActionListener() {
            @Override
            public void onAppLaunched(AppModel app) {
                closeAllOverlays();
            }

            @Override
            public void onAppLongClicked(AppModel app) {
                appDeckRepo.togglePin(app);
                horizontalShelfAdapter.notifyDataSetChanged();
                if (fallbackMatrixAdapter != null) fallbackMatrixAdapter.notifyDataSetChanged();
            }
        });

        // Dynamic Dossier Focus Listener
        horizontalShelfAdapter.setOnAppFocusChangeListener(this::updateAppDossier);
        recyclerHorizontalShelf.setAdapter(horizontalShelfAdapter);

        // Build Horizontal Category Tabs
        buildCategoryTabs();

        // Initial Load
        new Thread(() -> {
            appDeckRepo.reloadApps();
            runOnUiThread(this::refreshAppList);
        }).start();
    }

    private void buildCategoryTabs() {
        shelfCategoryTabs.removeAllViews();
        for (AppModel.Category cat : AppModel.Category.values()) {
            TextView tab = new TextView(this);
            tab.setText("[ " + cat.label + " ]");
            tab.setFontFeatureSettings("monospace");
            tab.setTextColor(0xFF00F0FF);
            tab.setTextSize(10f);
            tab.setPadding(20, 6, 20, 6);
            tab.setBackgroundResource(R.drawable.bg_cyber_tab_selector);
            tab.setFocusable(true);
            tab.setClickable(true);

            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.setMargins(6, 0, 6, 0);
            tab.setLayoutParams(lp);

            tab.setOnClickListener(v -> {
                CyberSoundSynth.get().playDataClick();
                activeCategory = cat;
                highlightActiveTab(tab);
                refreshAppList();
            });

            tab.setOnFocusChangeListener((v, hasFocus) -> {
                if (hasFocus) {
                    CyberSoundSynth.get().playDataClick();
                }
            });

            if (cat == activeCategory) {
                tab.setSelected(true);
            }
            shelfCategoryTabs.addView(tab);
        }
    }

    private void highlightActiveTab(View selectedTab) {
        for (int i = 0; i < shelfCategoryTabs.getChildCount(); i++) {
            View child = shelfCategoryTabs.getChildAt(i);
            child.setSelected(child == selectedTab);
        }
    }

    private void updateAppDossier(AppModel app) {
        if (app == null) return;
        dossierAppName.setText(app.getAppName());
        dossierAppCategory.setText("STATUS: INSTALLED // [" + app.getCategory().label + "]");
        dossierAppCategory.setTextColor(theme.getPrimary());
        dossierAppPackage.setText(app.getPackageName());
        if (app.getIcon() != null) {
            dossierAppIcon.setImageDrawable(app.getIcon());
        }
    }

    private void setupFallbackAppMatrix() {
        recyclerApps = findViewById(R.id.recycler_apps);
        categoriesChipContainer = findViewById(R.id.categories_chip_container);

        recyclerApps.setLayoutManager(new GridLayoutManager(this, 4));
        fallbackMatrixAdapter = new HolographicDeckAdapter(this, new HolographicDeckAdapter.OnAppActionListener() {
            @Override
            public void onAppLaunched(AppModel app) {
                closeAllOverlays();
            }

            @Override
            public void onAppLongClicked(AppModel app) {
                appDeckRepo.togglePin(app);
                fallbackMatrixAdapter.notifyDataSetChanged();
                horizontalShelfAdapter.notifyDataSetChanged();
            }
        });
        recyclerApps.setAdapter(fallbackMatrixAdapter);

        // Build Drawer Category Chips
        categoriesChipContainer.removeAllViews();
        for (AppModel.Category cat : AppModel.Category.values()) {
            TextView chip = new TextView(this);
            chip.setText("[ " + cat.label + " ]");
            chip.setFontFeatureSettings("monospace");
            chip.setTextColor(0xFF00F0FF);
            chip.setTextSize(11f);
            chip.setPadding(16, 8, 16, 8);
            chip.setFocusable(true);
            chip.setClickable(true);
            chip.setBackgroundResource(R.drawable.bg_cyber_tab_selector);
            chip.setOnClickListener(v -> {
                CyberSoundSynth.get().playDataClick();
                activeCategory = cat;
                refreshAppList();
            });
            categoriesChipContainer.addView(chip);
        }
    }

    private void refreshAppList() {
        List<AppModel> filtered = appDeckRepo.filter(activeCategory, null);
        horizontalShelfAdapter.setApps(filtered);
        if (fallbackMatrixAdapter != null) {
            fallbackMatrixAdapter.setApps(filtered);
        }
        if (!filtered.isEmpty()) {
            updateAppDossier(filtered.get(0));
            recyclerHorizontalShelf.post(() -> {
                RecyclerView.ViewHolder vh = recyclerHorizontalShelf.findViewHolderForAdapterPosition(0);
                if (vh != null) {
                    vh.itemView.requestFocus();
                }
            });
        }
    }

    private void setupTerminal() {
        consoleOutput = overlayConsole.findViewById(R.id.console_output);
        inputCommand = overlayConsole.findViewById(R.id.input_command);
        consoleScroll = overlayConsole.findViewById(R.id.console_scroll);

        consoleOutput.setText("// AETHER NEXUS TERMINAL ONLINE //\nREADY FOR QUERY OR APP EXECUTION...\nNOTE: Fire OS Vizzini Speech Service is restricted to signature apps.\n");

        overlayConsole.findViewById(R.id.btn_close_console).setOnClickListener(v -> {
            CyberSoundSynth.get().playChirp();
            closeAllOverlays();
        });

        overlayConsole.findViewById(R.id.btn_exec_command).setOnClickListener(v -> executeCurrentCommand());

        inputCommand.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEND ||
                    (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER && event.getAction() == KeyEvent.ACTION_DOWN)) {
                executeCurrentCommand();
                return true;
            }
            return false;
        });
    }

    private void executeCurrentCommand() {
        String query = inputCommand.getText().toString().trim();
        if (query.isEmpty()) return;

        CyberSoundSynth.get().playDataClick();
        inputCommand.setText("");
        appendConsole("AETHER:~$ " + query);

        commandEngine.execute(query, new CyberCommandEngine.CommandCallback() {
            @Override
            public void onOutput(String text) {
                appendConsole(text);
            }

            @Override
            public void onLaunchApp(AppModel app) {
                closeAllOverlays();
                horizontalShelfAdapter.notifyDataSetChanged();
            }

            @Override
            public void onOpenIptv() {
                openStreamDeck();
            }

            @Override
            public void onOpenArcade(String gameType) {
                openArcade(gameType);
            }
        });
    }

    private void appendConsole(String line) {
        consoleOutput.append(line + "\n\n");
        consoleScroll.post(() -> consoleScroll.fullScroll(View.FOCUS_DOWN));
    }

    private void setupStreamDeck() {
        streamDeck = new CyberStreamDeck(overlayStreamDeck);
        streamDeck.setOnCloseListener(this::closeAllOverlays);
    }

    private void setupArcade() {
        overlayArcade.setOnExitListener(this::closeAllOverlays);
    }

    public void openAppMatrix() {
        closeAllOverlays();
        drawerAppMatrix.setVisibility(View.VISIBLE);
        drawerAppMatrix.setAlpha(0f);
        drawerAppMatrix.animate().alpha(1f).setDuration(160).start();
        recyclerApps.requestFocus();
    }

    public void openTerminal() {
        closeAllOverlays();
        overlayConsole.setVisibility(View.VISIBLE);
        overlayConsole.setAlpha(0f);
        overlayConsole.animate().alpha(1f).setDuration(160).start();
        inputCommand.requestFocus();
    }

    public void openStreamDeck() {
        closeAllOverlays();
        overlayStreamDeck.setVisibility(View.VISIBLE);
        overlayStreamDeck.setAlpha(0f);
        overlayStreamDeck.animate().alpha(1f).setDuration(160).start();
        streamDeck.start();
    }

    public void openArcade(String gameType) {
        closeAllOverlays();
        overlayArcade.setVisibility(View.VISIBLE);
        if ("hack".equalsIgnoreCase(gameType)) {
            overlayArcade.setGameMode(HoloArcadeView.GameMode.CYBER_HACK);
        } else {
            overlayArcade.setGameMode(HoloArcadeView.GameMode.VECTOR_DODGE);
        }
        overlayArcade.resetGame();
        overlayArcade.requestFocus();
    }

    public void closeAllOverlays() {
        drawerAppMatrix.setVisibility(View.GONE);
        overlayConsole.setVisibility(View.GONE);
        overlayStreamDeck.setVisibility(View.GONE);
        overlayArcade.setVisibility(View.GONE);
        streamDeck.stop();
        recyclerHorizontalShelf.requestFocus();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_MENU) {
            cycleTheme();
            return true;
        }

        // TV Remote Navigation Shortcuts
        if (keyCode == KeyEvent.KEYCODE_DPAD_UP && drawerAppMatrix.getVisibility() != View.VISIBLE && overlayArcade.getVisibility() != View.VISIBLE) {
            // If focus is already in tabs or shelf, allow user to toggle hero or tabs
        }

        return super.onKeyDown(keyCode, event);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (gestureDetector != null && gestureDetector.onTouchEvent(event)) {
            return true;
        }
        return super.onTouchEvent(event);
    }

    @Override
    public void onBackPressed() {
        if (drawerAppMatrix.getVisibility() == View.VISIBLE ||
                overlayConsole.getVisibility() == View.VISIBLE ||
                overlayStreamDeck.getVisibility() == View.VISIBLE ||
                overlayArcade.getVisibility() == View.VISIBLE) {
            closeAllOverlays();
        } else {
            // At home base: do not exit launcher
            recyclerHorizontalShelf.requestFocus();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        widgetHostManager.handleActivityResult(requestCode, resultCode, data, widgetContainer);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (isHeroVideoActive) {
            tuneHeroChannel();
        } else if (arcReactorView != null) {
            arcReactorView.resumeAnimation();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (arcReactorView != null) {
            arcReactorView.pauseAnimation();
        }
        if (heroVideoPlayer != null && heroVideoPlayer.isPlaying()) {
            heroVideoPlayer.pause();
        }
    }

    @Override
    public void onTrimMemory(int level) {
        super.onTrimMemory(level);
        // Low-RAM Freezing Policy for 1GB Fire TV Stick
        if (level >= TRIM_MEMORY_BACKGROUND || level == TRIM_MEMORY_RUNNING_LOW) {
            if (arcReactorView != null) {
                arcReactorView.pauseAnimation();
            }
            if (heroVideoPlayer != null && isHeroVideoActive) {
                heroVideoPlayer.stopPlayback();
            }
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        widgetHostManager.startListening();
    }

    @Override
    protected void onStop() {
        super.onStop();
        widgetHostManager.stopListening();
        streamDeck.stop();
        if (heroVideoPlayer != null) {
            heroVideoPlayer.stopPlayback();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        chronoHandler.removeCallbacksAndMessages(null);
        telemetry.stop();
        gyroManager.stop();
    }
}
