package io.github.duckysmacky.combatdrones.client;

import io.github.duckysmacky.combatdrones.ModContent;
import io.github.duckysmacky.combatdrones.entity.EntityBomb;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.client.renderer.entity.RenderSnowball;
import net.minecraft.item.ItemStack;

/** Falling bomb drawn as its item sprite (placeholder until the art pass), picked by bomb size. */
public class RenderBomb extends RenderSnowball<EntityBomb> {
    public RenderBomb(RenderManager renderManager) {
        // The item passed here is unused: getStackToRender picks one per entity
        super(renderManager, ModContent.bomb, Minecraft.getMinecraft().getRenderItem());
    }

    @Override
    public ItemStack getStackToRender(EntityBomb bomb) {
        return new ItemStack(ModContent.bombItem(bomb.getBombType()));
    }
}
