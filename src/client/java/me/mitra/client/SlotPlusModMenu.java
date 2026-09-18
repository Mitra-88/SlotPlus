package me.mitra.client;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

@SuppressWarnings("unused")
public final class SlotPlusModMenu implements ModMenuApi {
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return parent -> SlotPlusConfig.HANDLER.generateGui().generateScreen(parent);
    }
}
