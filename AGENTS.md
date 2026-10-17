# AGENTS.md

Instructions for coding agents working on Combat Drones, a Minecraft 1.12.2 Forge mod.

Agent-only context lives in `.context/` (gitignored). **Read `.context/README.md` first**: it indexes the gameplay
spec, architecture, roadmap, dev notes and open issues. The spec there is the source of truth for names, stats and
behaviour.

## Identity

| | |
|---|---|
| Display name | Combat Drones |
| Mod ID (modid, resource-location namespace) | `combat_drones` |
| Java base package | `io.github.duckysmacky.combatdrones` |
| GitHub repo | `combat-drones` |

> [!NOTE]
> Forge mod IDs and Java package segments can't contain hyphens: the repo is `combat-drones`, the modid is
> `combat_drones` and the package segment is `combatdrones`. Keep this mapping consistent in `@Mod`/`mcmod.info`,
> resource locations (e.g. `combat_drones:racer_standard`) and every `package` line.

## Toolchain

- Minecraft 1.12.2, Forge **14.23.5.2860**, MCP **stable_39**, Java 8 source and target.
- **RetroFuturaGradle 1.4.9** on Gradle 8.14.3. Minecraft compiles and runs on a **JDK 8 toolchain** (foojay
  auto-download); the Gradle daemon runs on **JDK 21** (`gradle/gradle-daemon-jvm.properties`). Don't bump RFG to
  2.x without moving the daemon to JDK 25.
- **GeckoLib 1.12.2 (3.0.31)** renders models from Bedrock `geo.json`. Client-only (`required-client:geckolib3`):
  only `client/GeoModels` touches it, nothing implements `IAnimatable`.
- Tests: JUnit 5 in `src/test/java`, run by `./gradlew build`.

## Architecture rules

1. **Common/client/server split.** The dedicated server must start and run without touching any client-only
   class. Use proxies and `@SideOnly` correctly. GeckoLib classes are referenced only from `client/`.
2. **Server-authoritative state.** Position, orientation, battery, fuel, ammo, payload and route data all live
   server-side. Clients send *intent* (control input, waypoint edits, trigger presses) as packets; the server
   validates and applies. Never trust a client-reported position, hit, or "target destroyed" claim.
3. **Validate everything from the client.** Rate-limit control/trigger packets, clamp values to config bounds, and
   confirm the sender owns or is piloting the drone/munition before applying anything.
4. **Physics is pure Java.** Flight, guidance and autopilot math lives in `physics/` with zero `net.minecraft.*`
   imports, so it's JUnit-testable. Minecraft-side code adapts its output to entities and packets.
5. **Data-driven types.** Every drone/munition model and variant is data (`assets/combat_drones/drone_types/<id>.json`
   plus `[drones.<id>]` config), not a Java class per variant. Adding a variant shouldn't require new Java.

## Working method

1. Don't write code first. Read the relevant `.context/` docs and the code involved, then propose a short plan
   (steps, open questions, assumptions) and wait for approval.
2. Work in steps from `.context/ROADMAP.md` (or the agreed plan for a one-off change).
3. After each step: confirm it builds, run client and dedicated-server smoke tests where possible, list what to
   test manually in-game, and commit.
4. Keep `.context/spec/` in sync with what's built, in the same change. If a change forces a new or different
   architecture rule, update this file too.
5. Log anything deliberately deferred or cut in `.context/TODO.md` instead of dropping it silently.

## Build and test

```sh
./gradlew build          # compile, JUnit tests, reobfuscated jar in build/libs/
./gradlew runClient      # dev client
./gradlew runServer      # dev dedicated server
./gradlew runObfServer   # dedicated server on the reobfuscated jar: catches client-class leaks
```

Client and server share `run/` (gitignored). The dev server needs `run/eula.txt` with `eula=true`;
`runObfServer` needs `run/obfuscated/eula.txt`. Scripted server smoke test and asset generators: see
`.context/DEVELOPMENT.md`.

## Config

Everything tunable lives in one TOML file, `config/combat_drones.toml` (`config/ModConfig.java`). Balance numbers
are never hardcoded. Sections, keys and sync rules: `.context/spec/CONFIG.md`.

## Tools and skills

- **graphify**: build or query the code graph (`graphify-out/`, gitignored) for fast navigation before broad
  searches.
- **context-manager** (with **caveman**): multi-session work goes in `.context/<issue>/`, catalogued in
  `.context/ISSUES.md`.
- **investigate-first**: unclear bugs (desync, side leaks, intermittent behaviour) before editing.
- **minecraft-testing**: JUnit tests for the pure-Java physics and type parsing.
- **minecraft-resource-pack**: models, textures, sounds, lang and blockstates (use 1.12 formats: `.lang`,
  `pack_format` 3).
- **minecraft-ci-release**: CI, versioning and publishing for the public release.
- **minecraft-imagegen**: mod icon, promo art, concept textures.
- **generate-changelog**: release notes from commits.
- **minecraft-modding** covers modern loaders only: use it for general patterns, never for 1.12.2 APIs.

These skills target modern Minecraft versions; check every API, registry or file format against 1.12.2 Forge
before using it. Local dev tools (`tools/`, gitignored) are described in `.context/DEVELOPMENT.md`.
