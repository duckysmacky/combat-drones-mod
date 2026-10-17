# Changelog

All notable changes to Combat Drones, newest first.

## v0.2.1

Added

- Distance-banded sounds: engines, explosions, gun reports and projectiles in flight play as close, mid or far variants, each with its own falloff and Doppler shift.
- Munition dive sounds, plus a terminal sound as a munition closes on its target.
- Bullet whizz sounds as rounds pass close by.
- Flight sounds for rockets, shells and bombs.
- Air-burst and indoor explosion sounds.
- Far-range sounds for the Gunship cannon and howitzer.
- Subtitles for the new munition, bullet and projectile sounds.
- Client config options maxEngineVoices (default 4) and maxProjectileSounds (default 6). They cap how many engines and projectiles you hear at once, nearest first.
- CRT overlay on the gunner camera. The analog static setting also controls its strength.

Changed

- Gunship model reworked, with a larger hitbox.
- Gunship cannon, howitzer and rocket fire slower.
- Recon UAV has a lower top speed.
- Loiter routes are limited by a per-type minimum altitude and radius. Gunship and Recon UAV have these set.
- Mission Planner opens at a closer default zoom.

Fixed

- Mission Planner panning works with no unit selected, and the map centers on you when nothing is linked.
- Clicks that open a control session no longer fire the weapon. The trigger must be released once first.

## v0.2.0

### Added

- **Gunship**: a ramp-launched armed plane flown from a gunner seat, with a gimbal camera, a cannon and a rocket rack.
- **105 mm howitzer** station on the Gunship, with its own cooldown separate from the other weapons.
- **Heavy rockets** for the rocket pods: about twice the blast, fewer per bay.
- **Cruise missile**, **Missile Pad** and **Missile Control Tablet** for launching and controlling cruise missiles.

### Changed

- Launch ramps are linked to the Mission Planner. Right-click a ramp with the planner to link it, then plan and launch from anywhere instead of standing next to the ramp.
- The Mission Planner shows linked ramp units from anywhere and adds a **Launch & Fly** action.
- **Gunship Plane** is split into the **Strike Plane** (rocket pods) and the **Attack Plane** (gun mount). Ammo and tooltips are updated to match.
- Strike and Attack planes are rebalanced.
- The Gunship is slower and less agile, with its aim and cannon tuned.
- Loitering munitions and cruise missiles have bigger blasts. Missile shrapnel damage rises from 14 to 25.
- Pilot radio static is replaced by a handheld-radio squelch sound.
- Default pilot radio static range doubles, from 24 to 48.

### Fixed

- Planes no longer explode when they clip a player or drone. Only warheads detonate on entity contact.
- Pilots no longer hear their own radio static.

## v0.1.1

### Added

- Pilot radio static and a TX light on the pilot's stand-in body.
- Configurable recon sharing: sightings and active locks can be shared with your scoreboard team.
- Recon marks are shown in FPV.
- World border handling: drones and ramp units can collide with the world border. Off by default.
- Client config reload: your own client options are re-read without a restart.

### Changed

- Long-range tracing and strike designation are extended for munition cameras, with a configurable range (512 blocks by default).
- Designated strikes fly as a direct pursuit.
- Recon intel is shared with the owner's scoreboard team by default. Teams listed in `ignoredTeams` (default `ffa`) are not treated as teammates.

### Fixed

- Unit sync issues and stand-in body issues.

## v0.1.0

Initial release.

### Added

- Drones: Micro Racer, Standard Racer, Long-Range Racer, DJI Drone, Recon UAV, Light Loitering Munition, Heavy Loitering Munition
- Planes: Gunship Plane (Rocket Pod), Gunship Plane (Gun Mount)
- Items and blocks: FPV Remote, FPV Goggles, Mission Planner, Launch Ramp, Rearm Station, Light Battery Pack, Standard Battery Pack, Heavy Battery Pack, Fuel Can, Fragmentation Grenade, VOG Drop Grenade, Mortar Drop Bomb, Thermobaric Drop Bomb, Rocket, Rifle Rounds

