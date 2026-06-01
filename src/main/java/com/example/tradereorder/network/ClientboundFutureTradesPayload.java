package com.example.tradereorder.network;

import com.example.tradereorder.TradeReorder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.item.trading.MerchantOffers;

import java.util.Optional;

public record ClientboundFutureTradesPayload(Optional<MerchantOffers> offers)
        implements CustomPacketPayload {

    public static final Type<ClientboundFutureTradesPayload> PACKET_ID =
            new Type<>(TradeReorder.id("future_trades"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ClientboundFutureTradesPayload>
            PACKET_CODEC = StreamCodec.of(
                    (buf, payload) -> {
                        buf.writeBoolean(payload.offers().isPresent());
                        payload.offers().ifPresent(offers -> MerchantOffers.STREAM_CODEC.encode(buf, offers));
                    },
                    buf -> {
                        if (!buf.readBoolean()) {
                            return new ClientboundFutureTradesPayload(Optional.empty());
                        }
                        return new ClientboundFutureTradesPayload(
                                Optional.of(MerchantOffers.STREAM_CODEC.decode(buf)));
                    });

    public ClientboundFutureTradesPayload(MerchantOffers offers) {
        this(Optional.ofNullable(offers));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return PACKET_ID;
    }
}
