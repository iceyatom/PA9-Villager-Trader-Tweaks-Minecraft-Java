package com.example.tradereorder;

import net.fabricmc.api.ClientModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TradeReorderClient implements ClientModInitializer {
    public static final String MOD_ID = "trade-reorder";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitializeClient() {
        // Load any saved orderings from disk into memory.
        OrderStore.get().load();
        LOGGER.info("[Trade Reorder] client initialized");
    }
}
