package com.jubitus.birds;

import com.jubitus.birds.client.config.BirdConfig;
import com.jubitus.birds.proxy.CommonProxy;
import com.jubitus.jubitusbirds.Tags;

import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.SidedProxy;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

@Mod(modid = Tags.MOD_ID, name = Tags.MOD_NAME, version = Tags.VERSION)
public class JubitusBirds {
    public static final Logger LOGGER = LogManager.getLogger(Tags.MOD_NAME);

    @SidedProxy(
            clientSide = "com.jubitus.birds.proxy.ClientProxy",
            serverSide = "com.jubitus.birds.proxy.CommonProxy"
    )
    public static CommonProxy proxy;


    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        LOGGER.info("Hello From {}!", Tags.MOD_NAME);

        deleteOldRootConfig(event);

        // global config cache (your current system)
        BirdConfig.reloadFromGuiConfig();

        proxy.preInit(event);
    }



    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        proxy.init(event);
    }
    private void deleteOldRootConfig(FMLPreInitializationEvent event) {
        try {
            File configDir = event.getModConfigurationDirectory(); // /config
            Path oldCfg = new File(configDir, "jubitusbirds.cfg").toPath();
            Path oldLock = new File(configDir, "jubitusbirds.cfg.lock").toPath();
            Path oldBak = new File(configDir, "jubitusbirds.cfg.bak").toPath();

            // Only delete if it actually exists
            boolean deletedAny = false;

            if (Files.exists(oldCfg)) {
                Files.delete(oldCfg);
                deletedAny = true;
                LOGGER.info("[JubitusBirds] Deleted old config: {}", oldCfg.toAbsolutePath());
            }
            if (Files.exists(oldLock)) {
                Files.delete(oldLock);
                deletedAny = true;
                LOGGER.info("[JubitusBirds] Deleted old config lock: {}", oldLock.toAbsolutePath());
            }
            if (Files.exists(oldBak)) {
                Files.delete(oldBak);
                deletedAny = true;
                LOGGER.info("[JubitusBirds] Deleted old config backup: {}", oldBak.toAbsolutePath());
            }

            if (!deletedAny) {
                LOGGER.info("[JubitusBirds] No old root config to delete.");
            }

        } catch (Exception e) {
            LOGGER.warn("[JubitusBirds] Failed deleting old root config files.", e);
        }
    }

}

