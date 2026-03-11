package dooms.simpleplayertrades;

import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.item.ItemStack;

import java.util.*;


/**
 * Central manager for all trades.
 * Tracks pending trade requests (sent but not yet accepted).
 */
public class TradeManager {

    // --- Singleton ---

    private static final TradeManager INSTANCE = new TradeManager();

    private TradeManager() {}

    public static TradeManager getInstance() {
        return INSTANCE;
    }

    // --- State ---

    // Key: requester players UUID
    private final Map<UUID, ActiveTrade> pendingTradesByRequester = new HashMap<>();

    // Key: target players UUID
    private final Map<UUID, ActiveTrade> pendingTradesByTarget = new HashMap<>();

    private final Map<UUID, ActiveTrade> activeTrades = new HashMap<>();

    // --- Event Registration ---

    /**
     * Registers server-side events.
     * Called once from the mod initializer.
     */
    public void registerEvents() {
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
                handleDisconnect(handler.getPlayer(), server)
        );
    }

    // --- Public API ---

    public void sendTradeRequest(ServerPlayer sender, ServerPlayer target, MinecraftServer server) {
        UUID senderUuid = sender.getUUID();
        UUID targetUuid = target.getUUID();

        // Guard: sender already has an outgoing request
        if (pendingTradesByRequester.containsKey(senderUuid)) {
            sendMessage(sender, "§cYou already have a pending trade request. Use §e/tradecancel §cto cancel it.");
            return;
        }

        // Guard: target already has an incoming request
        if (pendingTradesByTarget.containsKey(targetUuid)) {
            sendMessage(sender, "§e" + target.getName().getString() + " §calready has a pending trade request.");
            return;
        }

        ActiveTrade trade = new ActiveTrade(sender, target);
        pendingTradesByRequester.put(senderUuid, trade);
        pendingTradesByTarget.put(targetUuid, trade);

        sendMessage(sender, "§aTrade request sent to §e" + target.getName().getString() + "§a.");
        sendMessage(target,
                "§e" + sender.getName().getString() + " §awants to trade with you!\n" +
                        "\n" +
                        "  §7Type §e/tradeaccept " + sender.getName().getString() + " §7to accept.\n" +
                        "  §7Type §e/tradedeny "   + sender.getName().getString() + " §7to deny."
        );

        SimplePlayerTrades.LOGGER.info("[Trades] {} sent a trade request to {}",
                sender.getName().getString(), target.getName().getString());
    }

    public void acceptTradeRequest(ServerPlayer acceptor, ServerPlayer requester, MinecraftServer server) {
        UUID acceptorUuid  = acceptor.getUUID();
        UUID requesterUuid = requester.getUUID();

        ActiveTrade trade = pendingTradesByTarget.get(acceptorUuid);

        // Guard: no matching pending request from this specific player
        if (trade == null || !trade.getRequesterUuid().equals(requesterUuid)) {
            sendMessage(acceptor, "§cYou don't have a pending trade request from §e" + requester.getName().getString() + "§c.");
            return;
        }

        pendingTradesByRequester.remove(requesterUuid);
        pendingTradesByTarget.remove(acceptorUuid);

        sendMessage(acceptor,  "§aTrade with §e" + requester.getName().getString() + " §aaccepted!");
        sendMessage(requester, "§e" + acceptor.getName().getString() + " §aaccepted your trade request!");



        SimplePlayerTrades.LOGGER.info("[Trades] {} accepted a trade request from {}",
                acceptor.getName().getString(), requester.getName().getString());

        openTradeGui(requester, acceptor, trade, server);
    }

    public void denyTradeRequest(ServerPlayer denier, ServerPlayer requester, MinecraftServer server) {
        UUID denierUuid    = denier.getUUID();
        UUID requesterUuid = requester.getUUID();

        ActiveTrade trade = pendingTradesByTarget.get(denierUuid);

        if (trade == null || !trade.getRequesterUuid().equals(requesterUuid)) {
            sendMessage(denier, "§cYou don't have a pending trade request from §e" + requester.getName().getString() + "§c.");
            return;
        }

        pendingTradesByRequester.remove(requesterUuid);
        pendingTradesByTarget.remove(denierUuid);

        sendMessage(denier,    "§cTrade request from §e" + requester.getName().getString() + " §cdenied.");
        sendMessage(requester, "§e" + denier.getName().getString() + " §cdenied your trade request.");

        SimplePlayerTrades.LOGGER.info("[Trades] {} denied a trade request from {}",
                denier.getName().getString(), requester.getName().getString());
    }

    public void cancelTrade(ServerPlayer canceler, MinecraftServer server) {
        UUID cancelerUuid = canceler.getUUID();

        // Case 1: canceler has an outgoing request
        if (pendingTradesByRequester.containsKey(cancelerUuid)) {
            ActiveTrade trade = pendingTradesByRequester.remove(cancelerUuid);
            pendingTradesByTarget.remove(trade.getTargetUuid());

            sendMessage(canceler, "§cTrade request cancelled.");

            ServerPlayer target = server.getPlayerList().getPlayer(trade.getTargetUuid());
            if (target != null) {
                sendMessage(target, "§e" + canceler.getName().getString() + " §ccancelled their trade request.");
            }

            SimplePlayerTrades.LOGGER.info("[Trades] {} cancelled their outgoing trade request.",
                    canceler.getName().getString());
            return;
        }

        // Case 2: canceler has an incoming request they want to dismiss
        if (pendingTradesByTarget.containsKey(cancelerUuid)) {
            ActiveTrade trade = pendingTradesByTarget.remove(cancelerUuid);
            pendingTradesByRequester.remove(trade.getRequesterUuid());

            sendMessage(canceler, "§cIncoming trade request dismissed.");

            ServerPlayer requester = server.getPlayerList().getPlayer(trade.getRequesterUuid());
            if (requester != null) {
                sendMessage(requester, "§e" + canceler.getName().getString() + " §cdismissed your trade request.");
            }
            return;
        }

        sendMessage(canceler, "§cYou have no active trade to cancel.");
    }

    /**
     * Called automatically on player disconnect.
     * Ensures no pending trade state is left dangling.
     */
    public void handleDisconnect(ServerPlayer player, MinecraftServer server) {
        UUID playerUuid = player.getUUID();

        if (pendingTradesByRequester.containsKey(playerUuid)) {
            ActiveTrade trade = pendingTradesByRequester.remove(playerUuid);
            pendingTradesByTarget.remove(trade.getTargetUuid());

            ServerPlayer target = server.getPlayerList().getPlayer(trade.getTargetUuid());
            if (target != null) {
                sendMessage(target, "§e" + player.getName().getString() + " §cdisconnected. Their trade request was cancelled.");
            }

            SimplePlayerTrades.LOGGER.info("[Trades] {} disconnected, cancelled their outgoing trade request.",
                    player.getName().getString());
        }

        if (pendingTradesByTarget.containsKey(playerUuid)) {
            ActiveTrade trade = pendingTradesByTarget.remove(playerUuid);
            pendingTradesByRequester.remove(trade.getRequesterUuid());

            ServerPlayer requester = server.getPlayerList().getPlayer(trade.getRequesterUuid());
            if (requester != null) {
                sendMessage(requester, "§e" + player.getName().getString() + " §cdisconnected. Your trade request was cancelled.");
            }

            SimplePlayerTrades.LOGGER.info("[Trades] {} disconnected, their incoming trade request was cleaned up.",
                    player.getName().getString());
        }
    }

    // --- Private Helpers ---

    private void sendMessage(ServerPlayer player, String message) {
        player.sendSystemMessage(Component.literal(message));
    }

    private void openTradeGui(ServerPlayer requester, ServerPlayer acceptor, ActiveTrade trade, MinecraftServer server) {
        SimpleContainer inventory = trade.getTradeInventory();

        TradeScreenHandler.setupLayout(inventory);

        activeTrades.put(requester.getUUID(), trade);
        activeTrades.put(acceptor.getUUID(), trade);
        trade.setState(ActiveTrade.TradeState.ACTIVE);

        requester.openMenu(new SimpleMenuProvider(
                (syncId, playerInventory, player) ->
                        new TradeScreenHandler(syncId, playerInventory, inventory, server, true, trade),
                Component.literal("Trade")
        ));

        acceptor.openMenu(new SimpleMenuProvider(
                (syncId, playerInventory, player) ->
                        new TradeScreenHandler(syncId, playerInventory, inventory, server, false, trade),
                Component.literal("Trade")
        ));
    }

    public void handleGuiClose(ServerPlayer player, MinecraftServer server) {
        UUID playerUuid = player.getUUID();
        ActiveTrade trade = activeTrades.get(playerUuid);
        if (trade == null) return;

        cancelActiveTrade(
                trade, player, server,
                "§cTrade cancelled.",
                "§e" + player.getName().getString() + " §cclosed the trade."
        );
    }

    private void returnItems(ActiveTrade trade, MinecraftServer server) {
        SimpleContainer inventory = trade.getTradeInventory();

        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (stack.isEmpty()) continue;

            int col = i % 9;
            int row = i / 9;

            // Border and button slots should never have real items, but skip them anyway
            if (col == 4 || row == 5) continue;

            // Ownership based on inventory index
            UUID ownerUuid = (col < 4)
                    ? trade.getRequesterUuid()
                    : trade.getTargetUuid();

            ServerPlayer owner = server.getPlayerList().getPlayer(ownerUuid);

            if (owner != null) {
                if (!owner.getInventory().add(stack)) {
                    owner.drop(stack, false);
                }
            } else {
                SimplePlayerTrades.LOGGER.warn("[Trades] Could not return item {} to offline player {}",
                        stack.getItem(), ownerUuid);
            }

            inventory.setItem(i, ItemStack.EMPTY);
        }
    }


    private void cancelActiveTrade(ActiveTrade trade, ServerPlayer initiator, MinecraftServer server, String initiatorMsg, String otherMsg) {
        // Remove first to prevent recursion from closeContainer()
        activeTrades.remove(trade.getRequesterUuid());
        activeTrades.remove(trade.getTargetUuid());

        returnItems(trade, server);

        ServerPlayer other = server.getPlayerList().getPlayer(
                trade.getOtherPlayerUuid(initiator.getUUID())
        );

        if (other != null) {
            other.closeContainer();
            sendMessage(other, otherMsg);
        }

        initiator.closeContainer();
        sendMessage(initiator, initiatorMsg);

        SimplePlayerTrades.LOGGER.info("[Trades] Trade between {} and {} was cancelled.",
                trade.getRequesterName(), trade.getTargetName());
    }


    public void handleDeny(ActiveTrade trade, ServerPlayer player, MinecraftServer server) {
        if (!activeTrades.containsKey(player.getUUID())) return;

        cancelActiveTrade(
                trade, player, server,
                "§cYou cancelled the trade.",
                "§e" + player.getName().getString() + " §cdenied the trade."
        );
    }


    public void handleConfirm(ActiveTrade trade, ServerPlayer player, MinecraftServer server, boolean isRequester) {
        if (!activeTrades.containsKey(player.getUUID())) return;

        boolean nowConfirmed = trade.toggleConfirm(isRequester);
        TradeScreenHandler.updateConfirmVisuals(trade.getTradeInventory(), trade);

        ServerPlayer other = server.getPlayerList().getPlayer(
                trade.getOtherPlayerUuid(player.getUUID())
        );

        if (nowConfirmed) {
            sendMessage(player, "§aTrade confirmed! Waiting for the other player...");
            if (other != null) {
                sendMessage(other, "§e" + player.getName().getString() + " §aconfirmed the trade!");
                playPling(other);
            }
            playPling(player);
        } else {
            sendMessage(player, "§cTrade unconfirmed.");
            if (other != null) {
                sendMessage(other, "§e" + player.getName().getString() + " §cunconfirmed the trade.");
            }
        }

        if (trade.isBothConfirmed()) {
            executeTrade(trade, server);
        }
    }



    private void executeTrade(ActiveTrade trade, MinecraftServer server) {
        // Remove first so closeContainer() calls don't trigger handleGuiClose logic
        activeTrades.remove(trade.getRequesterUuid());
        activeTrades.remove(trade.getTargetUuid());

        SimpleContainer inventory = trade.getTradeInventory();

        // Collect items from each side before clearing
        List<ItemStack> requesterItems = new ArrayList<>();
        List<ItemStack> targetItems    = new ArrayList<>();

        for (int i = 0; i < 45; i++) { // rows 0-4 only, skip button row
            ItemStack stack = inventory.getItem(i);
            if (stack.isEmpty()) continue;
            int col = i % 9;
            if      (col < 4) requesterItems.add(stack.copy());
            else if (col > 4) targetItems.add(stack.copy());
            inventory.setItem(i, ItemStack.EMPTY);
        }

        ServerPlayer requester = server.getPlayerList().getPlayer(trade.getRequesterUuid());
        ServerPlayer target    = server.getPlayerList().getPlayer(trade.getTargetUuid());

        // Give target's items to requester
        if (requester != null) {
            for (ItemStack stack : targetItems) {
                if (!requester.getInventory().add(stack)) {
                    requester.drop(stack, false);
                }
            }
            requester.closeContainer();
            sendMessage(requester, "§aTrade completed successfully!");
        }

        // Give requester's items to target
        if (target != null) {
            for (ItemStack stack : requesterItems) {
                if (!target.getInventory().add(stack)) {
                    target.drop(stack, false);
                }
            }
            target.closeContainer();
            sendMessage(target, "§aTrade completed successfully!");
        }

        SimplePlayerTrades.LOGGER.info("[Trades] Trade between {} and {} completed successfully.",
                trade.getRequesterName(), trade.getTargetName());
    }


    private void playPling(ServerPlayer player) {
        if (player == null) return;
        player.playSound(SoundEvents.NOTE_BLOCK_PLING.value(), 1.0f, 2.0f);
    }

}