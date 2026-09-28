package me.mitra.client;

import java.util.Arrays;

final class Bindings {
    // Container slot space: hotbar 0-8, main inventory 9-35, armor 36-39.
    // Offhand (40) and crafting-grid slots are deliberately not bindable.
    static final int MAX_SLOT = 39;
    private static final int HOTBAR_SLOTS = 9;
    private static final int ARMOR_SLOTS = 4;

    private static final int[] partner = new int[MAX_SLOT + 1];
    private static boolean dirty;

    static {
        Arrays.fill(partner, -1);
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

    static int hotbarSideOf(int a, int b) {
        return isHotbarSlot(a) ? a : b;
    }

    static int consistentPartner(int slot) {
        if (!isBindable(slot)) return -1;
        int p = partner[slot];
        if (p == -1) return -1;
        if (!isBindable(p) || p == slot || partner[p] != slot) {
            unbind(slot);
            return -1;
        }
        return p;
    }

    static void bind(int a, int b) {
        if (!isBindable(a) || !isBindable(b) || a == b) return;
        if (partner[a] == b && partner[b] == a) return;
        clearPartner(a);
        clearPartner(b);
        partner[a] = b;
        partner[b] = a;
        dirty = true;
    }

    static boolean unbind(int slot) {
        if (!isBindable(slot)) return false;
        boolean cleared = clearPartner(slot);
        if (cleared) dirty = true;
        return cleared;
    }

    private static boolean clearPartner(int slot) {
        int previous = partner[slot];
        if (previous == -1) return false;
        partner[slot] = -1;
        if (isBindable(previous) && partner[previous] == slot) {
            partner[previous] = -1;
        }
        return true;
    }

    static boolean isDirty() {
        return dirty;
    }

    static void markClean() {
        dirty = false;
    }

    static int[] snapshot() {
        return partner.clone();
    }

    static void restore(int[] snapshot) {
        if (snapshot == null) return;
        Arrays.fill(partner, -1);
        System.arraycopy(snapshot, 0, partner, 0, Math.min(snapshot.length, partner.length));
    }
}
