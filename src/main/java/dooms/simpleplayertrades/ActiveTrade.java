package dooms.simpleplayertrades;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
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
    }

    private final UUID requesterUuid;
    private final UUID targetUuid;
    private final String requesterName;
    private final String targetName;
    private boolean requesterConfirmed = false;
    private boolean targetConfirmed = false;
    private TradeState state;
    private final SimpleContainer tradeInventory = new SimpleContainer(54);
    private final long createdAt = System.currentTimeMillis();

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
    public SimpleContainer getTradeInventory() { return tradeInventory; }

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

    /**
     * Flips the confirmation state for the given player.
     * Returns the new confirmation state after toggling.
     */
    public boolean toggleConfirm(boolean isRequester) {
        if (isRequester) {
            requesterConfirmed = !requesterConfirmed;
            return requesterConfirmed;
        } else {
            targetConfirmed = !targetConfirmed;
            return targetConfirmed;
        }
    }

    /**
     * Returns true if the given player has confirmed the trade.
     */
    public boolean isConfirmed(boolean isRequester) {
        return isRequester ? requesterConfirmed : targetConfirmed;
    }

    /**
     * Returns true only when both players have confirmed.
     */
    public boolean isBothConfirmed() {
        return requesterConfirmed && targetConfirmed;
    }

    /**
     * Resets the confirmation for a specific player.
     * Called when that player moves an item in or out of the trade.
     */
    public void resetConfirm(boolean isRequester) {
        if (isRequester) {
            requesterConfirmed = false;
        } else {
            targetConfirmed = false;
        }
    }


    /**
     * Returns true if this trade request has been pending for longer than the given timeout in seconds.
     */
    public boolean isTimedOut(int timeoutSeconds) {
        return System.currentTimeMillis() - createdAt > timeoutSeconds * 1000L;
    }
}