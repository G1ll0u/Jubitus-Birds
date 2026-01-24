package com.jubitus.birds.client.commands;

import com.jubitus.birds.JubitusBirds;
import com.jubitus.birds.client.BirdManager;
import com.jubitus.birds.species.BirdSpeciesLoader;
import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.text.TextComponentString;
import net.minecraftforge.client.IClientCommand;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.List;

public class CommandJubitusBirdsReload extends CommandBase implements IClientCommand {

    @Override
    public String getName() {
        return "jubitusbirdsreload";
    }

    @Override
    public String getUsage(ICommandSender sender) {
        return "/jubitusbirdsreload";
    }

    // ⭐ This is the key for TAB autocomplete visibility
    @Override
    public int getRequiredPermissionLevel() {
        return 0;
    }

    // ⭐ Also ensure permission checks always pass (safe for client-only commands)
    @Override
    public boolean checkPermission(MinecraftServer server, ICommandSender sender) {
        return true;
    }

    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] args) {
        BirdSpeciesLoader.loadAllSpecies();

        if (BirdManager.INSTANCE != null) {
            BirdManager.INSTANCE.clearAllBirds();
        }

        JubitusBirds.LOGGER.info("[JubitusBirds] Reloaded species via command.");
        sender.sendMessage(new TextComponentString("§a[JubitusBirds] Reloaded species + cleared birds."));
    }

    // Optional: no args to complete
    @Override
    public List<String> getTabCompletions(MinecraftServer server, ICommandSender sender, String[] args, @Nullable net.minecraft.util.math.BlockPos targetPos) {
        return Collections.emptyList();
    }

    // IClientCommand requirement
    @Override
    public boolean allowUsageWithoutPrefix(ICommandSender sender, String message) {
        return false; // keep it as /jubitusbirdsreload
    }
}
