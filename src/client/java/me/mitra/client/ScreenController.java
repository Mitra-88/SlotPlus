package me.mitra.client;

import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;

final class ScreenController {
    ScreenController(AbstractContainerScreen<?> screen, Minecraft minecraft) {
        BindGesture gesture = new BindGesture(screen, minecraft);
        BindingOverlay overlay = new BindingOverlay(screen, gesture);
        ScreenMouseEvents.allowMouseClick(screen).register((_, event) -> gesture.onMouseClick(event));
        ScreenMouseEvents.allowMouseRelease(screen).register((_, event) -> gesture.onMouseRelease(event));
        ScreenKeyboardEvents.afterKeyPress(screen).register((_, event) -> gesture.onKeyPress(event));
        ScreenKeyboardEvents.afterKeyRelease(screen).register((_, event) -> gesture.onKeyRelease(event));
        ScreenEvents.afterForeground(screen).register((_, graphics, mouseX, mouseY, _) ->
                overlay.render(graphics, mouseX, mouseY));
        ScreenEvents.remove(screen).register(_ -> gesture.reset());
    }
}
