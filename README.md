# Combat Drones

![Platform](https://img.shields.io/badge/platform-Forge-E04E14)
![Minecraft](https://img.shields.io/badge/minecraft-1.12.2-62B47A)
![Version](https://img.shields.io/badge/version-0.5.2-4C8DFF)

Combat Drones is a Minecraft 1.12.2 Forge mod for PvP. It adds FPV kamikaze drones, bomb-dropping quadcopters,
loitering munitions, armed planes, a stealth bomber, a recon UAV and a cruise missile, plus the gear to counter them:
a pilot detector, EMP devices and a Stinger anti-air launcher. You fly the vehicles yourself or send them along
planned routes. Everything is loud, can be shot down and runs on the server, so it works on multiplayer servers.

Inspired by the [FPV Drones](https://www.curseforge.com/minecraft/mc-mods/fpvdrones) mod for Minecraft 1.20.1.

> [!NOTE]
> Combat Drones isn't on CurseForge or Modrinth yet, a release there is coming soon. For now, grab the jar from the
> [Releases page](https://github.com/duckysmacky/combat-drones-mod/releases) or build it from source
> (see [Installation](#installation)).

## Features

- **12 vehicles** in three families: hand-flown multirotors, fixed-wing units launched from ramps and runways, and a
  vertically launched cruise missile.
- **First-person piloting** through FPV goggles with an analog-style HUD, signal range and battery drain.
- **Mission Planner**: a tactical map to plan waypoint routes, launch from your linked ramps and runways anywhere,
  land planes on runways, re-route units in flight, watch through their cameras or take control.
- **Runways** for planes: they spool up and roll out for takeoff, and autopilot flies the approach and lands them,
  ready to refuel and rearm.
- **Recon and target locking**: the UAV spots enemies for you and your team, and a locked target stays tracked even
  out of sight.
- **Weapons**: grenades, drop bombs, rockets, machine guns, an autocannon, a 105 mm howitzer and aerial bombs.
- **Fuel types**: every kind of vehicle runs on its own fuel.
- **Counterplay**: every vehicle can be shot down and can be heard from far away. Remote pilots leave their body
  behind, and it gives off radio static. Fight back with the **Pilot Detector**, EMP devices (**EMP Field Station**,
  **EMP Blast Gadget**, **EMP Gun**) and the **Stinger** launcher.
- **Shot-down and EMPed states**: a plane shot to 0 HP falls burning with its pilot aboard, and an EMPed vehicle
  glides down without control, then flies on if the EMP wears off.
- **Server-friendly**: every stat is set in one config file and can be reloaded in game. It works with vanilla
  scoreboard teams for friendly fire and intel sharing.

## Vehicles

| Vehicle | Type | Role |
|---|---|---|
| Racer Drone | FPV drone | The fastest, most agile and most fragile drone. Explodes on impact |
| Standard Drone | FPV drone | Balanced kamikaze drone with a grenade slot |
| Recon Drone | FPV drone | Quiet, black long-range scout that locks on to enemies. Small blast, no payload |
| DJI Drone | Camera quadcopter | Stable and easy to fly. Drops bombs from above |
| Light Loitering Munition | Ramp-launched munition | Fast guided rocket: click a point on its camera and it strikes there |
| Heavy Loitering Munition | Ramp-launched munition | Slower, bigger warhead, flown by hand |
| Striker | Armed plane | Ground attack with rockets |
| Spitfire | Armed plane | Fast machine-gun plane for hunting other aircraft |
| Gunship | Armed plane | Huge, slow and loud. Circles overhead while you man the cannon, rockets and howitzer |
| Stealth Bomber | Armed plane | Flying wing that drops bombs through a bombsight, or by itself along a planned bombing run |
| Recon UAV | Unarmed plane | Long-endurance scout that marks enemies on your map |
| Cruise Missile | Vertical strike | Launched from a pad, waits out of sight, then you steer its dive onto a target |

## Gameplay

### FPV drones

1. Place a drone and right-click it with an **FPV Remote** to link it to you.
2. Right-click it with a **Battery Pack**. Light packs are fast but run out quickly; heavy packs last long but are
   slower. The Standard Drone can also take a **Fragmentation Grenade**, and the DJI Drone takes **drop bombs**.
3. Wear the **FPV Goggles**, hold the remote and press **G** to fly.

While you fly, your body stays where you were standing and can still be hit, and other players can hear its radio
static. FPV drones explode on any impact. Past half of its range the camera feed fills with more and more static.
However you leave a drone (exit, fly out of range, take off the goggles), it keeps going on your last input. Taking it back picks up in the same flight mode.

### Ramp and runway launched units

1. Place a **Munition Ramp** (loitering munitions), a **Light Runway** (Striker, Spitfire, Recon UAV) or a
   **Heavy Runway** (Gunship, Stealth Bomber, and light planes too), then right-click it with the unit. Runways are
   big flat strips (2 x 6 and 3 x 10 blocks) and need solid, clear ground.
2. Fuel the unit with its fuel (**100LL Avgas Fuel** for munitions, **Jet A-1 Fuel** for light planes, **JP-8 Military
   Grade Fuel** for heavy planes) and load ammo by right-clicking the ramp or runway with them.
3. Ramps and runways link to your **Mission Planner** when you place them. From then on you can plan and launch from
   anywhere. Right-click one with the planner to open it with the unit selected.
4. Open the planner (right-click air), plan a route on the map and press **Launch**. Some units also offer
   **Launch & Fly**.

Planes spool up for a few seconds, roll down the runway and climb out before turning onto their route.

You choose what a unit does at the end of its route:

- Loitering munitions: **Loiter**, **Dive** on the last waypoint, or **Destroy** a target marked on the map or in the
  world.
- Planes: **Loiter** or **Land on runway** (right-click a runway on the map and pick **Land here**, or use **Return
  to runway** in flight). The autopilot flies the approach, rolls out and parks the plane, ready to refuel, rearm
  and launch again.
- Recon UAV: **Loiter**, **Loop** or **Land on runway**.

The **Stealth Bomber** can also bomb on its route without you: right-click the planner map for **Add drop point here**,
or right-click a waypoint and set **Drop** to *Single* (one bomb) or *Carpet* (a strip of bombs of the length you
choose). The map shows where the bombs should land.

In the planner, the number keys select units from the list. **Shift+right-click** with the planner takes control of
your last selected unit if it is flying.

Once a unit is in the air, you can **View** through its camera or **Take control**. Planes have no warhead, but
their fuel and ammo still explode if they crash. They only survive a proper landing on a runway. A plane shot down
doesn't explode on the spot: it falls in a burning spiral with its pilot aboard and explodes on impact.

### Cruise Missile

1. Place a **Missile Launch Pad**, load a **Cruise Missile** and fuel it with **JP-10 Missile Fuel**.
2. Link a **Missile Control Tablet** to the pad, open the tablet and press **LAUNCH**.
3. The missile climbs out of sight and waits on standby until its fuel runs out.
4. Press **SELECT TARGET** and click a point on the map. You take over high above that point and steer the dive.
   Hold LMB to boost.

Everyone near the target hears a siren and gets an inbound warning. The missile is very tough, but it can be shot
down on the pad, during the climb and in the dive.

### Counter-drone gear

| Item | What it does |
|---|---|
| Pilot Detector | Right-click to scan nearby. Enemy pilots flying remotely get a red dot over their body for a while, even through walls. Recharges between uses |
| EMP Field Station | A placed device. Right-click it to switch on an EMP field (24 blocks) around it. It drains its charge while on and recharges while off. Sneak + right-click picks it up |
| EMP Blast Gadget | Hand-held. Right-click, and after a short build-up an EMP field grows around you (18 blocks). Recharges slowly |
| EMP Gun + EMP Cell | Shoulder-fired. Hold left-click to charge, release to fire an orb that flies straight, so lead your target. Its EMP field (12 blocks) travels with the orb or bursts where it lands. Right-click to aim |
| Stinger + Stinger Missile | Shoulder-fired anti-air launcher. Hold right-click to scope, keep an aircraft in the circle until it locks, then left-click to fire a homing missile |

An EMP field acts on every vehicle that is flying inside it, and spares your own (or your team's, depending on the
config). By default it disables them: the vehicle loses control and its motors cut out. It glides down, and flies
on when the EMP wears off, but hitting anything on the way down destroys it. A pilot keeps their view, so they can
be ready to catch it. Ramps and runways inside the field are knocked out for a while: nothing launches or lands. A
field can also be set to destroy vehicles outright. Units sitting on a ramp or runway, and missiles on their pad, are
untouched.

The Stinger only locks flying aircraft, never players or mobs. It downs anything up to the Striker in one hit, while
bigger planes and the cruise missile take two or three. Reload both launchers with **R** while holding them.

## Controls

All mod keys can be rebound under Controls -> Combat Drones.

**FPV drones**

| Key | Action |
|---|---|
| G | Enter / exit FPV |
| Mouse, arrow keys | Pitch and roll |
| A / D | Yaw |
| W / S | Throttle (climb / descend in Hover mode) |
| H | Flight mode: Acro, Angle, Hover |
| N | Motors on / off |
| LMB | Airburst the grenade (Standard); hold to lock on (Recon) |

**DJI Drone**

| Key | Action |
|---|---|
| G | Enter / exit FPV |
| W / A / S / D | Move |
| Space / Shift | Climb / descend |
| Mouse | Turn and tilt the camera |
| N | Motors on / off |
| LMB | Drop a bomb |

**Remote units** (from the Mission Planner)

| Key | Action |
|---|---|
| Mouse | Look, aim or fly |
| Mouse wheel | Camera zoom |
| W / S | Throttle (planes and the heavy munition) |
| H | Assisted / free flight |
| LMB | Fire, or strike the point under the crosshair |
| Space | Boost (munitions) |
| J | Switch weapon (Gunship) |
| B | Bombsight / flight view (Stealth Bomber) |
| G / Esc | Return to your body |

**Hand-held launchers** (Stinger, EMP Gun)

| Key | Action |
|---|---|
| RMB (hold) | Aim through the scope |
| LMB | Stinger: fire at a lock. EMP Gun: hold to charge, release to fire |
| R | Reload (only while holding a launcher) |

**Mission Planner**

| Key | Action |
|---|---|
| Right-click air | Open the planner |
| Shift + right-click | Take control of your last selected unit |
| 1-9, 0 | Select a unit from the list |

## For server owners

- All balance settings are in `config/combat_drones.toml`. `/combat_drones config_reload` (op) reloads it in game,
  and `/combat_drones reload_client_config` reloads a player's own client options.
- Damage from drones, shrapnel, rounds and rockets counts as coming from the pilot, so
  `/scoreboard teams option <team> friendlyfire false` protects teammates.
- Recon sightings and locks are shared with the owner's scoreboard team by default (`reconIntelForAllies`,
  `activeLockForAllies`). Teams listed in `ignoredTeams` (default `ffa`) don't share anything.
- Drones fly through the world border unless `worldBorderCollision = true`.
- Shot-down and EMPed behaviour is set per vehicle kind in `[state.shot_down]` and `[state.emped]`. The counter-drone
  gear has its own sections (`[pilot_detector]`, `[emp_station]`, `[emp_blast]`, `[emp_gun]`, `[stinger]`), where
  `onContact` (`disable` or `destroy`) and `spares` (`none`, `owner` or `team`) decide what an EMP does and whose
  vehicles it leaves alone. Runway takeoff is tuned in `[runways]`.

## Installation

### Requirements

- Minecraft **1.12.2** with Forge **14.23.5.2860**
- [GeckoLib](https://www.curseforge.com/minecraft/mc-mods/geckolib) for 1.12.2 (**3.0.31**). Clients need it to
  render the models. Dedicated servers don't need it.

### Download

Download `combat_drones-<version>.jar` from the latest release on the
[Releases page](https://github.com/duckysmacky/combat-drones-mod/releases).

### Building from source

Alternatively, build the jar yourself. Building needs JDK 21 and JDK 8. If JDK 8 is missing, Gradle downloads it
automatically.

```sh
git clone https://github.com/duckysmacky/combat-drones-mod.git
cd combat-drones-mod
./gradlew build
```

Copy `build/libs/combat_drones-<version>.jar` (the one without a `-dev` or `-shaded` suffix) into the `mods/`
folder of your client and server, together with GeckoLib on clients.

<details>
<summary>Development</summary>

- Arch Linux: `sudo pacman -S jdk21-openjdk jdk8-openjdk`
- Ubuntu / WSL2: `sudo apt install openjdk-21-jdk openjdk-8-jdk`. Clone inside the WSL filesystem, not under
  `/mnt/c`, because Gradle is very slow there.

```sh
./gradlew runClient      # dev client
./gradlew runServer      # dev server (needs run/eula.txt with eula=true)
./gradlew runObfServer   # dev server on the release jar
```

Built with [RetroFuturaGradle](https://github.com/GTNewHorizons/RetroFuturaGradle) 1.4.9 (Gradle 8.14.3, wrapper
included).

</details>

## Credits

- Inspired by [FPV Drones](https://www.curseforge.com/minecraft/mc-mods/fpvdrones) (Minecraft 1.20.1).
- Models are rendered with [GeckoLib](https://www.curseforge.com/minecraft/mc-mods/geckolib).

