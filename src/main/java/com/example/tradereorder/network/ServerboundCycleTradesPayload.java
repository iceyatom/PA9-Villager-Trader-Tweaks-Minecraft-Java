package com.example.tradereorder.network;

import com.example.tradereorder.TradeReorder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Sent by the client when the player presses the <em>Cycle</em> button in
 * View All mode. Carries no data — the server resolves the target villager from
 * the player's open merchant menu and re-rolls its trades if it is still fresh
 * (never traded with, lowest level, zero XP).
 */
public record ServerboundCycleTradesPayload() implements CustomPacketPayload {

    public static final Type<ServerboundCycleTradesPayload> PACKET_ID =
            new Type<>(TradeReorder.id("cycle_trades"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ServerboundCycleTradesPayload>
            PACKET_CODEC = StreamCodec.unit(new ServerboundCycleTradesPayload());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return PACKET_ID;
    }
}
