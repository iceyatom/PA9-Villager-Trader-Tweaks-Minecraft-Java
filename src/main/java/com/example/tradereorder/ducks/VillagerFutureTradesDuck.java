package com.example.tradereorder.ducks;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.trading.MerchantOffers;

import java.util.Optional;

public interface VillagerFutureTradesDuck {
    static VillagerFutureTradesDuck of(Villager villager) {
        return (VillagerFutureTradesDuck) villager;
    }

    Optional<MerchantOffers> tradeReorder$getFutureOffers();

    void tradeReorder$sendFutureOffers(ServerPlayer player);
}
