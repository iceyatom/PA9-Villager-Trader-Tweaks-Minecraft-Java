package com.example.tradereorder.mixin;

import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.item.trading.MerchantOffers;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * Lets us read the offers list the client currently holds for the open menu.
 */
@Mixin(MerchantMenu.class)
public interface MerchantMenuAccessor {

    @Invoker("getOffers")
    MerchantOffers tradeReorder$getOffers();
}
