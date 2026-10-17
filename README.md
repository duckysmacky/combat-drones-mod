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
```

`runObfServer` needs `run/obfuscated/eula.txt` (`eula=true`).

## Dependencies

[GeckoLib](https://www.curseforge.com/minecraft/mc-mods/geckolib) 1.12.2 (3.0.31) renders the models. It's needed on
clients only; the dedicated server runs without it.
