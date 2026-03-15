package dooms.simpleplayertrades;

import net.minecraft.world.item.ItemStack;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Handles writing trade events to a dedicated log file.
 * One log file is created per server start, stored in:
 *   config/simple-player-trades/logs/
 */
public class TradeLogger {

    // --- Singleton ---

    private static final TradeLogger INSTANCE = new TradeLogger();

    private TradeLogger() {}

    public static TradeLogger getInstance() {
        return INSTANCE;
    }

    // --- Constants ---

    private static final DateTimeFormatter FILE_NAME_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");
    private static final DateTimeFormatter ENTRY_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final Path LOG_DIR =
            Paths.get("config", "simple-player-trades", "logs");

    // --- State ---

    private boolean enabled = ModConfig.getInstance().isLoggingEnabled();
    private Path currentLogFile = null;

    // --- Initialization ---

    /**
     * Called once from SimplePlayerTrades.onInitialize().
     * Creates the log directory, rotates old files, and opens a new log file.
     */
    public void initialize() {
        try {
            Files.createDirectories(LOG_DIR);
            deleteOldestLogFile();

            String fileName = "trades-" + LocalDateTime.now().format(FILE_NAME_FORMAT) + ".log";
            currentLogFile = LOG_DIR.resolve(fileName);
            Files.createFile(currentLogFile);

            SimplePlayerTrades.LOGGER.info("[Trades] Trade log file created: {}", currentLogFile);
        } catch (IOException e) {
            SimplePlayerTrades.LOGGER.error("[Trades] Failed to initialize trade logger", e);
        }
    }

    /**
     * Deletes the oldest log file if the maximum number of files is exceeded.
     */
    private void deleteOldestLogFile() throws IOException {
        List<Path> logFiles = Files.list(LOG_DIR)
                .filter(p -> p.getFileName().toString().startsWith("trades-"))
                .sorted(Comparator.comparing(p -> p.getFileName().toString()))
                .collect(Collectors.toList());

        while (logFiles.size() >= ModConfig.getInstance().getMaxLogFiles()) {
            Files.deleteIfExists(logFiles.remove(0));
            SimplePlayerTrades.LOGGER.info("[Trades] Rotated old trade log file.");
        }
    }

    // --- Public Logging API ---

    /**
     * Logs a trade request being sent.
     */
    public void logRequest(String senderName, String targetName) {
        if (!enabled) return;

        StringBuilder sb = new StringBuilder();
        sb.append(timestamp()).append(" REQUEST\n");
        sb.append("  From : ").append(senderName).append("\n");
        sb.append("  To   : ").append(targetName).append("\n");

        write(sb.toString());
    }

    /**
     * Logs a completed trade with the full item contents of both sides.
     */
    public void logCompleted(ActiveTrade trade, List<ItemStack> requesterItems, List<ItemStack> targetItems) {
        if (!enabled) return;

        StringBuilder sb = new StringBuilder();
        sb.append(timestamp()).append(" COMPLETED\n");
        sb.append("  Requester : ").append(trade.getRequesterName()).append("\n");
        sb.append("  Target    : ").append(trade.getTargetName()).append("\n");
        sb.append("  Sent      :\n");
        appendItems(sb, requesterItems);
        sb.append("  Received  :\n");
        appendItems(sb, targetItems);

        write(sb.toString());
    }

    /**
     * Logs a cancelled trade with the reason.
     */
    public void logCancelled(ActiveTrade trade, String reason) {
        if (!enabled) return;

        StringBuilder sb = new StringBuilder();
        sb.append(timestamp()).append(" CANCELLED\n");
        sb.append("  Players : ").append(trade.getRequesterName())
                .append(" <-> ").append(trade.getTargetName()).append("\n");
        sb.append("  Reason  : ").append(reason).append("\n");

        write(sb.toString());
    }

    // --- Private Helpers ---

    private void appendItems(StringBuilder sb, List<ItemStack> items) {
        if (items.isEmpty()) {
            sb.append("    - nothing\n");
            return;
        }
        for (ItemStack stack : items) {
            sb.append("    - ")
                    .append(stack.getCount())
                    .append("x ")
                    .append(stack.getItem().getDescriptionId()
                            .replace("item.minecraft.", "")
                            .replace("block.minecraft.", ""))
                    .append("\n");
        }
    }

    private String timestamp() {
        return "[" + LocalDateTime.now().format(ENTRY_FORMAT) + "]";
    }

    private void write(String entry) {
        if (currentLogFile == null) return;
        try {
            Files.writeString(currentLogFile, entry + "\n", StandardOpenOption.APPEND);
        } catch (IOException e) {
            SimplePlayerTrades.LOGGER.error("[Trades] Failed to write to trade log", e);
        }
    }
}