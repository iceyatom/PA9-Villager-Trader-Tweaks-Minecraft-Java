# Trade Reorder — Minecraft 26.1.2 (Fabric)

A **client-side** mod for the villager trading screen that lets you reorder
trades per villager and restore that order on every subsequent visit. Orders
are saved independently for each villager and scoped to each world/server so
copies of a world never bleed into each other.

---

## How it works

Villager offer lists are server-authoritative — the server owns the list and
the client receives a copy. This mod works entirely on that client-side copy
and never sends anything to the server outside of normal trade packets.

**Display order.** On screen open, the mod captures the server's offer list as
a sequence of content fingerprints (`costA|costB|result` by item id and count),
then physically reorders the client's copy to match the saved order for that
villager. Because the reorder happens on the actual list object the screen
reads, the display follows without touching any render internals.

**Selection translation.** When you click a trade, vanilla computes
`shopItem = button.index + scrollOffset` and sends that index to the server via
`ServerboundSelectTradePacket`. Since the server's list is in the original
order, the mod intercepts `postButtonClick` and translates the display index
back to the original server index before the packet is sent, so the trade that
executes always matches the one you clicked.

**Persistence.** Orders are stored in
`.minecraft/config/trade-reorder/orders.json` as a flat map of
`<scope>|villager:<UUID>` → `[fingerprint, ...]`. The scope prefix is
`sp:<world folder>` for singleplayer or `mp:<server address>` for multiplayer,
which prevents UUID collisions between copied worlds or different servers.
Fingerprint matching means a saved order survives a villager gaining, losing,
or re-leveling trades — unrecognised trades are appended at the end.

---

## Requirements to build

- **JDK 25** — Minecraft 26.1 requires Java 25 for both compilation and the
  Gradle toolchain.
- **Gradle 9.4+** on your `PATH` — needed once to generate the wrapper, then
  `gradlew` is self-contained.
- Internet access to `maven.fabricmc.net` and Mojang's servers on first build
  (Loom downloads the 26.1.2 client jar).
- IntelliJ IDEA **2025.3+** if developing (earlier versions don't resolve
  mixins correctly for this MC version).

---

## Build

```bash
# 1. Unzip and enter the project folder.
cd trade-reorder

# 2. Confirm JDK 25 is active.
java -version   # must report 25

# 3. Generate the Gradle wrapper (once only).
gradle wrapper --gradle-version 9.4.0

# 4. Build.
gradlew.bat build        # Windows
./gradlew build          # macOS / Linux

# Output:
#   build/libs/trade-reorder-1.0.0.jar          ← install this
#   build/libs/trade-reorder-1.0.0-sources.jar  ← ignore
```

On subsequent rebuilds after source changes, step 3 is not needed — run
`gradlew.bat build` directly.

---

## Install

1. Install **Fabric Loader 0.18.4** for Minecraft 26.1.2 via the Fabric
   installer.
2. Place **Fabric API 0.150.0+26.1.2** in `.minecraft/mods/`.
3. Place `build/libs/trade-reorder-1.0.0.jar` in `.minecraft/mods/`.
4. Launch the `fabric-loader-26.1.2` profile.

---

## Using it in-game

1. Open a villager or wandering trader's trade screen.
2. The **Reorder: OFF** toggle sits above the top-left of the trade list.
   A **Reset** button sits immediately to its right.

**To reorder:**
1. Click **Reorder: OFF** → it becomes **Reorder: ON**.
2. Click any trade row to select it — the `#N/total` counter on the left
   updates to show which row is selected.
3. Click **Up** or **Down** (left side of the screen) to move the selected
   trade. The new order saves to disk immediately.
4. Click **Reorder: ON** → **OFF** to return to normal trading.

**To reset:**
- Click **Reset** at any time to restore the villager's original trade order
  and delete the saved ordering for that villager. Works whether reorder mode
  is on or off.

Saved orders persist across sessions. The next time you open the same villager
the list is restored to your saved order automatically.

---

## Technical notes

- **Class tweaker.** `trade-reorder.classtweaker` makes
  `MerchantScreen$TradeOfferButton` accessible to the compiler (it is private
  in the dev jar). No fields are made mutable — the mod reorders the
  `MerchantOffers` list instead of rewriting the buttons' `final index` field,
  which would cause an `IllegalAccessError` at runtime on Loader 0.18.
- **Mixin targets.** The mod injects into `MerchantScreen.init` (adds buttons),
  `MerchantScreen.extractContents` (calls `tryInit` on first render),
  `MerchantScreen.mouseClicked` (click-to-select in reorder mode), and
  `MerchantScreen.postButtonClick` (index translation). All targets are methods
  declared on `MerchantScreen` itself; the mod does not target inherited methods
  from `AbstractContainerScreen`.
- **Orphaned data.** If a villager dies its `orders.json` entry is never
  automatically removed. Entries are small (a handful of strings each) so this
  is not a performance problem, but you can manually delete `orders.json` to
  clear all saved orders, or use the Reset button per villager while it is
  alive.
- **Source set.** Everything lives in the `client` source set via
  `splitEnvironmentSourceSets()` — this is a client-only mod with no server
  entrypoint.

---

## Planned features

### Trade cycling (unlocked trades)

When a villager still has uses remaining on a trade, add a button to cycle
through and force-refresh that offer's presented variant — useful for trades
with RNG outcomes (enchanted books, maps) where you want a specific result
without having to close and reopen. This would work by sending repeated
select + deselect packets client-side to trigger the offer cycling behaviour
the server already supports, without actually consuming items.

The main considerations here are rate-limiting (to avoid looking like an
autoclicker to server anti-cheat), only enabling the button when the trade has
uses remaining (checking `MerchantOffer.isOutOfStock()`), and making it visually
clear which trade is being cycled.

### Viewing locked trades

When a trade runs out of uses (locked / greyed out in vanilla), the offer is
still present in the list but inaccessible. Add a toggle or separate panel to
show locked trades with their details still visible — costs, result, and a use
count or restock timer if that information is available client-side. This is
purely display: no interaction is needed, just rendering offer data from the
`MerchantOffers` list entries that are currently `isOutOfStock()`.

This pairs naturally with the reorder feature — you may want to keep a locked
trade visible in its saved position so you know to come back to it, rather than
having it silently grey out mid-list.

---

## Version history

### 1.0.0 — 2026-05-31

Initial release.

- Per-villager trade reordering via **Reorder: ON/OFF** toggle.
- **Up / Down** buttons move the selected trade row; order saves to disk immediately.
- **Reset** button restores the server's original trade order and removes the saved ordering for that villager.
- Orders persisted in `.minecraft/config/trade-reorder/orders.json` keyed by scope + villager UUID.
- Scope prefix (`sp:<world>` / `mp:<address>`) prevents UUID collisions across copied worlds or different servers.
- Fingerprint matching (`costA|costB|result`) keeps saved orders valid when a villager gains, loses, or re-levels trades — unknown trades are appended at the end.
- Index translation in `postButtonClick` maps the display-side index back to the server-side index before `ServerboundSelectTradePacket` is sent, ensuring the correct trade executes regardless of display order.
- Targets Minecraft **26.1.2** with Fabric Loader **0.18.4** and Fabric API **0.150.0+26.1.2**.
