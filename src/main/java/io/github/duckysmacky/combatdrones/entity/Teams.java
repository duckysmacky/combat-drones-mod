package io.github.duckysmacky.combatdrones.entity;

import com.mojang.authlib.GameProfile;
import io.github.duckysmacky.combatdrones.config.ModConfig;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.scoreboard.ScorePlayerTeam;
import net.minecraft.scoreboard.Team;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.fml.common.FMLCommonHandler;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * Who is on whose side, for everything that spares or shares with teammates (recon intel, the Pilot Detector, EMPs):
 * the same vanilla scoreboard team, unless that team is in ignoredTeams (a free-for-all game typically puts everyone
 * on one team, and then nobody counts as a teammate).
 */
public final class Teams {
    private Teams() {
    }

    /** Whether {@code player} is {@code owner} or on the owner's (not ignored) team. */
    public static boolean isTeammate(UUID owner, EntityPlayer player) {
        if (owner.equals(player.getUniqueID())) {
            return true;
        }
        Team team = player.getTeam();
        if (team == null || isIgnoredTeam(team.getName())) {
            return false;
        }
        String ownerName = nameOf(owner);
        return ownerName != null && team.getMembershipCollection().contains(ownerName);
    }

    /** The same for two player IDs, either of which may be offline. */
    public static boolean isTeammate(UUID owner, UUID other) {
        if (owner.equals(other)) {
            return true;
        }
        MinecraftServer server = FMLCommonHandler.instance().getMinecraftServerInstance();
        String ownerName = nameOf(owner);
        String otherName = nameOf(other);
        if (server == null || ownerName == null || otherName == null) {
            return false;
        }
        ScorePlayerTeam team = server.getWorld(0).getScoreboard().getPlayersTeam(otherName);
        return team != null && !isIgnoredTeam(team.getName()) && team.getMembershipCollection().contains(ownerName);
    }

    static boolean isIgnoredTeam(String teamName) {
        for (String ignored : ModConfig.ignoredTeams) {
            if (ignored.equalsIgnoreCase(teamName)) {
                return true;
            }
        }
        return false;
    }

    /** A player's name, even while they're offline (scoreboard teams are kept by name). */
    @Nullable
    private static String nameOf(UUID playerId) {
        MinecraftServer server = FMLCommonHandler.instance().getMinecraftServerInstance();
        if (server == null) {
            return null;
        }
        EntityPlayerMP online = server.getPlayerList().getPlayerByUUID(playerId);
        if (online != null) {
            return online.getName();
        }
        GameProfile profile = server.getPlayerProfileCache().getProfileByUUID(playerId);
        return profile == null ? null : profile.getName();
    }
}
