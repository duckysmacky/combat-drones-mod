package io.github.duckysmacky.combatdrones.client.hud;

import io.github.duckysmacky.combatdrones.CombatDrones;
import io.github.duckysmacky.combatdrones.client.HudOverlay;
import io.github.duckysmacky.combatdrones.config.ModConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.EntityRenderer;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.Side;

import java.util.HashSet;
import java.util.Set;

/**
 * The picture grade of a camera feed: a vanilla post shader ({@code shaders/post/<feed>.json}) put on while the
 * player looks through that feed and taken off after. Vanilla drops any post shader when the view entity changes,
 * so it's checked every tick. Off with {@code [client] staticIntensity = 0}, in third person, or where shaders
 * aren't available (then {@link io.github.duckysmacky.combatdrones.client.StaticOverlay} draws what it can).
 */
@Mod.EventBusSubscriber(modid = CombatDrones.MOD_ID, value = Side.CLIENT)
public final class FeedShader {
    private static final Set<ResourceLocation> FAILED = new HashSet<>();
    private static ResourceLocation loaded;

    private FeedShader() {
    }

    /** True while a feed's grade is on the picture. */
    public static boolean isActive() {
        return loaded != null;
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        Minecraft minecraft = Minecraft.getMinecraft();
        if (event.phase != TickEvent.Phase.END || minecraft.world == null) {
            if (minecraft.world == null) {
                loaded = null;
            }
            return;
        }
        Feed feed = HudOverlay.currentFeed();
        ResourceLocation wanted = feed == null || feed.shader == null || ModConfig.staticIntensity <= 0 || !OpenGlHelper.shadersSupported
                || minecraft.gameSettings.thirdPersonView != 0 ? null : new ResourceLocation(CombatDrones.MOD_ID, "shaders/post/" + feed.shader + ".json");
        EntityRenderer renderer = minecraft.entityRenderer;
        if (wanted == null || FAILED.contains(wanted)) {
            if (loaded != null) {
                if (renderer.isShaderActive()) {
                    renderer.stopUseShader();
                }
                loaded = null;
            }
            return;
        }
        if (!wanted.equals(loaded) || !renderer.isShaderActive()) {
            renderer.loadShader(wanted);
            if (renderer.isShaderActive()) {
                loaded = wanted;
            } else {
                // It didn't compile or framebuffers are off: don't try again every tick
                FAILED.add(wanted);
                loaded = null;
                CombatDrones.logger.warn("Camera feed shader {} isn't available; using the plain picture", wanted);
            }
        }
    }
}
