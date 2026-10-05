# Combat Drones

Forge 1.12.2 mod adding pilotable and autonomous combat drones for PvP.

## Toolchain

- Minecraft 1.12.2, Forge 14.23.5.2860, MCP `stable_39`
- [RetroFuturaGradle](https://github.com/GTNewHorizons/RetroFuturaGradle) 1.4.9 on Gradle 8.14.3 (wrapper included)
- Gradle runs on **JDK 21**, pinned in `gradle/gradle-daemon-jvm.properties`
- Minecraft compiles and runs on a **JDK 8 toolchain**, set in `build.gradle`. If no local JDK 8 is found, Gradle downloads one through the foojay resolver.

The JDK that launches `./gradlew` can be anything from 8 up. The daemon and toolchain JDKs get picked automatically.

## Setup

### Arch Linux

```sh
sudo pacman -S jdk21-openjdk jdk8-openjdk
./gradlew build
```

### Windows 11 + WSL2 (Ubuntu)

Clone inside the WSL filesystem (e.g. `~/dev`), not under `/mnt/c`, because Gradle on `/mnt/c` is very slow.

```sh
sudo apt install openjdk-21-jdk openjdk-8-jdk
./gradlew build
```

`runClient` needs WSLg (Windows 11 ships it), which gives a GUI window out of the box.

## Running

```sh
./gradlew build       # compile, run tests, build reobfuscated jar in build/libs/
./gradlew runClient   # dev client
./gradlew runServer   # dev dedicated server
```

Before the first `runServer`, accept the EULA: `echo eula=true > run/eula.txt`.

## Testing

```
./gradlew build                 # compiles and runs the JUnit tests (pure flight/route/lock math)
./gradlew runObfServer          # dedicated server on the reobfuscated jar: catches client-class leaks
tools/server_smoke_test.sh      # scripted server run: dive, destroy, low-fuel dive, gunship return, recon loop
```

`runObfServer` needs `run/obfuscated/eula.txt` (`eula=true`). The smoke test expects the flat dev world in
`run/world` and prints where each test unit ended up; its full log is `build/smoke/server.log`.

## Art and sound

Models are Bedrock `geo.json` files rendered with [GeckoLib](https://www.curseforge.com/minecraft/mc-mods/geckolib)
1.12.2 (3.0.31). GeckoLib is needed on clients only; the dedicated server runs without it.

```
python3 tools/art/gen_models.py [name ...] [--preview DIR]  # geo models + textures (DIR gets 4-view PNG sheets)
python3 tools/art/gen_items.py [--preview FILE]             # 16x16 item sprites, goggles armour layer, rearm station
python3 tools/art/build_sounds.py                           # engine, weapon and explosion sounds from reference_mods/
python3 tools/gen_sounds.py                                 # synthesized UI sounds (link, beeps, clicks, chime, hit)
```

The generated files are committed, so none of these are needed to build. Models open in Blockbench (Bedrock
format); once one is edited there, remove it from `MODELS` in `gen_models.py` so a rerun doesn't overwrite it.
`build_sounds.py` reads the reference mod jars, which stay local (`reference_mods/` is gitignored).

## Server admin

- All balance knobs live in `config/combat_drones.toml`. `/combat_drones config_reload` (op) re-reads it in game
  for the server and every player (each client re-reads its own `[client]` options) and lists what changed; a file
  that doesn't parse is rejected and the old values stay. Players can apply their own `[client]` edits with
  `/combat_drones reload_client_config`.
- Drones fly through the world border unless `worldBorderCollision = true`; the Mission Planner map shows the border.
- Friendly fire follows vanilla teams: drone blasts, shrapnel, gunfire and rockets are credited to the pilot, so
  `/scoreboard teams option <team> friendlyfire false` protects teammates.
- Recon intel is owner-only by default; `reconIntelForAllies = true` shares it with the owner's scoreboard team
  (leave it off for free-for-all games that put everyone on one team).
