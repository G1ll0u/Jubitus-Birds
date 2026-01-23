package com.jubitus.birds.proxy;

import com.jubitus.birds.client.BirdManager;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;

public class ClientProxy extends CommonProxy {

    @Override
    public void preInit(FMLPreInitializationEvent e) {
        MinecraftForge.EVENT_BUS.register(new BirdManager());
    }

    @Override
    public void init(FMLInitializationEvent e) {}
}
