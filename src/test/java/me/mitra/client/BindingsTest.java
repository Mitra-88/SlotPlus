package me.mitra.client;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BindingsTest {
    @BeforeEach
    void reset() {
        Bindings.restore(new int[Bindings.MAX_SLOT + 1]);
        Bindings.markClean();
    }

    @Test
    void bindStoresSymmetricPair() {
        Bindings.bind(2, 10);
        assertEquals(10, Bindings.consistentPartner(2));
        assertEquals(2, Bindings.consistentPartner(10));
    }

    @Test
    void bindRequiresHotbarSide() {
        Bindings.bind(10, 11);
        assertEquals(-1, Bindings.consistentPartner(10));
        assertEquals(-1, Bindings.consistentPartner(11));
    }

    @Test
    void hotbarToHotbarAllowed() {
        Bindings.bind(1, 4);
        assertEquals(4, Bindings.consistentPartner(1));
    }

    @Test
    void rebindReplacesOldPairBothSides() {
        Bindings.bind(0, 10);
        Bindings.bind(0, 11);
        assertEquals(-1, Bindings.consistentPartner(10));
        assertEquals(11, Bindings.consistentPartner(0));
        assertEquals(0, Bindings.consistentPartner(11));
    }

    @Test
    void unbindClearsBothSides() {
        Bindings.bind(3, 20);
        assertTrue(Bindings.unbind(3));
        assertEquals(-1, Bindings.consistentPartner(3));
        assertEquals(-1, Bindings.consistentPartner(20));
        assertFalse(Bindings.unbind(3));
    }

    @Test
    void selfBindAndOutOfRangeRejected() {
        Bindings.bind(3, 3);
        Bindings.bind(-1, 5);
        Bindings.bind(0, Bindings.MAX_SLOT + 1);
        assertEquals(-1, Bindings.consistentPartner(3));
        assertEquals(-1, Bindings.consistentPartner(0));
        assertEquals(-1, Bindings.consistentPartner(5));
    }

    @Test
    void consistentPartnerHealsAsymmetricState() {
        int[] asymmetric = new int[Bindings.MAX_SLOT + 1];
        asymmetric[5] = 12;
        Bindings.restore(asymmetric);
        assertEquals(-1, Bindings.consistentPartner(5));
        assertEquals(-1, Bindings.consistentPartner(12));
        assertTrue(Bindings.isDirty());
    }

    @Test
    void restoreMigratesShorterArray() {
        int[] shortArray = new int[20];
        shortArray[19] = 2;
        shortArray[2] = 19;
        Bindings.restore(shortArray);
        assertEquals(2, Bindings.consistentPartner(19));
        assertEquals(-1, Bindings.consistentPartner(Bindings.MAX_SLOT));
    }

    @Test
    void restoreMigratesLongerArray() {
        int[] longArray = new int[Bindings.MAX_SLOT + 5];
        longArray[7] = 25;
        longArray[25] = 7;
        Bindings.restore(longArray);
        assertEquals(25, Bindings.consistentPartner(7));
        assertEquals(-1, Bindings.consistentPartner(Bindings.MAX_SLOT));
    }

    @Test
    void slotBoundsHelpers() {
        assertTrue(Bindings.isHotbarSlot(0));
        assertTrue(Bindings.isHotbarSlot(8));
        assertFalse(Bindings.isHotbarSlot(9));
        assertTrue(Bindings.isBindable(35));
        assertFalse(Bindings.isBindable(36));
        assertFalse(Bindings.isBindable(-1));
    }

    @Test
    void hotbarSideOfPrefersHotbarArgument() {
        assertEquals(3, Bindings.hotbarSideOf(3, 12));
        assertEquals(3, Bindings.hotbarSideOf(12, 3));
        assertEquals(7, Bindings.hotbarSideOf(7, 9));
    }
}
