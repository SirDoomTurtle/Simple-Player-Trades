package dooms.simpleplayertrades;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

/**
 * Represents a single trade between two players.
 * One instance of this class exists per ongoing trade.
 * The TradeManager is responsible for creating and destroying these instances.
 */
public class ActiveTrade {

    public enum TradeState {
        PENDING,
        ACTIVE
        // CONFIRMING will be added when we implement the GUI
    }

    private final UUID requesterUuid;
    private final UUID targetUuid;
    private final String requesterName;
    private final String targetName;
    private TradeState state;

    public ActiveTrade(ServerPlayer requester, ServerPlayer target) {
        this.requesterUuid = requester.getUUID();
        this.targetUuid = target.getUUID();
        this.requesterName = requester.getName().getString();
        this.targetName = target.getName().getString();
        this.state = TradeState.PENDING;
    }

    // --- Getters ---

    public UUID getRequesterUuid() { return requesterUuid; }
    public UUID getTargetUuid()    { return targetUuid; }
    public String getRequesterName() { return requesterName; }
    public String getTargetName()    { return targetName; }
    public TradeState getState()   { return state; }

    // --- State ---

    public void setState(TradeState state) {
        this.state = state;
    }

    /**
     * Returns true if the given UUID is one of the two players in this trade.
     */
    public boolean involves(UUID playerUuid) {
        return requesterUuid.equals(playerUuid) || targetUuid.equals(playerUuid);
    }

    /**
     * Given one player's UUID, returns the UUID of the other player.
     */
    public UUID getOtherPlayerUuid(UUID playerUuid) {
        if (requesterUuid.equals(playerUuid)) return targetUuid;
        if (targetUuid.equals(playerUuid))    return requesterUuid;
        throw new IllegalArgumentException("UUID " + playerUuid + " is not part of this trade.");
    }
}