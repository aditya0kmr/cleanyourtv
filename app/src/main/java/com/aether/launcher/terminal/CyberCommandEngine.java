package com.aether.launcher.terminal;

import android.content.Context;
import android.os.Build;
import com.aether.launcher.apps.AppDeckRepository;
import com.aether.launcher.apps.AppModel;
import com.aether.launcher.audio.CyberSoundSynth;
import com.aether.launcher.hud.GyroParallaxManager;
import com.aether.launcher.hud.HologramTheme;
import com.aether.launcher.hud.SystemTelemetry;
import java.util.List;
import java.util.Locale;

public class CyberCommandEngine {

    public interface CommandCallback {
        void onOutput(String text);
        void onLaunchApp(AppModel app);
        void onOpenIptv();
        void onOpenArcade(String gameType);
    }

    private final Context context;
    private final AppDeckRepository appRepo;
    private final SystemTelemetry telemetry;
    private final GyroParallaxManager gyroManager;

    public CyberCommandEngine(Context context, AppDeckRepository repo, SystemTelemetry telemetry, GyroParallaxManager gyro) {
        this.context = context;
        this.appRepo = repo;
        this.telemetry = telemetry;
        this.gyroManager = gyro;
    }

    public void execute(String input, CommandCallback callback) {
        if (input == null || input.trim().isEmpty()) return;
        String raw = input.trim();
        String cmd = raw.toLowerCase(Locale.US);

        if (cmd.equals("help")) {
            callback.onOutput(
                    "// AETHER CYBER SHELL v1.0 //\n" +
                    "  calc <expr>       : Solve mathematical equation\n" +
                    "  sysinfo           : Hardware & diagnostic telemetry\n" +
                    "  theme <color>     : cyan | amber | green | crimson | violet\n" +
                    "  sfx <on|off>      : Procedural audio synthesizer\n" +
                    "  gyro <on|off>     : 3D parallax gyro sensors\n" +
                    "  iptv [play|ch]    : Holographic AV stream monitor\n" +
                    "  game [hack|vector]: Launch tactical mini-games\n" +
                    "  launch <app>      : Direct application executor\n" +
                    "  clear             : Purge console buffer"
            );
            return;
        }

        if (cmd.equals("clear")) {
            callback.onOutput("");
            return;
        }

        if (cmd.startsWith("calc ")) {
            String expr = raw.substring(5).trim();
            try {
                double result = evaluateMath(expr);
                callback.onOutput("// CALCULATION RESULT //\n  " + expr + " = " + result);
            } catch (Exception e) {
                callback.onOutput("// ERR: SYNTAX MALFUNCTION IN EXPRESSION //\n  " + e.getMessage());
            }
            return;
        }

        if (cmd.equals("sysinfo")) {
            long totalRamMb = telemetry.getTotalRamBytes() / (1024 * 1024);
            long availRamMb = telemetry.getAvailRamBytes() / (1024 * 1024);
            int ramPct = telemetry.getRamUsagePercent();
            int cores = Runtime.getRuntime().availableProcessors();

            callback.onOutput(
                    "// HARDWARE TELEMETRY REPORT //\n" +
                    "  DEVICE     : " + Build.MANUFACTURER.toUpperCase(Locale.US) + " " + Build.MODEL + "\n" +
                    "  KERNEL ARCH: " + Build.SUPPORTED_ABIS[0] + " // " + cores + " CORES\n" +
                    "  ANDROID OS : API " + Build.VERSION.SDK_INT + " (REL " + Build.VERSION.RELEASE + ")\n" +
                    "  BATTERY    : " + telemetry.getBatteryLevel() + "% (" + telemetry.getBatteryVoltageV() + "V) @ " + telemetry.getBatteryTempC() + "°C\n" +
                    "  RAM LOAD   : " + (totalRamMb - availRamMb) + "MB / " + totalRamMb + "MB (" + ramPct + "%)\n" +
                    "  NET STATUS : " + telemetry.getWifiSsid() + " (" + telemetry.getLocalIp() + ")\n" +
                    "  LINK SPEED : " + telemetry.getWifiSpeedMbps() + " Mbps"
            );
            return;
        }

        if (cmd.startsWith("theme")) {
            String[] parts = cmd.split("\\s+");
            if (parts.length > 1) {
                String t = parts[1];
                if (t.contains("cyan") || t.contains("jarvis")) HologramTheme.get().setTheme(HologramTheme.ThemeType.CYAN);
                else if (t.contains("amber") || t.contains("gold")) HologramTheme.get().setTheme(HologramTheme.ThemeType.AMBER);
                else if (t.contains("green") || t.contains("matrix")) HologramTheme.get().setTheme(HologramTheme.ThemeType.EMERALD);
                else if (t.contains("red") || t.contains("crimson")) HologramTheme.get().setTheme(HologramTheme.ThemeType.CRIMSON);
                else if (t.contains("violet") || t.contains("purple")) HologramTheme.get().setTheme(HologramTheme.ThemeType.VIOLET);
                else {
                    callback.onOutput("// UNKNOWN THEME. AVAILABLE: cyan, amber, green, crimson, violet //");
                    return;
                }
            } else {
                HologramTheme.get().cycleNext();
            }
            callback.onOutput("// HOLO THEME RECALIBRATED: " + HologramTheme.get().getThemeType().title + " //");
            return;
        }

        if (cmd.startsWith("sfx")) {
            if (cmd.contains("off")) {
                CyberSoundSynth.get().setEnabled(false);
                callback.onOutput("// PROCEDURAL AUDIO SYNTHESIZER: DISABLED //");
            } else {
                CyberSoundSynth.get().setEnabled(true);
                CyberSoundSynth.get().playChirp();
                callback.onOutput("// PROCEDURAL AUDIO SYNTHESIZER: ACTIVE //");
            }
            return;
        }

        if (cmd.startsWith("gyro")) {
            if (cmd.contains("off")) {
                gyroManager.setEnabled(false);
                callback.onOutput("// 3D PARALLAX GYRO: DISABLED //");
            } else {
                gyroManager.setEnabled(true);
                callback.onOutput("// 3D PARALLAX GYRO: CALIBRATING... ACTIVE //");
            }
            return;
        }

        if (cmd.startsWith("iptv") || cmd.startsWith("tv") || cmd.startsWith("stream")) {
            callback.onOutput("// ENGAGING HOLO STREAM MONITOR //");
            callback.onOpenIptv();
            return;
        }

        if (cmd.startsWith("game") || cmd.startsWith("arcade")) {
            String gType = cmd.contains("vector") ? "vector" : "hack";
            callback.onOutput("// ENGAGING HOLO ARCADE SIMULATOR: " + gType.toUpperCase(Locale.US) + " //");
            callback.onOpenArcade(gType);
            return;
        }

        // Fuzzy app launch check
        String searchTarget = cmd.startsWith("launch ") ? raw.substring(7).trim() : raw;
        List<AppModel> matches = appRepo.filter(null, searchTarget);
        if (!matches.isEmpty()) {
            AppModel app = matches.get(0);
            callback.onOutput("// EXECUTING SUB-ROUTINE: " + app.getAppName().toUpperCase(Locale.US) + " //");
            callback.onLaunchApp(app);
            return;
        }

        callback.onOutput("// COMMAND NOT RECOGNIZED: \"" + raw + "\". TYPE \"help\" FOR MANIFEST //");
    }

    private double evaluateMath(String str) {
        return new Object() {
            int pos = -1, ch;

            void nextChar() {
                ch = (++pos < str.length()) ? str.charAt(pos) : -1;
            }

            boolean eat(int charToEat) {
                while (ch == ' ') nextChar();
                if (ch == charToEat) {
                    nextChar();
                    return true;
                }
                return false;
            }

            double parse() {
                nextChar();
                double x = parseExpression();
                if (pos < str.length()) throw new RuntimeException("Unexpected token: " + (char) ch);
                return x;
            }

            double parseExpression() {
                double x = parseTerm();
                for (;;) {
                    if      (eat('+')) x += parseTerm();
                    else if (eat('-')) x -= parseTerm();
                    else return x;
                }
            }

            double parseTerm() {
                double x = parseFactor();
                for (;;) {
                    if      (eat('*')) x *= parseFactor();
                    else if (eat('/')) x /= parseFactor();
                    else if (eat('%')) x %= parseFactor();
                    else return x;
                }
            }

            double parseFactor() {
                if (eat('+')) return parseFactor();
                if (eat('-')) return -parseFactor();

                double x;
                int startPos = this.pos;
                if (eat('(')) {
                    x = parseExpression();
                    eat(')');
                } else if ((ch >= '0' && ch <= '9') || ch == '.') {
                    while ((ch >= '0' && ch <= '9') || ch == '.') nextChar();
                    x = Double.parseDouble(str.substring(startPos, this.pos));
                } else {
                    throw new RuntimeException("Syntax Error at: " + (char) ch);
                }

                if (eat('^')) x = Math.pow(x, parseFactor());
                return x;
            }
        }.parse();
    }
}
