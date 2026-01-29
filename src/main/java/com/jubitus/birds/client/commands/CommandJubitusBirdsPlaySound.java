package com.jubitus.birds.client.commands;

import com.jubitus.birds.client.ClientBird;
import com.jubitus.birds.client.config.BirdConfig;
import com.jubitus.birds.client.sound.BirdCallSound;
import com.jubitus.birds.client.sound.BirdCallType;
import com.jubitus.birds.client.sound.BirdSoundSystem;
import com.jubitus.birds.species.BirdSpeciesRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.SoundEvent;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.text.TextComponentString;
import net.minecraftforge.client.IClientCommand;

import javax.annotation.Nullable;

public class CommandJubitusBirdsPlaySound extends CommandBase implements IClientCommand {

    @Override
    public String getName() {
        return "jubitusbirdsplaysound";
    }

    @Override
    public String getUsage(ICommandSender sender) {
        return "/jubitusbirdsplaysound <speciesKey> [single|flock]";
    }

    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] args) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.world == null || mc.getSoundHandler() == null) {
            sender.sendMessage(new TextComponentString("§cNo world/sound handler."));
            return;
        }

        if (args.length < 1) {
            sender.sendMessage(new TextComponentString("§eUsage: " + getUsage(sender)));
            sender.sendMessage(new TextComponentString("§eLoaded default_species keys: " + BirdSoundSystem.getAllSpeciesKeys()));
            return;
        }

        String key = args[0].toLowerCase(java.util.Locale.ROOT);

        BirdCallType type = BirdCallType.SINGLE;
        if (args.length >= 2) {
            type = BirdCallType.fromString(args[1]);
        }

        if (!BirdSoundSystem.hasSounds(key, type)) {
            sender.sendMessage(new TextComponentString("§cNo sounds registered for speciesKey='" + key + "' type=" + type + "."));
            sender.sendMessage(new TextComponentString("§eLoaded default_species keys: " + BirdSoundSystem.getAllSpeciesKeys()));
            return;
        }
        ResourceLocation rl = BirdSoundSystem.getCallEvent(key, type);
        SoundEvent evt = BirdSoundSystem.getOrCreateEvent(rl);

// Fake bird that just follows player position (so you can test moving sound)
        ClientBird dummy = new ClientBird(
                mc.world,
                BirdSpeciesRegistry.pickForBiome(mc.world.getBiome(mc.player.getPosition()), new java.util.Random(), mc.world.isDaytime()),
                999999L,
                new Vec3d(mc.player.posX, mc.player.posY + mc.player.getEyeHeight(), mc.player.posZ),
                new Vec3d(0, 0, 1),
                0.0
        ) {
            @Override
            public long getId() {
                return 999999L;
            }
        };

// Use a sane test volume/pitch. Actual distance behavior comes from sounds.json attenuation_distance.
        float vol = (float) BirdConfig.masterBirdVolume;
        if (vol > (float) BirdConfig.masterBirdVolumeClamp) vol = (float) BirdConfig.masterBirdVolumeClamp;

        BirdCallSound sound = new BirdCallSound(
                dummy, mc.world, evt,
                vol, 1.0f,
                128.0f, 16.0f, 1.5f
        );


        mc.getSoundHandler().playSound(sound);

        sender.sendMessage(new TextComponentString("§aPlayed MOVING event=" + rl));
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 0;
    }

    @Override
    public boolean checkPermission(MinecraftServer server, ICommandSender sender) {
        return true;
    }

    @Override
    public java.util.List<String> getTabCompletions(MinecraftServer server, ICommandSender sender, String[] args,
                                                    @Nullable net.minecraft.util.math.BlockPos targetPos) {
        if (args.length == 1) {
            java.util.List<String> out = new java.util.ArrayList<>(BirdSoundSystem.getAllSpeciesKeys());
            return getListOfStringsMatchingLastWord(args, out);
        }
        return java.util.Collections.emptyList();
    }

    @Override
    public boolean allowUsageWithoutPrefix(ICommandSender sender, String message) {
        return false;
    }
}
