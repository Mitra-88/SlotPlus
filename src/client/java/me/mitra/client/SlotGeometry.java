package me.mitra.client;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.Slot;

final class SlotGeometry {
    private SlotGeometry() {
    }

    static boolean isPlayerSlot(Slot slot, boolean creative) {
        if (slot == null || !(slot.container instanceof Inventory)) return false;
        int raw = slot.getContainerSlot();
        if (creative) {
            if (raw >= 5 && raw < 9) return false;
            if (raw >= InventoryMenu.USE_ROW_SLOT_START && raw < InventoryMenu.USE_ROW_SLOT_END) return true;
            return raw >= InventoryMenu.INV_SLOT_START && Bindings.isBindable(raw);
        }
        return Bindings.isBindable(raw);
    }

    static int containerSlotOf(Slot slot, boolean creative) {
        int raw = slot.getContainerSlot();
        if (creative && raw >= InventoryMenu.USE_ROW_SLOT_START && raw < InventoryMenu.USE_ROW_SLOT_END) {
            return raw - InventoryMenu.USE_ROW_SLOT_START;
        }
        return raw;
    }

    static int menuSlotOfContainer(int containerSlot) {
        return Bindings.isHotbarSlot(containerSlot)
                ? InventoryMenu.USE_ROW_SLOT_START + containerSlot
                : containerSlot;
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
