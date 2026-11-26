# Combat Drones

Modern drone warfare for Minecraft 1.12.2 PvP: FPV kamikaze drones, loitering munitions, armed planes and a cruise missile. 

![Platform](https://img.shields.io/badge/platform-Forge-E04E14)
![Minecraft](https://img.shields.io/badge/minecraft-1.12.2-62B47A)
[![Version](https://img.shields.io/github/v/release/duckysmacky/combat-drones-mod?include_prereleases&label=version)](https://github.com/duckysmacky/combat-drones-mod/releases)
[![Downloads](https://img.shields.io/github/downloads/duckysmacky/combat-drones-mod/total?label=downloads)](https://github.com/duckysmacky/combat-drones-mod/releases)

## About

Combat Drones is 1.12.2 Forge mod which adds a variety of modern autonomous and controllable combat aircrafts, ranging from simple kamikaze FPV drones and loitering munition, to controllable warplanes, bomber planes, gunship and even a cruise missile. Fly them yourself in first person, or plan a route on a tactical map and send them out while you stay on the ground.

Nothing is a free kill. Every vehicle is loud, can be shot down, and leaves your body standing where you took off. There are also a variety of gadgets to counter the aircrafts: a pilot detector, EMP gear and a hominh Stinger launcher. 

All of it runs server-side, so it holds up on a real multiplayer server. Perfect for realistic modern gun PVP. Everything is data-driven and pretty much ***anything*** can be configured via the mod's configuration file.

This mod was inspired by the [FPV Drones](https://www.curseforge.com/minecraft/mc-mods/fpvdrones) mod for Minecraft 1.20.1. It was originally going to be a port of this modern version mod to 1.12.2 (for our custom gun PVP server), but the mod quickly grew into something more unique and feature complete.

> [!NOTE]
> Combat Drones isn't on CurseForge or Modrinth yet, a release there is coming soon. For now, grab the jar from the
> [Releases page](https://github.com/duckysmacky/combat-drones-mod/releases) or build it from source
> (see [Installation](#installation)).

*Screenshots coming soon*

## Highlights

- **12 vehicles** in four families: hand-flown FPV and DJI drones, loitering munitions launched from ramps, controllable combat planes and a vertically launched cruise missile.
- **First-person piloting** through FPV goggles, with an analog-style HUD, signal range and battery drain.
- **Mission Planner**: plan waypoint routes on a map, launch from your linked ramps and runways anywhere, land planes back on a runway, re-route units in flight, watch through their cameras or take control.
- **Recon and target locking**: scouts spot enemies for you and your team, and a locked target stays tracked even
  out of sight.
- **Counterplay**: the **Pilot Detector**, three EMP devices and the **Stinger** launcher. A plane shot to 0 HP falls burning with its pilot aboard, an EMPed vehicle glides down out of control, then flies on if the EMP wears off.
- **Server-friendly**: every stat lives in one config file that reloads in game, and vanilla scoreboard teams drive friendly fire and intel sharing.

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

## Documentation

The full manual lives in the [wiki](https://github.com/duckysmacky/combat-drones-mod/wiki):

- **[Gameplay](https://github.com/duckysmacky/combat-drones-mod/wiki/Gameplay)** - flying drones, launching units,
  the Mission Planner, the cruise missile and counter-drone gear.
- **[Controls](https://github.com/duckysmacky/combat-drones-mod/wiki/Controls)** - every keybind.
- **[Server Setup](https://github.com/duckysmacky/combat-drones-mod/wiki/Server-Setup)** - config, teams and
  friendly fire.

## Installation

Download `combat_drones-<version>.jar` from the latest release on the [Releases page](https://github.com/duckysmacky/combat-drones-mod/releases) and drop it into `mods/`.

### Requirements

- Minecraft **1.12.2** with Forge **14.23.5.2860**
- [GeckoLib](https://www.curseforge.com/minecraft/mc-mods/geckolib) for 1.12.2 (version **3.0.31**) for client side

### Building from source

Building needs JDK 21 and JDK 8. If JDK 8 is missing, Gradle downloads it automatically.

```sh
git clone https://github.com/duckysmacky/combat-drones-mod.git
cd combat-drones-mod
./gradlew build
```

Copy `build/libs/combat_drones-<version>.jar` (the one without a `-dev` or `-shaded` suffix) into the `mods/`
folder of your client and server, together with GeckoLib on clients.

<details>
<summary>Development</summary>

Requires **JDK-8** and **JDK-21**

```sh
./gradlew runClient      # dev client
./gradlew runServer      # dev server
./gradlew runObfServer   # dev server on the release jar
```

Built with [RetroFuturaGradle](https://github.com/GTNewHorizons/RetroFuturaGradle) 1.4.9 (Gradle 8.14.3, wrapper included).

</details>

## Credits

- Inspired by [FPV Drones](https://www.curseforge.com/minecraft/mc-mods/fpvdrones) (Minecraft 1.20.1).
- Models are rendered with [GeckoLib](https://www.curseforge.com/minecraft/mc-mods/geckolib).
