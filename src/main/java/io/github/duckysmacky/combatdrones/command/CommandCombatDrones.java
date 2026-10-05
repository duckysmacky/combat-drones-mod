package io.github.duckysmacky.combatdrones.command;

import io.github.duckysmacky.combatdrones.CombatDrones;
import io.github.duckysmacky.combatdrones.config.ModConfig;
import io.github.duckysmacky.combatdrones.network.ClientConfigReloadPacket;
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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * {@code /combat_drones config_reload} (ops): re-reads {@code combat_drones.toml} in game, pushes the new drone stats
 * and synced settings to every client and has every client re-read its own {@code [client]} options, for quick tuning
 * or switching settings between team and free-for-all games. {@code /combat_drones reload_client_config} (anyone):
 * just the sender's own {@code [client]} options. Messages are plain English so they read the same in the server
 * console.
 */
public class CommandCombatDrones extends CommandBase {
    private static final String RELOAD = "config_reload";
    private static final String RELOAD_CLIENT = "reload_client_config";
    private static final int OP_LEVEL = 2;

    @Override
    public String getName() {
        return CombatDrones.MOD_ID;
    }

    @Override
    public String getUsage(ICommandSender sender) {
        return "/" + CombatDrones.MOD_ID + " <" + RELOAD + "|" + RELOAD_CLIENT + ">";
    }

    /** Anyone may run it; config_reload checks for an op itself. */
    @Override
    public boolean checkPermission(MinecraftServer server, ICommandSender sender) {
        return true;
    }

    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
        if (args.length != 1) {
            throw new WrongUsageException(getUsage(sender));
        }
        if (RELOAD.equals(args[0])) {
            reloadAll(sender);
        } else if (RELOAD_CLIENT.equals(args[0])) {
            ModNetwork.CHANNEL.sendTo(new ClientConfigReloadPacket(), getCommandSenderAsPlayer(sender));
        } else {
            throw new WrongUsageException(getUsage(sender));
        }
    }

    private void reloadAll(ICommandSender sender) throws CommandException {
        if (!sender.canUseCommand(OP_LEVEL, getName())) {
            throw new CommandException("commands.generic.permission");
        }
        List<String> changes;
        try {
            changes = ModConfig.reload();
        } catch (RuntimeException e) {
            CombatDrones.logger.error("Config reload failed, keeping the previous values", e);
            sender.sendMessage(new TextComponentString(TextFormatting.RED + "Couldn't reload " + ModConfig.FILE_NAME
                    + ", keeping the previous values: " + e.getMessage()));
            return;
        }
        ModNetwork.syncTypesToAll();
        ModNetwork.CHANNEL.sendToAll(new ClientConfigReloadPacket());
        CombatDrones.logger.info("{} reloaded by {}, changed: {}", ModConfig.FILE_NAME, sender.getName(), changes);
        sender.sendMessage(new TextComponentString(TextFormatting.GREEN + ModConfig.FILE_NAME
                + " reloaded on the server and every client (invalid values fall back to defaults; see the server log)"));
        sender.sendMessage(new TextComponentString(changes.isEmpty() ? "No settings changed (drone, battery and bomb tables aren't listed)"
                : "Changed: " + String.join(", ", changes)));
    }

    @Override
    public List<String> getTabCompletions(MinecraftServer server, ICommandSender sender, String[] args, @Nullable BlockPos targetPos) {
        if (args.length != 1) {
            return Collections.emptyList();
        }
        List<String> options = new ArrayList<>();
        options.add(RELOAD_CLIENT);
        if (sender.canUseCommand(OP_LEVEL, getName())) {
            options.add(RELOAD);
        }
        return getListOfStringsMatchingLastWord(args, options);
    }
}
