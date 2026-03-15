package dooms.simpleplayertrades;

import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import java.util.concurrent.CompletableFuture;

public class TradeCommand {

    /**
     * Registers all trade-related commands.
     * Called once from the mod initializer.
     */
    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {

            // /trade <player>
            dispatcher.register(
                    Commands.literal("trade")
                            .then(Commands.argument("player", EntityArgument.player())
                                    .suggests(TradeCommand::suggestTradablePlayers)
                                    .executes(TradeCommand::executeTrade))
            );

            // /tradeaccept <player>
            dispatcher.register(
                    Commands.literal("tradeaccept")
                            .then(Commands.argument("player", EntityArgument.player())
                                    .suggests(TradeCommand::suggestIncomingRequest)
                                    .executes(TradeCommand::executeTradeAccept))
            );


            // /tradedeny <player>
            dispatcher.register(
                    Commands.literal("tradedeny")
                            .then(Commands.argument("player", EntityArgument.player())
                                    .suggests(TradeCommand::suggestIncomingRequest)
                                    .executes(TradeCommand::executeTradeDeny))
            );

            // /tradecancel
            dispatcher.register(
                    Commands.literal("tradecancel")
                            .executes(TradeCommand::executeTradeCancel)
            );
        });
    }

    // --- Command Executors ---

    private static int executeTrade(CommandContext<CommandSourceStack> context) {
        try {
            ServerPlayer sender = context.getSource().getPlayerOrException();

            if (!ModConfig.getInstance().isTradingEnabled()) {
                sender.sendSystemMessage(Component.literal("§cTrading is currently disabled on this server."));
                return 0;
            }

            ServerPlayer target = EntityArgument.getPlayer(context, "player");
            MinecraftServer server = context.getSource().getServer();

            if (ModConfig.getInstance().isRequireOp() &&
                    !java.util.Arrays.asList(server.getPlayerList().getOpNames()).contains(sender.getName().getString())) {
                sender.sendSystemMessage(Component.literal("§cYou do not have permission to trade."));
                return 0;
            }

            if (sender.getUUID().equals(target.getUUID())) {
                sender.sendSystemMessage(Component.literal("§cYou cannot trade with yourself."));
                return 0;
            }

            TradeManager.getInstance().sendTradeRequest(sender, target, server);
            return 1;

        } catch (Exception e) {
            SimplePlayerTrades.LOGGER.error("[Trades] Error in /trade command", e);
            return 0;
        }
    }

    private static int executeTradeAccept(CommandContext<CommandSourceStack> context) {
        try {
            ServerPlayer acceptor  = context.getSource().getPlayerOrException();
            ServerPlayer requester = EntityArgument.getPlayer(context, "player");
            MinecraftServer server = context.getSource().getServer();

            TradeManager.getInstance().acceptTradeRequest(acceptor, requester, server);
            return 1;

        } catch (Exception e) {
            SimplePlayerTrades.LOGGER.error("[Trades] Error in /tradeaccept command", e);
            return 0;
        }
    }

    private static int executeTradeDeny(CommandContext<CommandSourceStack> context) {
        try {
            ServerPlayer denier    = context.getSource().getPlayerOrException();
            ServerPlayer requester = EntityArgument.getPlayer(context, "player");
            MinecraftServer server = context.getSource().getServer();

            TradeManager.getInstance().denyTradeRequest(denier, requester, server);
            return 1;

        } catch (Exception e) {
            SimplePlayerTrades.LOGGER.error("[Trades] Error in /tradedeny command", e);
            return 0;
        }
    }

    private static int executeTradeCancel(CommandContext<CommandSourceStack> context) {
        try {
            ServerPlayer canceler  = context.getSource().getPlayerOrException();
            MinecraftServer server = context.getSource().getServer();

            TradeManager.getInstance().cancelTrade(canceler, server);
            return 1;

        } catch (Exception e) {
            SimplePlayerTrades.LOGGER.error("[Trades] Error in /tradecancel command", e);
            return 0;
        }
    }

    private static CompletableFuture<Suggestions> suggestTradablePlayers(
            CommandContext<CommandSourceStack> context,
            SuggestionsBuilder builder) {

        ServerPlayer sender;
        try {
            sender = context.getSource().getPlayerOrException();
        } catch (Exception e) {
            return builder.buildFuture();
        }

        // Only suggest online players that are not the sender
        context.getSource().getServer().getPlayerList().getPlayers().stream()
                .filter(p -> !p.getUUID().equals(sender.getUUID()))
                .map(p -> p.getName().getString())
                .forEach(builder::suggest);

        return builder.buildFuture();
    }


    private static CompletableFuture<Suggestions> suggestIncomingRequest(
            CommandContext<CommandSourceStack> context,
            SuggestionsBuilder builder) {

        ServerPlayer player;
        try {
            player = context.getSource().getPlayerOrException();
        } catch (Exception e) {
            return builder.buildFuture();
        }

        // Only suggest the player who sent this player a trade request
        ActiveTrade trade = TradeManager.getInstance().getPendingRequestFor(player);
        if (trade != null) {
            context.getSource().getServer().getPlayerList().getPlayers().stream()
                    .filter(p -> p.getUUID().equals(trade.getRequesterUuid()))
                    .map(p -> p.getName().getString())
                    .forEach(builder::suggest);
        }

        return builder.buildFuture();
    }
}