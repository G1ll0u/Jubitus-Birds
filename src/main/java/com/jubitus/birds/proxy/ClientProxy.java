package com.jubitus.birds.proxy;


import com.jubitus.birds.client.BirdManager;
import com.jubitus.birds.client.ClientBird;
import com.jubitus.birds.client.commands.CommandJubitusBirdsPlaySound;
import com.jubitus.birds.client.commands.CommandJubitusBirdsReload;
import com.jubitus.birds.client.sound.BirdSoundSystem;
import com.jubitus.birds.render.RenderBird;
import com.jubitus.birds.species.BirdSpeciesLoader;
import net.minecraftforge.client.ClientCommandHandler;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.client.registry.RenderingRegistry;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;

public class ClientProxy extends CommonProxy {

    @Override
    public void preInit(FMLPreInitializationEvent e) {
        MinecraftForge.EVENT_BUS.register(new BirdManager());

        RenderingRegistry.registerEntityRenderingHandler(ClientBird.class, manager -> new RenderBird(manager));

        BirdSoundSystem.installResourcePackOnce();
    }

    @Override
    public void init(FMLInitializationEvent e) {
        BirdSpeciesLoader.loadAllSpecies();
        BirdSoundSystem.reloadSoundsOnly();

        ClientCommandHandler.instance.registerCommand(new CommandJubitusBirdsReload());
        ClientCommandHandler.instance.registerCommand(new CommandJubitusBirdsPlaySound());
    }
}