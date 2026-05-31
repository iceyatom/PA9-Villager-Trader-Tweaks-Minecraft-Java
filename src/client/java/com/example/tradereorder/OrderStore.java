package com.example.tradereorder;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Stores, per merchant, a saved display permutation of its trade offers.
 *
 * <p>The key identifies a merchant. For villagers we use the entity UUID so the
 * ordering follows that specific villager across sessions. The value is a list
 * of "trade fingerprints" in the desired display order. We key on a content
 * fingerprint of each offer (not list position) so the saved order stays
 * meaningful even if the server adds, removes, or re-levels trades between
 * visits.</p>
 *
 * <p>This is a purely client-side cosmetic reordering layer: it never mutates
 * the server's authoritative offer list, only the order in which offers are
 * displayed and which list index a button maps to.</p>
 */
public final class OrderStore {

    private static final OrderStore INSTANCE = new OrderStore();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type TYPE =
            new TypeToken<Map<String, List<String>>>() {}.getType();

    private final Map<String, List<String>> orders = new ConcurrentHashMap<>();
    private Path file;

    private OrderStore() {}

    public static OrderStore get() {
        return INSTANCE;
    }

    private Path file() {
        if (file == null) {
            Path dir = FabricLoader.getInstance().getConfigDir().resolve("trade-reorder");
            try {
                Files.createDirectories(dir);
            } catch (IOException ignored) {
            }
            file = dir.resolve("orders.json");
        }
        return file;
    }

    public synchronized void load() {
        Path f = file();
        if (!Files.exists(f)) {
            return;
        }
        try (Reader r = Files.newBufferedReader(f)) {
            Map<String, List<String>> loaded = GSON.fromJson(r, TYPE);
            orders.clear();
            if (loaded != null) {
                orders.putAll(loaded);
            }
        } catch (Exception e) {
            TradeReorderClient.LOGGER.warn("[Trade Reorder] failed to load orders", e);
        }
    }

    public synchronized void save() {
        Path f = file();
        try (Writer w = Files.newBufferedWriter(f)) {
            GSON.toJson(orders, TYPE, w);
        } catch (Exception e) {
            TradeReorderClient.LOGGER.warn("[Trade Reorder] failed to save orders", e);
        }
    }

    /** Returns the saved fingerprint order for a merchant, or null if none. */
    public List<String> getOrder(String merchantKey) {
        List<String> o = orders.get(merchantKey);
        return o == null ? null : new ArrayList<>(o);
    }

    /** Stores a fingerprint order for a merchant and persists to disk. */
    public void putOrder(String merchantKey, List<String> fingerprintOrder) {
        if (merchantKey == null) {
            return;
        }
        orders.put(merchantKey, new ArrayList<>(fingerprintOrder));
        save();
    }

    public void clear(String merchantKey) {
        orders.remove(merchantKey);
        save();
    }
}
