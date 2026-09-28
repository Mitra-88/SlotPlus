package me.mitra.client;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.Slot;

final class SlotGeometry {
    private SlotGeometry() {
    }

    static boolean isPlayerSlot(Slot slot, boolean creative) {
        return slot != null && SlotRole.of(slot, creative) == SlotRole.BINDABLE;
    }

    enum SlotRole {
        BINDABLE(null),
        OFFHAND("offhand"),
        CRAFTING("crafting"),
        OTHER(null);

        final String messageKey;

        SlotRole(String keySuffix) {
            this.messageKey = keySuffix == null ? null : SlotPlusClient.MOD_ID + ".msg." + keySuffix;
        }

        static SlotRole of(Slot slot, boolean creative) {
            if (!(slot.container instanceof Inventory)) return creative ? OTHER : CRAFTING;
            int raw = slot.getContainerSlot();
            if (creative) {
                // Creative player-tab wrappers report InventoryMenu slot indices as their raw number:
                // hotbar 36-44, armor 5-8, offhand 45 (crafting 0-4 aliases hotbar container space).
                if (raw >= InventoryMenu.ARMOR_SLOT_START && raw < InventoryMenu.ARMOR_SLOT_END) return BINDABLE;
                if (raw == InventoryMenu.SHIELD_SLOT) return OFFHAND;
                if (raw >= InventoryMenu.USE_ROW_SLOT_START && raw < InventoryMenu.USE_ROW_SLOT_END) return BINDABLE;
                return raw >= InventoryMenu.INV_SLOT_START && Bindings.isBindable(raw) ? BINDABLE : OTHER;
            }
            if (raw == Inventory.SLOT_OFFHAND) return OFFHAND;
            return Bindings.isBindable(raw) ? BINDABLE : OTHER;
        }
    }

    static int containerSlotOf(Slot slot, boolean creative) {
        int raw = slot.getContainerSlot();
        if (creative) {
            if (raw >= InventoryMenu.ARMOR_SLOT_START && raw < InventoryMenu.ARMOR_SLOT_END) {
                return raw - InventoryMenu.ARMOR_SLOT_START + Bindings.firstArmorSlot();
            }
            if (raw >= InventoryMenu.USE_ROW_SLOT_START && raw < InventoryMenu.USE_ROW_SLOT_END) {
                return raw - InventoryMenu.USE_ROW_SLOT_START;
            }
        }
        return raw;
    }

    static int menuSlotOfContainer(int containerSlot) {
        if (Bindings.isHotbarSlot(containerSlot)) {
            return InventoryMenu.USE_ROW_SLOT_START + containerSlot;
        }
        if (Bindings.isArmorSlot(containerSlot)) {
            return InventoryMenu.ARMOR_SLOT_START + containerSlot - Bindings.firstArmorSlot();
        }
        return containerSlot;
    }

    static Slot slotAt(AbstractContainerScreen<?> screen, double mouseX, double mouseY) {
        for (Slot slot : screen.getMenu().slots) {
            if (!slot.isActive()) continue;
            int x = slotX(screen, slot);
            int y = slotY(screen, slot);
            if (mouseX >= x && mouseX < x + 16 && mouseY >= y && mouseY < y + 16) {
                return slot;
            }
        }
        return null;
    }

    static Slot findSlot(AbstractContainerScreen<?> screen, int containerSlot, boolean creative) {
        for (Slot slot : screen.getMenu().slots) {
            if (!isPlayerSlot(slot, creative)) continue;
            if (containerSlotOf(slot, creative) == containerSlot) return slot;
        }
        return null;
    }

    static int slotX(AbstractContainerScreen<?> screen, Slot slot) {
        return screen.leftPos + slot.x;
    }

    static int slotY(AbstractContainerScreen<?> screen, Slot slot) {
        return screen.topPos + slot.y;
    }

    static int slotCenterX(AbstractContainerScreen<?> screen, Slot slot) {
        return slotX(screen, slot) + 8;
    }

    static int slotCenterY(AbstractContainerScreen<?> screen, Slot slot) {
        return slotY(screen, slot) + 8;
    }
}
