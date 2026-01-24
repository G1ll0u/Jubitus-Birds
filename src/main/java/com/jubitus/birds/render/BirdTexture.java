package com.jubitus.birds.render;


import com.jubitus.birds.client.ClientBird;
import net.minecraft.util.ResourceLocation;

public class BirdTexture {
    public static ResourceLocation get(ClientBird b) {
        return (b != null) ? b.texture : null;
    }
}