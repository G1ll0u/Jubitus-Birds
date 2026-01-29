package com.jubitus.birds.client.gui;

import com.jubitus.birds.species.BirdSpecies;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.ITextureObject;
import net.minecraft.util.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

public final class ClientTextureReloader {

    private ClientTextureReloader() {
    }

    /**
     * Convenience: purge old list, set new list, then purge new list (covers renames/changes).
     */
    public static void replaceAndPurge(BirdSpecies s, List<ResourceLocation> newTextures) {
        if (s == null) return;

        // Purge old
        purge(s.textures);

        // Replace list
        s.textures = (newTextures != null) ? new ArrayList<>(newTextures) : new ArrayList<>();

        // Purge new too (in case it was previously cached)
        purge(s.textures);
    }

    /**
     * Purges these textures from MC texture cache so they get reloaded from disk/resource pack next bind.
     */
    public static void purge(List<ResourceLocation> tex) {
        if (tex == null || tex.isEmpty()) return;

        Minecraft mc = Minecraft.getMinecraft();
        for (ResourceLocation rl : tex) {
            if (rl == null) continue;
            ITextureObject obj = mc.getTextureManager().getTexture(rl);
            if (obj != null) {
                // delete GL texture + remove from map
                mc.getTextureManager().deleteTexture(rl);
            }
        }
    }
}
