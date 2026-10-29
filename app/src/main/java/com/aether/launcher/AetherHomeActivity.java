package com.aether.launcher;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
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

    // Hero Stage (Cinematic Artwork, Ambient Core, Live AV Engine)
    private enum HeroStageMode { CINEMATIC_ART, AMBIENT_CORE, LIVE_AV }
    private HeroStageMode currentHeroMode = HeroStageMode.CINEMATIC_ART;
    private FrameLayout containerHeroStage;
    private FrameLayout heroArtContainer;
    private ImageView heroBackdropImage;
    private FrameLayout heroVideoContainer;
    private VideoView heroVideoPlayer;
    private TextView btnToggleHero;
    private TextView heroChannelName;
    private TextView heroStreamStatus;
    private final List<StreamChannel> heroChannels = new ArrayList<>();
    private int currentHeroChannelIdx = 0;

    // Featured Spotlight Stage
    private ImageView dossierAppIcon;
    private TextView dossierAppName;
    private TextView dossierAppCategory;
    private TextView dossierAppPackage;
    private ImageView heroAppBanner;
    private View heroAppIconFrame;
    private TextView btnHeroLaunch;
    private AppModel currentlyFocusedApp;

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
    private View lastFocusedViewBeforeOverlay;

    private GestureDetector gestureDetector;
    private final Handler chronoHandler = new Handler(Looper.getMainLooper());
    private final SimpleDateFormat utcFormat = new SimpleDateFormat("HH:mm:ss", Locale.US);

    // Tactile Focus Scaler and Audio Feedback for TV Remote
    private final View.OnFocusChangeListener buttonFocusScaler = (v, hasFocus) -> {
        if (hasFocus) {
            v.animate().scaleX(1.06f).scaleY(1.06f).setDuration(120).start();
            CyberSoundSynth.get().playDataClick();
        } else {
            v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(120).start();
        }
    };

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

        // Preview Stage: Hero Core, Cinematic Art & Video Monitor
        containerHeroStage = findViewById(R.id.container_hero_stage);
        heroArtContainer = findViewById(R.id.hero_art_container);
        heroBackdropImage = findViewById(R.id.hero_backdrop_image);
        arcReactorView = findViewById(R.id.arc_reactor_view);
        heroVideoContainer = findViewById(R.id.hero_video_container);
        heroVideoPlayer = findViewById(R.id.hero_video_player);
        btnToggleHero = findViewById(R.id.btn_toggle_hero);
        heroChannelName = findViewById(R.id.hero_channel_name);
        heroStreamStatus = findViewById(R.id.hero_stream_status);

        // Default Hero Presentation: Cinematic Artwork Mode
        if (heroArtContainer != null) heroArtContainer.setVisibility(View.VISIBLE);
        if (arcReactorView != null) {
            arcReactorView.setVisibility(View.GONE);
            arcReactorView.pauseAnimation();
        }
        if (heroVideoContainer != null) heroVideoContainer.setVisibility(View.GONE);
        if (btnToggleHero != null) btnToggleHero.setText("⟐  AMBIENT CORE");

        // Featured Spotlight Stage
        dossierAppIcon = findViewById(R.id.dossier_app_icon);
        dossierAppName = findViewById(R.id.dossier_app_name);
        dossierAppCategory = findViewById(R.id.dossier_app_category);
        dossierAppPackage = findViewById(R.id.dossier_app_package);
        heroAppBanner = findViewById(R.id.hero_app_banner);
        heroAppIconFrame = findViewById(R.id.hero_app_icon_frame);
        btnHeroLaunch = findViewById(R.id.btn_hero_launch);

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

        // Bottom Dock Buttons
        View btnApps = findViewById(R.id.btn_open_apps);
        if (btnApps != null) {
            btnApps.setOnClickListener(v -> {
                CyberSoundSynth.get().playChirp();
                openAppMatrix();
            });
        }

        View btnIptv = findViewById(R.id.btn_open_iptv);
        if (btnIptv != null) {
            btnIptv.setOnClickListener(v -> {
                CyberSoundSynth.get().playChirp();
                openStreamDeck();
            });
        }

        View btnArcade = findViewById(R.id.btn_open_arcade);
        if (btnArcade != null) {
            btnArcade.setOnClickListener(v -> {
                CyberSoundSynth.get().playChirp();
                openArcade("vector");
            });
        }

        View btnTerminal = findViewById(R.id.btn_open_terminal);
        if (btnTerminal != null) {
            btnTerminal.setOnClickListener(v -> {
                CyberSoundSynth.get().playChirp();
                openTerminal();
            });
        }

        // Tactile Focus Scaler and Audio Feedback for TV Remote
        if (btnHeroLaunch != null) btnHeroLaunch.setOnFocusChangeListener(buttonFocusScaler);
        if (btnToggleHero != null) btnToggleHero.setOnFocusChangeListener(buttonFocusScaler);
        if (btnApps != null) btnApps.setOnFocusChangeListener(buttonFocusScaler);
        if (btnArcade != null) btnArcade.setOnFocusChangeListener(buttonFocusScaler);
        if (btnIptv != null) btnIptv.setOnFocusChangeListener(buttonFocusScaler);
        if (btnTerminal != null) btnTerminal.setOnFocusChangeListener(buttonFocusScaler);
        if (btnThemeCycle != null) btnThemeCycle.setOnFocusChangeListener(buttonFocusScaler);

        // Bi-directional D-Pad remote navigation listeners
        View.OnKeyListener heroButtonDownListener = (v, keyCode, event) -> {
            if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN && event.getAction() == KeyEvent.ACTION_DOWN) {
                if (shelfCategoryTabs != null && shelfCategoryTabs.getChildCount() > 0) {
                    View target = null;
                    for (int i = 0; i < shelfCategoryTabs.getChildCount(); i++) {
                        if (shelfCategoryTabs.getChildAt(i).isSelected()) {
                            target = shelfCategoryTabs.getChildAt(i);
                            break;
                        }
                    }
                    if (target == null) target = shelfCategoryTabs.getChildAt(0);
                    if (target != null) {
                        target.requestFocus();
                        return true;
                    }
                }
            }
            return false;
        };
        if (btnHeroLaunch != null) btnHeroLaunch.setOnKeyListener(heroButtonDownListener);
        if (btnToggleHero != null) btnToggleHero.setOnKeyListener(heroButtonDownListener);

        View.OnKeyListener dockUpListener = (v, keyCode, event) -> {
            if (keyCode == KeyEvent.KEYCODE_DPAD_UP && event.getAction() == KeyEvent.ACTION_DOWN) {
                if (recyclerHorizontalShelf != null && recyclerHorizontalShelf.getChildCount() > 0) {
                    View shelfChild = recyclerHorizontalShelf.getFocusedChild();
                    if (shelfChild != null) {
                        shelfChild.requestFocus();
                        return true;
                    }
                    recyclerHorizontalShelf.getChildAt(0).requestFocus();
                    return true;
                }
            }
            return false;
        };
        if (btnApps != null) btnApps.setOnKeyListener(dockUpListener);
        if (btnArcade != null) btnArcade.setOnKeyListener(dockUpListener);
        if (btnIptv != null) btnIptv.setOnKeyListener(dockUpListener);
        if (btnTerminal != null) btnTerminal.setOnKeyListener(dockUpListener);

        if (btnThemeCycle != null) {
            btnThemeCycle.setOnKeyListener((v, keyCode, event) -> {
                if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN && event.getAction() == KeyEvent.ACTION_DOWN) {
                    if (btnHeroLaunch != null) {
                        btnHeroLaunch.requestFocus();
                        return true;
                    }
                }
                return false;
            });
        }

        View btnCloseApps = findViewById(R.id.btn_close_apps);
        if (btnCloseApps != null) {
            btnCloseApps.setOnFocusChangeListener(buttonFocusScaler);
            btnCloseApps.setOnClickListener(v -> {
                CyberSoundSynth.get().playChirp();
                closeAllOverlays();
            });
        }
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
        if (dossierAppCategory != null) {
            dossierAppCategory.setTextColor(primary);
        }
    }

    private void toggleHeroStage() {
        switch (currentHeroMode) {
            case CINEMATIC_ART:
                // Switch to Ambient Core
                currentHeroMode = HeroStageMode.AMBIENT_CORE;
                if (heroArtContainer != null) heroArtContainer.setVisibility(View.GONE);
                if (heroVideoContainer != null) {
                    heroVideoPlayer.stopPlayback();
                    heroVideoContainer.setVisibility(View.GONE);
                }
                if (arcReactorView != null) {
                    arcReactorView.setVisibility(View.VISIBLE);
                    arcReactorView.resumeAnimation();
                }
                if (btnToggleHero != null) btnToggleHero.setText("▶  LIVE AV");
                break;
            case AMBIENT_CORE:
                // Switch to Live AV
                currentHeroMode = HeroStageMode.LIVE_AV;
                if (arcReactorView != null) {
                    arcReactorView.pauseAnimation();
                    arcReactorView.setVisibility(View.GONE);
                }
                if (heroArtContainer != null) heroArtContainer.setVisibility(View.GONE);
                if (heroVideoContainer != null) {
                    heroVideoContainer.setVisibility(View.VISIBLE);
                }
                if (btnToggleHero != null) btnToggleHero.setText("⟐  CINEMATIC ART");
                tuneHeroChannel();
                break;
            case LIVE_AV:
                // Switch back to Cinematic Artwork
                currentHeroMode = HeroStageMode.CINEMATIC_ART;
                if (heroVideoPlayer != null) heroVideoPlayer.stopPlayback();
                if (heroVideoContainer != null) heroVideoContainer.setVisibility(View.GONE);
                if (arcReactorView != null) {
                    arcReactorView.pauseAnimation();
                    arcReactorView.setVisibility(View.GONE);
                }
                if (heroArtContainer != null) heroArtContainer.setVisibility(View.VISIBLE);
                if (btnToggleHero != null) btnToggleHero.setText("⟐  AMBIENT CORE");
                break;
        }
    }

    private void tuneHeroChannel() {
        if (heroChannels.isEmpty()) return;
        StreamChannel ch = heroChannels.get(currentHeroChannelIdx);
        heroChannelName.setText(String.format(Locale.US, "CH %02d // %s", currentHeroChannelIdx + 1, ch.getName()));
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
            if (currentHeroMode == HeroStageMode.AMBIENT_CORE) {
                arcReactorView.setParallax(pitch, roll);
            }
            overlayArcade.applyGyroInput(roll);
        });
    }

    private void refreshTelemetryDisplay() {
        // Top Status Bar: clean status pills without slashes
        if (txtTelemetryTop != null) {
            if (telemetry.isAcPowered()) {
                txtTelemetryTop.setText(String.format(Locale.US, "WI-FI %s · %dM · RAM %d%% · AC",
                        telemetry.getWifiSsid(), telemetry.getWifiSpeedMbps(), telemetry.getRamUsagePercent()));
            } else {
                txtTelemetryTop.setText(String.format(Locale.US, "WI-FI %s · %dM · RAM %d%% · BAT %.1fV",
                        telemetry.getWifiSsid(), telemetry.getWifiSpeedMbps(), telemetry.getRamUsagePercent(), telemetry.getBatteryVoltageV()));
            }
        }
    }

    private void startChronoLoop() {
        chronoHandler.post(new Runnable() {
            @Override
            public void run() {
                if (txtChronoStardate != null) {
                    SimpleDateFormat timeFmt = new SimpleDateFormat("h:mm a", Locale.US);
                    String localTime = timeFmt.format(new Date());
                    txtChronoStardate.setText(String.format(Locale.US, "%s  ·  STARDATE %s", localTime, telemetry.getStardateString()));
                }
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
            tab.setText(cat.label.toUpperCase(Locale.US));
            tab.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            tab.setTextColor(getResources().getColorStateList(R.color.tab_text_color_selector));
            tab.setTextSize(12.5f);
            tab.setLetterSpacing(0.04f);
            tab.setPadding(24, 8, 24, 8);
            tab.setBackgroundResource(R.drawable.bg_cyber_tab_selector);
            tab.setFocusable(true);
            tab.setClickable(true);
            tab.setNextFocusDownId(R.id.recycler_horizontal_shelf);
            tab.setNextFocusUpId(R.id.btn_hero_launch);

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

            tab.setOnKeyListener((v, keyCode, event) -> {
                if (event.getAction() == KeyEvent.ACTION_DOWN) {
                    if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                        if (recyclerHorizontalShelf != null && recyclerHorizontalShelf.getChildCount() > 0) {
                            View shelfChild = recyclerHorizontalShelf.getFocusedChild();
                            if (shelfChild != null) {
                                shelfChild.requestFocus();
                                return true;
                            }
                            recyclerHorizontalShelf.getChildAt(0).requestFocus();
                            return true;
                        }
                    } else if (keyCode == KeyEvent.KEYCODE_DPAD_UP) {
                        if (btnHeroLaunch != null) {
                            btnHeroLaunch.requestFocus();
                            return true;
                        }
                    }
                }
                return false;
            });

            tab.setOnFocusChangeListener((v, hasFocus) -> {
                if (hasFocus) {
                    v.animate().scaleX(1.06f).scaleY(1.06f).setDuration(120).start();
                    CyberSoundSynth.get().playDataClick();
                } else {
                    v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(120).start();
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
        currentlyFocusedApp = app;

        if (dossierAppName != null) {
            dossierAppName.setText(app.getAppName());
        }
        if (dossierAppCategory != null) {
            dossierAppCategory.setText(app.getCategory().label.toUpperCase(Locale.US));
            dossierAppCategory.setTextColor(theme.getPrimary());
        }
        if (dossierAppPackage != null) {
            dossierAppPackage.setText(app.isPinned() ? "★ PINNED FAVORITE  ·  1080p" : "1080p Television Optimized");
        }

        // Show official 16:9 Leanback TV banner if available; fallback to high-res icon badge & cinematic backdrop
        if (heroAppBanner != null && heroAppIconFrame != null) {
            if (app.getBanner() != null) {
                heroAppBanner.setImageDrawable(app.getBanner());
                heroAppBanner.setVisibility(View.VISIBLE);
                heroAppIconFrame.setVisibility(View.GONE);
                if (heroBackdropImage != null) {
                    heroBackdropImage.setImageDrawable(app.getBanner());
                }
            } else {
                heroAppBanner.setVisibility(View.GONE);
                heroAppIconFrame.setVisibility(View.VISIBLE);
                if (dossierAppIcon != null && app.getIcon() != null) {
                    dossierAppIcon.setImageDrawable(app.getIcon());
                }
                if (heroBackdropImage != null) {
                    heroBackdropImage.setImageResource(R.drawable.bg_hero_cinematic);
                }
            }
        } else if (dossierAppIcon != null && app.getIcon() != null) {
            dossierAppIcon.setImageDrawable(app.getIcon());
        }

        if (btnHeroLaunch != null) {
            String appName = app.getAppName();
            if (appName != null && !appName.isEmpty() && appName.length() <= 16) {
                btnHeroLaunch.setText("▶  OPEN " + appName.toUpperCase(Locale.US));
            } else {
                btnHeroLaunch.setText("▶  OPEN APPLICATION");
            }
            btnHeroLaunch.setOnClickListener(v -> {
                CyberSoundSynth.get().playWarp();
                try {
                    Intent intent = getPackageManager().getLaunchIntentForPackage(app.getPackageName());
                    if (intent != null) {
                        startActivity(intent);
                    }
                } catch (Exception ignored) {}
            });
        }
    }

    private void setupFallbackAppMatrix() {
        recyclerApps = findViewById(R.id.recycler_apps);
        categoriesChipContainer = findViewById(R.id.categories_chip_container);

        recyclerApps.setLayoutManager(new GridLayoutManager(this, 4));
        fallbackMatrixAdapter = new HolographicDeckAdapter(this, false, new HolographicDeckAdapter.OnAppActionListener() {
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
            chip.setNextFocusUpId(R.id.btn_close_apps);
            chip.setNextFocusDownId(R.id.recycler_apps);
            chip.setBackgroundResource(R.drawable.bg_cyber_tab_selector);
            chip.setOnFocusChangeListener((v, hasFocus) -> {
                if (hasFocus) {
                    v.animate().scaleX(1.06f).scaleY(1.06f).setDuration(120).start();
                    CyberSoundSynth.get().playDataClick();
                } else {
                    v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(120).start();
                }
            });
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
                View first = recyclerHorizontalShelf.getChildAt(0);
                if (first != null) {
                    first.requestFocus();
                } else {
                    RecyclerView.ViewHolder vh = recyclerHorizontalShelf.findViewHolderForAdapterPosition(0);
                    if (vh != null) {
                        vh.itemView.requestFocus();
                    } else {
                        recyclerHorizontalShelf.scrollToPosition(0);
                    }
                }
            });
        } else {
            currentlyFocusedApp = null;
            if (dossierAppName != null) {
                dossierAppName.setText("NO APPLICATIONS FOUND");
            }
            if (dossierAppCategory != null) {
                dossierAppCategory.setText("EMPTY  ·  " + activeCategory.label.toUpperCase(Locale.US));
            }
            if (dossierAppPackage != null) {
                dossierAppPackage.setText("No apps registered in this category");
            }
            if (heroAppBanner != null) {
                heroAppBanner.setVisibility(View.GONE);
            }
            if (heroAppIconFrame != null) {
                heroAppIconFrame.setVisibility(View.VISIBLE);
            }
            if (heroBackdropImage != null) {
                heroBackdropImage.setImageResource(R.drawable.bg_hero_cinematic);
            }
            if (dossierAppIcon != null) {
                dossierAppIcon.setImageResource(R.drawable.ic_launcher);
            }
            if (btnHeroLaunch != null) {
                btnHeroLaunch.setOnClickListener(null);
            }
        }
    }

    private void setupTerminal() {
        consoleOutput = overlayConsole.findViewById(R.id.console_output);
        inputCommand = overlayConsole.findViewById(R.id.input_command);
        consoleScroll = overlayConsole.findViewById(R.id.console_scroll);

        consoleOutput.setText("// AETHER NEXUS TERMINAL ONLINE //\nREADY FOR QUERY OR APP EXECUTION...\nNOTE: Fire OS Vizzini Speech Service is restricted to signature apps.\n");

        View btnCloseConsole = overlayConsole.findViewById(R.id.btn_close_console);
        if (btnCloseConsole != null) {
            btnCloseConsole.setOnFocusChangeListener(buttonFocusScaler);
            btnCloseConsole.setOnClickListener(v -> {
                CyberSoundSynth.get().playChirp();
                closeAllOverlays();
            });
        }

        View btnExec = overlayConsole.findViewById(R.id.btn_exec_command);
        if (btnExec != null) {
            btnExec.setOnFocusChangeListener(buttonFocusScaler);
            btnExec.setOnClickListener(v -> executeCurrentCommand());
        }

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
        lastFocusedViewBeforeOverlay = getCurrentFocus();
        closeAllOverlays();
        drawerAppMatrix.setVisibility(View.VISIBLE);
        drawerAppMatrix.setAlpha(0f);
        drawerAppMatrix.animate().alpha(1f).setDuration(160).start();
        recyclerApps.requestFocus();
    }

    public void openTerminal() {
        lastFocusedViewBeforeOverlay = getCurrentFocus();
        closeAllOverlays();
        overlayConsole.setVisibility(View.VISIBLE);
        overlayConsole.setAlpha(0f);
        overlayConsole.animate().alpha(1f).setDuration(160).start();
        inputCommand.requestFocus();
    }

    public void openStreamDeck() {
        lastFocusedViewBeforeOverlay = getCurrentFocus();
        closeAllOverlays();
        overlayStreamDeck.setVisibility(View.VISIBLE);
        overlayStreamDeck.setAlpha(0f);
        overlayStreamDeck.animate().alpha(1f).setDuration(160).start();
        streamDeck.start();
        View btnNext = overlayStreamDeck.findViewById(R.id.btn_next_ch);
        if (btnNext != null) btnNext.requestFocus();
    }

    public void openArcade(String gameType) {
        lastFocusedViewBeforeOverlay = getCurrentFocus();
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
        if (lastFocusedViewBeforeOverlay != null && lastFocusedViewBeforeOverlay.isShown()) {
            lastFocusedViewBeforeOverlay.requestFocus();
        } else {
            recyclerHorizontalShelf.requestFocus();
        }
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
        if (currentHeroMode == HeroStageMode.LIVE_AV) {
            tuneHeroChannel();
        } else if (currentHeroMode == HeroStageMode.AMBIENT_CORE && arcReactorView != null) {
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
            if (heroVideoPlayer != null && currentHeroMode == HeroStageMode.LIVE_AV) {
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
