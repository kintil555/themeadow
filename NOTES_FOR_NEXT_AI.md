# The Meadow — Handoff Notes (checkpoint)

Status: early scaffolding only. Do NOT assume the project compiles yet —
it has never been built. No CI, no in-game test yet.

## What this mod is
- Minecraft Java 26.2, Fabric, mojmap (no Yarn — 26.x ships Mojang mappings
  natively, confirmed from the real client jar).
- Adds a new dimension "The Meadow": rolling (non-flat) grassy hills with
  poppies. Reached via portal ENTITIES (not portal blocks/frames) that
  randomly spawn near the player:
  - wall-mounted: flush in a solid wall, ~2 blocks tall, player-width
  - floating: vertical orientation but facing a RANDOM horizontal direction,
    and MUST look jagged/torn at the edges (user explicitly said "gak kotak
    sempurna" / not a perfect rectangle) — like the reference screenshot
    (image 1 in the conversation: a glowing rift with a jagged white outline).
  Portal see-through / no loading screen / real-time cross-dimension view
  is exactly what Seamless Portals (Immersive Portals fork) gives for free
  once you spawn its Portal entity — do not build any teleport-fade/loading
  screen logic, that would be wrong per user's ask.
- The Meadow's "owner" entity: **the Shepherd**. Walks around, sometimes
  stands still staring at the player from a distance, sometimes chases,
  then VANISHES (teleports away) instead of ever attacking. User supplied
  a transparent PNG (a cartoonish black-robed figure with a crook) meant to
  be rendered as a flat 2D billboard sprite, NOT a normal 3D block-model mob.

## Dependency: Seamless Portals (Immersive Portals fork for 26.2)
- User-uploaded jar: `libs/seamlessportals-fabric-1_0_0.jar` (already copied
  into the project, referenced in build.gradle as a local file dep).
- fabric.mod.json id `seamlessportals`, depends on minecraft>=26.2,
  fabricloader>=0.19.3, fabric-api, cloth-config (bundled jar-in-jar, don't
  add cloth-config separately).
- Key classes (already decompiled with CFR into /home/claude/work/dec2,
  dec4 if that scratch dir still exists — otherwise re-decompile, it's fast,
  see "How to decompile" below):
  - `qouteall.imm_ptl.core.portal.Portal` — the portal entity itself.
    `Portal.ENTITY_TYPE` is `public static final EntityType<Portal>`,
    registry id `immersive_portals:portal`. It's a normal spawnable Entity
    (`MobCategory.MISC`, sized 0x0 collision box — actual portal plane size
    comes from setWidth/setHeight below). Spawn via normal
    `level.addFreshEntity(portal)` after configuring it (McHelper.spawnServerEntity
    also works, PortalAPI.spawnServerEntity is a thin deprecated wrapper for it).
  - `qouteall.imm_ptl.core.api.PortalAPI` (this is the real workhorse):
    - `setPortalOrthodoxShape(Portal, Direction facing, AABB portalArea)` —
      use this for the WALL-MOUNTED portal variant. Give it the wall's
      facing direction and an AABB matching the desired opening rectangle
      (e.g. 1 wide x 2 tall x thin depth against the wall).
    - `setPortalPositionOrientationAndSize(Portal, Vec3 position,
      DQuaternion orientation, double width, double height)` — use this
      for the FLOATING portal variant. Build a DQuaternion that keeps the
      portal plane vertical but rotates it to a random yaw (see
      "Floating portal orientation" below for the math).
    - `setPortalTransformation(Portal, ResourceKey<Level> destDimension,
      Vec3 destPosition, @Nullable DQuaternion rotation, double scale)` —
      call this to actually link the portal to The Meadow. rotation can be
      null, scale should be 1.0 unless we want a size mismatch effect.
    - `addGlobalPortal(ServerLevel, Portal)` exists too (for portals that
      should always be network-synced/visible regardless of chunk loading —
      probably NOT needed for player-triggered random portals, only spawn
      normally with addFreshEntity; addGlobalPortal is more for fixed
      always-there portals). Don't use unless normal spawning turns out
      unreliable.
    - `teleportEntity(Entity, ServerLevel targetWorld, Vec3 targetPos)` is
      available if we ever need manual teleport outside of just walking
      through the portal plane (probably NOT needed — walking into the
      portal entity's plane should auto-teleport per Immersive Portals'
      own logic, that's the whole point of the mod).
  - There's also a higher-level `com.warwa.seamlessportals.api.SeamlessPortalsAPI`
    + `IPortalDefinition` — this is for registering a whole CUSTOM PORTAL
    TYPE (like "these blocks form a portal automatically"), which is NOT
    what we want since we're spawning portal entities directly and
    procedurally, not defining a block-pattern portal. Ignore this API
    unless requirements change to "portal appears when you build X blocks".
  - `qouteall.dimlib.api.DimensionAPI` — only needed for RUNTIME dynamic
    dimension creation. We don't need this: The Meadow will be a normal
    static datapack dimension (JSON files under
    src/main/resources/data/themeadow/dimension/, dimension_type/,
    worldgen/), registered at load time like any vanilla-style custom dim.
    Do NOT use DimensionAPI.addDimensionIfNotExists unless we decide to
    make Meadow instances per-player/dynamic later (out of scope now).

### Floating portal orientation (the jagged one, still unimplemented)
User confirmed: floating portal must stay perfectly VERTICAL (like a
nether portal standing in mid-air) but face a RANDOM horizontal direction
(random yaw), AND the visual shape must look torn/jagged at the edges —
explicitly NOT a clean rectangle.
- Vertical + random yaw = build a DQuaternion representing a rotation
  around the world Y axis by a random angle, then feed that into
  setPortalPositionOrientationAndSize as the `orientation` param (axisW
  becomes the horizontal in-plane axis after rotation, axisH stays +Y).
  Have NOT located DQuaternion's exact factory methods yet (it's in
  `qouteall.q_misc_util.my_util.DQuaternion` — need to decompile it before
  writing this code; skipped so far in favor of getting Shepherd/build
  files done first).
- Jagged edges: the portal's actual teleport/collision plane must stay a
  clean rectangle (gameplay reasons — Immersive Portals' teleport math
  assumes a flat quad). The "torn" look should be done purely visually:
  either (a) a custom overlay texture with alpha-cutout jagged edges drawn
  slightly in front of/around the portal's own render, via a Fabric
  rendering-events callback (WorldRenderEvents.AFTER_TRANSLUCENT or similar
  — NOT yet confirmed against the 26.2 Fabric API, check fabric-api docs/
  jar), or (b) see if Portal has a way to set a custom "wobble"/"cutout"
  edge mask directly (check qouteall.imm_ptl.core.render, class names like
  `PortalRenderer`, `PortalRenderInfo`, or similar — NOT yet inspected).
  This is the single biggest unresolved design/implementation question
  in the whole project. Do this investigation BEFORE writing any portal
  spawner code, since it may change what shape data we need to store per
  portal (e.g. a random seed for a jagged mask pattern).

## What's already written and jar-verified (safe to trust, do not redo)
- `build.gradle`, `settings.gradle`, `gradle.properties` — fabric-loom
  1.15.2, MC 26.2, fabric-loader 0.19.5, fabric-api 0.160.0+26.2, Java 25
  toolchain, local file dep on libs/seamlessportals-fabric-1_0_0.jar.
  NEVER built with real Gradle yet (network sandbox likely can't reach
  Fabric/Mojang/Modrinth maven anyway — see "Build/verify limitations"
  below). Treat version numbers as best-effort, re-check if a real build
  ever runs and fails on dependency resolution.
- `src/main/resources/fabric.mod.json` — depends on fabric-api and
  seamlessportals (id "seamlessportals", matches the jar's own modid).
  mixins array is empty (no mixins written yet).
- `src/main/java/com/themeadow/entity/ShepherdEntity.java` — COMPLETE,
  every method signature cross-checked against the real decompiled 26.2
  jar (see list of gotchas below). Custom PathfinderMob, empty
  registerGoals() (goal list deliberately unused), full behavior driven
  from customServerAiStep with a 4-state machine: IDLE → WATCHING →
  CHASING → VANISHING → back to IDLE (teleports 18-32 blocks away from
  player on vanish, plays ENDERMAN_TELEPORT sound). doHurtTarget always
  returns false (never damages player). isPushable/canBeCollidedWith
  return false (per user: it's a spooky watcher, not a physical mob you
  bump into — this was MY design choice, not explicitly requested;
  reconsider if user wants it physically solid).
  Exposes isWatching()/isVanishing() for the renderer to use (e.g. fade
  out during vanish, maybe a "stare" pose while watching — NOT yet
  consumed by any renderer code).
- `src/main/java/com/themeadow/entity/client/ShepherdRenderState.java` —
  just a stub (3 fields: vanishing, watching, walkAnim). The actual
  ShepherdRenderer class (extends EntityRenderer<ShepherdEntity,
  ShepherdRenderState> directly — NOT LivingEntityRenderer, since that
  requires a real EntityModel and we want flat billboard geometry instead)
  has NOT been written yet. This is the next big piece of work.

## 26.2 API facts verified from the real jars (both mcjar 26_2-0_19_5.jar
## and the seamlessportals jar) — use these, don't re-guess from older MC knowledge
- Rendering moved to a submit-node architecture. `EntityRenderer<T,S>` is
  abstract over an entity type T and a per-frame `EntityRenderState` S.
  Must implement `createRenderState()` (returns a fresh S) and can override
  `extractRenderState(T entity, S state, float partialTicks)` to copy
  per-frame data from the entity into the state (this runs off the render
  thread's synced snapshot — don't touch live entity fields inside
  `submit()`, only inside `extractRenderState`).
- Actual drawing happens in `submit(S state, PoseStack poseStack,
  SubmitNodeCollector submitNodeCollector, CameraRenderState camera)`.
  For arbitrary custom geometry (our billboard quad), call
  `submitNodeCollector.submitCustomGeometry(PoseStack, RenderType,
  SubmitNodeCollector.CustomGeometryRenderer)` — the callback interface is
  `void render(PoseStack.Pose, VertexConsumer)`. Build the quad manually
  inside that callback using the OLD familiar VertexConsumer chain:
  `vertexConsumer.addVertex(pose, x, y, z).setUv(u,v).setColor(r,g,b,a)
  .setLight(light).setOverlay(OverlayTexture.NO_OVERLAY).setNormal(...)`
  — this part of the API is unchanged from pre-26.x Minecraft.
- Use `RenderTypes.entityCutout(Identifier texture)` (package
  `net.minecraft.client.renderer.rendertype`) as the RenderType for the
  Shepherd PNG — it's alpha-cutout (hard transparency, matches a
  transparent-background PNG with no soft edges) and unaffected by light
  tinting issues that "entityTranslucent" can have. If the PNG turns out
  to have soft/antialiased edges (it does — it's a "removebg" PNG, likely
  has semi-transparent fringe pixels), reconsider `entityTranslucent`
  instead for a cleaner look; test both in-game once buildable.
- `Camera` (net.minecraft.client.Camera) — for billboard-facing math, use
  `camera.rotation()` (returns Quaternionf) or the simpler
  `camera.yRot()`/`camera.xRot()` floats, plus `camera.position()` for the
  camera's world position (needed to compute yaw-to-camera if we want the
  sprite to always face the viewer like a classic billboard, e.g. for the
  "Shepherd standing still staring at player from a distance" beat — a
  billboard sprite doesn't need to actually rotate to face the camera
  necessarily; could also just rotate the WHOLE sprite plane to face
  camera every frame, standard Minecraft billboard technique, similar to
  how vanilla item-frame/text-display or particle billboards work — look
  at how vanilla `ItemRenderer`/`ArmorStandArmorLayer` or particle
  `SingleQuadParticle` build their billboard quads for a copyable pattern,
  NOT yet cross-referenced in this session, do that before writing
  ShepherdRenderer).
- `Mob.createMobAttributes()` still exists, `AttributeSupplier.Builder`
  unchanged.
- `TargetingConditions.forNonCombat()/forCombat()/.range(double)
  /.ignoreLineOfSight()` all still exist with the same names.
- `ServerLevel`/`Level` no longer has a simple
  `getNearestPlayer(Entity, double range)` convenience — must go through
  `ServerEntityGetter.getNearestPlayer(TargetingConditions, LivingEntity
  source, double x, double y, double z)` (default method on an interface
  ServerLevel implements). ShepherdEntity already does this correctly.
- `Mob.getBlockPathType(LevelReader, BlockPos)` is GONE in 26.2 (path-type
  preference logic moved into `NodeEvaluator` subclasses instead). Don't
  try to override it on Mob/PathfinderMob anymore.
- `LookControl.setLookAt(Entity, float yMaxRotSpeed, float xMaxRotAngle)`
  and `PathNavigation.moveTo(Entity target, double speedModifier)` are
  unchanged.
- `Heightmap.Types.MOTION_BLOCKING` unchanged; `ServerLevel.getHeightmapPos`
  inherited, same signature as always.
- `Entity.canBeCollidedWith(@Nullable Entity other)` takes a nullable
  param now (used to be non-null in older versions) — matches what's in
  ShepherdEntity.java already.
- `LivingEntity.pushEntities()` is `protected void`, NOT on Mob directly —
  already overridden correctly in ShepherdEntity.

## How to decompile more classes (proven working recipe this session)
Sandbox has NO javap for JDK25 classfiles (only JDK21 installed, can't
read class version 69). Use CFR instead:
```
curl -sL -o /home/claude/work/cfr.jar \
  "https://github.com/leibnitz27/cfr/releases/download/0.152/cfr-0.152.jar"
java -jar /home/claude/work/cfr.jar path/to/Some.class --outputdir out_dir
```
The real 26.2 client jar (user-uploaded, `/mnt/user-data/uploads/26_2-0_19_5.jar`)
was extracted to `/home/claude/work/mcjar/` — if that scratch dir is gone,
re-extract with `unzip -oq <jar> -d mcjar`. Do the same for
seamlessportals-fabric-1_0_0.jar → `/home/claude/work/ip_jar/`. CFR
correctly decompiles both class versions and gives real, readable mojmap
source with correct method signatures — this is the ONLY reliable way to
verify 26.2 API surfaces in this environment (no internet access to
javadoc/mappings sites from bash_tool — network egress is restricted to
package registries like Maven/npm/PyPI/GitHub, not to Mojang's own sites
or javadoc hosts). Do NOT guess signatures from pre-26.x Minecraft
knowledge/memory without checking — this session caught several breaking
changes that would have caused silent runtime failures or compile errors
(getNearestPlayer, getBlockPathType removal, submit-node rendering).

## Build/verify limitations in this sandbox
- No real Gradle build has been attempted. bash_tool's network egress is
  allowlisted to package registries (npm, PyPI, crates, GitHub) — likely
  does NOT include maven.fabricmc.net, Mojang's piston-meta, or Modrinth's
  maven, so `./gradlew build` will almost certainly fail on dependency
  resolution in this container. If a real build/test is needed, either
  ask the user to run it locally, or check current bash_tool network
  allowlist again (it may change) before assuming it's impossible.
- No decompiled source can be recompiled/linked directly (CFR output is
  read-only reference, not a real classpath) — all API verification has
  been "read the decompiled signature, hand-write matching Java", not
  compiler-checked. Double check anything non-trivial by decompiling
  again if unsure.

## Still TO DO (in rough priority order)
1. DONE — `com.themeadow.portal.PortalOrientation` written. Uses
   `DQuaternion.fromFacingVecs(axisW, axisH)` (confirmed via decompile:
   internally `matrixToQuaternion(axisW, axisH, axisW.cross(axisH))`).
   axisW = horizontal right vector at random yaw, axisH = fixed (0,1,0)
   to force vertical. `randomVerticalFacing(Random)` gives a ready
   DQuaternion for `setPortalPositionOrientationAndSize`.
2. DONE — jagged edge solved via the mod's OWN shape system, not a
   render-event overlay (better than the two options guessed earlier).
   `Portal.setPortalShape(PortalShape)` accepts a `SpecialFlatPortalShape`
   wrapping a `Mesh2D`. Wrote `com.themeadow.portal.JaggedPortalShape
   .build(halfWidth, halfHeight, jaggedness, edgePoints, Random)` — fan-
   triangulates a randomized radial polygon (center point + rim points at
   randomized radius) into a Mesh2D. IMPORTANT: this only changes the
   VISUAL mesh; per user's own requirement the teleport/collision plane
   must stay Portal's normal flat quad (width/height), so do NOT shrink
   width/height to match the jagged silhouette — call
   `portal.setPortalShape(JaggedPortalShape.build(...))` in addition to,
   not instead of, the normal size/orientation setup. Not yet tested for
   visual quality in-game (can't run Gradle in this sandbox) — if the
   torn look is too subtle/too extreme, tune `jaggedness` (try 0.25-0.4)
   and `edgePoints` (16-24).
3. DONE (basic version) — `com.themeadow.portal.RandomPortalSpawner`
   written. `trySpawnFor(ServerPlayer, Random)`: rolls a chance, scans a
   12-block radius for a valid 2-tall solid wall face (wall-mounted case,
   uses `setPortalOrthodoxShape`), else falls back to a random open-air
   spot (floating case, uses `PortalOrientation.randomVerticalFacing` +
   `JaggedPortalShape.build`). NOT YET WIRED to any tick event — caller
   (TheMeadow's server init) must call this from a tick callback, see
   TODO #6/#8 below. Two placeholders left deliberately for TODO #4:
   - `RandomPortalSpawner.meadowDimension` (static field, currently null
     — spawner no-ops until this is set) needs the real Meadow
     ResourceKey<Level> once the dimension JSON exists.
   - `MEADOW_ARRIVAL` is a fixed Vec3(0.5, 100, 0.5) — replace with a
     real safe-surface finder once Meadow terrain worldgen exists.
   Also unverified in-game (no Gradle in sandbox): wall-scan brute-forces
   every block in a 25x7x25 box per call, which is naive — fine for
   testing, revisit for perf once the spawn-chance/frequency is tuned.
4. DONE (first pass) — dimension registration JSON written:
   `data/themeadow/dimension_type/the_meadow.json`, `data/themeadow/
   dimension/the_meadow.json`, `data/themeadow/worldgen/biome/meadow.json`.
   Confirmed via decompiled vanilla jar that 26.2's dimension_type format
   changed from older MC versions: `fixed_time` (int) is now
   `has_fixed_time` (bool) + `default_clock` (references a
   `data/<ns>/world_clock/<name>.json` file — found these are empty `{}`
   files in vanilla, e.g. `world_clock/the_end.json`, so the ACTUAL time
   logic isn't in that file, likely hardcoded per-id in game code) +
   `timelines` (a tag like `#minecraft:in_end`/`#minecraft:in_nether`,
   new feature this session hadn't seen before). Rather than guess this
   brand-new system's semantics, the_meadow.json copies the_end.json's
   time-related fields verbatim (`default_clock: minecraft:the_end`,
   `timelines: #minecraft:in_end`) since End is the only vanilla example
   of a "static time, no day/night cycle" dimension — gives fixed daytime
   lighting without inventing an unverified custom clock/timeline. Sky
   values (fog/sky color) are kept overworld-like for a bright meadow
   look, only the time-related fields are borrowed from the_end.
   Similarly avoided writing custom noise_settings/density_function from
   scratch (overworld.json alone is ~2750 lines, not worth the tokens):
   `dimension/the_meadow.json`'s generator reuses
   `"settings": "minecraft:overworld"` (vanilla's existing noise/terrain
   shape — rolling hills come for free from this) with a `minecraft:fixed`
   biome_source pointing at our one custom biome
   `themeadow:meadow` (grass + poppy features only, monster spawners
   emptied so The Shepherd is the dimension's only real threat/presence).
   `RandomPortalSpawner.meadowDimension` and `MEADOW_ARRIVAL` are NOT yet
   wired to this real key — still need one line in TheMeadow's init:
   `RandomPortalSpawner.meadowDimension = ResourceKey.create(Registries.DIMENSION, Identifier.of("themeadow", "the_meadow"));`
   MEADOW_ARRIVAL's Y=100 is a guess and untested — overworld noise
   settings' actual surface height near spawn may put solid ground lower
   or higher; verify/adjust once in-game testing is possible.
   NOT tested in-game (no Gradle in sandbox) — dimension_type field names
   for 26.2 in particular are a genuine risk area since this is a newer
   MC feature revision than most available documentation covers; if the
   dimension fails to load, re-check `has_fixed_time`/`default_clock`/
   `timelines` against a fresh decompile of the real jar's dimension_type
   loader/codec class first.
5. DONE (first pass, untested) — `com.themeadow.entity.client.ShepherdRenderer`
   written, extends EntityRenderer<ShepherdEntity, ShepherdRenderState>
   directly. Every signature checked against a fresh decompile of the
   real 26.2 jar this session: `SubmitNodeCollector.CustomGeometryRenderer
   .render(PoseStack.Pose, VertexConsumer)`,
   `OrderedSubmitNodeCollector.submitCustomGeometry(PoseStack, RenderType,
   CustomGeometryRenderer)` (3 args, confirmed), `RenderTypes.entityCutout
   (Identifier)` (confirmed), `CameraRenderState.pos` (Vec3, confirmed —
   class is actually in
   `net.minecraft.client.renderer.state.level.CameraRenderState`, not
   directly under `.state` as NOTES had guessed), base
   `EntityRenderState.x/y/z` (confirmed, used for billboard yaw calc),
   `LivingEntity.walkAnimation` + `WalkAnimationState.speed(float
   partialTicks)` (confirmed, unchanged). Billboard math: yaw-only
   rotation toward camera each frame via `Mth.atan2(camPos - entityPos)`,
   NOT a full spherical billboard (sprite stays upright), per user's flat-
   2D-sprite spec. Vanishing state currently just skips drawing entirely
   (crude — entity has no fade-progress field, only a boolean, so no
   partial-alpha fade is possible yet without adding one).
   Texture copied from the user's uploaded
   `the-shepherd.zip` → `assets/themeadow/textures/entity/shepherd.png`
   as-is (533x411 RGBA) — the alpha-fringe cleanup NOTES mentioned was
   NOT done (skipped to save time per user's "don't overanalyze"
   instruction; revisit only if the fringe is visibly bad in-game).
   Sprite half-size (HALF_WIDTH/HALF_HEIGHT constants) is a guess, not
   matched to the real PNG aspect ratio — tune once visible in-game.
6. DONE — `com.themeadow.TheMeadow` (ModInitializer) and
   `com.themeadow.TheMeadowClient` (ClientModInitializer) written; both
   were referenced in fabric.mod.json but missing, which would have
   crashed on load — this blocker is now resolved.
   `TheMeadow.onInitialize()`: registers SHEPHERD via
   `FabricEntityTypeBuilder` + `Registry.register`, calls
   `FabricDefaultAttributeRegistry.register`, sets
   `RandomPortalSpawner.meadowDimension` to the real
   `themeadow:the_meadow` dimension key (wiring TODO #4 mentioned),
   and registers `ServerTickEvents.END_SERVER_TICK` to call
   `RandomPortalSpawner.trySpawnFor` for every online player each tick.
   `TheMeadowClient.onInitializeClient()`: registers ShepherdRenderer via
   `EntityRendererRegistry.register`.
   IMPORTANT naming gotcha caught this session: 26.2 renamed
   `ResourceLocation` → `Identifier` (confirmed via decompile,
   `net.minecraft.resources.Identifier`, same
   `fromNamespaceAndPath(namespace, path)` factory method). All new files
   this session use `Identifier`; if any OLDER file in this project still
   says `ResourceLocation`, that's a bug — grep and fix it.
   NOT tested in-game (no Gradle in sandbox) — FabricEntityTypeBuilder /
   FabricDefaultAttributeRegistry / EntityRendererRegistry package paths
   are the standard modern fabric-api locations (verified via web search,
   not decompile, since we don't have the real fabric-api jar) but not
   decompile-confirmed against this exact fabric-api version's jar.
7. DONE — `assets/themeadow/lang/en_us.json` written: "The Shepherd"
   (entity.themeadow.shepherd), "The Meadow" (dimension.themeadow.the_meadow).
8. DONE — confirmed via web search that
   `net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
   .END_SERVER_TICK` is still current with no reported breaking changes
   through recent fabric-api/MC versions. Wired into TheMeadow.java (see
   #6 above). NOT decompile-confirmed (no real fabric-api jar available
   in this sandbox), only web-search-confirmed — if the real build fails
   here, that's the first thing to re-check against the actual jar.

## Session update — jagged floating portal (DONE)
- Decompiled (this session, minimal targeted classes only): `DQuaternion`,
  `Portal`, `PortalShape`, `SpecialFlatPortalShape`, `Mesh2D`, `PortalAPI`,
  `PortalRenderInfo`, `StencilPortalRenderer` — confirmed no cutout/mask
  API exists in the library (it renders via stencil buffer on a plain
  quad), so the "opsi (a)/(b)" investigation in the old notes is resolved:
  neither is needed.
- `com.themeadow.portal.PortalOrientation.randomVerticalFacing(random)` —
  uses `DQuaternion.fromFacingVecs(axisW, axisH)` (decompile-confirmed
  factory method) with `axisH=(0,1,0)` fixed and `axisW` a random
  horizontal unit vector. Vertical + random yaw, exactly as specced.
- `com.themeadow.portal.JaggedPortalShape.build(jaggedness, edgePoints,
  random)` — returns a real `PortalShape` via
  `new SpecialFlatPortalShape(mesh)`. Builds a `Mesh2D` (coords normalized
  -1..1, confirmed from decompiled `Mesh2D.indexPoint`) by walking a
  rectangle perimeter and jittering each sample point inward/outward, fan-
  triangulated from center. `SpecialFlatPortalShape` handles collision/
  raytrace/clipping/rendering itself — no custom render event, no manual
  stencil work.
- `RandomPortalSpawner.spawnFloating` now calls both and
  `portal.setPortalShape(...)` (confirmed real method on `Portal`) — was
  previously calling non-existent classes, this is now wired and
  self-consistent. NOT yet real-Gradle-compiled (see TODO #1).
- Still NOT verified in-game: exact visual "torn" look (jaggedness=0.3,
  edgePoints=20 are guesses, tune once visible), and whether fan-
  triangulation from center ever produces a degenerate/self-intersecting
  triangle for extreme jaggedness values (unlikely at 0.3 but untested).

## Still TO DO (in rough priority order)
1. **Actually try a real Gradle build** if/when network access allows —
   everything above is hand-verified against decompiled vanilla+
   seamlessportals jars, NOT compiler-checked. fabric-api itself was
   never available to decompile in this sandbox (network egress doesn't
   reach Fabric's maven), so FabricEntityTypeBuilder/
   FabricDefaultAttributeRegistry/EntityRendererRegistry/ServerTickEvents
   are the biggest remaining unverified-by-decompile surface.
2. `fabric.mod.json`'s entrypoints now resolve to real classes (TheMeadow/
   TheMeadowClient) — but double check the `mixins: []` empty array
   doesn't matter (it doesn't, no mixins written) and that `depends`
   versions (fabricloader >=0.19.0, minecraft >=26.2) don't need bumping
   once real dependency resolution is possible.
3. Sprite sizing/vanish-fade polish in ShepherdRenderer (see #5 above) —
   cosmetic, do only after a real in-game test shows what's actually off.
4. MEADOW_ARRIVAL's fixed Y=100 in RandomPortalSpawner (see old TODO #4
   entry above) is still an untested guess — needs a real safe-surface
   finder once in-game testing is possible, or at minimum verify Y=100
   isn't inside solid terrain for the reused overworld noise settings.
5. Consider whether ShepherdEntity should register any Goals at all
   (registerGoals() is currently empty by design, see "What's already
   written" section) — fine as-is, just flagging it's an intentional
   choice, not an oversight, in case a future request wants vanilla-style
   pathfinding-around-obstacles behavior added via Goals instead of the
   hand-rolled state machine.

## User's explicit corrections/constraints (do not violate)
- Portal must be an ENTITY spawn, never a placed portal BLOCK/frame. User
  corrected this explicitly mid-session ("portalnya bukan spawn block
  loh ya").
- Floating portal: vertical, random horizontal facing, jagged/non-rectangular
  visual (explicitly not a clean rectangle) — user's second explicit
  correction/clarification.
- Shepherd never kills the player — chases then vanishes only. Already
  implemented correctly in ShepherdEntity.
- No loading screen on portal crossing, portal must be see-through from
  Overworld in real time — this is inherent to Immersive/Seamless Portals'
  design, just don't break it by adding custom teleport logic that
  bypasses the portal entity's own teleport handling.
- User wants decompiling used but NOT over-relied on, and wants time/
  tokens not wasted over-analyzing irrelevant code — this session tried
  to honor that by only decompiling the specific classes needed for each
  decision point, not whole packages.
