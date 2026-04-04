# WhySoSlow

A Forge 1.20.1 mod that performs extremely detailed performance analysis of mods during startup, world loading, and world generation.

## WARNING

**This mod is a debugging tool only. Do not ship it in production modpacks.**

WhySoSlow uses Mixin to inject into Forge internals and Minecraft's worldgen pipeline. This means:

- **It is itself a source of performance loss.** Every mod event, every placed feature, every structure, every carver, and every noise fill operation is wrapped with timing instrumentation. This overhead is unavoidable and will make your game slower while the mod is installed.
- **It mixes into critical code paths.** Mixin class transformations are applied to `ModContainer`, `MinecraftServer`, `ChunkGenerator`, `NoiseBasedChunkGenerator`, `PlacedFeature`, `StructureStart`, and `ConfiguredWorldCarver`. While these injections are carefully written, they carry inherent risk.
- **Install it, collect your data, then remove it.** Do not leave this mod installed longer than necessary.

## What It Does

### Startup Profiling (Automatic)

On every game launch, WhySoSlow intercepts Forge's `ModContainer.acceptEvent` to time every mod's handling of every lifecycle event. When loading completes, a report is written to:

```
logs/whysoslow/startup.log
```

The report includes for every mod, sorted slowest to fastest:
- Wall-clock time, CPU time, and memory delta per loading phase (Construction, Registry, Common Setup, Client Setup, Load Complete, IMC)
- Registry entry counts (blocks, items, entities, etc.)
- Overall phase breakdown and total startup time

### World Load Profiling (Automatic)

When you load into a world, WhySoSlow measures dimension creation time, memory usage, and mod contributions during the server start lifecycle. A report is written to:

```
logs/whysoslow/worldload.log
```

### World Generation Profiling (On-Demand)

Use the `/whysoslow` command to profile runtime world generation:

| Command | Description |
|---|---|
| `/whysoslow start` | Begin profiling. Builds registry lookup maps and starts recording all worldgen operations. |
| `/whysoslow stop` | Stop profiling and write the report. |
| `/whysoslow status` | Show current profiling state and stats. |

Explore the world while profiling is active to generate chunks, then stop to get your report at:

```
logs/whysoslow/worldgen.log
```

The worldgen report breaks down:
- **Overall category split** - Noise generation, surface building, feature placement, structure generation, and carvers with percentages
- **Every placed feature** - Total time, average time, max time, and call count, attributed to the owning mod
- **Features grouped by mod** - Which mods' worldgen features cost the most
- **Every structure** - Generation time per structure type
- **Every carver** - Cave/ravine carving time per carver type

## Mixin Targets

| Mixin | Target | Purpose |
|---|---|---|
| `MixinModContainer` | `ModContainer.acceptEvent` | Per-mod lifecycle event timing |
| `MixinMinecraftServer` | `MinecraftServer.createLevels` | Dimension creation timing |
| `MixinChunkGenerator` | `ChunkGenerator.applyBiomeDecoration` | Per-chunk decoration timing |
| `MixinNoiseBasedChunkGenerator` | `fillFromNoise`, `buildSurface` | Terrain and surface timing |
| `MixinPlacedFeature` | `PlacedFeature.placeWithBiomeCheck` | Per-feature timing |
| `MixinStructureStart` | `StructureStart.placeInChunk` | Per-structure timing |
| `MixinConfiguredWorldCarver` | `ConfiguredWorldCarver.carve` | Per-carver timing |

## Building

Requires Java 17.

```bash
export JAVA_HOME="/path/to/jdk-17"
./gradlew build
```

Output JAR will be in `build/libs/`.

## License

[MIT License](LICENSE.md)
