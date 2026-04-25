 ty for downloading 
get .exe for windows

get arm 64x .dmg for mac but make sure to run this command if the app says iceyclient is damaged and cant be opened: 
xacttr -cr /Applications/Icey\ Client.app 

---

## What's new in v1.66.0

- **Fix: HUD dragging didn't work on 1.21.11.** Screen's `mouseClicked / mouseDragged / mouseReleased` were re-signatured to take a `Click` object. Loom remaps method descriptors at jar build, so on 1.21.11 our `@Override` methods stopped overriding anything → drag events never reached our handler. HudEditScreen now polls the left mouse button via raw GLFW inside `render()` (whose signature didn't change) and runs the drag state machine itself. Works on both 1.21.8 and 1.21.11.
- **Removed Seed Predictor** (V → "Predict from Seed" is gone). Per request — wasn't the approach you wanted.

## What's new in v1.65.1

- **Only ping when a structure is actually there.** Block-sample detections now require multiple signature hits in the same chunk before declaring a find — a single player-placed `crying_obsidian` / `purpur_block` / `lodestone` won't trigger false positives anymore. Per-type thresholds:
  - End City: 5 hits (broad signature, easy to false-trigger on a single purpur block)
  - Nether Fortress: 3 hits
  - Bastion: 2 hits
  - Ocean Monument: 3 hits
  - Ruined Portal: 2 hits
  - Desert Pyramid: 2 hits
  - Ancient City: 1 hit (reinforced_deepslate is genuinely unique — even one is reliable)

## What's new in v1.65.0

- **Seed Predictor for End Cities.** Press `V` → "Predict from Seed". Paste your world seed (numeric or string — same hash semantics as vanilla), set a search radius, and we replicate vanilla's region-grid placement algorithm (spacing 20, separation 11, salt 10387313, seeded with `worldSeed + rx*341873128712 + rz*132897987541 + salt`) to spit out every candidate End-City start chunk inside the radius. Sorted by distance from origin.
- **One-click waypoint** — "Waypoint Top 10 Closest" or "Waypoint All" drops named waypoints (`End City Pred 1`…`N`) so you can fly straight to them. Y guess is 60 (typical outer-end island height).
- Candidates aren't biome-verified, so a small fraction will be empty when visited; the rest land you on or next to a real city.
- Works for singleplayer and any server where you know the seed (realm owners, friend's SMP). Public servers usually hide their seed.

## What's new in v1.64.2

- **Real chat message on every structure find**, not action-bar — persists in the chat log so you can scroll back and re-read. Format: `§b[IceyClient] §aTrial Chamber found! §8(x, y, z)`. Same change for the End-Anchor gateway-hop message.

## What's new in v1.64.1

- **Action-bar ping on every structure find** — `§b[Icey] §a<Type> found! §7x/y/z` shows above your hotbar the moment a new Trial Chamber / End City / Village / etc. is detected. Fires once per cluster — re-entering the same structure won't spam.
- **End rescan 4× faster** — periodic chunk rescan was every 1 s; now 0.25 s in the End so newly-loaded outer-island chunks register the moment they arrive.
- **Tighter End clustering** — End-dim detections now cluster at 40 blocks (vs 80) so an End City and its End Ship register as separate entries with separate waypoints.
- **End Anchor auto-waypoint** — gateway-teleporting in the End drops an "End Anchor" waypoint at landing so you can return to that outer island without re-rolling RNG.

## What's new in v1.64.0

- **End City detection — way more sensitive.** Now samples for `purpur_pillar`, `purpur_block`, `purpur_stairs`, `purpur_slab`, AND `end_stone_bricks` (was just pillars). Scan step finer (2 blocks vs 4) and Y range wider (30–110 vs 40–90). Any sliver of a city or end ship inside a loaded chunk should register now.
- **New structure type: End Gateway.** Detected via `EndGatewayBlockEntity`. Auto-waypoint them so you can systematically gateway-hop to outer-end islands — far faster than elytra-flying for finding new cities.
- **Caveat:** the End is huge and the server only sends chunks within your render distance. No mod can see structures outside loaded chunks — use gateways + max render distance.

## What's new in v1.63.1

- **Freecam smoothness + range fix.** Movement was running on the 20 Hz tick loop, so motion stepped on a 60+ fps display. Moved input-read + position update into the per-frame Camera path with delta-time scaling — now it glides. Also clamped the camera within your render-distance radius so you can't fly past the chunks the server actually sent (which was making it look like "doesn't render everything").

## What's new in v1.63.0

- **Freecam (spectator-style)** — press **F4** to detach the camera from the player and fly around with WASD. Mouse rotates the camera, Space/Shift go up/down, Sprint key (Ctrl) ~3× speed. Player stays in place — vanilla movement input is suppressed while freecam is active so you don't walk into lava under your own feet. Press F4 again to return to first-person.
- Auto-switches to third-person on enter, restores original perspective on exit. Works on servers (purely client-side rendering — server still sees the player at the saved position).

## What's new in v1.62.1

- **Fix: clicking "Install" in the mods browser did nothing** for many mods. The Install button used inline `onclick="..."` with the mod name embedded as a JS string, so any apostrophe / ampersand / unusual character in the name silently broke the handler. Rewritten to use data-attributes + a single delegated click listener — works regardless of mod name content.

## What's new in v1.62.0

- **Structure Locator: 8 new structure types** — Nether Fortress, Bastion Remnant, End City, Ocean Monument, Ancient City, Ruined Portal, Desert Pyramid, Village. Plus the existing Trial Chamber, Stronghold, Player Base — 11 types total.
- **"Select Structures" screen** — press `V` → "Select Structures" to toggle which types you want to find. 2-column grid of green/grey checkboxes. Toggling triggers a rescan of currently-loaded chunks so your selection takes effect immediately.
- **How detection works (block-entity vs block-sample):**
  - Trial Chambers / Strongholds / Player Bases / Villages → block entities (Trial Spawner, End Portal, Ender Chest, Bell, etc.) — fast and reliable.
  - Nether Fortress, Bastion, End City, Ocean Monument, Ancient City, Ruined Portal, Desert Pyramid → coarse block sampling for unique signature blocks (nether brick fence, lodestone, purpur pillar, prismarine bricks, reinforced deepslate, crying obsidian, chiseled sandstone). Each is unique to its structure in vanilla generation.
- **Render-distance bound, dimension-tagged, deduped** — same as before. 80-block clustering, per-dimension scan state, periodic tick rescan as fallback.

## What's new in v1.61.6

- **Real fix: iceymod keybinds now appear and work on 1.21.11.** The previous compat shim did `Class.forName("net.minecraft.client.option.KeyBinding$Category")` at runtime — but Loom only remaps compile-time class references, not string literals, so in production the class is under its intermediary name and that lookup silently failed. The shim now enumerates `KeyBinding.class.getConstructors()` (where the Class object itself IS remapped), finds the 4-arg constructor, inspects its 4th parameter type to detect legacy-vs-new path, and pulls any built-in Category instance off that type's public static fields. No hardcoded class-name strings — works in both dev and production regardless of obfuscation.

## What's new in v1.61.5

- **Fix: on 1.21.11, iceymod keybinds (Y menu, zoom, waypoints, structures, etc.) didn't appear in the Controls screen and didn't respond to key presses.** The compat shim was creating a custom `KeyBinding.Category` via `Identifier`, but Fabric 1.21.11 only shows categories that are registered via its internal category registry — our custom ones fell off the radar. Keybinds are now created under the built-in `MISC` category, so they show up under "Miscellaneous" in Controls and fire normally.

## What's new in v1.61.4

- **Fix: 1.21.11 crash in SplashTextMixin.** `SplashTextRenderer(String)` constructor was removed in 1.21.11, so the themed splash text mixin was crashing the title screen reload with `NoSuchMethodError`. `require=0` on the injection wasn't enough because the *target* still existed — the failing call was inside the mixin body. Every `@Inject` mixin now wraps its body in `try/catch(Throwable)` so runtime API drift silently falls through to vanilla instead of crashing.


## What's new in v1.61.2

- **Fix: 1.21.11 startup crash from CameraMixin.** `Camera.update`'s signature changed in 1.21.11 and the freelook mixin couldn't find its injection target, crashing the game during class load. All mod mixins now tolerate missing targets (`require=0, expect=0`) — on a version where a target signature changed, the affected feature silently disables instead of taking down the whole game.
- **Fix: Structure Locator stuck on "Scanning chunks…" on versions where `ClientChunkEvents.CHUNK_LOAD` doesn't fire.** Added a per-second tick rescan fallback — already-scanned chunks are deduped, new chunks get picked up regardless of whether the chunk-load event delivered. So the locator works even if the Fabric lifecycle event isn't available.
- Effect on 1.21.11: freelook/zoom/custom logo/splash text may not apply, but everything else (HUD modules, waypoints, minimap, structure locator, shader browser) works normally.

## What's new in v1.61.1

- **Fix: "Clear All" soft-locked the Structure Locator.** Clearing findings also emptied the scanned-chunks cache, but currently-loaded chunks don't re-fire `CHUNK_LOAD`, so nothing scanned until you walked to new chunks. Now "Clear All" re-sweeps every chunk in range immediately.

## What's new in v1.61.0

- **Fix: Structure Locator getting stuck at "Scanning chunks…"** — every dimension trip (Overworld → Nether → back) was wiping all your found chambers/bases. Now each finding is tagged with the dimension it was discovered in, and findings + scan state survive dimension switches. The HUD only shows entries for the dimension you're currently in.
- **Clear All** now only clears the current dimension's findings — your Nether list is safe when you clear Overworld.


## What's new in v1.60.0

- **Structure Locator** — press `V` to open the menu (or enable the "Structure Locator" module in the Y menu). It scans chunks as they load for:
  - **Trial Chambers** — detects Trial Spawners and Vault blocks (only exist in chambers).
  - **Strongholds** — detects active End Portal blocks.
  - **Player Bases** — detects Ender Chests, Shulker Boxes, and Beacons (crafted only, never natural).
- **Waypoint-style menu** — Find New Structures / Pause · Waypoint a Structure · Delete · Clear All.
- **Auto-waypoint** on discovery (toggleable). Distance-clustered so one chamber = one entry, not fifty.
- **Rescan on enable** — already standing in a chamber when you toggle it on? It rescans every loaded chunk so the chamber shows up immediately.
- **HUD list** — nearest structures shown with name, distance, and direction arrow (like waypoints).

## What's new in v1.53.0

- **Xaero-style minimap** — press Y → enable "Minimap". A square terrain map in the top-right with biome-tinted colors (forests look green, swamps muddy, oceans match the biome shade), height shading, rotating player arrow, and waypoint dots. Drag it like any other HUD module.
- **Tunable** — size 64–192 px, radius 16–192 blocks, toggles for biome tint, height shading, north indicator, coords under map, and waypoint dots. All in the module's gear menu.
- **1.21.11 crash fix** — the mod was crashing at startup on 1.21.11 because `KeyBinding`'s constructor signature changed between versions. Now uses a reflection-based compat shim so the same jar works from 1.21.8 through 1.21.11.
