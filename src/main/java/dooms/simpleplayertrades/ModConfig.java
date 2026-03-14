package dooms.simpleplayertrades;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

/**
 * Handles reading and writing the mod's config file.
 * Located at: config/simple-player-trades/config.properties
 *
 * If the file does not exist on startup, it is created with default values.
 */
public class ModConfig {

    // --- Singleton ---

    private static final ModConfig INSTANCE = new ModConfig();

    private ModConfig() {}

    public static ModConfig getInstance() {
        return INSTANCE;
    }

    // --- Constants ---

    private static final Path CONFIG_PATH = Paths.get("config", "simple-player-trades", "config.properties");

    // --- Config Values ---

    private int timeoutSeconds = 60;
    private int requestCooldownSeconds = 5;
    private boolean loggingEnabled = true;
    private int maxLogFiles = 30;
    private boolean tradingEnabled = true;
    private boolean requireOp = false;

    // --- Initialization ---

    public void initialize() {
        try {
            Files.createDirectories(CONFIG_PATH.getParent());

            if (Files.exists(CONFIG_PATH)) {
                load();
            } else {
                save();
                SimplePlayerTrades.LOGGER.info("[Trades] Config file created with defaults at {}", CONFIG_PATH);
            }
        } catch (IOException e) {
            SimplePlayerTrades.LOGGER.error("[Trades] Failed to initialize config", e);
        }
    }

    private void load() {
        Properties props = new Properties();
        try (InputStream in = Files.newInputStream(CONFIG_PATH)) {
            props.load(in);

            timeoutSeconds = parseInt(props, "timeout_seconds", 60);
            requestCooldownSeconds = parseInt(props, "request_cooldown_seconds", 10);
            loggingEnabled = parseBoolean(props, "logging_enabled", true);
            maxLogFiles = parseInt(props, "max_log_files", 30);
            tradingEnabled = parseBoolean(props, "trading_enabled", true);
            requireOp = parseBoolean(props, "require_op", false);

            SimplePlayerTrades.LOGGER.info("[Trades] Config loaded from {}", CONFIG_PATH);
        } catch (IOException e) {
            SimplePlayerTrades.LOGGER.error("[Trades] Failed to load config, using defaults", e);
        }
    }

    private void save() {
        // Properties.store() does not support blank lines between entries,
        // so we write the file manually for full control over formatting
        String content =
                "# How long (in seconds) before a pending trade request expires\n" +
                        "timeout_seconds=" + timeoutSeconds + "\n" +
                        "\n" +
                        "# Minimum seconds a player must wait before sending another trade request\n" +
                        "request_cooldown_seconds=" + requestCooldownSeconds + "\n" +
                        "\n" +
                        "# Set to false to disable writing trade logs to file\n" +
                        "logging_enabled=" + loggingEnabled + "\n" +
                        "\n" +
                        "# Maximum number of trade log files to keep before deleting the oldest\n" +
                        "max_log_files=" + maxLogFiles + "\n" +
                        "\n" +
                        "# Set to false to disable all trading on the server\n" +
                        "trading_enabled=" + tradingEnabled + "\n" +
                        "\n" +
                        "# Set to true to restrict trading to OP players only (permission level 3+)\n" +
                        "# Note that this only applies to sending trade requests, non-OP players\n" +
                        "# can still accept and deny incoming trades from OP players\n" +
                        "require_op=" + requireOp + "\n";

        try (OutputStream out = Files.newOutputStream(CONFIG_PATH)) {
            out.write(content.getBytes());
        } catch (IOException e) {
            SimplePlayerTrades.LOGGER.error("[Trades] Failed to save config", e);
        }
    }

    // --- Private Helpers ---

    /**
     * Reads an integer value from the properties file.
     * Falls back to the default if the key is missing or the value is malformed.
     */
    private int parseInt(Properties props, String key, int defaultValue) {
        try {
            return Integer.parseInt(props.getProperty(key, String.valueOf(defaultValue)).trim());
        } catch (NumberFormatException e) {
            SimplePlayerTrades.LOGGER.warn("[Trades] Invalid value for '{}', using default: {}", key, defaultValue);
            return defaultValue;
        }
    }

    /**
     * Reads a boolean value from the properties file.
     * Falls back to the default if the key is missing.
     */
    private boolean parseBoolean(Properties props, String key, boolean defaultValue) {
        String value = props.getProperty(key);
        if (value == null) return defaultValue;
        return Boolean.parseBoolean(value.trim());
    }

    // --- Getters ---

    public int getTimeoutSeconds() { return timeoutSeconds; }
    public int getRequestCooldownSeconds() { return requestCooldownSeconds; }
    public boolean isLoggingEnabled() { return loggingEnabled; }
    public int getMaxLogFiles() { return maxLogFiles; }
    public boolean isTradingEnabled() { return tradingEnabled; }
    public boolean isRequireOp() { return requireOp; }
}