# WhySoSlow

A NeoForge 26.1 mod that performs extremely detailed performance analysis of mods during startup, world loading, and world generation.

## WARNING

**This mod is a debugging tool only. Do not ship it in production modpacks.**

WhySoSlow uses Mixin to inject into Forge internals and Minecraft's worldgen pipeline. This means:

- **It is itself a source of performance loss.** Every mod event, every placed feature, every structure, every carver, and every noise fill operation is wrapped with timing instrumentation. This overhead is unavoidable and will make your game slower while the mod is installed.
- **It mixes into critical code paths.** Mixins are applied to `MinecraftServer`, `ServerLevel`, `ChunkStatusTasks`, `ChunkGenerator`, `NoiseBasedChunkGenerator`, `PlacedFeature`, `StructureStart`, `ConfiguredWorldCarver`, `SimpleReloadInstance` and, on the client, `SpriteLoader`. While these injections are carefully written, they carry inherent risk.
- **It samples thread stacks.** A background thread takes stack samples of any mod listener, gap between listeners or server tick that runs long. Sampling briefly pauses the sampled thread.
- **Install it, collect your data, then remove it.** Do not leave this mod installed longer than necessary.

## What It Does

### Startup Profiling (Automatic)

On every game launch, WhySoSlow adds highest and lowest priority listeners to every mod's event bus to time each mod's handling of every lifecycle event. When loading completes, a report is written to:

```
logs/whysoslow/startup.log
```

The report includes for every mod, sorted slowest to fastest:
- Wall-clock time, CPU time, and memory delta per loading phase (Construction, Registry, Common Setup, Client Setup, Load Complete, IMC)
- Registry entry counts (blocks, items, entities, etc.)
- Overall phase breakdown and total startup time
- **Hot spots** for any mod listener that runs longer than 250ms: which mods and methods the thread was in, and the most common stacks
- **Framework work between mod listeners**: busy time on a loading thread while no mod listener was running (registry freezing, the block state cache rebuild), with the mods before and after and what the thread was doing

### Resource Reload Profiling (Automatic)

Every client resource reload and server data pack reload is profiled per reload listener (prepare and apply time), and every texture atlas is recorded with its size, mip level and the textures that limit its mip level. The report is rewritten after each reload:

```
logs/whysoslow/resources.log
```

### World Load Profiling (Automatic)

When you load into a world, WhySoSlow measures each dimension's construction time and memory, the initial spawn search (with a per-step breakdown of the spawn area chunks it generates and the slowest structures, features and carvers inside it), and mod contributions during the server start lifecycle. A report is written to:

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
- **Structure starts** - Layout and jigsaw assembly time per structure, separate from block placement
- **Chunk pipeline** - Time per generation step (structure starts through full), the asynchronous noise and biome fills, and per-chunk latency percentiles from the first step to fully generated
- **CPU by thread group** - CPU time of every thread during profiling, grouped by name, so mod-owned worker pools show up
- **Server ticks** - Tick time distribution, and stack samples of the server thread during ticks over 100ms

## Mixin Targets

| Mixin | Target | Purpose |
|---|---|---|
| `MixinMinecraftServer` | `createLevels`, `setInitialSpawn`, `tickServer` | Dimension construction, spawn search and tick timing |
| `MixinServerLevel` | `ServerLevel.<init>` | Dimension load timeline |
| `MixinChunkStatusTasks` | every `ChunkStatusTasks` step | Per-step and per-chunk generation timing |
| `MixinChunkGenerator` | `applyBiomeDecoration`, `tryGenerateStructure` | Per-chunk decoration and per-structure start timing |
| `MixinNoiseBasedChunkGenerator` | `doFill`, `doCreateBiomes`, `buildSurface` | Terrain, biome and surface timing |
| `MixinSimpleReloadInstance` | `SimpleReloadInstance.prepareTasks` | Per-listener reload timing |
| `MixinSpriteLoader` (client) | `SpriteLoader.stitch` | Atlas size and mip limiting textures |
| `MixinPlacedFeature` | `PlacedFeature.placeWithBiomeCheck` | Per-feature timing |
| `MixinStructureStart` | `StructureStart.placeInChunk` | Per-structure timing |
| `MixinConfiguredWorldCarver` | `ConfiguredWorldCarver.carve` | Per-carver timing |

## Building

Requires Java 25.

```bash
export JAVA_HOME="/path/to/jdk-25"
./gradlew build
```

Output JAR will be in `build/libs/`.

## License

[MIT License](LICENSE.md)
