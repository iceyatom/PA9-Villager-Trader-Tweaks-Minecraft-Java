package com.example.tradereorder.mixin;

import com.example.tradereorder.OrderStore;
import com.example.tradereorder.TradeKeys;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.MerchantScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;

/**
 * Trade reordering, per-screen (no shared global state).
 *
 * <p>Design: we physically reorder the client's copy of the merchant offer
 * list ({@code MerchantMenu.getOffers()}) into the saved display order. Because
 * each vanilla trade button selects the offer at its own list position, simply
 * reordering the backing list makes both the display and click-selection follow
 * the saved order, with no need to rewrite the buttons' {@code index} field
 * (which is {@code final} and cannot be safely mutated at runtime).</p>
 *
 * <p>State is held on the screen instance, so multiple open villagers don't
 * interfere with one another.</p>
 */
@Mixin(MerchantScreen.class)
public abstract class MerchantScreenMixin
        extends AbstractContainerScreen<MerchantMenu> {

    private MerchantScreenMixin(MerchantMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
    }

    @Shadow private MerchantScreen.TradeOfferButton[] tradeOfferButtons;
    @Shadow private int scrollOff;
    @Shadow private int shopItem;

    @Unique private boolean tradeReorder$edit = false;
    @Unique private int tradeReorder$selectedRow = -1;
    @Unique private String tradeReorder$key = null;
    @Unique private boolean tradeReorder$inited = false;
    // The server's original offer order, captured as fingerprints before any
    // reordering. Used to translate a clicked display index back to the index
    // the server expects in ServerboundSelectTradePacket.
    @Unique private final List<String> tradeReorder$original = new ArrayList<>();
    @Unique private Button tradeReorder$upButton;
    @Unique private Button tradeReorder$downButton;

    @Unique
    private MerchantOffers tradeReorder$offers() {
        return ((MerchantMenuAccessor) (Object) this.menu).tradeReorder$getOffers();
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void tradeReorder$addButtons(CallbackInfo ci) {
        tradeReorder$key = TradeKeys.merchantKey(this.title.getString());
        tradeReorder$edit = false;
        tradeReorder$selectedRow = -1;
        tradeReorder$inited = false;
        tradeReorder$original.clear();

        Button toggle = Button.builder(tradeReorder$label(), b -> {
            tradeReorder$edit = !tradeReorder$edit;
            b.setMessage(tradeReorder$label());
            if (tradeReorder$edit && tradeReorder$selectedRow < 0
                    && tradeReorder$count() > 0) {
                tradeReorder$selectedRow = 0;
            }
            tradeReorder$refresh();
        }).bounds(this.leftPos + 5, this.topPos - 22, 96, 18).build();
        this.addRenderableWidget(toggle);

        Button reset = Button.builder(Component.literal("Reset"), b -> tradeReorder$reset())
                .bounds(this.leftPos + 103, this.topPos - 22, 48, 18).build();
        this.addRenderableWidget(reset);

        int bx = this.leftPos - 46;
        int by = this.topPos + 18;
        tradeReorder$upButton = Button.builder(Component.literal("Up"), b -> tradeReorder$move(-1))
                .bounds(bx, by, 42, 20).build();
        tradeReorder$downButton = Button.builder(Component.literal("Down"), b -> tradeReorder$move(1))
                .bounds(bx, by + 22, 42, 20).build();
        this.addRenderableWidget(tradeReorder$upButton);
        this.addRenderableWidget(tradeReorder$downButton);
        tradeReorder$refresh();
    }

    @Unique
    private Component tradeReorder$label() {
        return Component.literal(tradeReorder$edit ? "Reorder: ON" : "Reorder: OFF");
    }

    @Unique
    private int tradeReorder$count() {
        MerchantOffers o = tradeReorder$offers();
        return o == null ? 0 : o.size();
    }

    /** Capture original order and apply any saved display order once offers exist. */
    @Unique
    private void tradeReorder$tryInit() {
        if (tradeReorder$inited) {
            return;
        }
        MerchantOffers offers = tradeReorder$offers();
        if (offers == null || offers.isEmpty()) {
            return;
        }
        tradeReorder$inited = true;

        // Record the server's order first; this is what packet indices refer to.
        tradeReorder$original.clear();
        for (MerchantOffer o : offers) {
            tradeReorder$original.add(TradeKeys.fingerprint(o));
        }

        List<String> saved = OrderStore.get().getOrder(tradeReorder$key);
        if (saved == null || saved.isEmpty()) {
            return;
        }
        List<MerchantOffer> remaining = new ArrayList<>(offers);
        List<MerchantOffer> ordered = new ArrayList<>(remaining.size());
        for (String fp : saved) {
            for (int i = 0; i < remaining.size(); i++) {
                if (TradeKeys.fingerprint(remaining.get(i)).equals(fp)) {
                    ordered.add(remaining.remove(i));
                    break;
                }
            }
        }
        ordered.addAll(remaining);
        offers.clear();
        offers.addAll(ordered);
    }

    @Unique
    private void tradeReorder$refresh() {
        boolean on = tradeReorder$edit;
        if (on && tradeReorder$selectedRow < 0 && tradeReorder$count() > 0) {
            tradeReorder$selectedRow = 0;
        }
        int size = tradeReorder$count();
        if (tradeReorder$upButton != null) {
            tradeReorder$upButton.visible = on;
            tradeReorder$upButton.active = on && tradeReorder$selectedRow > 0;
        }
        if (tradeReorder$downButton != null) {
            tradeReorder$downButton.visible = on;
            tradeReorder$downButton.active =
                    on && tradeReorder$selectedRow >= 0 && tradeReorder$selectedRow < size - 1;
        }
    }

    @Unique
    private void tradeReorder$move(int delta) {
        MerchantOffers offers = tradeReorder$offers();
        if (offers == null) {
            return;
        }
        int from = tradeReorder$selectedRow;
        int to = from + delta;
        int n = offers.size();
        if (from < 0 || from >= n || to < 0 || to >= n) {
            return;
        }
        List<MerchantOffer> list = new ArrayList<>(offers);
        MerchantOffer moved = list.remove(from);
        list.add(to, moved);
        offers.clear();
        offers.addAll(list);

        // Persist the new display order.
        List<String> order = new ArrayList<>(offers.size());
        for (MerchantOffer o : offers) {
            order.add(TradeKeys.fingerprint(o));
        }
        OrderStore.get().putOrder(tradeReorder$key, order);

        tradeReorder$selectedRow = to;
        tradeReorder$refresh();
    }

    /**
     * Restore the merchant's offers to the server's original order and forget any
     * saved ordering for this merchant. Safe to call any time after offers load.
     */
    @Unique
    private void tradeReorder$reset() {
        MerchantOffers offers = tradeReorder$offers();
        if (offers == null || tradeReorder$original.isEmpty()) {
            return;
        }
        // Rebuild the list in original fingerprint order.
        List<MerchantOffer> remaining = new ArrayList<>(offers);
        List<MerchantOffer> ordered = new ArrayList<>(remaining.size());
        for (String fp : tradeReorder$original) {
            for (int i = 0; i < remaining.size(); i++) {
                if (TradeKeys.fingerprint(remaining.get(i)).equals(fp)) {
                    ordered.add(remaining.remove(i));
                    break;
                }
            }
        }
        ordered.addAll(remaining);
        offers.clear();
        offers.addAll(ordered);

        OrderStore.get().clear(tradeReorder$key);
        tradeReorder$selectedRow = tradeReorder$count() > 0 ? 0 : -1;
        tradeReorder$refresh();
    }

    /**
     * Translate a current display index into the index the server expects.
     * The offer shown at display row {@code displayRow} has a fingerprint; we
     * return that fingerprint's position in the captured original (server) order.
     */
    @Unique
    private int tradeReorder$realIndex(int displayRow) {
        MerchantOffers offers = tradeReorder$offers();
        if (offers == null || displayRow < 0 || displayRow >= offers.size()) {
            return displayRow;
        }
        String fp = TradeKeys.fingerprint(offers.get(displayRow));
        int idx = tradeReorder$original.indexOf(fp);
        return idx >= 0 ? idx : displayRow;
    }

    /**
     * The vanilla press handler sets {@code shopItem = button.getIndex() + scrollOff}
     * (a <em>display</em> index into our reordered client list) and then
     * postButtonClick() uses it for setSelectionHint, tryMoveItems, and the
     * ServerboundSelectTradePacket. Since the server's list is NOT reordered, we
     * rewrite shopItem here to the original server index of the displayed offer,
     * so the trade that executes matches the trade the player clicked.
     */
    @Inject(method = "postButtonClick", at = @At("HEAD"))
    private void tradeReorder$translateSelection(CallbackInfo ci) {
        if (tradeReorder$original.isEmpty()) {
            return;
        }
        this.shopItem = tradeReorder$realIndex(this.shopItem);
    }

    /**
     * While reorder mode is ON, a left-click on a trade row selects that row for
     * moving (instead of executing the trade). The clicked display row is found
     * from the on-screen trade buttons; {@code scrollOff} maps the visible button
     * index to the absolute row. We cancel vanilla handling so editing a list
     * never accidentally performs a trade.
     */
    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void tradeReorder$onClick(MouseButtonEvent event, boolean doubleClick,
                                      CallbackInfoReturnable<Boolean> cir) {
        if (!tradeReorder$edit || event.button() != 0 || this.tradeOfferButtons == null) {
            return;
        }
        double mx = event.x();
        double my = event.y();
        for (int i = 0; i < this.tradeOfferButtons.length; i++) {
            MerchantScreen.TradeOfferButton btn = this.tradeOfferButtons[i];
            if (btn == null || !btn.visible) {
                continue;
            }
            if (mx >= btn.getX() && mx < btn.getX() + btn.getWidth()
                    && my >= btn.getY() && my < btn.getY() + btn.getHeight()) {
                int row = this.scrollOff + i;
                if (row >= 0 && row < tradeReorder$count()) {
                    tradeReorder$selectedRow = row;
                    tradeReorder$refresh();
                }
                cir.setReturnValue(true);   // consume the click; don't trade
                return;
            }
        }
    }

    // Target extractContents (declared on MerchantScreen itself, runs every
    // frame). extractRenderState is declared on the parent AbstractContainerScreen,
    // which a MerchantScreen mixin cannot target directly.
    @Inject(method = "extractContents", at = @At("TAIL"))
    private void tradeReorder$onExtract(GuiGraphicsExtractor graphics, int mouseX, int mouseY,
                                        float delta, CallbackInfo ci) {
        tradeReorder$tryInit();

        // NOTE: we intentionally do NOT rewrite TradeOfferButton.index here.
        // The backing MerchantOffers list is physically reordered (see
        // tradeReorder$tryInit and tradeReorder$move), so display row i already
        // maps to offer i and the button's natural captured index selects the
        // correct offer. Rewriting the final 'index' field was both redundant
        // and required a fragile 'mutable' class-tweak that fails at runtime
        // with IllegalAccessError on Loader 0.18.

        if (tradeReorder$edit) {
            tradeReorder$refresh();
            int size = tradeReorder$count();
            String label = tradeReorder$selectedRow >= 0
                    ? ("#" + (tradeReorder$selectedRow + 1) + "/" + size)
                    : ("/" + size);
            graphics.text(this.font, label, this.leftPos - 46, this.topPos + 64,
                    0xFFFFFFFF, true);
        }
    }
}
