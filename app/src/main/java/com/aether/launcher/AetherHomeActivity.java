package com.aether.launcher;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.GestureDetector;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.GridLayoutManager;
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
import com.aether.launcher.terminal.CyberCommandEngine;
import com.aether.launcher.widgets.CyberWidgetHostManager;
import java.util.Locale;

public class AetherHomeActivity extends Activity {

    // HUD & Sensors
    private ArcReactorView arcReactorView;
    private SystemTelemetry telemetry;
    private GyroParallaxManager gyroManager;
    private HologramTheme theme;

    // Repositories & Engines
    private AppDeckRepository appDeckRepo;
    private HolographicDeckAdapter appDeckAdapter;
    private CyberCommandEngine commandEngine;
    private CyberWidgetHostManager widgetHostManager;
    private CyberStreamDeck streamDeck;

    // Views & Overlays
    private View drawerAppMatrix;
    private View overlayConsole;
    private View overlayStreamDeck;
    private HoloArcadeView overlayArcade;
    private FrameLayout widgetContainer;

    // Telemetry textviews
    private TextView txtTelemetryLeft;
    private TextView txtTelemetryRight;
    private TextView btnThemeCycle;

    // Terminal views
    private TextView consoleOutput;
    private EditText inputCommand;
    private ScrollView consoleScroll;

    // App drawer views
    private LinearLayout categoriesContainer;
    private RecyclerView recyclerApps;
    private AppModel.Category activeCategory = AppModel.Category.ALL;

    private GestureDetector gestureDetector;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_aether_home);

        theme = HologramTheme.get();
        telemetry = new SystemTelemetry(this);
        gyroManager = new GyroParallaxManager(this);
        appDeckRepo = new AppDeckRepository(this);
        commandEngine = new CyberCommandEngine(this, appDeckRepo, telemetry, gyroManager);
        widgetHostManager = new CyberWidgetHostManager(this);

        initViews();
        setupGestureDetector();
        setupTelemetry();
        setupAppMatrix();
        setupTerminal();
        setupStreamDeck();
        setupArcade();

        CyberSoundSynth.get().playPowerUp();
    }

    private void initViews() {
        arcReactorView = findViewById(R.id.arc_reactor_view);
        widgetContainer = findViewById(R.id.widget_container);
        txtTelemetryLeft = findViewById(R.id.txt_telemetry_left);
        txtTelemetryRight = findViewById(R.id.txt_telemetry_right);
        btnThemeCycle = findViewById(R.id.btn_theme_cycle);

        drawerAppMatrix = findViewById(R.id.drawer_app_matrix);
        overlayConsole = findViewById(R.id.overlay_console);
        overlayStreamDeck = findViewById(R.id.overlay_stream_deck);
        overlayArcade = findViewById(R.id.overlay_arcade);

        // Reactor center tap
        arcReactorView.setOnCoreClickListener(this::openTerminal);

        // Theme Switcher Button
        btnThemeCycle.setOnClickListener(v -> {
            CyberSoundSynth.get().playChirp();
            theme.cycleNext();
            btnThemeCycle.setText("[ " + theme.getThemeType().name() + " ]");
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
                        // Swipe Up -> Open App Matrix
                        openAppMatrix();
                        return true;
                    } else {
                        // Swipe Down -> Open Terminal
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
        telemetry.start(() -> runOnUiThread(() -> {
            if (telemetry.isAcPowered()) {
                txtTelemetryLeft.setText("POWER // AC LINE ACTIVE");
            } else {
                txtTelemetryLeft.setText(String.format(Locale.US, "BAT %.1fV // %.1f°C",
                        telemetry.getBatteryVoltageV(), telemetry.getBatteryTempC()));
            }
            txtTelemetryRight.setText(String.format(Locale.US, "%s // %dM",
                    telemetry.getWifiSsid(), telemetry.getWifiSpeedMbps()));
        }));

        gyroManager.start((pitch, roll) -> {
            arcReactorView.setParallax(pitch, roll);
            overlayArcade.applyGyroInput(roll);
        });
    }

    private void setupAppMatrix() {
        recyclerApps = findViewById(R.id.recycler_apps);
        categoriesContainer = findViewById(R.id.categories_chip_container);

        recyclerApps.setLayoutManager(new GridLayoutManager(this, 4));
        appDeckAdapter = new HolographicDeckAdapter(this, new HolographicDeckAdapter.OnAppActionListener() {
            @Override
            public void onAppLaunched(AppModel app) {
                closeAllOverlays();
            }

            @Override
            public void onAppLongClicked(AppModel app) {
                appDeckRepo.togglePin(app);
                appDeckAdapter.notifyDataSetChanged();
            }
        });
        recyclerApps.setAdapter(appDeckAdapter);

        // Build Category Filter Chips
        categoriesContainer.removeAllViews();
        for (AppModel.Category cat : AppModel.Category.values()) {
            TextView chip = new TextView(this);
            chip.setText("[ " + cat.label + " ]");
            chip.setFontFeatureSettings("monospace");
            chip.setTextColor(0xFF00F0FF);
            chip.setTextSize(11f);
            chip.setPadding(16, 8, 16, 8);
            chip.setOnClickListener(v -> {
                CyberSoundSynth.get().playDataClick();
                activeCategory = cat;
                refreshAppList();
            });
            categoriesContainer.addView(chip);
        }

        new Thread(() -> {
            appDeckRepo.reloadApps();
            runOnUiThread(this::refreshAppList);
        }).start();
    }

    private void refreshAppList() {
        appDeckAdapter.setApps(appDeckRepo.filter(activeCategory, null));
    }

    private void setupTerminal() {
        consoleOutput = overlayConsole.findViewById(R.id.console_output);
        inputCommand = overlayConsole.findViewById(R.id.input_command);
        consoleScroll = overlayConsole.findViewById(R.id.console_scroll);

        consoleOutput.setText("// AETHER SYSTEM ONLINE //\nREADY FOR QUERY OR APP EXECUTION...\n");

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
                appDeckAdapter.notifyDataSetChanged();
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
    }

    public void closeAllOverlays() {
        drawerAppMatrix.setVisibility(View.GONE);
        overlayConsole.setVisibility(View.GONE);
        overlayStreamDeck.setVisibility(View.GONE);
        overlayArcade.setVisibility(View.GONE);
        streamDeck.stop();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_MENU) {
            CyberSoundSynth.get().playChirp();
            theme.cycleNext();
            btnThemeCycle.setText("[ " + theme.getThemeType().name() + " ]");
            return true;
        }
        if (keyCode == KeyEvent.KEYCODE_DPAD_UP && drawerAppMatrix.getVisibility() != View.VISIBLE) {
            CyberSoundSynth.get().playChirp();
            openAppMatrix();
            return true;
        }
        if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN && overlayConsole.getVisibility() != View.VISIBLE) {
            CyberSoundSynth.get().playChirp();
            openTerminal();
            return true;
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
            // Already at home base
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
        if (arcReactorView != null) {
            arcReactorView.resumeAnimation();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (arcReactorView != null) {
            arcReactorView.pauseAnimation();
        }
    }

    @Override
    public void onTrimMemory(int level) {
        super.onTrimMemory(level);
        if (level >= TRIM_MEMORY_BACKGROUND || level == TRIM_MEMORY_RUNNING_LOW) {
            if (arcReactorView != null) {
                arcReactorView.pauseAnimation();
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
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        telemetry.stop();
        gyroManager.stop();
    }
}
