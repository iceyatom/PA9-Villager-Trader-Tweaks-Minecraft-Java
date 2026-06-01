package com.example.tradereorder;

import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.item.trading.MerchantOffers;

import java.util.Optional;

public interface ClientMerchantMenuDuck {
    static ClientMerchantMenuDuck of(MerchantMenu menu) {
        return (ClientMerchantMenuDuck) menu;
    }

    MerchantOffers tradeReorder$getCurrentOffers();

    MerchantOffers tradeReorder$getDisplayOffers();

    void tradeReorder$setFutureOffers(Optional<MerchantOffers> offers);

    void tradeReorder$setShowAllTrades(boolean showAll);
}
