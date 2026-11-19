package io.github.duckysmacky.combatdrones.client;

import io.github.duckysmacky.combatdrones.CombatDrones;
import io.github.duckysmacky.combatdrones.Fx;
import io.github.duckysmacky.combatdrones.client.fx.FxParticle;
import io.github.duckysmacky.combatdrones.client.fx.Impact;
import io.github.duckysmacky.combatdrones.client.fx.ScanWave;
import io.github.duckysmacky.combatdrones.ModSounds;
import io.github.duckysmacky.combatdrones.entity.EntityRampUnit;
import io.github.duckysmacky.combatdrones.network.EffectPacket;
import io.github.duckysmacky.combatdrones.network.GunFirePacket;
import io.github.duckysmacky.combatdrones.physics.SoundBands;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.ISound;
import net.minecraft.client.audio.PositionedSoundRecord;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

/** Client-built effects: explosions (near/far sound with a speed-of-sound delay), catapult launches, gunfire. */
public final class Effects {
    /** A missile liftoff closer than this is the roar itself; further out, the delayed rumble. */
    private static final double NEAR_DISTANCE = 64;
    /** Blast layers by size (small, medium, large), crossfaded by distance like the engines (see {@link SoundBands}). */
    private static final String[][] BLAST_EVENTS = {
            {"explosion.small.near", "explosion.small.far"},
            {"explosion.medium.near", "explosion.medium.far"},
            {"explosion.large.near", "explosion.large.mid", "explosion.large.far"}};
    private static final double[][] BLAST_UNTILS = {{0.3, 1}, {0.3, 1}, {0.3, 0.65, 1}};
    private static final double[] TWO_BANDS = {0.3, 1};
    private static final double BLAST_CROSSFADE = 0.2;
    private static final double PARTICLE_DISTANCE = 192;
    /** Rounds passing closer than this crack overhead... */
    private static final double FLYBY_DISTANCE = 5;
    /** ...and whiz past out to this, fainter further off: being shot at is heard, not just the hits. */
    private static final double WHIZ_DISTANCE = 16;

    private static final List<Pending> PENDING = new ArrayList<>();

    private Effects() {
    }

    public static void onEffect(EffectPacket packet) {
        World world = Minecraft.getMinecraft().world;
        EntityPlayer player = Minecraft.getMinecraft().player;
        if (world == null || player == null) {
            return;
        }
        switch (packet.getKind()) {
            case CANNON_SHOT:
            case HOWITZER_SHOT:
                gunShot(player.getPositionEyes(1), packet.getPosition(), packet.getKind() == EffectPacket.Kind.HOWITZER_SHOT, world.rand);
                recoil(world, packet.getPosition(), packet.getKind() == EffectPacket.Kind.HOWITZER_SHOT ? "recoil_howitzer" : "recoil_cannon");
                muzzleBlast(world, player, packet.getPosition(), packet.getKind() == EffectPacket.Kind.HOWITZER_SHOT);
                break;
            case EXPLOSION:
            case AIR_EXPLOSION:
                explosion(world, player, packet.getPosition(), packet.getScale(), packet.getKind() == EffectPacket.Kind.AIR_EXPLOSION);
                break;
            case LAUNCH:
                launch(world, packet.getPosition(), packet.getDirection());
                RenderLaunchRamp.launched(world, packet.getPosition());
                break;
            case MISSILE_LIFTOFF:
                missileLiftoff(world, player, packet.getPosition());
                break;
            case STRIKE_ALERT:
                // Not positional: a siren in your head, wherever the missile is
                play(new PositionedSoundRecord(ModSounds.MISSILE_ALARM.getSoundName(), SoundCategory.MASTER, 1.0F, 1.0F, false, 0,
                        ISound.AttenuationType.NONE, 0, 0, 0), 0);
                HudOverlay.showStrikeAlert();
                break;
            case SCAN:
                ScanWave.start(packet.getPosition(), packet.getScale());
                break;
            case EMP_ZAP:
                empZap(world, packet.getPosition(), packet.getScale());
                break;
            case EMP_BLAST:
                empBlast(world, player, packet.getPosition(), packet.getScale());
                break;
            case STINGER_LAUNCH:
                stingerLaunch(world, player, packet.getPosition(), packet.getDirection());
                break;
            case EMP_GUN_FIRE:
                empGunFire(world, player, packet.getPosition(), packet.getDirection());
                break;
        }
    }

    private static void explosion(World world, EntityPlayer player, Vec3d at, float power, boolean airBurst) {
        double distance = player.getPositionEyes(1).distanceTo(at);
        blastSound(player.getPositionEyes(1), at, power, airBurst, 0.9F + world.rand.nextFloat() * 0.2F - Math.min(0.25F, power / 40));
        Impact.blast(power, distance);
        if (distance > PARTICLE_DISTANCE) {
            return;
        }
        Random random = world.rand;
        double radius = 0.4 + power * 0.25;
        float size = 0.6F + power * 0.12F;
        // A flash and a shockwave ring, a fireball, sparks thrown wide, then smoke: grey rolling out, soot climbing
        fx(world, Fx.FLASH, at, Vec3d.ZERO, size * 1.6F);
        fx(world, Fx.RING, at, Vec3d.ZERO, size);
        for (int index = 0; index < power * 6; index++) {
            Vec3d out = randomDirection(random);
            double speed = (0.08 + random.nextDouble() * 0.16) * Math.sqrt(power);
            fx(world, Fx.FIRE, at.add(out.scale(radius * 0.3)), out.scale(speed * 0.5), size);
        }
        for (int index = 0; index < power * 5; index++) {
            Vec3d out = randomDirection(random);
            double speed = (0.25 + random.nextDouble() * 0.45) * Math.sqrt(power);
            fx(world, Fx.SPARK, at, new Vec3d(out.x * speed, Math.abs(out.y) * speed + 0.1, out.z * speed), 0.8F);
        }
        for (int index = 0; index < power * 6; index++) {
            Vec3d out = randomDirection(random);
            fx(world, Fx.SMOKE, new Vec3d(at.x + out.x * radius, at.y + Math.abs(out.y) * radius, at.z + out.z * radius),
                    new Vec3d(out.x * 0.12, 0.03 + random.nextDouble() * 0.08, out.z * 0.12), size);
        }
        for (int index = 0; index < power * 3; index++) {
            Vec3d out = randomDirection(random);
            fx(world, Fx.DARK_SMOKE, new Vec3d(at.x + out.x * radius * 0.6, at.y + Math.abs(out.y) * radius, at.z + out.z * radius * 0.6),
                    new Vec3d(out.x * 0.03, 0.06 + random.nextDouble() * 0.1, out.z * 0.03), size);
        }
        IBlockState ground = world.getBlockState(new BlockPos(at).down());
        if (ground.getMaterial().isSolid()) {
            int state = Block.getStateId(ground);
            for (int index = 0; index < power * 10; index++) {
                Vec3d out = randomDirection(random);
                world.spawnParticle(EnumParticleTypes.BLOCK_DUST, true, at.x, at.y + 0.2, at.z,
                        out.x * 0.4, 0.3 + random.nextDouble() * 0.5, out.z * 0.4, state);
            }
            // Chunks thrown up and a skirt of dust along the ground
            for (int index = 0; index < power * 3; index++) {
                Vec3d out = randomDirection(random);
                fx(world, Fx.DEBRIS, at.add(0, 0.2, 0), new Vec3d(out.x * 0.35, 0.35 + random.nextDouble() * 0.5, out.z * 0.35), 1.0F);
                fx(world, Fx.DUST, at.add(out.x * radius, 0.15, out.z * radius), new Vec3d(out.x * 0.2, 0.02, out.z * 0.2), size);
            }
        }
    }

    /**
     * A cruise missile leaving its pad: a positional roar up close, a delayed rumble far out (like explosions), and a
     * cloud of exhaust rolling out across the ground round the pad.
     */
    private static void missileLiftoff(World world, EntityPlayer player, Vec3d pad) {
        double distance = player.getPositionEyes(1).distanceTo(pad);
        if (distance < NEAR_DISTANCE * 2) {
            play(new PositionedSoundRecord(ModSounds.MISSILE_LIFTOFF, SoundCategory.NEUTRAL, 16.0F, 1.0F,
                    (float) pad.x, (float) pad.y, (float) pad.z), 0);
        } else {
            float volume = (float) Math.max(0.15, (1 - distance / EffectPacket.EXPLOSION_RANGE) * 0.9);
            play(new PositionedSoundRecord(new ResourceLocation(CombatDrones.MOD_ID, "explosion.large.far"), SoundCategory.NEUTRAL, volume, 0.6F,
                    false, 0, ISound.AttenuationType.NONE, (float) pad.x, (float) pad.y, (float) pad.z), (int) (distance / SoundBands.SOUND_SPEED));
        }
        if (distance > PARTICLE_DISTANCE) {
            return;
        }
        Impact.blast(4, distance);
        Random random = world.rand;
        fx(world, Fx.FLASH, pad.add(0, 0.5, 0), Vec3d.ZERO, 2.0F);
        for (int index = 0; index < 70; index++) {
            double angle = random.nextDouble() * Math.PI * 2;
            double speed = 0.25 + random.nextDouble() * 0.35;
            fx(world, index % 3 == 0 ? Fx.DARK_SMOKE : Fx.SMOKE, pad.add(0, 0.3, 0),
                    new Vec3d(Math.cos(angle) * speed, 0.02 + random.nextDouble() * 0.05, Math.sin(angle) * speed), 1.6F);
        }
        for (int index = 0; index < 24; index++) {
            fx(world, Fx.FIRE, pad.add(random.nextDouble() - 0.5, 0.2, random.nextDouble() - 0.5),
                    new Vec3d((random.nextDouble() - 0.5) * 0.3, 0.02, (random.nextDouble() - 0.5) * 0.3), 1.2F);
        }
    }

    /** A vehicle fried by an EMP: arcs crawling all over it, a shower of sparks, the crack of the discharge. */
    private static void empZap(World world, Vec3d at, float size) {
        Random random = world.rand;
        for (int index = 0; index < 6 + size * 6; index++) {
            Vec3d out = randomDirection(random).scale(size * (0.3 + random.nextDouble() * 0.5));
            fx(world, Fx.ARC, at.add(out), Vec3d.ZERO, 0.4F + size * 0.25F);
        }
        for (int index = 0; index < 8; index++) {
            Vec3d out = randomDirection(random);
            fx(world, Fx.SPARK, at, new Vec3d(out.x * 0.3, Math.abs(out.y) * 0.3 + 0.1, out.z * 0.3), 0.6F);
        }
        play(new PositionedSoundRecord(ModSounds.EMP_ZAP, SoundCategory.NEUTRAL, 2.0F, 0.9F + random.nextFloat() * 0.2F,
                (float) at.x, (float) at.y, (float) at.z), 0);
    }

    /** An EMP blast going off: a ring and a burst of arcs, the discharge's crack, a jolt close in. */
    private static void empBlast(World world, EntityPlayer player, Vec3d at, float radius) {
        Random random = world.rand;
        fx(world, Fx.RING, at, Vec3d.ZERO, 0.5F + radius * 0.08F);
        for (int index = 0; index < 24; index++) {
            Vec3d out = randomDirection(random).scale(0.4 + random.nextDouble() * 1.4);
            fx(world, Fx.ARC, at.add(out), Vec3d.ZERO, 0.8F + random.nextFloat() * 0.6F);
        }
        for (int index = 0; index < 14; index++) {
            Vec3d out = randomDirection(random);
            fx(world, Fx.SPARK, at, new Vec3d(out.x * 0.45, Math.abs(out.y) * 0.4 + 0.1, out.z * 0.45), 0.7F);
        }
        play(new PositionedSoundRecord(ModSounds.EMP_BLAST, SoundCategory.PLAYERS, 4.0F, 0.95F + random.nextFloat() * 0.1F,
                (float) at.x, (float) at.y, (float) at.z), 0);
        double distance = player.getPositionEyes(1).distanceTo(at);
        if (distance < radius * 2) {
            Impact.shake((float) (0.35 * (1 - distance / (radius * 2))));
        }
    }

    /**
     * A Stinger firing: the motor lighting at the muzzle, and the backblast thrown out of the back of the tube, some
     * two blocks behind it. A jolt for whoever stands close.
     */
    private static void stingerLaunch(World world, EntityPlayer player, Vec3d muzzle, Vec3d direction) {
        Random random = world.rand;
        Vec3d breech = muzzle.subtract(direction.scale(2.4));
        fx(world, Fx.FLASH, muzzle, Vec3d.ZERO, 0.9F);
        fx(world, Fx.FLASH, breech, Vec3d.ZERO, 0.7F);
        for (int index = 0; index < 6; index++) {
            fx(world, Fx.SMOKE, muzzle, direction.scale(0.1 + random.nextDouble() * 0.15).add(randomDirection(random).scale(0.04)), 0.7F);
        }
        for (int index = 0; index < 14; index++) {
            Vec3d back = direction.scale(-(0.15 + random.nextDouble() * 0.45)).add(randomDirection(random).scale(0.08));
            fx(world, index % 3 == 0 ? Fx.FIRE : Fx.SMOKE, breech, back, 0.8F + random.nextFloat() * 0.5F);
        }
        for (int index = 0; index < 5; index++) {
            fx(world, Fx.SPARK, breech, direction.scale(-(0.4 + random.nextDouble() * 0.4)).add(randomDirection(random).scale(0.15)), 0.6F);
        }
        double distance = player.getPositionEyes(1).distanceTo(muzzle);
        if (distance < 6) {
            Impact.shake((float) (0.3 * (1 - distance / 6)));
        }
    }

    /** An EMP Gun firing: a flash and arcs snapping out of the emitter, sparks thrown ahead, a jolt close by. */
    private static void empGunFire(World world, EntityPlayer player, Vec3d muzzle, Vec3d direction) {
        Random random = world.rand;
        fx(world, Fx.FLASH, muzzle, Vec3d.ZERO, 0.8F);
        for (int index = 0; index < 10; index++) {
            fx(world, Fx.ARC, muzzle.add(randomDirection(random).scale(0.2 + random.nextDouble() * 0.5)), Vec3d.ZERO, 0.4F + random.nextFloat() * 0.3F);
        }
        for (int index = 0; index < 6; index++) {
            fx(world, Fx.SPARK, muzzle, direction.scale(0.3 + random.nextDouble() * 0.3).add(randomDirection(random).scale(0.12)), 0.5F);
        }
        double distance = player.getPositionEyes(1).distanceTo(muzzle);
        if (distance < 6) {
            Impact.shake((float) (0.25 * (1 - distance / 6)));
        }
    }

    private static void fx(World world, Fx kind, Vec3d at, Vec3d velocity, float size) {
        FxParticle.spawn(world, kind, at.x, at.y, at.z, velocity.x, velocity.y, velocity.z, size);
    }

    /** A cannon or howitzer firing: a flash and smoke at the gun, and a jolt for the crew. */
    private static void muzzleBlast(World world, EntityPlayer player, Vec3d at, boolean howitzer) {
        float size = howitzer ? 1.5F : 0.9F;
        fx(world, Fx.FLASH, at, Vec3d.ZERO, size);
        for (int index = 0; index < (howitzer ? 8 : 4); index++) {
            Vec3d out = randomDirection(world.rand);
            fx(world, index % 2 == 0 ? Fx.FIRE : Fx.SMOKE, at, out.scale(0.08), size * 0.7F);
        }
        if (player.getPositionEyes(1).distanceTo(at) < 12) {
            Impact.shake(howitzer ? 0.4F : 0.2F);
        }
    }

    /** A gunship gun fired from this point: the nearest unit's barrel kicks back. */
    private static void recoil(World world, Vec3d at, String bone) {
        EntityRampUnit nearest = null;
        for (EntityRampUnit unit : world.getEntitiesWithinAABB(EntityRampUnit.class, new AxisAlignedBB(at, at).grow(8))) {
            if (nearest == null || unit.getDistanceSq(at.x, at.y, at.z) < nearest.getDistanceSq(at.x, at.y, at.z)) {
                nearest = unit;
            }
        }
        if (nearest != null) {
            RenderRampUnit.kick(nearest, bone);
        }
    }

    /** Catapult shot: a burst of compressed air at the back of the rail and a puff along it. */
    private static void launch(World world, Vec3d ramp, Vec3d heading) {
        Random random = world.rand;
        for (int index = 0; index < 16; index++) {
            double along = -1.8 + random.nextDouble() * 3.6;
            Vec3d at = ramp.add(heading.scale(along)).add(0, 0.4 + (along + 1.8) * 0.27, 0);
            fx(world, index < 6 ? Fx.SMOKE : Fx.DUST, at,
                    new Vec3d(-heading.x * 0.1 + (random.nextDouble() - 0.5) * 0.1, 0.03, -heading.z * 0.1 + (random.nextDouble() - 0.5) * 0.1), 0.9F);
        }
    }

    /** Muzzle flash, tracer sparks, the impact (dust of whatever was hit + sound) and a crack for anyone it passed. */
    public static void gunFire(GunFirePacket packet) {
        World world = Minecraft.getMinecraft().world;
        EntityPlayer player = Minecraft.getMinecraft().player;
        if (world == null || player == null) {
            return;
        }
        Random random = world.rand;
        Vec3d from = packet.getFrom();
        Vec3d to = packet.getTo();
        Vec3d direction = to.subtract(from).normalize();
        fx(world, Fx.FLASH, from, Vec3d.ZERO, 0.5F);
        fx(world, Fx.SMOKE, from, new Vec3d(direction.x * 0.05, 0.02, direction.z * 0.05), 0.4F);
        double length = from.distanceTo(to);
        Impact.tracer(from, to);
        if (packet.isImpact()) {
            IBlockState state = world.getBlockState(new BlockPos(to.add(direction.scale(0.1))));
            for (int index = 0; index < 6; index++) {
                if (state.getMaterial().isSolid()) {
                    world.spawnParticle(EnumParticleTypes.BLOCK_DUST, to.x, to.y, to.z, (random.nextDouble() - 0.5) * 0.2,
                            0.1 + random.nextDouble() * 0.2, (random.nextDouble() - 0.5) * 0.2, Block.getStateId(state));
                }
                if (index < 3) {
                    fx(world, Fx.SPARK, to, new Vec3d((random.nextDouble() - 0.5) * 0.4, random.nextDouble() * 0.3, (random.nextDouble() - 0.5) * 0.4), 0.5F);
                }
            }
            fx(world, Fx.DUST, to, new Vec3d(0, 0.04, 0), 0.5F);
            play(new PositionedSoundRecord(ModSounds.BULLET_IMPACT, SoundCategory.NEUTRAL, 1.5F, 0.85F + random.nextFloat() * 0.3F,
                    (float) to.x, (float) to.y, (float) to.z), 0);
        }
        Entity shooter = world.getEntityByID(packet.getShooter());
        if (shooter != null) {
            RenderRampUnit.kick(shooter, "recoil_gun");
            if (player.getRidingEntity() == shooter) {
                Impact.shake(0.12F);
            }
        }
        if (shooter == null || player.getRidingEntity() != shooter) {
            Vec3d ear = player.getPositionEyes(1);
            double along = Math.max(0, Math.min(length, ear.subtract(from).dotProduct(direction)));
            Vec3d closest = from.add(direction.scale(along));
            double miss = closest.distanceTo(ear);
            if (along > 3 && miss < FLYBY_DISTANCE) {
                play(new PositionedSoundRecord(ModSounds.BULLET_FLYBY, SoundCategory.NEUTRAL, 1.0F, 0.9F + random.nextFloat() * 0.2F,
                        (float) closest.x, (float) closest.y, (float) closest.z), 0);
            } else if (along > 3 && miss < WHIZ_DISTANCE) {
                float volume = (float) (0.9 - 0.65 * (miss - FLYBY_DISTANCE) / (WHIZ_DISTANCE - FLYBY_DISTANCE));
                play(new PositionedSoundRecord(new ResourceLocation(CombatDrones.MOD_ID, "bullet.whiz"), SoundCategory.NEUTRAL, volume,
                        0.85F + random.nextFloat() * 0.3F, false, 0, ISound.AttenuationType.NONE, (float) closest.x, (float) closest.y,
                        (float) closest.z), 0);
            }
        }
    }

    private static Vec3d randomDirection(Random random) {
        Vec3d direction = new Vec3d(random.nextGaussian(), random.nextGaussian(), random.nextGaussian());
        return direction.lengthSquared() < 1.0E-6 ? new Vec3d(0, 1, 0) : direction.normalize();
    }

    /**
     * A blast's sound layers for this listener: close crack, mid boom, far rumble by distance over a range that grows
     * with the blast, all arriving at the speed of sound. An air burst's far layers are its distant boom; from indoors
     * it's the muffled thump through the walls instead.
     */
    private static void blastSound(Vec3d listener, Vec3d at, float power, boolean airBurst, float pitch) {
        double distance = listener.distanceTo(at);
        double fraction = distance / (EffectPacket.EXPLOSION_RANGE * Math.min(1, 0.35 + 0.065 * power));
        if (fraction >= 1) {
            return;
        }
        int size = power < 3 ? 0 : power < 6 ? 1 : 2;
        boolean indoors = DroneSounds.isIndoors(listener);
        String[] events = indoors ? new String[]{"explosion.indoor.near", "explosion.indoor.far"}
                : airBurst ? new String[]{BLAST_EVENTS[size][0], "explosion.air.far"} : BLAST_EVENTS[size];
        double[] gains = SoundBands.bandGains(fraction, indoors || airBurst ? TWO_BANDS : BLAST_UNTILS[size], BLAST_CROSSFADE);
        // Booms carry: a gentler falloff than engines, louder for bigger blasts
        double loudness = Math.sqrt(SoundBands.distanceGain(fraction)) * Math.min(1, 0.6 + power / 15) * (indoors ? 0.7 : 1);
        float bandPitch = pitch * (float) SoundBands.distancePitch(fraction);
        for (int band = 0; band < events.length; band++) {
            double volume = gains[band] * loudness;
            if (volume > 0.002) {
                play(new PositionedSoundRecord(new ResourceLocation(CombatDrones.MOD_ID, events[band]), SoundCategory.BLOCKS, (float) volume, bandPitch,
                        false, 0, ISound.AttenuationType.NONE, (float) at.x, (float) at.y, (float) at.z), (int) (distance / SoundBands.SOUND_SPEED));
            }
        }
    }

    /**
     * A gunship gun's report for this listener: the sharp shot close in, a deep, delayed boom further out (the
     * howitzer heavier and heard much further), from where the plane is.
     */
    private static void gunShot(Vec3d listener, Vec3d at, boolean howitzer, Random random) {
        double distance = listener.distanceTo(at);
        double fraction = distance / (howitzer ? EffectPacket.HOWITZER_RANGE : EffectPacket.CANNON_RANGE);
        if (fraction >= 1) {
            return;
        }
        String[] events = howitzer ? new String[]{"gunship.howitzer", "gunship.howitzer_far"} : new String[]{"gunship.cannon", "gunship.cannon_far"};
        double[] gains = SoundBands.bandGains(fraction, TWO_BANDS, BLAST_CROSSFADE);
        double loudness = Math.sqrt(SoundBands.distanceGain(fraction)) * (DroneSounds.isIndoors(listener) ? 0.7 : 1);
        float pitch = (0.95F + random.nextFloat() * 0.1F) * (float) SoundBands.distancePitch(fraction);
        for (int band = 0; band < events.length; band++) {
            double volume = gains[band] * loudness;
            if (volume > 0.002) {
                play(new PositionedSoundRecord(new ResourceLocation(CombatDrones.MOD_ID, events[band]), SoundCategory.NEUTRAL, (float) volume, pitch,
                        false, 0, ISound.AttenuationType.NONE, (float) at.x, (float) at.y, (float) at.z), (int) (distance / SoundBands.SOUND_SPEED));
            }
        }
    }

    private static void play(ISound sound, int delayTicks) {
        if (delayTicks <= 0) {
            Minecraft.getMinecraft().getSoundHandler().playSound(sound);
        } else {
            PENDING.add(new Pending(sound, delayTicks));
        }
    }

    /** Client tick: plays delayed sounds when they're due; drops them on leaving the world. */
    static void tick(boolean inWorld) {
        if (!inWorld) {
            PENDING.clear();
            return;
        }
        for (Iterator<Pending> iterator = PENDING.iterator(); iterator.hasNext(); ) {
            Pending pending = iterator.next();
            if (--pending.ticks <= 0) {
                Minecraft.getMinecraft().getSoundHandler().playSound(pending.sound);
                iterator.remove();
            }
        }
    }

    private static final class Pending {
        final ISound sound;
        int ticks;

        Pending(ISound sound, int ticks) {
            this.sound = sound;
            this.ticks = ticks;
        }
    }
}
