package com.jubitus.birds.proxy;


import com.jubitus.birds.client.BirdManager;
import com.jubitus.birds.client.commands.CommandJubitusBirdsPlaySound;
import com.jubitus.birds.client.commands.CommandJubitusBirdsReload;
import com.jubitus.birds.client.sound.BirdSoundSystem;
import com.jubitus.birds.species.BirdSpeciesLoader;
import net.minecraftforge.client.ClientCommandHandler;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;

public class ClientProxy extends CommonProxy {

    @Override
    public void preInit(FMLPreInitializationEvent e) {
        MinecraftForge.EVENT_BUS.register(new BirdManager());

        // Install early so it gets picked up by the normal startup reload (no extra refresh)
        BirdSoundSystem.installResourcePackOnce();
    }


    @Override
    public void init(FMLInitializationEvent e) {
        // Load default_species (fills BirdSoundSystem pools + textures)
        BirdSpeciesLoader.loadAllSpecies();

        // Instead of Minecraft.getMinecraft().refreshResources();
        BirdSoundSystem.reloadSoundsOnly();

        ClientCommandHandler.instance.registerCommand(new CommandJubitusBirdsReload());
        ClientCommandHandler.instance.registerCommand(new CommandJubitusBirdsPlaySound());
    }


}

