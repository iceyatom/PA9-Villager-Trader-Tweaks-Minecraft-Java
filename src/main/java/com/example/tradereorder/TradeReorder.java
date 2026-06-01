package com.example.tradereorder;

import com.example.tradereorder.network.ClientboundFutureTradesPayload;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TradeReorder implements ModInitializer {
    public static final String MOD_ID = "trade-reorder";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        PayloadTypeRegistry.clientboundPlay().register(
                ClientboundFutureTradesPayload.PACKET_ID,
                ClientboundFutureTradesPayload.PACKET_CODEC);
        LOGGER.info("[Trade Reorder] initialized");
    }

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MOD_ID, path);
    }
}
