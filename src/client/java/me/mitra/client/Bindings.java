package me.mitra.client;

import java.util.Arrays;

public final class Bindings {
    public static final int HOTBAR_SLOTS = 9;
    public static final int MAX_SLOT = 35;

    private static final Bindings INSTANCE = new Bindings();

    private final int[] partner = new int[MAX_SLOT + 1];
    private boolean dirty;

    private Bindings() {
        Arrays.fill(partner, -1);
    }

    public static Bindings get() {
        return INSTANCE;
    }

    public static boolean isHotbarSlot(int containerSlot) {
        return containerSlot >= 0 && containerSlot < HOTBAR_SLOTS;
    }

    public static boolean isBindable(int containerSlot) {
        return containerSlot >= 0 && containerSlot <= MAX_SLOT;
    }

    public int consistentPartner(int slot) {
        if (!isBindable(slot)) return -1;
        int p = partner[slot];
        if (p == -1) return -1;
        if (!isBindable(p) || p == slot || partner[p] != slot) {
            unbind(slot);
            return -1;
        }
        return p;
    }

    public void bind(int a, int b) {
        if (!isBindable(a) || !isBindable(b) || a == b) return;
        if (!isHotbarSlot(a) && !isHotbarSlot(b)) return;
        if (partner[a] == b && partner[b] == a) return;
        clearPartner(a, b);
        clearPartner(b, a);
        partner[a] = b;
        partner[b] = a;
        dirty = true;
    }

    public boolean unbind(int slot) {
        if (!isBindable(slot)) return false;
        int p = partner[slot];
        if (p == -1) return false;
        partner[slot] = -1;
        if (isBindable(p) && partner[p] == slot) {
            partner[p] = -1;
        }
        dirty = true;
        return true;
    }

    private void clearPartner(int slot, int keep) {
        int previous = partner[slot];
        if (previous == -1 || previous == keep) return;
        partner[slot] = -1;
        if (isBindable(previous) && partner[previous] == slot) {
            partner[previous] = -1;
        }
    }

    public boolean isDirty() {
        return dirty;
    }

    public void markClean() {
        dirty = false;
    }

    public int[] snapshot() {
        return partner.clone();
    }

    public void restore(int[] partner) {
        if (partner != null && partner.length == this.partner.length) {
            System.arraycopy(partner, 0, this.partner, 0, partner.length);
        }
    }
}
