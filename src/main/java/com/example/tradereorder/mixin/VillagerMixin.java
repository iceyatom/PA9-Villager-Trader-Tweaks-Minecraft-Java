package com.example.tradereorder.mixin;

import com.example.tradereorder.ducks.VillagerFutureTradesDuck;
import com.example.tradereorder.network.ClientboundFutureTradesPayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ReputationEventHandler;
import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerData;
import net.minecraft.world.entity.npc.villager.VillagerDataHolder;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.item.trading.TradeSet;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Mixin(Villager.class)
public abstract class VillagerMixin extends AbstractVillager
        implements ReputationEventHandler, VillagerDataHolder, VillagerFutureTradesDuck {

    @Shadow public abstract VillagerData getVillagerData();

    @Shadow public abstract int getVillagerXp();

    @Shadow private void resendOffersToTradingPlayer() {
        throw new AssertionError("shadow");
    }

    @Unique private static final String TRADE_REORDER_FUTURE_OFFERS = "TradeReorderFutureOffers";

    @Unique private List<MerchantOffers> tradeReorder$futureOffers;

    protected VillagerMixin(EntityType<? extends AbstractVillager> entityType, Level level) {
        super(entityType, level);
    }

    @Inject(method = "addAdditionalSaveData", at = @At("HEAD"))
    private void tradeReorder$saveFutureTrades(ValueOutput output, CallbackInfo ci) {
        if (tradeReorder$futureOffers != null && !tradeReorder$futureOffers.isEmpty()) {
            output.store(TRADE_REORDER_FUTURE_OFFERS,
                    MerchantOffers.CODEC.listOf(),
                    tradeReorder$futureOffers);
        }
    }

    @Inject(method = "readAdditionalSaveData", at = @At("TAIL"))
    private void tradeReorder$readFutureTrades(ValueInput input, CallbackInfo ci) {
        Optional<List<MerchantOffers>> saved =
                input.read(TRADE_REORDER_FUTURE_OFFERS, MerchantOffers.CODEC.listOf());
        tradeReorder$futureOffers = saved.map(ArrayList::new).orElse(null);
    }

    @Inject(method = "updateTrades", at = @At("HEAD"), cancellable = true)
    private void tradeReorder$useStoredFutureTradeSet(ServerLevel level, CallbackInfo ci) {
        if (this.offers == null || this.offers.isEmpty()) {
            tradeReorder$futureOffers = null;
            return;
        }
        if (tradeReorder$futureOffers == null || tradeReorder$futureOffers.isEmpty()) {
            return;
        }

        MerchantOffers nextOffers = tradeReorder$futureOffers.remove(0);
        this.offers.addAll(nextOffers);
        tradeReorder$trimFutureOffers();
        ci.cancel();
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void tradeReorder$trimInvalidFutureTrades(CallbackInfo ci) {
        if (this.offers == null) {
            tradeReorder$futureOffers = null;
            return;
        }
        tradeReorder$trimFutureOffers();
    }

    @Override
    public Optional<MerchantOffers> tradeReorder$getFutureOffers() {
        tradeReorder$ensureFutureOffers();
        if (tradeReorder$futureOffers == null || tradeReorder$futureOffers.isEmpty()) {
            return Optional.empty();
        }

        MerchantOffers condensed = new MerchantOffers();
        for (MerchantOffers offers : tradeReorder$futureOffers) {
            condensed.addAll(offers);
        }
        return condensed.isEmpty() ? Optional.empty() : Optional.of(condensed);
    }

    @Override
    public void tradeReorder$sendFutureOffers(ServerPlayer player) {
        Optional<MerchantOffers> offers = tradeReorder$getFutureOffers();
        ServerPlayNetworking.send(player, new ClientboundFutureTradesPayload(offers));
    }

    @Override
    public boolean tradeReorder$canCycleTrades() {
        return getVillagerXp() == 0
                && getVillagerData().level() == VillagerData.MIN_VILLAGER_LEVEL
                && this.offers != null
                && !this.offers.isEmpty();
    }

    @Override
    public void tradeReorder$cycleTrades(ServerPlayer player) {
        if (!(this.level() instanceof ServerLevel serverLevel)) {
            return;
        }
        if (!tradeReorder$canCycleTrades()) {
            return;
        }

        // Wipe the current offers and drop the cached future tiers, then let the
        // vanilla generation path roll a fresh set for the current (lowest) level.
        // Clearing first makes tradeReorder$useStoredFutureTradeSet fall through to
        // vanilla instead of replaying a stored set, so the trades are genuinely re-rolled.
        this.offers = new MerchantOffers();
        tradeReorder$futureOffers = null;
        this.updateTrades(serverLevel);

        // Push the refreshed current offers and regenerated future tiers to the client.
        resendOffersToTradingPlayer();
        tradeReorder$sendFutureOffers(player);
    }

    @Unique
    private void tradeReorder$ensureFutureOffers() {
        if (tradeReorder$futureOffers != null) {
            return;
        }
        if (!(this.level() instanceof ServerLevel serverLevel)) {
            return;
        }
        if (this.offers == null || this.offers.isEmpty()) {
            return;
        }

        List<MerchantOffers> generated = new ArrayList<>();
        int level = getVillagerData().level();
        while (level++ < 5) {
            MerchantOffers offers = tradeReorder$generateOffersForLevel(serverLevel, level);
            if (!offers.isEmpty()) {
                generated.add(offers);
            }
        }
        tradeReorder$futureOffers = generated;
        tradeReorder$trimFutureOffers();
    }

    @Unique
    private MerchantOffers tradeReorder$generateOffersForLevel(ServerLevel serverLevel, int level) {
        MerchantOffers generated = new MerchantOffers();
        VillagerData data = getVillagerData().withLevel(level);
        ResourceKey<TradeSet> trades = data.profession().value().getTrades(data.level());
        if (trades != null) {
            this.addOffersFromTradeSet(serverLevel, generated, trades);
        }
        return generated;
    }

    @Unique
    private void tradeReorder$trimFutureOffers() {
        if (tradeReorder$futureOffers == null) {
            return;
        }
        int requiredSets = Math.max(0, 5 - getVillagerData().level());
        while (tradeReorder$futureOffers.size() > requiredSets) {
            tradeReorder$futureOffers.remove(0);
        }
        if (tradeReorder$futureOffers.isEmpty()) {
            tradeReorder$futureOffers = null;
        }
    }
}
