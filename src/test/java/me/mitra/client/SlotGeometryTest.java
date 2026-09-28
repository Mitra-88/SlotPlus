package me.mitra.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SlotGeometryTest {
    @Test
    void armorMenuSlotsRunOppositeToContainerSlots() {
        // Vanilla adds armor menu slots head-first while container slots count down:
        // menu 5 = head (container 39), menu 8 = feet (container 36).
        assertEquals(5, SlotGeometry.menuSlotOfContainer(39));
        assertEquals(6, SlotGeometry.menuSlotOfContainer(38));
        assertEquals(7, SlotGeometry.menuSlotOfContainer(37));
        assertEquals(8, SlotGeometry.menuSlotOfContainer(36));
    }

    @Test
    void nonArmorMenuSlotsMapIdentityOrHotbarOffset() {
        assertEquals(36, SlotGeometry.menuSlotOfContainer(0));
        assertEquals(44, SlotGeometry.menuSlotOfContainer(8));
        assertEquals(12, SlotGeometry.menuSlotOfContainer(12));
        assertEquals(40, SlotGeometry.menuSlotOfContainer(40));
    }

    @Test
    void creativeArmorWrappersAreHeadFirstToo() {
        // Wrapper raw numbers are InventoryMenu indices: raw 5 = head (container 39), raw 8 = feet (container 36).
        assertEquals(39, SlotGeometry.creativeContainerSlot(5));
        assertEquals(38, SlotGeometry.creativeContainerSlot(6));
        assertEquals(37, SlotGeometry.creativeContainerSlot(7));
        assertEquals(36, SlotGeometry.creativeContainerSlot(8));
    }

    @Test
    void creativeHotbarAndMainWrappersMapAsBefore() {
        assertEquals(3, SlotGeometry.creativeContainerSlot(39));
        assertEquals(0, SlotGeometry.creativeContainerSlot(36));
        assertEquals(12, SlotGeometry.creativeContainerSlot(12));
    }
}
