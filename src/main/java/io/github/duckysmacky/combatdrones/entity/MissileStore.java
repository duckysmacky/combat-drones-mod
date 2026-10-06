package io.github.duckysmacky.combatdrones.entity;

import io.github.duckysmacky.combatdrones.CombatDrones;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.world.World;
import net.minecraft.world.storage.MapStorage;
import net.minecraft.world.storage.WorldSavedData;
import net.minecraftforge.common.util.Constants;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Every cruise missile launched from a pad, keyed by the pad's UUID, saved with the world
 * ({@code data/combat_drones_missiles.dat}). A missile is only an entity while it climbs and while it dives; in between
 * (standby) it exists only here, invisible to everyone. One record per pad: a new launch replaces
 * the last, finished one, and a pad with a missile still up can't launch again (so one tablet, one missile).
 *
 * <p>Times are world total ticks, which every dimension shares with the overworld in 1.12. Standby fuel isn't ticked
 * down: it's {@code standbyEndTick}, the moment the tank runs dry, so the state survives restarts unchanged.
 */
public final class MissileStore extends WorldSavedData {
    public static final String NAME = CombatDrones.MOD_ID + "_missiles";

    public enum State {
        /** Launched: the entity is on the pad or climbing. */
        CLIMBING,
        /** Out of sight on standby, from {@code readyTick} until the tank runs dry at {@code standbyEndTick}. */
        AIRBORNE,
        /** Holding over the strike point and diving (the entity again). */
        STRIKING,
        IMPACT,
        DESTROYED,
        LOST;

        /** Still up (or about to be): its pad can't launch another. */
        public boolean isActive() {
            return this == CLIMBING || this == AIRBORNE || this == STRIKING;
        }
    }

    public static final class Record {
        public final UUID padId;
        @Nullable
        public final UUID owner;
        public final String typeId;
        public final int dimension;
        public final double padX;
        public final double padY;
        public final double padZ;
        public State state = State.CLIMBING;
        public long launchTick;
        /** Ticks of standby the tank holds (fuel / drain), fixed at launch. */
        public long standbyTicks;
        public long readyTick;
        public long standbyEndTick;
        /** CLIMBING / STRIKING: past this, the entity is presumed gone and the missile LOST. */
        public long deadline;
        public double targetX;
        public double targetZ;
        public float health = 1;

        Record(UUID padId, @Nullable UUID owner, String typeId, int dimension, double padX, double padY, double padZ) {
            this.padId = padId;
            this.owner = owner;
            this.typeId = typeId;
            this.dimension = dimension;
            this.padX = padX;
            this.padY = padY;
            this.padZ = padZ;
        }

        NBTTagCompound toNbt() {
            NBTTagCompound tag = new NBTTagCompound();
            tag.setUniqueId("Pad", padId);
            if (owner != null) {
                tag.setUniqueId("Owner", owner);
            }
            tag.setString("Type", typeId);
            tag.setInteger("Dim", dimension);
            tag.setDouble("PadX", padX);
            tag.setDouble("PadY", padY);
            tag.setDouble("PadZ", padZ);
            tag.setString("State", state.name());
            tag.setLong("LaunchTick", launchTick);
            tag.setLong("StandbyTicks", standbyTicks);
            tag.setLong("ReadyTick", readyTick);
            tag.setLong("StandbyEnd", standbyEndTick);
            tag.setLong("Deadline", deadline);
            tag.setDouble("TargetX", targetX);
            tag.setDouble("TargetZ", targetZ);
            tag.setFloat("Health", health);
            return tag;
        }

        @Nullable
        static Record fromNbt(NBTTagCompound tag) {
            if (!tag.hasUniqueId("Pad")) {
                return null;
            }
            Record record = new Record(tag.getUniqueId("Pad"), tag.hasUniqueId("Owner") ? tag.getUniqueId("Owner") : null, tag.getString("Type"),
                    tag.getInteger("Dim"), tag.getDouble("PadX"), tag.getDouble("PadY"), tag.getDouble("PadZ"));
            try {
                record.state = State.valueOf(tag.getString("State"));
            } catch (IllegalArgumentException e) {
                record.state = State.LOST;
            }
            record.launchTick = tag.getLong("LaunchTick");
            record.standbyTicks = tag.getLong("StandbyTicks");
            record.readyTick = tag.getLong("ReadyTick");
            record.standbyEndTick = tag.getLong("StandbyEnd");
            record.deadline = tag.getLong("Deadline");
            record.targetX = tag.getDouble("TargetX");
            record.targetZ = tag.getDouble("TargetZ");
            record.health = tag.getFloat("Health");
            return record;
        }
    }

    private final Map<UUID, Record> records = new LinkedHashMap<>();

    public MissileStore(String name) {
        super(name);
    }

    /** The server-wide store (global map storage, shared by every dimension). */
    public static MissileStore get(World world) {
        MapStorage storage = world.getMapStorage();
        MissileStore store = (MissileStore) storage.getOrLoadData(MissileStore.class, NAME);
        if (store == null) {
            store = new MissileStore(NAME);
            storage.setData(NAME, store);
        }
        return store;
    }

    @Nullable
    public Record get(UUID padId) {
        return records.get(padId);
    }

    public Collection<Record> all() {
        return records.values();
    }

    /** A new launch from {@code pad}, replacing its last (finished) record. */
    public Record launch(EntityMissilePad pad, String typeId, long now, long standbyTicks, long deadline) {
        Record record = new Record(pad.getUniqueID(), pad.getOwner(), typeId, pad.world.provider.getDimension(), pad.posX, pad.posY, pad.posZ);
        record.launchTick = now;
        record.standbyTicks = standbyTicks;
        record.deadline = deadline;
        records.put(record.padId, record);
        markDirty();
        return record;
    }

    /** The climb is over and the missile out of sight: on standby right away, on whatever fuel it was launched with. */
    public void climbed(UUID padId, long now) {
        Record record = records.get(padId);
        if (record != null && record.state == State.CLIMBING) {
            record.state = State.AIRBORNE;
            record.readyTick = now;
            record.standbyEndTick = record.readyTick + record.standbyTicks;
            markDirty();
        }
    }

    /** Strike ordered: the missile is over its target. */
    public void striking(Record record, double targetX, double targetZ, long deadline) {
        record.state = State.STRIKING;
        record.targetX = targetX;
        record.targetZ = targetZ;
        record.deadline = deadline;
        record.health = 1;
        markDirty();
    }

    public void setHealth(UUID padId, float health) {
        Record record = records.get(padId);
        if (record != null && record.state.isActive()) {
            record.health = health;
            markDirty();
        }
    }

    /** The end of the line: IMPACT, DESTROYED or LOST. Only an active missile can finish. */
    public void finish(UUID padId, State result) {
        Record record = records.get(padId);
        if (record != null && record.state.isActive()) {
            record.state = result;
            markDirty();
        }
    }

    @Override
    public void readFromNBT(NBTTagCompound compound) {
        records.clear();
        NBTTagList list = compound.getTagList("Missiles", Constants.NBT.TAG_COMPOUND);
        for (int index = 0; index < list.tagCount(); index++) {
            Record record = Record.fromNbt(list.getCompoundTagAt(index));
            if (record != null) {
                records.put(record.padId, record);
            }
        }
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound compound) {
        NBTTagList list = new NBTTagList();
        for (Record record : records.values()) {
            list.appendTag(record.toNbt());
        }
        compound.setTag("Missiles", list);
        return compound;
    }
}
