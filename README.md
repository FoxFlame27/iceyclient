lb ty for downloading 
get .exe for windows

get arm 64x .dmg for mac but make sure to run this command if the app says iceyclient is damaged and cant be opened: 
xacttr -cr /Applications/Icey\ Client.app 

---

## What's new in v1.73.2

- **Fix: Fabric "Unfixable conflicts" crash on MC 1.21.1 (and other short-version installations).** The launch code was matching the Fabric loader dir with `d.includes(version)` — when both `fabric-loader-X-1.21.1` and `fabric-loader-Y-1.21.11` existed, launching the 1.21.1 installation could pick up the 1.21.11 profile (because `"1.21.11"` contains `"1.21.1"`). The launcher then paired the 1.21.11 intermediary mappings with the 1.21.1 client jar and TinyRemapper crashed during deobfuscation with hundreds of method-mapping conflicts. Switched the matcher to `d.endsWith('-' + version)` so only the exact version matches. Also added a clearer warning log when no Fabric dir is found for the installation's MC version.

## What's new in v1.73.1

- **Fix: Installations page rendered blank.** v1.71.0 added `position: relative` to `#page-installations` to anchor the drag-drop overlay, but `.page` already sets `position: absolute; inset: 0` — overriding to relative made `inset: 0` stop stretching the element, so the page's HTML was rendered but the layout collapsed and only the panorama showed. Removed the override; the drop overlay still anchors correctly because `.page`'s own `position: absolute` is already a positioning context.

## What's new in v1.73.0

- **Auto-prompt to install E4MC after a world import.** When the import finishes, if the target Fabric installation doesn't already have E4MC in its `mods/` folder, a small modal pops up offering to install it from Modrinth (matched to the installation's MC version). One click, no leaving the launcher. "Skip" remembers the choice in `localStorage` so you're not pestered next time.
- The install pulls E4MC's primary jar via the existing Modrinth API + downloadFile IPC — same path the Mods browser uses.

## What's new in v1.72.0

- **Map import is now Fabric-only.** Worlds can only be imported into Fabric installations. The intent: most "play with friends remotely without LAN/port-forwarding" tooling — E4MC, Hopper, etc. — is Fabric-only, so gating import to Fabric keeps the workflow consistent and stops you from importing into a vanilla install you can't share from.
  - Detail-panel button is disabled on Vanilla installations with a tooltip explaining why.
  - Header "Import World" chooser only lists Fabric installations.
  - Drag-and-drop onto a Vanilla card (or no card with no Fabric installations) shows a clear error toast — `_runImport` does a final defensive check so no path slips through.

### How to play an imported map with a remote friend (no LAN, no port forwarding)

Install **[E4MC](https://modrinth.com/mod/e4mc)** in your Fabric installation (Mods page → search `e4mc` → Install). Open the imported world → press the E4MC keybind → it gives you a tunneled link. Friend installs E4MC, joins via the link. Done. No Hamachi, no router config, no Realms subscription.

## What's new in v1.71.1

Bug pass on the import-world flow. Two real fixes (the third was a Windows-specific concern).

- **Drop overlay was disappearing after page refresh.** `InstallationsPageInit()` reassigns the page's `innerHTML`, which silently removed the overlay element we'd appended. The "already installed" flag then prevented re-adding it. Listener-bind and overlay-create are now separated: listeners bind once via a `data-drag-bound` attribute on the page element (survives `innerHTML` reassignment), the overlay is recreated every init, and the dragenter/dragleave/drop handlers re-fetch the overlay via a small `getOverlay()` helper.
- **Hardened path-traversal on Windows.** The previous `path.resolve` + `startsWith` check was correct, but `path.join` on Windows treats `\` as a directory separator. A malicious zip with entries like `..\..\evil.txt` could in theory squeeze through. Added an explicit pre-check: any entry name containing `\` or any `..` / empty segment is rejected before reaching `path.join`. Belt-and-braces.
- Cross-platform reminder: pure Node `fs` + `zlib` + `path`, no native deps. Verified the Linux ARM64 path resolves to `~/.iceyclient/installations/<id>/game/saves` via `getDataDir()`.

## What's new in v1.71.0

- **Drag-and-drop world import.** Drop a `.zip` file anywhere on the Installations page to import it. Drop on a specific installation card to target that installation directly; drop in the empty area to use the currently-selected installation (or open the chooser if you've got several).
- **Sanity-check on import.** Zips that don't contain a `level.dat` are rejected up front instead of writing junk into `saves/`. Clear error toast on `.rar` / `.7z` (extract to `.zip` first).
- **Cross-platform paths verified end-to-end.** The launcher resolves saves to:
  - **Windows**: `%APPDATA%\IceyClient\installations\<id>\game\saves`
  - **macOS**: `~/Library/Application Support/IceyClient/installations/<id>/game/saves`
  - **Linux** (incl. ARM64): `~/.iceyclient/installations/<id>/game/saves`
  Per-installation isolated saves, no clobbering vanilla MC, no PATH magic.
- Visual: dashed cyan overlay covers the page while you're dragging a file over it, with a "Drop world .zip to import" message.

## What's new in v1.70.1

- **"Import World" button in the installations header.** Press it without selecting an installation first — picks the zip, then if you have more than one installation pops a chooser modal to ask which one. With one installation (or one already selected), goes straight in.
- Per-installation Import World button (in the detail panel) is still there.
- Success toast wording is now `World loaded: <name>` and shows in the top-right (where the existing toast container lives) so it's hard to miss.

## What's new in v1.70.0

- **Import World (.zip).** New button on each installation's detail panel — click to open a file picker, pick a Minecraft world ZIP (the kind you download from Planet Minecraft / mcpedl / etc.), and it gets unzipped straight into that installation's `saves/` folder. Launch the install and the world is right there in the singleplayer list.
- Auto-detects whether the zip has a single root folder (most do — extracted as-is) or has files at the top level (wrapped under the zip's filename so MC still finds `level.dat`).
- Re-importing the same zip won't overwrite — the second copy gets `(2)`, `(3)`, etc. appended so you keep both.
- Path-traversal guarded — any zip entry trying to escape `saves/` is rejected.

## What's new in v1.69.0

- **Per-world waypoints.** Saved waypoints are now scoped to the world they were created in. Server play uses the server address as the key (e.g. `lifesteal.net`), singleplayer uses the save's level name, so a Spawn waypoint on one server doesn't show up on another or in your singleplayer worlds. The waypoint config file (`config/iceymod_waypoints.json`) gained a `worlds` map under it; pre-existing flat-list files are auto-migrated into a `default` key on first launch so nothing is lost.

## What's new in v1.68.1

Follow-up fixes to v1.68.0 — the core release was good but the waypoint HUD had list-overflow + duplicate-spam issues.

- **Waypoints HUD capped at 5 nearest.** Sorted by distance to player; if you have more, a "§7+ N more" footer line appears. Stops the list overflowing past the screen and breaking drag.
- **Auto-waypoints dedupe by name + 100 m proximity.** Trial Chamber finds (and every other auto-create path: structures, biomes, deaths) skip if a same-named waypoint already exists nearby. Manual Set-Here / chat-coord clicks bypass dedup.
- **Structure cluster radius tightened to 50 m** (was 80 m); biomes stay at 256 m (any tighter would fragment one biome blob into many entries).
- **Empty Waypoints module shows a placeholder** ("§7No waypoints") so the HUD widget stays visible and draggable when your list is empty.
- **HUD edit drag no longer skips the bottom 32 px** — only the actual Done-button rect is excluded, so modules positioned near the bottom of the screen are now draggable.
- **Death waypoint dedupe** at 32 m so dying repeatedly in the same lava pit doesn't make 20 "Last Death" waypoints.

## What's new in v1.68.0

Module search · Item Glow · Death waypoint · Chat coords · Waypoint recolor.

- **Module search bar.** Press Y → type in the box at the top to filter modules by name across whatever category is selected. Esc / clear box to reset.
- **Item Glow** — new module under Combat. Outlines dropped items you care about (Mace, Totem, Netherite gear/blocks/scrap, Elytra, Beacon, Nether Star, Dragon Egg, Heart of the Sea, Trident, Shulker Shells, optionally Enchanted Books) with the vanilla glow shader so they pop through walls. Per-item toggles in module settings. Done via a client-side `Entity.isGlowing` override mixin — server doesn't know.
- **Last Death is a real waypoint now.** Removed the `Last Death` HUD module; replaced with an `Auto-Waypoint on Death` setting on the Waypoints module (default ON). Every time you die, a red "Last Death" waypoint drops at your last position so you can fly back for your stuff. Dedupes within 32 m so dying repeatedly in the same lava pit doesn't create 20 waypoints.
- **Click coordinates in chat to waypoint them.** Server / system messages containing coordinate triples (parens or no parens, comma/slash/space separators) get rewritten into a clickable underlined link. Click → drops a waypoint there. Internally goes through a registered `/iceywp x y z [name]` client command so signed-message security isn't broken.
- **Recolor any waypoint** — Press B → "🎨 Recolor Waypoint" → pick a waypoint → opens the RGB color picker (sliders + hex field + palette, same one as module colors). Saves to `iceymod_waypoints.json` and the beam color updates immediately.

**HUD-list fixes** (caught while testing):

- **Waypoints HUD capped at 5 nearest.** Sorted by distance to player; if you have more, a "§7+ N more" footer line appears. Stops the list overflowing past the screen and breaking drag.
- **Auto-waypoints dedupe by name + 100 m proximity.** Trial Chamber finds (and every other auto-create path: structures, biomes, deaths) skip if a same-named waypoint already exists nearby. Manual Set-Here / chat-coord clicks bypass dedup.
- **Structure cluster radius tightened to 50 m** (was 80 m); biomes stay at 256 m (any tighter would fragment one biome blob into many entries).
- **Empty Waypoints module shows a placeholder** ("§7No waypoints") so the HUD widget stays visible and draggable when your list is empty.
- **HUD edit drag no longer skips the bottom 32 px** — only the actual Done-button rect is excluded, so modules positioned near the bottom of the screen are now draggable.

Everything stays 1.21.11-safe — the glow mixin uses `require=0, expect=0` + try/catch so it falls through to vanilla on any API drift, and the chat-rewrite + click-handling are wrapped so unsupported events on newer versions silently disable.

## What's new in v1.67.1

- **Keyboard shortcuts in iceymod menus now work on 1.21.11.** `Screen.keyPressed`'s signature changed in 1.21.11 (now takes a `KeyInput` object), so our overrides silently stopped firing. Replaced both with raw GLFW polling inside `render()`:
  - **Y menu** — arrow keys to navigate, Enter/Space to toggle module, Page Up/Down to switch pages.
  - **Waypoint menu** — Enter to confirm rename / edit-coords without clicking Save.
- Mouse handling was already fine; this just restores the keyboard shortcuts.

## What's new in v1.67.0

- **Biome Locator** — press `K` to open. Same UX as the Structure Locator: Find/Pause, Select Biomes (12 toggles, rare ones default-on), Waypoint, Delete, Clear. Detects 12 biomes: Cherry Grove, Mushroom Fields, Ice Spikes, Sunflower Plains, Bamboo Jungle, Eroded Badlands, Deep Dark, Pale Garden, Deep Frozen Ocean, Badlands, Jungle, Savanna. Auto-waypoint + chat ping on first find of each.
- **End City detection — pushed to the limit.** Now biome-gated: in `END_HIGHLANDS` / `END_MIDLANDS` (the only biomes cities spawn in), we sample EVERY block (step=1) over the full Y 0-128, and a single purpur block triggers a find. Outside those biomes the scan is skipped entirely. Roughly 8× the per-chunk effort but only on island fragments where it matters.
- **Shulker entity → 100% reliable End City marker.** Shulkers only spawn in End Cities naturally — if one's loaded in your world, we declare a city at that position. Even works through walls.
- **Rescan radius bumped past view distance.** Now `viewDistance + 4` chunks so we catch any extra chunks the server lazy-sends (simulation distance / neighbor pre-load). null-skip is O(1) so it's free.

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
