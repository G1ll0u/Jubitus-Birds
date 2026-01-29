package com.jubitus.birds.client.gui;

import com.jubitus.birds.client.config.JubitusBirdsConfig;
import com.jubitus.jubitusbirds.Tags;
import net.minecraft.client.gui.GuiScreen;
import net.minecraftforge.common.config.ConfigElement;
import net.minecraftforge.fml.client.config.GuiConfig;
import net.minecraftforge.fml.client.config.IConfigElement;

import java.util.List;

public class JubitusMainConfigGui extends GuiConfig {

    public JubitusMainConfigGui(GuiScreen parent) {
        super(parent, getElements(), Tags.MOD_ID, false, false, Tags.MOD_NAME + " Options");
    }

    private static List<IConfigElement> getElements() {
        // Builds categories from @Config-annotated class
        return ConfigElement.from(JubitusBirdsConfig.class).getChildElements();
    }
}
