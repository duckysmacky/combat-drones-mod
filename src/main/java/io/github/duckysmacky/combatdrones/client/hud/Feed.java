package io.github.duckysmacky.combatdrones.client.hud;

import io.github.duckysmacky.combatdrones.type.DroneType;

/**
 * What a vehicle's camera feed looks like to whoever is flying or watching it: the OSD layout and the picture grade.
 * Chosen from data every type already has (its control scheme and whether it's a munition), so a new variant gets
 * a feed without any Java.
 */
public enum Feed {
    /** Racers: analog FPV goggles. Betaflight-style OSD, a warm, slightly bulged picture with scanlines. */
    ANALOG("analog"),
    /** DJI Drone: a clean digital link. Status pills and a gimbal scale, no grade. */
    DIGITAL(null),
    /** Light munition: a monochrome electro-optical seeker with a target gate. */
    SEEKER("seeker"),
    /** Heavy munition: a cheap, noisy nose camera with a bare OSD. */
    NOSECAM("nosecam"),
    /** Strike and Attack Planes: a green head-up display over a clear picture. */
    FLIGHT(null),
    /** Gunship: a white-hot gunner sight with a weapon box. */
    GUNNER("flir"),
    /** Recon UAV: a surveillance feed with data bars. */
    RECON("recon"),
    /** Cruise missile: the terminal seeker, a cone reticle and the impact countdown. */
    MISSILE("seeker");

    /** Post shader name under {@code shaders/post/}, or null for an ungraded picture. */
    public final String shader;

    Feed(String shader) {
        this.shader = shader;
    }

    public static Feed of(DroneType type) {
        DroneType.ControlScheme scheme = type.getControlScheme();
        if (scheme == null) {
            return type.getCategory() == DroneType.Category.HAND_FLOWN ? ANALOG : FLIGHT;
        }
        switch (scheme) {
            case FPV:
                return ANALOG;
            case DJI:
                return DIGITAL;
            case CLICK_DESIGNATE:
                return SEEKER;
            case GUNNER:
                return GUNNER;
            case GIMBAL_OBSERVE:
                return RECON;
            case MISSILE:
                return MISSILE;
            default:
                return type.isImpactDetonates() ? NOSECAM : FLIGHT;
        }
    }
}
