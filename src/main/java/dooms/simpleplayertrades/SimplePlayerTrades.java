package dooms.simpleplayertrades;

import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SimplePlayerTrades implements ModInitializer {

	public static final String MOD_ID = "simple-player-trades";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		LOGGER.info("[Trades] Loading Simple Player Trades...");

		TradeCommand.register();
		TradeManager.getInstance().registerEvents();
		TradeLogger.getInstance().initialize();

		LOGGER.info("[Trades] Simple Player Trades loaded successfully.");
	}
}