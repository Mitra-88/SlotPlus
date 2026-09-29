package me.mitra.client;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BindingsTest {
    @BeforeEach
    void reset() {
        Bindings.restore(new int[0][]);
        Bindings.markClean();
    }

    @Test
    void bindStoresSymmetricPair() {
        Bindings.bind(2, 10);
        assertTrue(Bindings.consistentPartners(2).contains(10));
        assertTrue(Bindings.consistentPartners(10).contains(2));
        assertEquals(1, Bindings.consistentPartners(2).size());
    }

    @Test
    void bindAddsMultiplePartners() {
        Bindings.bind(39, 11);
        Bindings.bind(39, 18);
        assertTrue(Bindings.consistentPartners(39).contains(11));
        assertTrue(Bindings.consistentPartners(39).contains(18));
        assertTrue(Bindings.consistentPartners(11).contains(39));
        assertTrue(Bindings.consistentPartners(18).contains(39));
        assertEquals(2, Bindings.consistentPartners(39).size());
    }

    @Test
    void bindArmorToHotbar() {
        Bindings.bind(36, 4);
        assertTrue(Bindings.consistentPartners(36).contains(4));
        assertTrue(Bindings.consistentPartners(4).contains(36));
    }

    @Test
    void hotbarToHotbarAllowed() {
        Bindings.bind(1, 4);
        assertTrue(Bindings.consistentPartners(1).contains(4));
    }

    @Test
    void unbindClearsEveryPartnerSide() {
        Bindings.bind(3, 20);
        Bindings.bind(3, 21);
        assertTrue(Bindings.unbind(3));
        assertTrue(Bindings.consistentPartners(3).isEmpty());
        assertTrue(Bindings.consistentPartners(20).isEmpty());
        assertTrue(Bindings.consistentPartners(21).isEmpty());
        assertFalse(Bindings.unbind(3));
    }

    @Test
    void selfBindAndOutOfRangeRejected() {
        Bindings.bind(3, 3);
        Bindings.bind(-1, 5);
        Bindings.bind(0, Bindings.MAX_SLOT + 1);
        assertTrue(Bindings.consistentPartners(3).isEmpty());
        assertTrue(Bindings.consistentPartners(0).isEmpty());
        assertTrue(Bindings.consistentPartners(5).isEmpty());
    }

    @Test
    void restoreAcceptsPairEntriesAndIgnoresMalformed() {
        Bindings.restore(new int[][]{{2, 10}, {7}});
        assertTrue(Bindings.consistentPartners(2).contains(10));
        assertTrue(Bindings.consistentPartners(10).contains(2));
        assertTrue(Bindings.consistentPartners(7).isEmpty());
    }

    @Test
    void legacyPartnerArrayConvertsToPairs() {
        int[] legacy = new int[Bindings.MAX_SLOT + 1];
        Arrays.fill(legacy, -1);
        legacy[2] = 10;
        legacy[10] = 2;
        legacy[20] = 99;
        legacy[30] = 30;
        int[][] pairs = BindingsStore.legacyPartnerArrayToPairs(legacy);
        assertEquals(1, pairs.length);
        assertEquals(2, pairs[0][0]);
        assertEquals(10, pairs[0][1]);
    }

    @Test
    void slotBoundsHelpers() {
        assertTrue(Bindings.isHotbarSlot(0));
        assertTrue(Bindings.isHotbarSlot(8));
        assertFalse(Bindings.isHotbarSlot(9));
        assertTrue(Bindings.isBindable(35));
        assertTrue(Bindings.isBindable(36));
        assertTrue(Bindings.isBindable(39));
        assertFalse(Bindings.isBindable(40));
        assertFalse(Bindings.isBindable(-1));
        assertTrue(Bindings.isArmorSlot(36));
        assertTrue(Bindings.isArmorSlot(39));
        assertFalse(Bindings.isArmorSlot(35));
        assertFalse(Bindings.isArmorSlot(40));
    }

    @Test
    void offhandIsOutsideBindableRange() {
        Bindings.bind(40, 2);
        assertTrue(Bindings.consistentPartners(40).isEmpty());
        assertTrue(Bindings.consistentPartners(2).isEmpty());
    }
}
