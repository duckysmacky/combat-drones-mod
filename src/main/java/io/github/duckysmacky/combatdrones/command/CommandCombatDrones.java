package io.github.duckysmacky.combatdrones.command;

import io.github.duckysmacky.combatdrones.CombatDrones;
import io.github.duckysmacky.combatdrones.config.ModConfig;
import io.github.duckysmacky.combatdrones.network.ModNetwork;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.List;

/**
 * {@code /combat_drones config_reload} (ops): re-reads {@code combat_drones.toml} in game and pushes the new drone
 * stats to every client, for quick tuning or switching settings between team and free-for-all games. Messages are
 * plain English so they read the same in the server console.
 */
public class CommandCombatDrones extends CommandBase {
    private static final String RELOAD = "config_reload";

    @Override
    public String getName() {
        return CombatDrones.MOD_ID;
    }

    @Override
    public String getUsage(ICommandSender sender) {
        return "/" + CombatDrones.MOD_ID + " " + RELOAD;
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 2;
    }

    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
        if (args.length != 1 || !RELOAD.equals(args[0])) {
            throw new WrongUsageException(getUsage(sender));
        }
        try {
            ModConfig.reload();
        } catch (RuntimeException e) {
            CombatDrones.logger.error("Config reload failed, keeping the previous values", e);
            sender.sendMessage(new TextComponentString(TextFormatting.RED + "Couldn't reload " + ModConfig.FILE_NAME
                    + ", keeping the previous values: " + e.getMessage()));
            return;
        }
        ModNetwork.syncTypesToAll();
        CombatDrones.logger.info("{} reloaded by {}", ModConfig.FILE_NAME, sender.getName());
        sender.sendMessage(new TextComponentString(TextFormatting.GREEN + ModConfig.FILE_NAME
                + " reloaded (invalid values fall back to defaults; see the server log)"));
    }

    @Override
    public List<String> getTabCompletions(MinecraftServer server, ICommandSender sender, String[] args, @Nullable BlockPos targetPos) {
        return args.length == 1 ? getListOfStringsMatchingLastWord(args, RELOAD) : Collections.emptyList();
    }
}
