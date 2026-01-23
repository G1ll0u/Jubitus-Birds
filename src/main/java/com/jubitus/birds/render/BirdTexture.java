package com.jubitus.birds.render;


import com.jubitus.jubitusbirds.Tags;
import net.minecraft.util.ResourceLocation;

public class BirdTexture {
    public static ResourceLocation get(int idx) {
        return new ResourceLocation(Tags.MOD_ID, "textures/entity/bird0.png");
    }
}