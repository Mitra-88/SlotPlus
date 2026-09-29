package me.mitra.client;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

final class Bindings {
    // Container slot space: hotbar 0-8, main inventory 9-35, armor 36-39.
    // Offhand (40) and crafting-grid slots are deliberately not bindable.
    static final int MAX_SLOT = 39;
    private static final int HOTBAR_SLOTS = 9;
    private static final int ARMOR_SLOTS = 4;

    // Binds are symmetric and additive: one slot can hold several partners, so a
    // single armor/hotbar slot can be bound to every helmet in the inventory.
    @SuppressWarnings("unchecked")
    private static final List<Integer>[] partners = new List[MAX_SLOT + 1];
    private static boolean dirty;

    static {
        for (int slot = 0; slot <= MAX_SLOT; slot++) partners[slot] = new ArrayList<>();
    }

    private Bindings() {
    }

    static boolean isHotbarSlot(int containerSlot) {
        return containerSlot >= 0 && containerSlot < HOTBAR_SLOTS;
    }

    static boolean isArmorSlot(int containerSlot) {
        return containerSlot >= firstArmorSlot() && containerSlot <= MAX_SLOT;
    }

    static int firstArmorSlot() {
        return MAX_SLOT - ARMOR_SLOTS + 1;
    }

    static boolean isBindable(int containerSlot) {
        return containerSlot >= 0 && containerSlot <= MAX_SLOT;
    }

    // Re-validates the partner list on every read and silently drops broken
    // entries; a partner must list this slot back.
    static List<Integer> consistentPartners(int slot) {
        if (!isBindable(slot)) return List.of();
        List<Integer> list = partners[slot];
        Iterator<Integer> iterator = list.iterator();
        while (iterator.hasNext()) {
            int p = iterator.next();
            if (!isBindable(p) || p == slot || !partners[p].contains(slot)) {
                iterator.remove();
                SlotPlusLog.info("self-heal: slot {} no longer lists invalid partner {}", slot, p);
            }
        }
        return list;
    }

    static void bind(int a, int b) {
        if (!isBindable(a) || !isBindable(b) || a == b) return;
        if (partners[a].contains(b)) return;
        partners[a].add(b);
        partners[b].add(a);
        dirty = true;
        SlotPlusLog.info("bind: {} <-> {} (slot {} now has {} partner(s))", a, b, a, partners[a].size());
    }

    static boolean unbind(int slot) {
        if (!isBindable(slot) || partners[slot].isEmpty()) return false;
        for (int p : partners[slot]) {
            partners[p].remove(Integer.valueOf(slot));
        }
        SlotPlusLog.info("unbind: {} (was bound to {})", slot, partners[slot]);
        partners[slot].clear();
        dirty = true;
        return true;
    }

    static boolean isDirty() {
        return dirty;
    }

    static void markClean() {
        dirty = false;
    }

    static int[][] snapshot() {
        List<int[]> pairs = new ArrayList<>();
        for (int slot = 0; slot <= MAX_SLOT; slot++) {
            for (int p : partners[slot]) {
                if (p > slot) pairs.add(new int[]{slot, p});
            }
        }
        return pairs.toArray(new int[0][]);
    }

    static void restore(int[][] pairs) {
        for (List<Integer> list : partners) list.clear();
        if (pairs == null) return;
        for (int[] pair : pairs) {
            if (pair.length == 2) bind(pair[0], pair[1]);
        }
    }

    static String describe() {
        StringBuilder text = new StringBuilder();
        for (int slot = 0; slot <= MAX_SLOT; slot++) {
            for (int p : partners[slot]) {
                if (p > slot) {
                    text.append(text.length() == 0 ? "" : ", ").append(slot).append("<->").append(p);
                }
            }
        }
        return text.length() == 0 ? "none" : text.toString();
    }
}
