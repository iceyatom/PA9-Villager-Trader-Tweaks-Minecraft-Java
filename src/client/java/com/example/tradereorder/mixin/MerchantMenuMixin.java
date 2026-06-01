package com.example.tradereorder.mixin;

import com.example.tradereorder.ClientMerchantMenuDuck;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.item.trading.Merchant;
import net.minecraft.world.item.trading.MerchantOffers;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;

@Mixin(MerchantMenu.class)
public abstract class MerchantMenuMixin implements ClientMerchantMenuDuck {

    @Shadow @Final private Merchant trader;

    @Unique private MerchantOffers tradeReorder$futureOffers;
    @Unique private MerchantOffers tradeReorder$combinedOffers;
    @Unique private boolean tradeReorder$showAllTrades;

    @Override
    public MerchantOffers tradeReorder$getCurrentOffers() {
        return this.trader.getOffers();
    }

    @Override
    public MerchantOffers tradeReorder$getDisplayOffers() {
        if (tradeReorder$showAllTrades) {
            tradeReorder$rebuildCombinedOffers();
            if (tradeReorder$combinedOffers != null) {
                return tradeReorder$combinedOffers;
            }
        }
        return tradeReorder$getCurrentOffers();
    }

    @Override
    public void tradeReorder$setFutureOffers(Optional<MerchantOffers> offers) {
        tradeReorder$futureOffers = offers.orElse(null);
        tradeReorder$combinedOffers = null;
    }

    @Override
    public void tradeReorder$setShowAllTrades(boolean showAll) {
        tradeReorder$showAllTrades = showAll;
        if (!showAll) {
            tradeReorder$combinedOffers = null;
        }
    }

    @Inject(method = "getOffers", at = @At("RETURN"), cancellable = true)
    private void tradeReorder$useCombinedOffersInViewAll(CallbackInfoReturnable<MerchantOffers> cir) {
        if (tradeReorder$showAllTrades) {
            cir.setReturnValue(tradeReorder$getDisplayOffers());
        }
    }

    @Unique
    private void tradeReorder$rebuildCombinedOffers() {
        if (tradeReorder$futureOffers == null || tradeReorder$futureOffers.isEmpty()) {
            tradeReorder$combinedOffers = null;
            return;
        }

        MerchantOffers combined = new MerchantOffers();
        combined.addAll(tradeReorder$getCurrentOffers());
        combined.addAll(tradeReorder$futureOffers);
        tradeReorder$combinedOffers = combined;
    }
}
