# Changelog

All notable changes to Combat Drones, newest first.

## v0.5.0

#### Added

- **EMP Gun**
- **Light and Heavy Runways**
- **Runway landing**: planes land on runways. Return flies an approach, rolls out and parks the plane. Planes no longer land as items anywhere else.
- **Mission Planner runway support**: runways show on the map. Right-click one to land there at the end of a route. "Return to runway" sets an airborne plane's route aside and lands it.
- **Shot-down state**: planes shot to 0 HP go down in a burning, spinning arc with the pilot still aboard, then explode on impact. Drones can use it too if enabled. Tuned in `[state.shot_down]`.
- **EMPed state**: EMP-disabled vehicles glide down without control but keep their pilot. They fly on when the EMP wears off. Tuned in `[state.emped]`.
- **Per-vehicle fuel types**: 100LL Avgas Fuel for munitions, Jet A-1 Fuel for light planes, JP-8 Military Grade Fuel for heavy planes and JP-10 Missile Fuel for cruise missiles.
- Mission Planner units can be selected with the number keys, and the planner remembers the last selected unit.
- Shift+right-click on the Mission Planner takes control of the selected unit.
- Recon Drone has a UAV-style lock-on. Hold the left mouse button to lock on.
- EMP fields now disable runways and launch ramps. You can View and Take control of disabled vehicles.

#### Changed

- **Drones reworked**: Micro Racer, Standard Racer and Long-Range Racer are now the Racer, Standard and Recon Drones. The Recon Drone is a quiet, black long-range scout.
- Attack Plane and Strike Plane are now the **Spitfire** and **Striker**, with bigger models and hitboxes and lower top speeds. Light munition is slower.
- Launch ramps link to the Mission Planner when placed.
- Some keybinds have clearer names: "Switch drone/unit camera", "Switch weapon" and "Switch camera view".
- Signal loss grows exponentially past half of a drone's range.
- A drone keeps its last input when its pilot leaves, however they leave, and keeps its flight mode when the pilot takes it back.
- Recon and DJI drones are quieter.
- Hand-flown drone settings moved to a `[drone]` config table, with one table per drone type.
- Some blast and missile config keys were renamed for consistency.

#### Removed

- **Rearm Station**.
- The generic **Fuel Can**, replaced by the fuel types above.

#### Fixed

- The weapon switch key is now J, since R clashed with reloading. Reload only applies to held launchers.

## v0.4.0

#### Added

- Stealth Bomber, a bomber plane that drops aerial bombs.
- EMP Field Station, an area EMP device.
- EMP Blast Gadget, an EMP item.
- Pilot Detector, which finds players flying drones and planes.
- Stinger anti-air launcher.

#### Changed

- UAV reworked into tracking dots and charged lock-ons.
- Recon camera holds its view on the ground and no longer follows targets.
- Recon settings moved to a new [uav] config section.
- ignoredTeams is a general setting. It applies to every team check: recon tracking and locks, the Pilot Detector, EMP sparing, and sharing.

#### Fixed

- Lock-ons box now targets through walls instead of steering the camera.

## v0.3.0

#### Added

- Visual and sound feedback when you use drones, launch ramps and missile pads.
- Custom particles and impact effects.
- Interaction and UI sounds.

#### Changed

- Visual overhaul: redrawn entity textures and item sprites, more detailed and animated models, and a new mod icon.
- Per-vehicle camera feeds have their own looks: analog, FLIR, nose camera, recon and seeker.
- Mission Planner and Missile Tablet restyled.
- Sound loudness rebalanced.
- Handling sounds replaced with recordings. Fuel now uses the vanilla bucket pour sound.
- Gunship close-range engine sound replaced with a new recording.
- Launch ramp models and hitboxes reworked.
- Cruise missile no longer shows hover exhaust while arming. The feed shows static until the dive.

## v0.2.1

#### Added

- Distance-banded sounds: engines, explosions, gun reports and projectiles in flight play as close, mid or far variants, each with its own falloff and Doppler shift.
- Munition dive sounds, plus a terminal sound as a munition closes on its target.
- Bullet whizz sounds as rounds pass close by.
- Flight sounds for rockets, shells and bombs.
- Air-burst and indoor explosion sounds. Far-range sounds for the Gunship cannon and howitzer.
- Subtitles for the new munition, bullet and projectile sounds.
- Client config options maxEngineVoices (default 4) and maxProjectileSounds (default 6). They cap how many engines and projectiles you hear at once, nearest first.
- CRT overlay on the gunner camera. The analog static setting also controls its strength.

#### Changed

- Gunship model reworked, with a larger hitbox.
- Gunship cannon, howitzer and rocket fire slower.
- Recon UAV has a lower top speed.
- Loiter routes are limited by a per-type minimum altitude and radius. Gunship and Recon UAV have these set.
- Mission Planner opens at a closer default zoom.

#### Fixed

- Mission Planner panning works with no unit selected, and the map centers on you when nothing is linked.
- Clicks that open a control session no longer fire the weapon. The trigger must be released once first.

## v0.2.0

#### Added

- **Gunship**: a ramp-launched armed plane flown from a gunner seat, with a gimbal camera, a cannon and a rocket rack.
- **105 mm howitzer** station on the Gunship, with its own cooldown separate from the other weapons.
- **Heavy rockets** for the rocket pods: about twice the blast, fewer per bay.
- **Cruise missile**, **Missile Pad** and **Missile Control Tablet** for launching and controlling cruise missiles.

#### Changed

- Launch ramps are linked to the Mission Planner. Right-click a ramp with the planner to link it, then plan and launch from anywhere instead of standing next to the ramp.
- The Mission Planner shows linked ramp units from anywhere and adds a **Launch & Fly** action.
- **Gunship Plane** is split into the **Strike Plane** (rocket pods) and the **Attack Plane** (gun mount). Ammo and tooltips are updated to match.
- Strike and Attack planes are rebalanced.
- The Gunship is slower and less agile, with its aim and cannon tuned.
- Loitering munitions and cruise missiles have bigger blasts. Missile shrapnel damage rises from 14 to 25.
- Pilot radio static is replaced by a handheld-radio squelch sound.
- Default pilot radio static range doubles, from 24 to 48.

#### Fixed

- Planes no longer explode when they clip a player or drone. Only warheads detonate on entity contact.
- Pilots no longer hear their own radio static.

## v0.1.1

#### Added

- Pilot radio static and a TX light on the pilot's stand-in body.
- Configurable recon sharing: sightings and active locks can be shared with your scoreboard team.
- Recon marks are shown in FPV.
- World border handling: drones and ramp units can collide with the world border. Off by default.
- Client config reload: your own client options are re-read without a restart.

#### Changed

- Long-range tracing and strike designation are extended for munition cameras, with a configurable range (512 blocks by default).
- Designated strikes fly as a direct pursuit.
- Recon intel is shared with the owner's scoreboard team by default. Teams listed in `ignoredTeams` (default `ffa`) are not treated as teammates.

#### Fixed

- Unit sync issues and stand-in body issues.

## v0.1.0

Initial release.

#### Added

- Drones: Micro Racer, Standard Racer, Long-Range Racer, DJI Drone, Recon UAV, Light Loitering Munition, Heavy Loitering Munition
- Planes: Gunship Plane (Rocket Pod), Gunship Plane (Gun Mount)
- Items and blocks: FPV Remote, FPV Goggles, Mission Planner, Launch Ramp, Rearm Station, Light Battery Pack, Standard Battery Pack, Heavy Battery Pack, Fuel Can, Fragmentation Grenade, VOG Drop Grenade, Mortar Drop Bomb, Thermobaric Drop Bomb, Rocket, Rifle Rounds

