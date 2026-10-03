package io.github.duckysmacky.combatdrones.client;

import io.github.duckysmacky.combatdrones.ModContent;
import io.github.duckysmacky.combatdrones.entity.EntityRocket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.client.renderer.entity.RenderSnowball;

/** Rocket drawn as its item sprite (placeholder until the art pass). */
public class RenderRocket extends RenderSnowball<EntityRocket> {
    public RenderRocket(RenderManager renderManager) {
        super(renderManager, ModContent.rocket, Minecraft.getMinecraft().getRenderItem());
    }
}
