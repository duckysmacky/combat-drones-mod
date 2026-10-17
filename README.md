# Combat Drones

![Platform](https://img.shields.io/badge/platform-Forge-E04E14)
![Minecraft](https://img.shields.io/badge/minecraft-1.12.2-62B47A)
![Version](https://img.shields.io/badge/version-0.2.0-4C8DFF)

Combat Drones is a Minecraft 1.12.2 Forge mod for PvP. It adds FPV kamikaze drones, bomb-dropping quadcopters,
loitering munitions, armed planes, a recon UAV and a cruise missile. You fly them yourself or send them along
planned routes. Everything is loud, can be shot down and runs on the server, so it works on multiplayer servers.

Inspired by the [FPV Drones](https://www.curseforge.com/minecraft/mc-mods/fpvdrones) mod for Minecraft 1.20.1.

> [!NOTE]
> Combat Drones isn't on CurseForge or Modrinth yet, a release there is coming soon. For now, grab the jar from the
> [Releases page](https://github.com/duckysmacky/combat-drones-mod/releases) or build it from source
> (see [Installation](#installation)).

## Features

- **11 vehicles** in three families: hand-flown multirotors, ramp-launched fixed-wing units and a vertically
  launched cruise missile.
- **First-person piloting** through FPV goggles with an analog-style HUD, signal range and battery drain.
- **Mission Planner**: a tactical map to plan waypoint routes, launch from linked ramps anywhere, re-route units in
  flight, watch through their cameras or take control.
- **Recon and target locking**: the UAV spots enemies for you and your team, and a locked target stays tracked even
  out of sight.
- **Weapons**: grenades, drop bombs, rockets, machine guns, an autocannon and a 105 mm howitzer.
- **Counterplay**: every vehicle can be shot down and can be heard from far away. Remote pilots leave their body
  behind, and it gives off radio static.
- **Server-friendly**: every stat is set in one config file and can be reloaded in game. It works with vanilla
  scoreboard teams for friendly fire and intel sharing.

## Vehicles

| Vehicle | Type | Role |
|---|---|---|
| Micro Racer | FPV drone | The fastest and most fragile drone. Explodes on impact |
| Standard Racer | FPV drone | Balanced kamikaze drone with a grenade slot |
| Long-Range Racer | FPV drone | Slower, with the longest range and a grenade slot |
| DJI Drone | Camera quadcopter | Stable and easy to fly. Drops bombs from above |
| Light Loitering Munition | Ramp-launched munition | Fast guided rocket: click a point on its camera and it strikes there |
| Heavy Loitering Munition | Ramp-launched munition | Slower, bigger warhead, flown by hand |
| Strike Plane | Armed plane | Ground attack with rockets |
| Attack Plane | Armed plane | Fast machine-gun plane for hunting other aircraft |
| Gunship | Armed plane | Huge, slow and loud. Circles overhead while you man the cannon, rockets and howitzer |
| Recon UAV | Unarmed plane | Long-endurance scout that marks enemies on your map |
| Cruise Missile | Vertical strike | Launched from a pad, waits out of sight, then you steer its dive onto a target |

## Gameplay

### FPV drones

1. Place a drone and right-click it with an **FPV Remote** to link it to you.
2. Right-click it with a **Battery Pack**. Light packs are fast but run out quickly; heavy packs last long but are
   slower. Racers can also take a **Fragmentation Grenade**, and the DJI Drone takes **drop bombs**.
3. Wear the **FPV Goggles**, hold the remote and press **G** to fly.

While you fly, your body stays where you were standing and can still be hit, and other players can hear its radio
static. Racers explode on any impact. Fly out of signal range and the drone keeps going on your last input. If you
exit on purpose, it hovers in place until you come back.

### Ramp-launched units

1. Place a **Munition Ramp** (loitering munitions) or a **Plane Ramp** (planes and the Recon UAV), then right-click it
   with the unit.
2. Fuel the unit with a **Fuel Can** and load ammo by right-clicking the ramp with it.
3. Right-click the ramp with a **Mission Planner** to link it. From then on you can plan and launch from anywhere.
4. Open the planner (right-click air), plan a route on the map and press **Launch**. Some units also offer
   **Launch & Fly**.

You choose what a unit does at the end of its route:

- Loitering munitions: **Loiter**, **Dive** on the last waypoint, or **Destroy** a target marked on the map or in the
  world.
- Planes: **Loiter** or **Return** to land at the ramp.
- Recon UAV: **Loiter**, **Loop** or **Return**.

Once a unit is in the air, you can **View** through its camera or **Take control**. Planes have no warhead, but
their fuel and ammo still explode if they crash or are shot down. They only survive a proper landing.

### Cruise Missile

1. Place a **Missile Launch Pad**, load a **Cruise Missile** and fuel it.
2. Link a **Missile Control Tablet** to the pad, open the tablet and press **LAUNCH**.
3. The missile climbs out of sight and waits on standby until its fuel runs out.
4. Press **SELECT TARGET** and click a point on the map. You take over high above that point and steer the dive.
   Hold LMB to boost.

Everyone near the target hears a siren and gets an inbound warning. The missile is very tough, but it can be shot
down on the pad, during the climb and in the dive.

## Controls

All mod keys can be rebound under Controls -> Combat Drones.

**FPV racers**

| Key | Action |
|---|---|
| G | Enter / exit FPV |
| Mouse, arrow keys | Pitch and roll |
| A / D | Yaw |
| W / S | Throttle (climb / descend in Hover mode) |
| H | Flight mode: Acro, Angle, Hover |
| N | Motors on / off |
| LMB | Airburst the grenade |

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
| R | Switch weapon (Gunship) |
| G / Esc | Return to your body |

## For server owners

- All balance settings are in `config/combat_drones.toml`. `/combat_drones config_reload` (op) reloads it in game,
  and `/combat_drones reload_client_config` reloads a player's own client options.
- Damage from drones, shrapnel, rounds and rockets counts as coming from the pilot, so
  `/scoreboard teams option <team> friendlyfire false` protects teammates.
- Recon sightings and locks are shared with the owner's scoreboard team by default (`reconIntelForAllies`,
  `activeLockForAllies`). Teams listed in `ignoredTeams` (default `ffa`) don't share anything.
- Drones fly through the world border unless `worldBorderCollision = true`.

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

