package com.jubitus.birds.proxy;

import com.jubitus.birds.client.BirdManager;
import com.jubitus.birds.client.commands.CommandJubitusBirdsReload;
import com.jubitus.birds.species.BirdSpeciesLoader;
import net.minecraftforge.client.ClientCommandHandler;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;

public class ClientProxy extends CommonProxy {

    @Override
    public void preInit(FMLPreInitializationEvent e) {
        // Register manager early (fine)
        MinecraftForge.EVENT_BUS.register(new BirdManager());
    }

    @Override
    public void init(FMLInitializationEvent e) {
        // Load species + register dynamic textures AFTER MC is initialized enough
        BirdSpeciesLoader.loadAllSpecies();
        ClientCommandHandler.instance.registerCommand(new CommandJubitusBirdsReload());
    }
}

