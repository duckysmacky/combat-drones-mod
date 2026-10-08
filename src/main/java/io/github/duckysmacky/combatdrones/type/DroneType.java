package io.github.duckysmacky.combatdrones.type;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;

import javax.annotation.Nullable;
import java.io.Reader;
import java.io.StringReader;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * One drone/munition "model", loaded from {@code drone_types/<id>.json}. Every roster variant is an
 * instance of this, not a subclass. Kept free of Minecraft classes so it can be unit-tested.
 */
public final class DroneType {
    /** Hand-flown multirotors, ramp-launched fixed-wing units, or missiles launched straight up from a pad. */
    public enum Category { HAND_FLOWN, RAMP_AUTOPILOT, VERTICAL_STRIKE }

    /**
     * How the drone is flown. Hand-flown: FPV (acro/angle/hover quad) or DJI (stabilized velocity control).
     * Ramp-launched (manual takeover from the Mission Planner): STICK, CLICK_DESIGNATE or GIMBAL_OBSERVE.
     * Vertical strike: MISSILE (the dive is steered from the Missile Control Tablet within a cone around straight down).
     */
    public enum ControlScheme {
        FPV, DJI, STICK, CLICK_DESIGNATE, GIMBAL_OBSERVE, MISSILE;

        public boolean isHandFlown() {
            return this == FPV || this == DJI;
        }

        /** Ramp-launched schemes that allow "Take control" (the recon gimbal is view-only). */
        public boolean isFlyable() {
            return this == STICK || this == CLICK_DESIGNATE;
        }
    }

    public enum WeaponType { NONE, ROCKET_POD, GUN_MOUNT }

    /** What a hand-flown drone's payload slots hold: grenades boost a racer's impact blast, bombs are dropped. */
    public enum PayloadKind { GRENADE, BOMB }

    private static final Gson GSON = new Gson();

    private transient String id;

    private Category category;
    /** Blocks per tick. */
    private double maxSpeed;
    /** Blocks per tick², at full throttle. */
    private double acceleration;
    /** Battery (hand-flown, with a Standard pack) or fuel (ramp-launched) units. */
    private double batteryOrFuelCapacity;
    /** Units drained per tick at full throttle. */
    private double drainRatePerTick;
    /** Explosion strength, same scale as vanilla (TNT = 4). 0 = no blast. */
    private float blastPower;
    /** Payload items the drone can carry. 0 = no payload slot. */
    private int payloadCapacity;
    /** Hand-flown only, when payloadCapacity > 0: what goes in the slots. */
    private PayloadKind payload;
    private float maxHP;
    /** Hand-flown only: blocks from the pilot before the link drops. */
    private double signalRange;
    /** Hand-flown only: the pack size this airframe is designed around (shown in its tooltip). */
    private BatteryPack defaultBattery;
    /** Blocks from which the motor can be heard. Drones are meant to be loud: hearing one is the counterplay. */
    private double motorSoundRange = 64;
    /** Collision box (blocks): the fuselage core, not the wingtips. What gets hit, so it's a balance stat too. */
    private double hitboxWidth = 0.6;
    private double hitboxHeight = 0.3;
    /**
     * Engine loop sound event under {@code combat_drones:} (e.g. "unit.engine_shahed"): heard by others for hand-flown
     * drones, by everyone for ramp units. Null = the family default.
     */
    private String engineSound;
    /** Ramp-launched only: blocks above the launch point. */
    private double defaultCruiseAltitude;
    /** Ramp-launched only: multiplies roll, pitch and turn rates (1 = plane-like, higher = rocket-like). */
    private double agility = 1.0;
    /** Ramp-launched only: multiplies the pitch rate on top of agility (snappier climbs and dives). */
    private double pitchAgility = 1.0;
    /** Input mapping: FPV/DJI for hand-flown types, the Mission Planner takeover scheme for ramp-launched ones. */
    private ControlScheme controlScheme;
    private WeaponType weaponType = WeaponType.NONE;
    /** Detonates on any collision (racer family). */
    private boolean impactDetonates;
    /** False for the recon UAV: no warhead or weapon, never dives or strikes (a crash can still set off its fuel). */
    private boolean armed = true;
    /** Carries a detection sensor (recon): sweeps for enemies and lets its camera lock targets. */
    private boolean sensor;

    /** Parses and validates one type file. Gson's {@code fromJson(Reader)} is lenient, so {@code //} comments are allowed. */
    public static DroneType parse(String id, Reader reader) {
        DroneType type;
        try {
            type = GSON.fromJson(reader, DroneType.class);
        } catch (JsonParseException e) {
            throw new IllegalArgumentException("Drone type '" + id + "': malformed JSON: " + e.getMessage(), e);
        }
        if (type == null) {
            throw new IllegalArgumentException("Drone type '" + id + "': file is empty");
        }
        type.id = id;
        type.validate();
        return type;
    }

    /**
     * The balance numbers a server admin may tune in the config, in declaration order. Stats that don't apply to
     * this category are left out (signal range is hand-flown only, cruise altitude ramp-launched only).
     */
    public Map<String, Number> numericStats() {
        Map<String, Number> stats = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> entry : GSON.toJsonTree(this).getAsJsonObject().entrySet()) {
            JsonElement value = entry.getValue();
            if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) {
                continue;
            }
            String name = entry.getKey();
            if ((name.equals("signalRange") && category != Category.HAND_FLOWN)
                    || ((name.equals("defaultCruiseAltitude") || name.equals("agility") || name.equals("pitchAgility"))
                            && category != Category.RAMP_AUTOPILOT)) {
                continue;
            }
            stats.put(name, value.getAsNumber());
        }
        return stats;
    }

    /** A copy with some numeric stats replaced, re-validated. Unknown or non-numeric stat names are rejected. */
    public DroneType withStats(Map<String, Number> overrides) {
        JsonObject json = GSON.toJsonTree(this).getAsJsonObject();
        for (Map.Entry<String, Number> override : overrides.entrySet()) {
            JsonElement current = json.get(override.getKey());
            if (current == null || !current.isJsonPrimitive() || !current.getAsJsonPrimitive().isNumber()) {
                throw new IllegalArgumentException("Drone type '" + id + "': '" + override.getKey() + "' is not a tunable stat");
            }
            json.addProperty(override.getKey(), override.getValue());
        }
        return parse(id, new StringReader(json.toString()));
    }

    /** Serialized form for syncing to clients; {@link #parse} reads it back (the ID travels separately). */
    public String toJson() {
        return GSON.toJson(this);
    }

    private void validate() {
        // Unknown enum names deserialize to null, so these also catch typos
        require(category != null, "missing or unknown 'category'");
        require(weaponType != null, "unknown 'weaponType'");
        require(maxSpeed > 0, "'maxSpeed' must be > 0");
        require(acceleration > 0, "'acceleration' must be > 0");
        require(batteryOrFuelCapacity > 0, "'batteryOrFuelCapacity' must be > 0");
        require(drainRatePerTick >= 0, "'drainRatePerTick' must be >= 0");
        require(blastPower >= 0, "'blastPower' must be >= 0");
        require(payloadCapacity >= 0, "'payloadCapacity' must be >= 0");
        require(maxHP > 0, "'maxHP' must be > 0");
        require(motorSoundRange >= 0, "'motorSoundRange' must be >= 0");
        require(agility > 0, "'agility' must be > 0");
        require(pitchAgility > 0, "'pitchAgility' must be > 0");
        require(hitboxWidth >= 0.1 && hitboxWidth <= 4, "'hitboxWidth' must be in 0.1..4");
        require(hitboxHeight >= 0.1 && hitboxHeight <= 8, "'hitboxHeight' must be in 0.1..8");
        if (category == Category.HAND_FLOWN) {
            require(signalRange > 0, "hand-flown types need 'signalRange' > 0");
            require(defaultBattery != null, "hand-flown types need a valid 'defaultBattery' (LIGHT, STANDARD or HEAVY)");
            require(controlScheme != null && controlScheme.isHandFlown(), "hand-flown types need 'controlScheme' FPV or DJI");
            require(payloadCapacity == 0 || payload != null, "hand-flown types with payload slots need 'payload' GRENADE or BOMB");
        } else if (category == Category.RAMP_AUTOPILOT) {
            require(controlScheme != null && !controlScheme.isHandFlown() && controlScheme != ControlScheme.MISSILE,
                    "ramp-launched types need 'controlScheme' STICK, CLICK_DESIGNATE or GIMBAL_OBSERVE");
            require(defaultCruiseAltitude > 0, "ramp-launched types need 'defaultCruiseAltitude' > 0");
        } else {
            require(controlScheme == ControlScheme.MISSILE, "vertical strike types need 'controlScheme' MISSILE");
            require(armed && impactDetonates && weaponType == WeaponType.NONE && payloadCapacity == 0,
                    "vertical strike types are armed, detonate on impact and carry no weapon or payload");
        }
        if (!armed) {
            // blastPower is allowed: it's the fuel going up in a crash, not a warhead
            require(!impactDetonates && weaponType == WeaponType.NONE && payloadCapacity == 0, "unarmed types can't detonate on impact or carry a weapon or payload");
            require(controlScheme == null || controlScheme == ControlScheme.GIMBAL_OBSERVE, "unarmed types can only use GIMBAL_OBSERVE");
        }
    }

    private void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalArgumentException("Drone type '" + id + "': " + message);
        }
    }

    public String getId() {
        return id;
    }

    public Category getCategory() {
        return category;
    }

    public double getMaxSpeed() {
        return maxSpeed;
    }

    public double getAcceleration() {
        return acceleration;
    }

    public double getBatteryOrFuelCapacity() {
        return batteryOrFuelCapacity;
    }

    public double getDrainRatePerTick() {
        return drainRatePerTick;
    }

    public float getBlastPower() {
        return blastPower;
    }

    public int getPayloadCapacity() {
        return payloadCapacity;
    }

    public PayloadKind getPayload() {
        return payload;
    }

    public float getMaxHP() {
        return maxHP;
    }

    public double getSignalRange() {
        return signalRange;
    }

    public BatteryPack getDefaultBattery() {
        return defaultBattery;
    }

    public double getMotorSoundRange() {
        return motorSoundRange;
    }

    public double getDefaultCruiseAltitude() {
        return defaultCruiseAltitude;
    }

    public float getHitboxWidth() {
        return (float) hitboxWidth;
    }

    public float getHitboxHeight() {
        return (float) hitboxHeight;
    }

    @Nullable
    public String getEngineSound() {
        return engineSound;
    }

    public double getAgility() {
        return agility;
    }

    public double getPitchAgility() {
        return pitchAgility;
    }

    public ControlScheme getControlScheme() {
        return controlScheme;
    }

    public WeaponType getWeaponType() {
        return weaponType;
    }

    /** The weapon on {@code station} (0 = primary, 1 = secondary), or NONE. */
    public WeaponType getWeapon(int station) {
        return station == 0 ? weaponType : WeaponType.NONE;
    }

    /** Rounds (or rocket bay units, for a rocket pod) {@code station} holds. */
    public int getStationCapacity(int station) {
        return station == 0 ? payloadCapacity : 0;
    }

    /** The station with {@code weapon}, or -1 if the airframe doesn't carry it. */
    public int stationOf(WeaponType weapon) {
        for (int station = 0; station < Loadout.STATIONS; station++) {
            if (weapon != WeaponType.NONE && getWeapon(station) == weapon) {
                return station;
            }
        }
        return -1;
    }

    public boolean isImpactDetonates() {
        return impactDetonates;
    }

    public boolean isSensor() {
        return sensor;
    }

    public boolean isArmed() {
        return armed;
    }
}
