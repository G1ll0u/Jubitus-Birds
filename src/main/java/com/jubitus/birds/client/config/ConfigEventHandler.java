package com.jubitus.birds.client.config;


import com.jubitus.jubitusbirds.Tags;
import net.minecraftforge.common.config.Config;
import net.minecraftforge.common.config.ConfigManager;
import net.minecraftforge.fml.client.event.ConfigChangedEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

@Mod.EventBusSubscriber(modid = Tags.MOD_ID)
public class ConfigEventHandler {

    @SubscribeEvent
    public static void onConfigChanged(ConfigChangedEvent.OnConfigChangedEvent event) {
        if (!Tags.MOD_ID.equals(event.getModID())) return;

        // Pull values from the GUI into JubitusBirdsConfig.* fields
        ConfigManager.sync(Tags.MOD_ID, Config.Type.INSTANCE);

        // Copy annotated config -> runtime cache used by flight code
        BirdConfig.reloadFromGuiConfig();
    }
}
