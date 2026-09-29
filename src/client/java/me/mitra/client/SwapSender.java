package me.mitra.client;

import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Util;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.InventoryMenu;

final class SwapSender {
    private static final int RATE_LIMIT_MS = 50;

    private final long[] lastSwapAt = new long[Bindings.MAX_SLOT + 1];

    boolean trySend(InventoryMenu menu, MultiPlayerGameMode gameMode, LocalPlayer player,
                    int clickedSlot, int button, ContainerInput input, int rateLimitKey) {
        if (isRateLimited(rateLimitKey)) return false;
        SlotPlusLog.info("click sent: containerId={} menuSlot={} button={} {}",
                menu.containerId, clickedSlot, button, input);
        gameMode.handleContainerInput(menu.containerId, clickedSlot, button, input, player);
        return true;
    }

    boolean trySendCreative(InventoryMenu menu, Player player, int menuSlot, int button, ContainerInput input, int rateLimitKey) {
        if (isRateLimited(rateLimitKey)) return false;
        SlotPlusLog.info("creative click: menuSlot={} button={} {} -> inventoryMenu.clicked() + broadcastChanges()",
                menuSlot, button, input);
        menu.clicked(menuSlot, button, input, player);
        menu.broadcastChanges();
        return true;
    }

    // Pairs without a hotbar side replay their full by-hand click sequence (PICKUPs)
    // from one input, spaced ~100 ms apart by the gesture's queue. One rate-limit
    // gate covers the whole action.
    boolean tryBeginAction(int rateLimitKey) {
        return !isRateLimited(rateLimitKey);
    }

    void sendActionClick(InventoryMenu menu, MultiPlayerGameMode gameMode, LocalPlayer player, int menuSlot) {
        SlotPlusLog.info("click sent: containerId={} menuSlot={} button=0 PICKUP", menu.containerId, menuSlot);
        gameMode.handleContainerInput(menu.containerId, menuSlot, 0, ContainerInput.PICKUP, player);
    }

    void sendCreativeActionClick(InventoryMenu menu, Player player, int menuSlot) {
        SlotPlusLog.info("creative action click: menuSlot={} button=0 PICKUP -> inventoryMenu.clicked() + broadcastChanges()", menuSlot);
        menu.clicked(menuSlot, 0, ContainerInput.PICKUP, player);
        menu.broadcastChanges();
    }

    private boolean isRateLimited(int rateLimitKey) {
        long now = Util.getMillis();
        long since = now - lastSwapAt[rateLimitKey];
        if (since < RATE_LIMIT_MS) {
            SlotPlusLog.info("rate limited: slot {} clicked {} ms ago (< {} ms) - click passes through to vanilla",
                    rateLimitKey, since, RATE_LIMIT_MS);
            return true;
        }
        lastSwapAt[rateLimitKey] = now;
        return false;
    }
}
