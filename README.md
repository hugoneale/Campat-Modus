# Campat Modus

Run **NeoForge 1.21.1** mods, and experimentally **Forge / NeoForge 1.20.1** mods, on **Forge 1.21.1** (Forge 52).

Drop `campat-modus-<version>.jar` into `mods/` next to the other mods. On start it finds the
NeoForge and 1.20.1 jars, converts them into Forge 1.21.1 mods (cached in `.campat/cache`, so
only changed jars are converted again) and loads them together with a compatibility runtime.
What happened to each jar is written to `.campat/report.txt`.

> No compatibility layer can promise that *every* mod works. Mods that patch Minecraft
> internals with mixins written against NeoForge's patched classes, or that depend on NeoForge
> internals, may still fail. The numbers below are measured, not guessed.

## Status

| What | How it was checked | Result |
|---|---|---|
| NeoForge 1.21.1 API coverage | a NeoForge test mod exercising mod loading, registries, networking, config, capabilities, attachments, data maps, custom ingredients, GUI layers, menus, client extensions, ticks, ... on a real Forge server **and** a real (headless) client that joins it | 65/65 checks pass |
| Real NeoForge 1.21.1 mods | the 200 most downloaded NeoForge 1.21.1 mods on Modrinth, each booted on a Forge 1.21.1 dedicated server with its dependencies | see `tools/test/boot_matrix.py`; latest run below |
| Forge 1.20.1 API layer | the same test mod and headless client, plus 14 popular 1.20.1 client/common mods (AppleSkin, Jade, Patchouli, GeckoLib, Tom's Storage, Cloth Config, ...) converted and loaded together; the client joins the server | 65/65 checks pass, world joined |
| Real Forge 1.20.1 mods | 138 most downloaded Forge 1.20.1 mods, each booted on a Forge 1.21.1 dedicated server with its dependencies; static link audit of all 207 jars (`./gradlew :tools:audit`) | **108 of 138 boot**; see below |

Latest server boot matrix (NeoForge 1.21.1 corpus): **BOOT_OK of 197 boot**, see the end of this
file for what the rest need.

## How it works

**Loader** (`loader/`, a Forge mod locator service in the same jar)

* detects NeoForge jars (`neoforge.mods.toml`, NeoForge 20.x style `mods.toml`, nested jar-in-jar)
  and Minecraft 1.20.1 jars (version ranges plus SRG member names in the classes);
* hides them from Forge's own mods folder scan and hands converted copies to FML;
* converts `neoforge.mods.toml` to Forge's `mods.toml`, access transformers, mixin configs,
  refmaps, data pack formats (`neoforge:conditions`, biome/structure modifiers, loot modifiers);
* rewrites bytecode with ASM: class renames (NeoForge -> Forge, ~1000 generated pairs),
  member renames resolved through the class hierarchy, redirects of call sites, field reads and
  method references to static helpers, class/interface kind fixes, mixin field retypes, and
  **signature evolution** (overrides of a method whose signature changed get a bridge with the new
  signature; old-signature calls are adapted), and generates a Forge `@Mod` entry per NeoForge mod.

**Runtime** (`runtime/`, embedded in the jar, loaded as mod `campat_modus`)

* NeoForge API classes under their NeoForge names where Forge has no equivalent (networking
  payloads, `DeferredRegister`/`DeferredHolder`, capabilities, data attachments, data maps,
  custom ingredients, the 60 extension interfaces mixed into vanilla classes, events, ...);
* bridges that fire NeoForge events from their Forge counterparts and copy results back;
* two-way capability interop, attachment persistence, GUI layer mapping, client extensions;
* NeoForge's `@OnlyIn`-free behaviour on dedicated servers.

**1.20.1 layer** (`tools/legacy*`, `runtime/.../legacy`)

* SRG member names are chained through Fabric intermediary names (stable across Minecraft
  versions) to 1.21.1 Mojang names: ~64k members, 116 renamed classes;
* generated from a diff of 1.20.1 and 1.21.1 (`tools/.../LegacyGen.java`): bridges for members
  whose types changed mechanically (registry objects becoming `Holder`s / `ResourceKey`s, narrowed
  types, Codec to MapCodec), constructor and method adapters that reorder, drop, convert or supply
  arguments (block constructors, `register(...)` methods, ...), packet constructors via their
  `STREAM_CODEC`, evolutions for overridable methods and access widening for members that were
  public in 1.20.1; the same diff runs over Forge 47 and Forge 52;
* converter features beyond renaming: constructor adapters that also work for `super(...)` calls,
  generated implementations of abstract methods platform types gained (`codec()`, ...), bridges
  for changed override hooks (`Block.use` to `useItemOn`, `playerWillDestroy`, ...), lambdas of
  changed functional interfaces, stand-in parents for classes that became final (enchantments),
  cast redirects and accessor redirects for mixin accessors of removed members;
* emulation of removed APIs: item NBT on data components (with Mojang's data fixers for old NBT),
  recipes on `Container`s, JSON recipe / ingredient / condition / loot serializers as codecs,
  advancement triggers, armor materials and tool tiers, entity data definition, Forge 47
  `SimpleChannel` / event channels, HUD overlays, brewing, potions, resource packs, the old
  `BufferBuilder` lifecycle, ...;
* data packs: singular folder names, recipe results, Forge 47 conditions, loot functions.

## Building and testing

```
./gradlew campatJar                 # build/libs/campat-modus-<version>.jar
./gradlew :tools:setupEnvs          # Forge / NeoForge installs used by the tools and tests
tools/test/e2e.sh <workDir>         # Forge server + headless client with the NeoForge test mod
python3 tools/test/boot_matrix.py <modsDir> <outDir>   # boot each mod (and its deps) on a server
./gradlew :tools:audit -Pmods=<dir> # static link check of converted mods against Forge 1.21.1
./gradlew :tools:checkMappings :tools:checkLegacyMappings   # every redirect target exists
```

## Known limitations

* **Mixins against patched code.** NeoForge and Forge patch Minecraft differently; a mixin that
  targets a NeoForge patch, a lambda name or local variable layout of NeoForge's build can fail.
  Forge 1.21.1 also ships an older Mixin than NeoForge (no `@Inject` into the middle of
  constructors), so such mixins fail too.
* **Kotlin mods** need Kotlin for Forge (the Forge build) installed, as they need it on NeoForge.
* **Client-only mods on a server**: some reference client classes that are not in any server jar;
  they fail on NeoForge servers as well.
* **1.20.1 mods** are best effort (108 of 138 popular mods boot a server; of the rest, about half
  are Kotlin, client-only, Fabric-based or FML-internal mods that cannot work this way). Minecraft changed a great deal between 1.20.1 and 1.21.1
  (data components, holders, rendering); small and mid-sized mods have the best chance, large
  content mods usually still hit removed APIs. Their mixins are made non-fatal, which keeps the
  game running but may disable parts of a mod.
* NeoForge features not emulated yet are listed per jar in `.campat/report.txt`.

## License

LGPL-2.1 (contains code derived from NeoForge, which is LGPL-2.1).
