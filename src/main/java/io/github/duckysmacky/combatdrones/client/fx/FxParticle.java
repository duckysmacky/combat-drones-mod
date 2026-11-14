package io.github.duckysmacky.combatdrones.client.fx;

import io.github.duckysmacky.combatdrones.CombatDrones;
import io.github.duckysmacky.combatdrones.Fx;
import net.minecraft.client.Minecraft;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.entity.Entity;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;
import net.minecraftforge.client.event.TextureStitchEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;

import java.util.Random;

/**
 * Every particle the mod draws itself: one class, told apart by {@link Fx}. Sprites are 16x16 pixel art
 * ({@code textures/particle/}, stitched into the block atlas); each kind has its own life, size curve, weight and
 * whether it glows. They follow the player's Particles setting: fewer on Decreased, only the bright ones on Minimal.
 */
@Mod.EventBusSubscriber(modid = CombatDrones.MOD_ID, value = Side.CLIENT)
public final class FxParticle extends Particle {
    private static final String[][] SPRITES = {
            {"smoke_0", "smoke_1", "smoke_2"}, {"smoke_0", "smoke_1", "smoke_2"}, {"fire_0", "fire_1", "fire_2"}, {"spark"},
            {"debris_0", "debris_1", "debris_2"}, {"dust_0", "dust_1"}, {"ring"}, {"flash"}};

    private final float startScale;
    private final float endScale;
    private final boolean glow;
    /** Share of its life after which it starts fading out. */
    private final float fadeFrom;
    private final float drag;

    private FxParticle(World world, Fx kind, double x, double y, double z, double velocityX, double velocityY, double velocityZ, float size) {
        super(world, x, y, z);
        Random random = world.rand;
        motionX = velocityX;
        motionY = velocityY;
        motionZ = velocityZ;
        String[] names = SPRITES[kind.ordinal()];
        TextureMap atlas = Minecraft.getMinecraft().getTextureMapBlocks();
        setParticleTexture(atlas.getAtlasSprite(CombatDrones.MOD_ID + ":particle/" + names[random.nextInt(names.length)]));
        float shade = 0.85F + random.nextFloat() * 0.15F;
        setRBGColorF(shade, shade, shade);
        canCollide = false;
        switch (kind) {
            case SMOKE:
                particleMaxAge = 24 + random.nextInt(24);
                particleGravity = -0.04F;
                startScale = size * 1.6F;
                endScale = size * 4.5F;
                glow = false;
                fadeFrom = 0.3F;
                drag = 0.92F;
                particleAlpha = 0.8F;
                break;
            case DARK_SMOKE:
                particleMaxAge = (int) ((70 + random.nextInt(90)) * Math.min(2.0F, Math.max(0.6F, size)));
                particleGravity = -0.02F;
                startScale = size * 2.2F;
                endScale = size * 8.0F;
                glow = false;
                fadeFrom = 0.5F;
                drag = 0.9F;
                setRBGColorF(shade * 0.42F, shade * 0.41F, shade * 0.42F);
                particleAlpha = 0.9F;
                break;
            case FIRE:
                particleMaxAge = 6 + random.nextInt(9);
                particleGravity = -0.03F;
                startScale = size * 2.0F;
                endScale = size * 3.4F;
                glow = true;
                fadeFrom = 0.45F;
                drag = 0.86F;
                setRBGColorF(1.0F, 1.0F, 1.0F);
                break;
            case SPARK:
                particleMaxAge = 8 + random.nextInt(14);
                particleGravity = 0.7F;
                startScale = size * 0.9F;
                endScale = size * 0.5F;
                glow = true;
                fadeFrom = 0.6F;
                drag = 0.97F;
                canCollide = true;
                setRBGColorF(1.0F, 1.0F, 1.0F);
                break;
            case DEBRIS:
                particleMaxAge = 30 + random.nextInt(40);
                particleGravity = 1.0F;
                startScale = size * (0.9F + random.nextFloat() * 0.8F);
                endScale = startScale;
                glow = false;
                fadeFrom = 0.8F;
                drag = 0.98F;
                canCollide = true;
                particleAngle = random.nextFloat() * 6.28F;
                break;
            case DUST:
                particleMaxAge = 18 + random.nextInt(22);
                particleGravity = 0.05F;
                startScale = size * 1.4F;
                endScale = size * 3.6F;
                glow = false;
                fadeFrom = 0.25F;
                drag = 0.88F;
                setRBGColorF(shade * 0.78F, shade * 0.72F, shade * 0.62F);
                particleAlpha = 0.7F;
                break;
            case RING:
                particleMaxAge = 7;
                particleGravity = 0;
                startScale = size * 2.0F;
                endScale = size * 26.0F;
                glow = true;
                fadeFrom = 0.0F;
                drag = 1.0F;
                particleAlpha = 0.7F;
                break;
            default:
                particleMaxAge = 2 + random.nextInt(2);
                particleGravity = 0;
                startScale = size * 5.0F;
                endScale = size * 7.0F;
                glow = true;
                fadeFrom = 0.3F;
                drag = 1.0F;
                setRBGColorF(1.0F, 1.0F, 1.0F);
                break;
        }
        particleScale = startScale;
    }

    /** Adds a particle, thinned out by the player's Particles setting. */
    public static void spawn(World world, Fx kind, double x, double y, double z, double velocityX, double velocityY, double velocityZ, float size) {
        Minecraft minecraft = Minecraft.getMinecraft();
        int setting = minecraft.gameSettings.particleSetting;
        boolean bright = kind == Fx.FIRE || kind == Fx.RING || kind == Fx.FLASH;
        if ((setting == 1 && world.rand.nextInt(2) == 0) || (setting == 2 && (!bright || world.rand.nextInt(3) != 0))) {
            return;
        }
        minecraft.effectRenderer.addEffect(new FxParticle(world, kind, x, y, z, velocityX, velocityY, velocityZ, size));
    }

    @SubscribeEvent
    public static void onTextureStitch(TextureStitchEvent.Pre event) {
        if (event.getMap() != Minecraft.getMinecraft().getTextureMapBlocks()) {
            return;
        }
        for (String[] names : SPRITES) {
            for (String name : names) {
                event.getMap().registerSprite(new ResourceLocation(CombatDrones.MOD_ID, "particle/" + name));
            }
        }
    }

    @Override
    public void onUpdate() {
        prevPosX = posX;
        prevPosY = posY;
        prevPosZ = posZ;
        prevParticleAngle = particleAngle;
        if (particleAge++ >= particleMaxAge) {
            setExpired();
            return;
        }
        motionY -= 0.04 * particleGravity;
        move(motionX, motionY, motionZ);
        motionX *= drag;
        motionY *= drag;
        motionZ *= drag;
        if (onGround) {
            motionX *= 0.6;
            motionZ *= 0.6;
        } else if (canCollide && particleGravity >= 1.0F) {
            particleAngle += 0.4F; // debris tumbles while it flies
        }
    }

    @Override
    public void renderParticle(BufferBuilder buffer, Entity viewer, float partialTicks, float rotationX, float rotationZ, float rotationYZ,
                               float rotationXY, float rotationXZ) {
        float life = Math.min(1.0F, (particleAge + partialTicks) / particleMaxAge);
        particleScale = startScale + (endScale - startScale) * life;
        float alpha = particleAlpha;
        if (life > fadeFrom) {
            particleAlpha = alpha * (1 - (life - fadeFrom) / (1 - fadeFrom));
        }
        super.renderParticle(buffer, viewer, partialTicks, rotationX, rotationZ, rotationYZ, rotationXY, rotationXZ);
        particleAlpha = alpha;
    }

    @Override
    public int getBrightnessForRender(float partialTicks) {
        return glow ? 0xF000F0 : super.getBrightnessForRender(partialTicks);
    }

    @Override
    public int getFXLayer() {
        return 1;
    }

    @Override
    public boolean shouldDisableDepth() {
        return true;
    }
}
