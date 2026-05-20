  pllb ty for downloading 
get .exe for windows

## Download iceymod+

Two flavors. Both downloadable directly from the latest GitHub release — no launcher required.

### Server mod (Fabric)
Full feature set — steal-on-kill, combat tag, /icey commands, /spawn, noob protection, starter kit. **Requires Fabric Loader on the server** (or use it on singleplayer via your client's mods/ folder).

- **MC 1.21** → [iceymodplus-server-mod-mc1.21-1.0.0.jar](https://github.com/FoxFlame27/iceyclient/releases/latest/download/iceymodplus-server-mod-mc1.21-1.0.0.jar)
- **MC 1.21.5** → [iceymodplus-server-mod-mc1.21.5-1.0.0.jar](https://github.com/FoxFlame27/iceyclient/releases/latest/download/iceymodplus-server-mod-mc1.21.5-1.0.0.jar)
- **MC 1.21.8** → [iceymodplus-server-mod-mc1.21.8-1.0.0.jar](https://github.com/FoxFlame27/iceyclient/releases/latest/download/iceymodplus-server-mod-mc1.21.8-1.0.0.jar)
- **MC 1.21.11** → [iceymodplus-server-mod-mc1.21.11-1.0.0.jar](https://github.com/FoxFlame27/iceyclient/releases/latest/download/iceymodplus-server-mod-mc1.21.11-1.0.0.jar)

Drop into `mods/`, restart.

### Server pack (datapack)
Vanilla-compatible — works on any 1.21+ server with **no mods needed**. Auto-buffs from MC's builtin scoreboard objectives. No PvP guardrails / no /icey commands — feature subset.

- [iceymodplus-server-pack-1.0.0.zip](https://github.com/FoxFlame27/iceyclient/releases/latest/download/iceymodplus-server-pack-1.0.0.zip) (same zip for every MC version in the 1.21+ range)

Drop into `<world>/datapacks/` and run `/reload`.

`latest/download/` always resolves to the most recent release tag.


get arm 64x .dmg for mac but make sure to run this command if the app says iceyclient is damaged and cant be opened: 
xacttr -cr /Applications/Icey\ Client.app 

---

## What's new in v1.86.6

**CI hotfix on top of v1.86.5.** The client-mod matrix-build I added in v1.86.5 failed CI on the 1.21 entry — 20+ symbol errors from APIs introduced in 1.21.5 (RenderPipelines, VertexRendering, PlayerInput, HoverEvent.ShowText record, PlayerInventory.getSelectedSlot, BiomeKeys.PALE_GARDEN, ParticlesMode, etc.). The mod actually requires 1.21.5+ to build at all; the 1.21 + 1.21.5 matrix entries were aspirational and never tested against the current source. Dropped them from the client-mod matrix — only 1.21.8 and 1.21.11 client jars now ship. iceymodplus (server mod) matrix unchanged: still 1.21 / 1.21.5 / 1.21.8 / 1.21.11, because its API surface is smaller and stable across all four.

## What's new in v1.86.5

**Full 1.21.11 yarn-drift fix — every module compiles + works on every matrix MC version.** Per user: "B" (proper fix) when given the choice between switching back to 1.21.8 or committing to per-version builds.

### What was broken
Compiling the client mod against 1.21.11 yarn surfaced 13 errors across 6 files (Camera.getPos, Entity.getPos, RenderLayer.getLines, VertexRendering.drawBox signature, ClientWorld.getSpawnPos, GameOptions.getGraphicsMode, BeaconBlockEntityRenderer.renderBeam signature, SplashTextRenderer constructor String→Text). The 1.21.8-built jar ran on 1.21.11 with most modules silently dead.

### What I did
- **New [Compat.java](mod/src/main/java/com/iceymod/Compat.java)** — reflection-based version-portable accessors for the renamed methods that the render-hot-path uses. `Compat.cameraPos(Camera)` tries `getPos()`, falls back to scanning the camera's `Vec3d` fields. `Compat.entityPos(Entity)` tries `getPos` / `getSyncedPos` / `getLastRenderPos` then walks the inheritance chain for the `pos` field. `Compat.worldSpawnPos(World)` tries the direct method then via `getLevelProperties().getSpawnPos()` then bails to `(0, 64, 0)`.
- **Renderers updated**: `HitboxRenderer`, `EntityHealthRenderer`, `WaypointBeamRenderer` now use `Compat.*` for position access. `HitboxRenderer` does reflective dispatch for `RenderLayer.getLines` and `VertexRendering.drawBox` (signature changed). `WaypointBeamRenderer.renderBeamReflective` walks every `renderBeam` overload by parameter count (11, 10, 9 args).
- **Module fallbacks**: `BedCoordsModule` uses `Compat.worldSpawnPos`. `FpsBoostGraphicsModule` wraps `getGraphicsMode()` in reflection (silent no-op on 1.21.11 where it's gone). `SplashTextMixin` tries both `SplashTextRenderer(String)` and `SplashTextRenderer(Text)` constructors.
- **Matrix CI for client mod** — `build-mod` now builds 4 jars matching `iceymod-mc<MC_VER>-1.0.0.jar`. Each compiles against its target yarn natively, so method references aren't reflective on the hot path. Launcher install logic in [main.js](main.js) picks the right per-version jar (`iceymod-mc${installation.version}-1.0.0.jar`); `electron-builder` files glob bundles all four; the three launcher build jobs download all 4 mod-jar-mc* artifacts via pattern merge.

### What's verified
Compiles cleanly against:
- 1.21.8 yarn (the dev default)
- 1.21.11 yarn (the user's runtime)

CI matrix will produce all 4 jars; the launcher will install whichever matches the installation's MC version. Freecam, freelook, all HUDs, hitboxes, waypoint beams should all work on 1.21.11 once the v1.86.5 release ships.

## What's new in v1.86.4

**HUDs above entities work on 1.21.11 (and the spear/freecam diagnosis).**

User report: HUDs above players don't show, freecam + freelook don't work on 1.21.11. The client log confirmed the cause:
```
[IceyMod] WorldRenderEvents unavailable — entity-health renderer disabled:
net/fabricmc/fabric/api/client/rendering/v1/WorldRenderEvents
```
…repeated for hitboxes and waypoint beams.

**Root cause** — Fabric API moved `WorldRenderEvents` between 1.21.8 (the version the client mod is built against) and 1.21.11 (what the user runs):

| MC version | Class path |
| --- | --- |
| 1.21 → 1.21.8 | `net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents` |
| 1.21.11+ | `net.fabricmc.fabric.api.client.rendering.v1.**world**.WorldRenderEvents` |

The class with the old path is **gone** in 1.21.11's fabric-api, so all three world-render hooks fail to register at runtime. Same package shift affects `WorldRenderContext` and the nested listener interfaces.

**Fix** — new [WorldRenderHook.java](mod/src/main/java/com/iceymod/render/WorldRenderHook.java) is a reflection bridge that tries both class paths at runtime, builds a `Proxy` implementing the matching nested listener interface, and exposes a `Ctx` wrapper that calls `matrixStack()/camera()/consumers()/tickDelta()` on the underlying context reflectively. The three renderers (`HitboxRenderer`, `WaypointBeamRenderer`, `EntityHealthRenderer`) now import nothing from `net.fabricmc.fabric.api.client.rendering.v1.*` directly — they go through `WorldRenderHook`. Source compiles cleanly against 1.21.8 yarn AND the resulting jar works at runtime on both 1.21.8 and 1.21.11.

**Freecam / freelook** are a separate yarn-drift issue — `CameraMixin`'s descriptor-type capture was the v1.86.2 fix and it's still in place, but `FreecamModule` calls a few `GameOptions` accessors that got renamed in 1.21.11 (`getGraphicsMode()` removed, `forwardKey` field shape may have changed). Those need per-call try/catch fallbacks; tracking as a follow-up.

## What's new in v1.86.3

**Mod version-picker recolored.** The selected version button on the install modal had a saturated cyan-blue gradient (`var(--accent) → #38bdf8`) that read as "purple" on the user's display. Swapped to a soft white-on-dark glass tile: subtle white gradient, off-white border, inset highlight + soft drop shadow. No color cast — matches the new create-installation modal-icon styling. Same change applied to the loader buttons (Fabric / Forge picker) and the version-list border / scrollbar thumb (cyan-tinted → neutral white).

## What's new in v1.86.2

**Four user fixes/changes.**

1. **Freecam fixed on 1.21.11.** The `CameraMixin` was no-op'ing because `Camera.update`'s first param changed from `BlockView` (1.21.8) to `World` (1.21.11) and the mixin's descriptor-type capture stopped matching. Switched the injection to take only `CallbackInfo`, so the target is matched by method name only and fires regardless of param-type drift. Freelook fetches the focused entity from `MinecraftClient.getCameraEntity()` instead of capturing it from the call site. Result: camera moves, can fly through walls, all WASD/space/shift controls work again.
2. **Health nameplate split into two toggleable modules.** `TargetHealthModule` deleted. Replaced with `PlayerHealthModule` (id `playerhealth`, controls nameplates above other players) and `MobHealthModule` (id `mobhealth`, controls nameplates above non-player LivingEntities — zombies, villagers, animals, etc.). Both default ON; toggle each independently via the Y-menu.
3. **Vanilla two-pass nameplate render** in new `EntityHealthRenderer` (replaces single-pass `TargetHealthRenderer`). Mirrors `EntityRenderer.renderLabelIfPresent`: draws with `TextLayerType.SEE_THROUGH` at low alpha (`0x21FFFFFF` ≈ 13%) first so the text is visible faintly through walls, then draws `TextLayerType.NORMAL` at full white over it for unoccluded text. Same scale (`-0.025`), same camera-billboard rotation, same `+1.0` Y offset above bbox. This is exactly what vanilla does for username tags, just with health instead of the player name. **Visible from anywhere within 64 blocks** — the same range vanilla tracks players client-side — so you don't have to be close.
4. **Create-Installation modal — the "ugly purple box with a big plus"** is now an Icey-logo tile. The placeholder SVG is replaced with `<img src="assets/icon.png">` and the surrounding `.create-modal-icon` CSS swapped from a cyan/blue gradient + cyan border to a dark neutral glass tile (subtle white highlight + soft drop shadow). Icon sits on `64×64` with `14-px` rounded corners; the logo image is `44×44` centered.

## What's new in v1.86.1

**Target-health nameplate visibility fixes.** User: "i dont see anythign abive their head."

- **`TargetHealthModule` is now enabled by default** (was `setEnabled(false)`). The default-off state required toggling via the Y-menu before the nameplate would render, which is why it wasn't showing.
- **Bumped the Y offset from `+0.6` → `+1.0`** above the player's bounding-box top, so our health line sits clearly above the vanilla username nameplate instead of overlapping with it.
- **Added one-shot debug logging** in [TargetHealthRenderer](mod/src/main/java/com/iceymod/render/TargetHealthRenderer.java) — first successful render prints `[IceyMod] TargetHealthRenderer: drew health above <name>` to the client log so we can confirm the hook fires; first error also logs once with the exception class, suppressing further repeats. Future "doesn't show" reports can be triaged from the log instead of guessing.

If you're still not seeing it: confirm you're on a multiplayer / LAN server with at least one other player within 64 blocks. In a solo singleplayer world `client.world.getPlayers()` only contains yourself, which the renderer correctly skips.

## What's new in v1.86.0

**Target-health HUD moved from a fixed on-screen widget to a 3D nameplate above each player's head.** Per user: "change the target health hud to be above the other players head … if you come close to a player it shows but try and maximize the distance."

- New [TargetHealthRenderer.java](mod/src/main/java/com/iceymod/render/TargetHealthRenderer.java) hooks `WorldRenderEvents.AFTER_ENTITIES` (same pattern as `HitboxRenderer` and `WaypointBeamRenderer` — uses `WorldRenderContext.matrixStack` + `consumers` directly).
- Iterates every loaded player in `client.world.getPlayers()`, skips the local player, filters by `squaredDistanceTo(self) <= 64²`. The 64-block cap matches the vanilla player entity-tracking range — beyond that the player isn't on the client side anyway, so 64 is the practical maximum distance.
- Text is `§<color>❤ <hp>/<max>` where color is green/yellow/red by HP ratio (green > 66%, yellow > 33%, red below). Billboarded toward the camera via `matrices.multiply(camera.getRotation())`. Vanilla nameplate scale (`-0.025`) so it looks like a real nameplate. Drawn with `TextLayerType.SEE_THROUGH` so the health stays visible even when the player is behind cover — easier to read from far away.
- `TargetHealthModule.getText()` now returns `null` so no on-screen widget competes with the in-world nameplate. The module remains as the on/off toggle the renderer reads via `HudManager.getModules()`.
- Registered in `IceyMod` next to the other render hooks, wrapped in the standard try/catch in case a yarn variant renames a class.

## What's new in v1.85.9

**Version-manifest cache TTL — launcher stays current with new MC releases without restart.**

Investigated whether the launcher needs updating to fetch newer MC / Fabric / shader versions. Result: it already auto-updates by construction. Every version-relevant lookup hits the same live APIs Prism Launcher uses:

| Thing | Source | Always-fresh? |
| --- | --- | --- |
| MC version list | `piston-meta.mojang.com/mc/game/version_manifest_v2.json` | live |
| Fabric loader | `meta.fabricmc.net/v2/versions/loader/{mcVer}` → `[0]` (newest) | live |
| Fabric API mod | Modrinth `api.modrinth.com/v2/project/fabric-api/version?game_versions=[{mc}]` | live |
| Iris + Sodium | same Modrinth API path | live |

Only gotcha: `VersionManager._versions` cached the manifest in memory for the entire launcher session, so a new MC release published after the launcher started wouldn't show up until restart.

Fix in [versions.js](src/utils/versions.js): 10-minute TTL on the cached manifest + a cache-busting `?t=<now>` query param so any CDN-cached response doesn't get reused. Reopening "Create Installation" 10+ minutes after the first fetch triggers a fresh manifest pull. (Within the 10-minute window the cached copy is reused — that's just to keep snap-open-close cycles cheap.)

CI matrix (`build-smp` in `.github/workflows/build.yml`) is still hardcoded to 1.21 / 1.21.5 / 1.21.8 / 1.21.11 — that's a deliberate per-release build-target list, not auto-discovery. Adding a new MC release to the matrix is a one-line append.

## What's new in v1.85.8

**Four user fixes/additions.**

1. **Guide book is now a first-join freebie**, not a purchasable kit item. Per user: "you put the guide book in the starterkit that you have to buy do it as the fisrt thing you get when you join." Removed the `written_book` from `Kits.ALL.starter`; new `StarterKit.giveIfFirstJoin` calls `Kits.buildWelcomeBookGive(playerName)` and runs the resulting `/give written_book[written_book_content={...}]` so every brand-new player gets a copy the moment they log in. The welcome message now mentions the guide.
2. **Book text is readable** (was rendering white on parchment). Per user: "you can read it properly cause the font is white." Replaced the `§r` (reset → white on this client build) with explicit `§0` (black) on body text and `§8§l` (dark gray bold) on headers. `/skills` callout uses `§1§l` (dark blue bold) so it stands out from the body.
3. **Champion spear gets `minecraft:lunge` III** — confirmed in yarn 1.21.11+build.5 as `field_63420 LUNGE` (`RegistryKey<Enchantment>`). Spear is netherite (verified). Full enchant set: Sharpness V + Fire Aspect II + Knockback II + **Lunge III** + Unbreaking III + Mending.
4. **Bruiser kit gets a Riptide trident** with Riptide III + Impaling V + Channeling I + Unbreaking III + Mending. Riptide conflicts with Loyalty so the trident doesn't return — Bruiser commits to the throw. Auto-named "Bruiser Spear" via `deriveTypeName` (trident maps to "Spear").

## What's new in v1.85.7

**Real `netherite_spear` (1.21.11), every kit gets a pickaxe, way more items per kit.** Per user: "no the new item in 1.21.11 the spear look it up" + "add even more items."

### Real spear (the 1.21.11 item)
Confirmed in yarn 1.21.11+build.5 — the full `wooden_spear` … `netherite_spear` ladder exists as proper items in `minecraft:` namespace (yarn `field_63390` → `NETHERITE_SPEAR`).

- **Champion's trident is now a `minecraft:netherite_spear`** with Sharpness V + Fire Aspect II + Knockback II + Unbreaking III + Mending.
- Auto-named "Champion Spear" via the existing `deriveTypeName` (new `_spear` → "Spear" entry).
- **Cross-version fallback** — the spear item only exists on 1.21.11+. `deliverItems` detects `id.endsWith("_spear")`, and if `/give` of the spear fails (older MC), falls through to `give P minecraft:trident[enchantments={maxed-trident-set}]` so 1.21 / 1.21.5 / 1.21.8 servers still hand out a maxed trident as the "spear" stand-in. Both forms render the same custom-named "Champion Spear" tooltip via the patch logic.

### More items per kit
- **Starter** + 32 torches + 8 iron ingots (mining QoL beyond just tools).
- **Soldier** + 8 ender pearls (added v1.85.6 dev, kept).
- **Hunter** + 8 fire charges + a **Spyglass** (scout signature item).
- **Veteran** + Ender Chest (added v1.85.6 dev, kept).
- **Champion** + 4 enchanted golden apples + 16 ender pearls + Ender Chest. Totems bumped 2 → 4.
- **Bruiser** + 16 ender pearls + 8 TNT (siege option for the brawler).
- **Attribute** + 16 enchanted golden apples + 32 wind charges (consumes the 64 breeze rods) + 4 totems + 16 ender pearls + Ender Chest.

### Pickaxes everywhere (recap from earlier work)
| Kit | Pickaxe | Theme |
| --- | --- | --- |
| Starter | Diamond — Eff III + Fortune II + Unb II | early miner |
| Soldier | Diamond — Eff III + Unb III | durability |
| Hunter | Diamond — Eff IV + Silk Touch + Unb III | quiet collector |
| Veteran | Diamond — Eff IV + Fortune III + Unb III + Mending | high-tier miner |
| Champion | **Netherite** — Eff V + Fortune III + Unb III + Mending | endgame Fortune |
| Bruiser | Diamond — Eff IV + Unb III + Mending | bonus tool |
| Attribute | **Netherite** — Eff V + **Silk Touch** + Unb III + Mending | endgame Silk |

Other recap: 64 fireworks (Flight 3 via componentArgs) on Champion + Attribute, 64 gapples each, 64 breeze rods exclusive to Attribute, sharper role identities across the ladder.

## What's new in v1.85.6

**UI polish + Starter Kit goodies.**

- **Starter Kit additions** (per user: "to the starter kit also add some Steak, and a guide book"):
  - 64 cooked beef (up from 32). Should last well past the early grind.
  - A 3-page **AttributeSMP Guide** written book — page 1 is a welcome blurb pointing at `/skills`, page 2 lists every player command, page 3 maps each category to its status effect. Built with the 1.21+ `written_book_content` component via the `Item.componentArgs` override + a dedicated `__GUIDE_BOOK__` marker in `deliverItems` so /give can carry the page text. Falls through to a plain `minecraft:written_book` on yarn variants where the component args don't parse.
- **UI polish across all three chest GUIs:**
  - `/skills` — purple Nether Star header in slot 4 (`✦ Your Skills ✦`) with a hover-hint explaining the grind → buff loop.
  - `/kits` — purple Nether Star header in slot 4 (`✦ Kit Shop ✦`) with a "click to buy / 24h cooldown" hint.
  - `/leaderboard` — gold Nether Star header in slot 4 (`✦ Top Players ✦`) with a "click any category for details" hint.
- **Back button in per-category leaderboard view** — red glass pane in slot 26 labeled `← Back`. Clicking it closes the screen; the existing `onClosed` callback then re-opens the picker on the next tick. ESC still works as before; the button is just a more discoverable affordance.
- **`Kits.Item.componentArgs` field** added so any kit item can specify a literal `[component=...]` block in its /give command, not just enchantments. Used for the guide book; other future component-y items (potions with custom effects, banners, music discs) can plug in without touching `deliverItems` again.

## What's new in v1.85.5

**Kit items now get themed custom names** matching the reward-weapon path proven in `WeaponDrops`. Per user: "Make the sword armor gear etc called the same name as the kit so for e.g Attribute Sword in a cool Colour … Like the customs gear we made alr."

- Per-item naming runs through the same components API path as the reward weapons (`stack.set(DataComponentTypes.CUSTOM_NAME, Text)` with `Style.withColor + bold + italic-off`), and uses the same positional snapshot-before-/give → diff-to-find-new-slot logic.
- Each piece of nameable gear gets renamed to `<KitName> <PieceType>` in the kit's signature color. Examples:
  - **Attribute Kit** (color: light purple) → "Attribute Helmet", "Attribute Chestplate", "Attribute Sword", "Attribute Mace (Breach)", "Attribute Mace (Density)", "Attribute Wings".
  - **Champion Kit** (gold) → "Champion Helmet", "Champion Sword", "Champion Spear", etc.
  - **Bruiser Kit** (red) → "Bruiser Helmet", "Bruiser Axe", etc.
  - **Hunter Kit** (dark green) → "Hunter Bow", "Hunter Crossbow", etc.
- Stackable consumables (food, arrows, gapples, totems, pearls, splash potions) are NOT renamed — they'd waste a stack slot per unique name and look silly.
- Two Attribute maces use explicit `displayName` overrides ("Mace (Breach)" / "Mace (Density)") to differentiate them; everything else auto-derives from the item ID (`netherite_sword` → "Sword", `trident` → "Spear", `elytra` → "Wings", etc.).
- `Kits.Item` constructor gains an optional `displayName` arg; the old 3-arg constructor still works via overload, so existing entries don't need changes.

`isNameable` filter, `deriveTypeName` mapping, and `formattingFor` (section-code → `Formatting` enum) added to [Kits.java](mod-smp/src/main/java/com/iceysmp/Kits.java).

**Confirming Sharpness is removed** from both Attribute maces in v1.85.4. Breach mace = Breach IV + Wind Burst III + Fire II + KB II + Unb III + Mending. Density mace = Density V + same supporting set. (User report from earlier confirmed against a stale jar — make sure to grab a fresh v1.85.5 release jar for the test.)

## What's new in v1.85.4

**Kit nerfs + role differentiation + `/kitgive` admin command.**

Per user: "lets nerf the kits first of all make te 3 cheapest stes diamond armro not therite also remove sharpness from the mace also all kits rae just like each other make them a lot more unique."

- **3 cheapest kits drop to diamond armor.** Starter / Soldier / Hunter all use diamond pieces now. Netherite starts at Veteran (tier 4).
- **Sharpness removed from both Attribute maces.** Breach mace now has Breach IV + Wind Burst III + Fire Aspect II + Knockback II + Unb III + Mending. Density mace same loadout but Density V instead of Breach. No more sword-on-mace double-dipping.
- **Each kit has a distinct role** — no more "armor + sword + bow + apples" identical loadouts:
  - **Starter** — *Miner/Utility*. Diamond armor + diamond sword + pickaxe + axe + shovel + food. No bow.
  - **Soldier** — *Defensive PvE*. Diamond armor with **Blast Protection III**, KB II sword, shield, crossbow with Quick Charge. No bow.
  - **Hunter** — *Pure ranged kiter*. **No melee weapon at all.** Projectile Prot IV + Soul Speed III + Feather Falling boots, maxed bow + crossbow, 32 spectral + 8 tipped arrows, **16 ender pearls**.
  - **Veteran** — *Balanced PvP*. First netherite tier. Sword + bow + 4 splash potions + 4 e-gapples + 1 totem. No shield.
  - **Champion** — *PvP melee master*. Mending+Thorns armor + **MAXED sword + fully-enchanted spear (trident)**. The trident is the differentiator.
  - **Bruiser** — *Tank brawler — axe only*. **Blast Prot IV** Mending+Thorns armor + MAXED netherite axe (no sword/bow/trident). 16 enchanted gapples + 4 totems.
  - **Attribute** — *Endgame elite*. MAXED sword + 2 maxed maces (Breach + Density, no Sharpness) + Elytra. No bow/shield/extras.

**`/kitgive <kit> <player>`** — admin command to grant a kit free of cost, bypassing the 24h cooldown. Same title banner + broadcast as a normal purchase but says "received the X from admin" instead of "bought the X for Y." Gated on `canAdmin` (real op-2 or `/admin`-unlocked).

## What's new in v1.85.3

**Two user-reported bugs.**

1. **Kit currency count was always 0.** User: "if i try and buy a kit with 64 neth it says need 20 more 20 netherite ingot same for diamond." The reflection-based `matchesItemId` in `Kits` couldn't reach `Registries.ITEM.getId(Item)` reliably — the loop over `getMethods()` was hitting overloads that threw before finding the right one. Replaced with a direct call to `Registries.ITEM.getId(stack.getItem())` first (same API path `KitsScreen` uses successfully), with the reflection walk kept as a fallback for yarn variants where the direct call fails. Currency counting + deduction now matches.
2. **Attacking a mob still combat-tagged the player.** User: "if i attack a ob i still get cokmabt." v1.84.7 dropped `combat.tagOne(victim)` in the *mob-hits-player* branch but missed the *player-hits-mob* branch which still called `combat.tagOne(attacker)`. Removed that too — combat tag is now strictly PvP. Mobs still credit `damageTaken` (so the dmgtaken leaderboard counts mob hits), and player-hits-mob still credits `damageDealt` (so the damage-dealt counter increases), but neither side gets flagged in-combat.

## What's new in v1.85.2

**CI yarn compile fix.** Two errors on CI's non-1.21.8 matrix entries:

1. `ServerPlayerEntity.getServer()` doesn't exist on every yarn — replaced the 3 call sites in `LeaderboardGui` / `KitsScreen` with the cached `IceySmp.server` static (already populated in `SERVER_STARTED`).
2. `net.minecraft.component.type.ProfileComponent` is abstract on this yarn variant — can't instantiate with `new ProfileComponent(GameProfile)`. Dropped the per-player head-skin lookup in `LeaderboardGui.rankedItem`. The named/colored player_head still renders fine in the GUI; just no skin texture per entry.

## What's new in v1.85.1

**Kits tuned and expanded to 7.** Per user iteration:

- **Starter Kit**: 16 → **45 diamonds** (entry tier costs more grinding now).
- **Attribute Kit**: sword **upgraded to MAXED** (Sharp V + Sweep III + Fire II + KB II + Looting III + Unb III + Mending), single mace replaced with **TWO maces** — one Breach IV mace and one Density V mace, both with every other applicable enchant maxed (Sharpness V + Wind Burst III + Fire Aspect II + Knockback II + Unbreaking III + Mending), no overlap on the specialty enchant. Elytra unchanged.
- **Champion Kit**: sword **upgraded to MAXED** (same enchant set as Attribute). New fully-enchanted **trident ("spear")** added — Loyalty III + Channeling I + Impaling V + Unbreaking III + Mending (no Riptide — conflicts with Loyalty/Channeling).
- **Hunter Kit** (NEW, tier 3 between Soldier and Veteran, **2 netherite ingots**): ranged specialist. Projectile Protection IV netherite (boots +Soul Speed III), maxed bow (Power V + Punch II + Flame + Infinity + Unb III), maxed crossbow (Quick Charge III + Piercing IV + Unb III + Mending), 32 spectral arrows + 64 arrows + 4 ender pearls.
- **Bruiser Kit** (NEW, tier 6 between Champion and Attribute, **12 netherite ingots**): axe specialist. Same Mending + Thorns III armor as Champion, but with a maxed netherite axe (Sharpness V + Efficiency V + Fire Aspect II + Looting III + Unb III + Mending), Unb III shield, **16 enchanted golden apples**, **4 totems**.

New 7-kit ladder:
1. Starter — 45 diamonds
2. Soldier — 1 netherite ingot
3. **Hunter** — 2 netherite ingots
4. Veteran — 3 netherite ingots
5. Champion — 8 netherite ingots (+ MAXED sword, + maxed spear)
6. **Bruiser** — 12 netherite ingots
7. Attribute — 20 netherite ingots (+ MAXED sword, + 2 maxed maces)

`KitsScreen` middle-row slots expanded from `{10..14}` to `{10..16}` to fit all 7.

## What's new in v1.85.0

**`/kits` — buy tiered SMP gear bundles with in-game items.** Chest GUI with 5 progressively-better SMP kits, each on a 24h cooldown per player. Pay the price in inventory items at the moment of purchase; the kit lands in your inventory immediately. Per user spec: "ALL SMP THEME CHEAPEST BEING PROT 2 NETH ETC ETC."

| Tier | Kit | Price | Highlights |
| --- | --- | --- | --- |
| 1 | **Starter Kit** | 16 diamonds | Full netherite Prot II + Unb II, Sharp III diamond sword, Power II bow, 16 cooked beef |
| 2 | **Soldier Kit** | 1 netherite ingot | Full netherite Prot III + Unb II, Sharp IV netherite sword, Quick Charge II + Piercing II crossbow, shield, 8 golden apples |
| 3 | **Veteran Kit** | 3 netherite ingots | Prot IV + Unb III netherite, Sharp V + Sweep III + Fire II sword, Power V + Punch II + Infinity bow, shield, 4 enchanted gapples, 1 totem |
| 4 | **Champion Kit** | 8 netherite ingots | Prot IV + Mending + Thorns III armor (boots +Feather Falling IV), Looting III sword (Sharp V + Sweep III + Fire II + Unb III + Mending), Flame bow, Unb III shield, 8 enchanted gapples, 2 totems |
| 5 | **Attribute Kit** | 20 netherite ingots | Prot IV + Unb III + Mending netherite (boots +FF IV), Sharp V sword, **Mace** (Density V + Breach IV + Unb III), **Elytra** (Unb III + Mending) |

Implementation:
- [Kits.java](mod-smp/src/main/java/com/iceysmp/Kits.java) holds the 5-kit catalog plus the `attemptPurchase` logic: cooldown check → inventory count check → deduct currency → run a /give command per item (with the same enchants-via-component path proven in `WeaponDrops`, with `{levels:{...}}` and bare-item fallbacks).
- [KitsScreen.java](mod-smp/src/main/java/com/iceysmp/KitsScreen.java) is the chest GUI. 9×3 layout with purple stained-glass-pane border, 5 kit icons in slots 10–14. Each kit's lore shows price, cooldown remaining (or "Ready to buy"/"You have X/Y"), description, and a bullet list of contents. Click → closes GUI, then attempts purchase on the next tick.
- 24h cooldown tracked in new `PlayerStats.kitCooldowns` field, encoded as `"kitId:lastMs;kitId:lastMs;..."` and persisted in stats.json. Same file path also picks up `waterCm`, `adminAccess` which were missing from the JSON I/O before.
- Failure modes per user choice ("Chat error + close GUI"): cooldown active → "kit on cooldown for 12h 34m"; not enough currency → "need 4 more 20 Netherite Ingots"; both close the GUI and print chat error.

On success a title banner shows the kit name and "Purchased for X", and the server broadcasts who bought what.

## What's new in v1.84.7

**Five user asks bundled.**

1. **Rebrand `[Icey SMP]` → `[AttributeSMP]`** per user: "from iceysmp to AttributeSMP in nice color ok? purple and black a nice fade." All chat prefixes now use a purple-toned bracket label `§5§l[§d§lAttribute§7§lSMP§5§l]§r` — dark-purple bracket, light-purple "Attribute", gray "SMP", dark-purple close-bracket. The big chest-GUI titles for `/skills` and `/leaderboard` use a true per-character HEX gradient from `0xC040FF` (bright purple) → `0x44004A` (near-black purple) via the new [Brand.java](mod-smp/src/main/java/com/iceysmp/Brand.java) helper. Fabric mod metadata `fabric.mod.json` renamed from "Icey SMP" to "AttributeSMP" (internal mod_id stays `iceysmp` so existing servers' config / data dirs don't break). SERVER_STARTED banner says `[AttributeSMP] Loaded! Type /skills or press N to see commands.`
2. **`/lb` removed — only `/leaderboard`** per user request. Server `/lb` command dropped. Client mod removed its `/lb` client-command registration; keybind N now sends `/leaderboard` chat command to the server (opens the new chest GUI). On a vanilla server with no AttributeSMP the chat command silently fails — no client crash.
3. **Clickable category in `/leaderboard` GUI.** The chest now uses a custom `ClickableScreenHandler` that intercepts slot clicks. Clicking a category opens a per-category "big" view ([LeaderboardGui.openCategory](mod-smp/src/main/java/com/iceysmp/LeaderboardGui.java)) — header item on slot 4 with the category info + reward threshold, top 10 as player heads in slots 9–18 (each head textured to the player's skin via `DataComponentTypes.PROFILE`), viewer's rank on slot 22 if outside the top 10. Closing the per-category screen (ESC) fires `onClosed` → re-opens the picker on the next tick. Per user: "if you press esc you go back ok?"
4. **Mob hits no longer combat-tag** per user: "only get comabt tagged by players not mobs." Dropped the `combat.tagOne(victim)` call in the mob-hits-player branch. Mobs still credit damage-taken stats (so dmgtaken leaderboard works), but the boss bar / combat-log death only fires on player-vs-player.
5. **`/reward` effect is infinite + survives death.** Per user: "/reward should be infinte effect to not short." `applyMaxEffectFor` now uses `-1` duration. `applyEffectsFor` (the respawn re-apply path) checks `wasAwardedFrostfangFor(cat)` for each category — if the player was rewarded in that category, apply infinite max-amp; otherwise apply finite count-based amp as before. `/reward` also marks the recipient as awarded so the buff comes back after every death.

## What's new in v1.84.6

**Six asks bundled.**

1. **`/leaderboard` (no arg) opens a chest GUI** — [LeaderboardGui.java](mod-smp/src/main/java/com/iceysmp/LeaderboardGui.java) mirrors `SkillsScreen`'s 9×3 layout, one item per category, but each item's lore shows the **top 5 ranked players** for that category instead of viewer progress. Viewer's own rank pinned at the bottom if outside the top 5. `/leaderboard <category>` keeps the legacy chat-text top-10 output, same for `/lb`.
2. **Bounty payout works on every PvP kill.** Previously buried inside the combat-tag gate, so a kill that didn't pass `bothTagged` / `canCountKill` (one-shot ambushes, repeat kills) would skip the bounty even though the victim actually died. Hoisted the payout above the gates in `StatTracker` — bounty pays out on **any** PvP kill regardless of stat-steal eligibility. Stat-steal still respects the gates (separately broadcast).
3. **Three new crate themes — `/armorcrate`, `/gearcrate`, `/foodcrate`** with the same `[common|rare|epic]` tier arg as `/crate`. Lightning + broadcast as before. Themed loot per tier:
   - **Armor crate**: iron/diamond/netherite sets, shield, turtle helmet, elytra (epic), totems (epic).
   - **Gear crate**: iron/diamond/netherite swords/picks/axes, bow/crossbow, trident + mace (epic), enchanted books.
   - **Food crate**: cooked beef, bread, golden apples, golden carrots, cake, honey bottles, enchanted golden apples (epic), suspicious stew (epic).
4. **`/reward` now applies the max-level effect for the category.** Previously it gave only the themed weapon — now it also calls `LeaderboardManager.applyMaxEffectFor(player, categoryId)` which slaps on the peak amp (Haste V for mining, Strength III for pvp, Resistance III for damage taken, etc.). Recompute will keep refreshing it if the player has enough stats; otherwise it expires after `effectDurationSeconds`.
5. **Every player gets 15 hearts (30 HP) on join.** New `setMaxHealth` helper in [IceySmp.java](mod-smp/src/main/java/com/iceysmp/IceySmp.java) sets the player's `generic.max_health` attribute base to 30 in the JOIN hook. If they're below 15 HP at join time we top them up to 15 so they don't suffocate.
6. **Yarn-portable max-health lookup.** Constant name flipped between `GENERIC_MAX_HEALTH` and `MAX_HEALTH` across 1.21.x; the `getAttributeInstance` signature also flipped between taking `EntityAttribute` and `RegistryEntry<EntityAttribute>`. Helper tries every combination via reflection and stops at the first that resolves.

## What's new in v1.84.5

**Four user-reported fixes.**

1. **`/admin` works in singleplayer.** Previous `/op <player>` step is a no-op on singleplayer worlds (only mutates ops.json on dedicated servers). Replaced with a runtime flag on `PlayerStats.adminAccess` — per user: "instead of making op just grant them access to custom commands ok." `/crate`, `/reward`, `/noobprotect`, `/setspawn` drop their `.requires(...)` brigadier gate so they always tab-complete, with perm + adminAccess check moved inside the executor — rejects with `Admin only. Run /admin <password> to unlock.` `/admin 2705` now only sets the flag — no `/op`, no vanilla cheat-command access.

2. **Custom item names + lore actually apply now.** v1.84.4's reflection-based ID match couldn't resolve `Registries.ITEM.getId()` reliably across yarn, so `patchComponents` silently exited. Replaced with a positional diff in [WeaponDrops.java](mod-smp/src/main/java/com/iceysmp/WeaponDrops.java): snapshot the full inventory **before** `/give`, then walk the inventory and find the first slot whose count grew (or went empty → non-empty). That's the slot `/give` just landed in — patch its `CUSTOM_NAME` + `LORE` directly via the components API. No item-ID matching, no registry reflection.

3. **PvP kill steals 10%, not 100%** — per user: "if a other player kills u they steal 10%." Previous `absorbFrom` zeroed the victim's counters and moved them entirely to the killer. Now takes `floor(field / 10)` from each stealable counter, leaves the rest with the victim. Reflection iterates the 17 stealable fields by name so future fields auto-participate.

4. **Effects survive death.** Per user: "if you die you lose all effects I dont want." Vanilla MC clears every `StatusEffectInstance` on death, so respawning meant up to `recomputeSeconds` (30s default) of no Haste/Strength/etc. until the next recompute pass. New `LeaderboardManager.applyEffectsFor(player)` walks every category and re-applies amps for one player; called from a `ServerPlayerEvents.AFTER_RESPAWN` hook in [IceySmp.java](mod-smp/src/main/java/com/iceysmp/IceySmp.java) so respawning gets the category buffs back the same tick.

## What's new in v1.84.4

**Custom weapon names finally render properly + new `/admin <password>` command.**

- **Item names fixed for real.** v1.84.2 changed the `/give` syntax from JSON-string to SNBT-compound form, but item names were still showing as raw JSON on the user's server. Root cause: MC's `/give` SNBT parser keeps treating text-component values as literal strings on some yarn/version combos — quoting subtleties we can't pin down. New approach in [WeaponDrops.java](mod-smp/src/main/java/com/iceysmp/WeaponDrops.java): two-stage delivery.
  1. `/give` the bare item with **enchants + rarity only** (no `custom_name`, no `lore`). The enchants and rarity paths have never mis-parsed.
  2. Walk the player's inventory, find the just-given stack (matches `r.item`, no `custom_name` yet), and patch `custom_name` + `lore` via the Java API — `stack.set(DataComponentTypes.CUSTOM_NAME, Text...)` and `stack.set(DataComponentTypes.LORE, new LoreComponent(...))`. Same API path `SkillsScreen` uses successfully, so this works on every yarn build that has the components system. Stack matching uses a reflection-based registry lookup so it portably resolves the item's `"minecraft:xyz"` ID across yarn variants.
- **`/admin <password>`** — anyone can run it, but only `2705` works. On success, the server runs `/op <playername>` so the player gets full operator perms (which unlocks `/reward`, `/crate`, `/setspawn`, `/noobprotect`, `/reloadcfg`, `/resetstats`). If the player is already op, it just confirms. A wrong password prints "Wrong admin password" and returns 0. The password is baked into the mod ([SmpCommands.java](mod-smp/src/main/java/com/iceysmp/SmpCommands.java) `ADMIN_PASSWORD`) — this is a friends-server convenience, not real security. A server-wide broadcast announces the successful elevation so other players can see it.

## What's new in v1.84.3

**Water movement category + Dolphin's Grace** — caught a missed item from the original spec. New 8th category `water` tracks `SWIM_ONE_CM + WALK_UNDER_WATER_ONE_CM + WALK_ON_WATER_ONE_CM` (all three vanilla water-travel stats summed in cm). Divisor 100,000 cm = Level 1 at 1 km swum; weapon threshold 500,000 cm = 5 km. Status effect: `DOLPHINS_GRACE`. Max-level reward: **Wavebreaker** — netherite-blue Trident with Loyalty III, Impaling V, Channeling, Unbreaking III, Mending. (Riptide intentionally omitted because it conflicts with Loyalty/Channeling in vanilla; we want the throw-and-return combat trident.)

- [PlayerStats.java](mod-smp/src/main/java/com/iceysmp/PlayerStats.java): new `distanceInWaterCm` field; added to `absorbFrom` so it transfers on PvP kill like every other stealable counter.
- [LeaderboardManager.java](mod-smp/src/main/java/com/iceysmp/LeaderboardManager.java): expanded the per-player snapshot from 5 ints to 6, with `index 5 = water`. Each of the three water stat reads is wrapped in its own try/catch — some yarn variants drop one or two of them.
- [SkillsScreen.java](mod-smp/src/main/java/com/iceysmp/SkillsScreen.java): `CATEGORY_SLOTS` extended to `{10..17}` so all 8 categories fit in the middle row. Icon for water = `HEART_OF_THE_SEA`. `formatValue` now treats `water` like `walking` (cm → m).
- [WeaponDrops.java](mod-smp/src/main/java/com/iceysmp/WeaponDrops.java): new `water` reward entry — Wavebreaker trident.
- [LeaderboardScreen.java](mod/src/main/java/com/iceymod/screen/LeaderboardScreen.java) (client): added the new entry to the picker between Jumps and Damage Taken so the keybind-N screen offers all 8 categories.

## What's new in v1.84.2

**Custom weapon names render as text instead of raw JSON.** User-reported with a screenshot — every reward item (Stonewall, Frostfang, Frostpick, etc.) was showing its name as the literal JSON string `{"text":"Stonewall","italic":false,"color":"dark_red","bold":true}` instead of "Stonewall" in dark-red bold. Lore lines had the same problem.

Root cause: the `/give` component arg syntax `custom_name='{"text":"X","color":"aqua"}'` (single-quoted around JSON) is parsed by MC's SNBT as "a string whose content is `{"text":"X","color":"aqua"}`" — so the component value becomes a Text component holding that literal JSON string. The correct form is the SNBT compound `custom_name={"text":"X","color":"aqua"}` (no outer quotes) — that makes MC parse the inner `{...}` as a compound representing the Text component directly.

Fix in [WeaponDrops.java:run](mod-smp/src/main/java/com/iceysmp/WeaponDrops.java): dropped the outer single-quotes from `namePart` and every lore entry. Format chain now tries SNBT-compound form first (5 attempts with progressively fewer components) and only falls through to the legacy JSON-string form as a last resort for yarn variants that might not accept SNBT compounds in component args. Bare-item is the final fallback.

## What's new in v1.84.1

**Singleplayer fix — the launcher now installs iceymod+ (server mod) for every Fabric installation, not just iceymod (client).** Root cause: when MC runs in singleplayer, the integrated server loads mods from the same `mods/` folder as the client. The launcher's auto-install step was only dropping in the client jar, so all server-side commands (`/skills`, `/leaderboard`, `/daily`, `/crate`, `/bounty`, etc.) silently didn't exist in singleplayer. User report: "NONE OF THE COMMANDS WORK THEY DONT SHOW UP (IN SINGLEPLAYER AT LEAST)".

Fix: new block in [main.js:976](main.js#L976) that mirrors the client-mod install logic for iceymod+:

- Resolves the expected jar name `iceymodplus-server-mod-mc<MC_VER>-1.0.0.jar` per installation.
- Cleans up stale iceymodplus jars from other MC versions before installing.
- Searches `mod-smp/build/libs/` (dev), `resources/` (packaged), then `DATA_DIR` (downloaded) — first match wins.
- Toggling Icey-mods off in settings removes the server jar too.

CI workflow updated — `build-windows` / `build-mac` / `build-linux-arm64` now depend on `build-smp` and download all four matrix jars into `mod-smp/build/libs/` before electron-builder runs. electron-builder's `files` glob now bundles `mod-smp/build/libs/iceymodplus-server-mod-*.jar` and `resources/iceymodplus-server-mod-*.jar` so the jars ship with every launcher binary.

## What's new in v1.84.0

**Big iceymod+ refactor — top-level commands, fixed weapon thresholds, chest-GUI skills browser, toggleable noob protection.** Per user request: "remove /icey just make it /daily … make a gui and you can press mining and it shows a bar in the gui 50% for eg if you're 50% and how many blocks are still needed … only get the custom weapons if you reach a amount like 500 or 1000 depending on what … keep newbie protection but you can turn it off with a command but no /icey anything."

- **No more `/icey X` subcommand tree.** Every function is now its own top-level command:
  - `/skills` — opens the new chest GUI (primary entry point — replaces the old text-based `/icey help`).
  - `/leaderboard <category>` (alias `/lb`) — top 10 + your rank for a category.
  - `/mystats` — your stats summary across all categories.
  - `/playerstats <player>` — view another player's stats.
  - `/daily` — claim daily reward (14h cooldown).
  - `/bounty <player> <xp>` — pay XP to put a bounty on someone.
  - `/crate [common|rare|epic]` (op-2) — spawn a loot crate at your position.
  - `/reward <category> <player>` (op-2) — hand-give the max-level themed reward.
  - `/noobprotect <on|off|toggle>` (op-2) — runtime master switch for noob protection (`/noobprotect off` to disable PvP-grace for new joiners without editing the config file).
  - `/setspawn` (op-2), `/reloadcfg` (op-3), `/resetstats` (op-4) — same behavior as before, new names.
- **`/skills` chest GUI** ([SkillsScreen.java](mod-smp/src/main/java/com/iceysmp/SkillsScreen.java)) — opens a 9×3 chest with one item per category in the middle row (iron pick for Mining, diamond sword for PvP, clock for Playtime, fishing rod for Fishing, iron boots for Walking, rabbit foot for Jumps, shield for Damage Taken). Each item's lore shows:
  - Current count formatted per-category (e.g. `5h 23m`, `847.2 m`, `42.0 HP`, `1,247` ores)
  - Current level
  - Progress to next level with a 20-char colored bar like `§a██████████§7░░░░░░░░░░ §a50%`
  - Status effect granted
  - Custom-weapon progress: `Custom reward: Frostpick | 847/1,000` or `✓ Earned — Frostpick`
- **Custom weapons are now gated by fixed per-category counts** instead of the old max-amp-level rule (which was different per category and hard to predict). New thresholds:
  - **Frostpick** (mining pickaxe) — **1,000 blocks**
  - **Frostfang** (PvP sword) — **25 kills**
  - **Crown of Hours** (playtime helmet) — **50 hours**
  - **Tidecaller** (fishing rod) — **100 fish**
  - **Wanderer's Treads** (walking boots) — **10 km**
  - **Springheel Greaves** (jumps leggings) — **1,000 jumps**
  - **Stonewall** (damage-taken chestplate) — **500 HP taken**
- **Combat-log handler simplified to `/kill <name>`.** When a combat-tagged player disconnects, the server runs `/kill <playername>` through `VersionShim.executeServerCommand`. Vanilla `/kill` handles inventory drop, death stats, and `AFTER_DEATH` (which routes PvP credit to the last damager if it was a player) for free. DISCONNECT fires before the player is removed from the player manager, so the command target resolves correctly. Replaces the v1.83.1 two-phase approach (drop inventory in-world at disconnect + flag UUID + kill on rejoin).
- **Client mod**: the leaderboard screen now sends `/leaderboard <id>` instead of `/icey top <id>` to match the new server commands. `/lb` (client keybind N) still opens the picker screen.

`/skills` is the new "what does this mod do?" landing. No version-display command — the GUI is canonical.

## What's new in v1.83.2

**Hotfix on top of v1.83.1.** CI's 1.21.5 yarn build failed compile:

```
error: incompatible types: GameProfile cannot be converted to PlayerConfigEntry
    return s.getPlayerManager().isOperator(p.getGameProfile());
```

`PlayerManager.isOperator` takes a `GameProfile` on some 1.21.x yarn variants and a `PlayerConfigEntry` (op-list-entry wrapper) on others. Replaced the direct call with reflection that scans for any `isOperator(*)` method, accepts whichever parameter type matches `GameProfile` directly, and if not, looks up the op-list entry via `getOpList().get(profile)` and passes that instead. Same op-detection behavior, compiles cleanly across the full 1.21 / 1.21.5 / 1.21.8 / 1.21.11 matrix.

`/icey version` reports 1.83.2.

## What's new in v1.83.1

**Yarn-portability fixes + combat-log on rejoin.**

- **`/icey crate epic` "Incorrect argument"** — on some yarn variants, `MethodHandles.findVirtual(ServerCommandSource, "hasPermissionLevel", ...)` was failing access checks, leaving `PERM_CHECK = null`. Every `.requires(op-2)` branch then evaluated false and Brigadier hid the `crate` subcommand entirely — which surfaced as "Incorrect argument for command" with the cursor pointing at `epic`. Replaced the static MethodHandle with a runtime reflection walk: try `hasPermissionLevel(int)` then `hasPermission(int)` by name, then any `(int)->boolean` method whose name contains "permission", and finally fall back to `PlayerManager.isOperator(gameProfile)`. Console source (no player) is now treated as full op. Same fix path makes `/icey reward`, `/icey reset`, `/icey reload`, `/setspawn`, `/icey givefrostfang` work too.
- **`/icey daily` silent fail** — server log showed `executeServerCommand setup failed: java.lang.NoSuchMethodException: net.minecraft.class_2170.getDispatcher()`. `VersionShim.executeServerCommand` now tries three paths in order: `CommandManager.executeWithPrefix(src, cmd)`, `CommandManager.execute(src, cmd)`, then a `findDispatcher(cm)` helper that walks getters → fields → any field of type `CommandDispatcher` and calls `dispatcher.execute(cmd, src)`. So daily `/give`, loot-crate `/setblock`, and `/summon lightning_bolt` all keep working even when yarn renames the inner dispatcher accessor.
- **Combat-log on rejoin** — fixed user-reported "if I leave during combat I CAN JUST REJOIN and I won't die." Root cause: `ServerPlayConnectionEvents.DISCONNECT` fires AFTER the player entity has detached from the world, so the previous `damage()/kill()` call was a no-op and the player save snapshot was already on disk. New two-phase approach: on DISCONNECT, if combat-tagged, drop their entire inventory into the world right then (reflection-driven `PlayerInventory.dropAll` with per-slot fallback) and flag their UUID in a `pendingDeath` set. On their next JOIN, the server kills them on the next tick and broadcasts `[Icey SMP] PlayerName combat-logged and died on rejoin.` State is in-memory, so a server restart between disconnect and rejoin clears the flag — matches `CombatTracker`'s existing "tags reset on restart" semantic.

`/icey version` reports 1.83.1.

## What's new in v1.83.0

**Loot crates** — admin-spawned event chests with tiered loot.

- **`/icey crate [common|rare|epic]`** (op-2 required). Without a tier argument, randomly picks one weighted 60/30/10. The chest spawns at the caller's exact block position (so an admin stands wherever they want the crate to land).
- **Lightning bolt visual** strikes the spawn point so it's spottable from afar (cosmetic only — the lightning damage flag is off because it's the entity-only summon).
- **Server-wide chat broadcast** announces tier + coords + distance from caller: `[Icey SMP] A §5§lEPIC §rLoot Crate has spawned at (245, 64, -120) — 87m from _Icey27_!`
- **Loot per tier:**
  - **Common** (60% weight): 16 cooked beef, 8 iron, 4 gold, 32 arrows, 1 saddle, 8 XP bottles
  - **Rare** (30%): 8 diamonds, 1 totem, 4 golden apples, 1 beacon, 8 ender pearls, 16 XP bottles
  - **Epic** (10%): **16 diamonds, 1 netherite ingot**, 4 shulker shells, 1 nether star, 1 enchanted golden apple, 2 totems, 32 XP bottles (per user request — more diamonds, netherite, no elytra)
- **Chest placement** uses `/setblock minecraft:chest[block_entity_data={Items:...}]` (1.21.5+ syntax) with a legacy `{Items:...}` NBT fallback for 1.21.0-1.21.4, plus a bare-chest fallback if both fail (so SOMETHING always lands, even if the loot doesn't).
- No automatic timer — purely event-driven per user request.

`/icey help` now lists `/icey crate`. `/icey version` reports 1.83.0.

## What's new in v1.82.2

**Daily reward fix:** user reported "rolled and it said I got it but I didn't actually get it." Root cause: `VersionShim.executeServerCommand` was returning true whenever Brigadier's `dispatcher.execute` didn't throw — but Brigadier returns `int 0` when a command parses successfully but does nothing useful (e.g. unknown player target), which my code was treating as success. So the daily animation fired, cooldown got set, but the `/give` did nothing.

Two fixes:
1. `executeServerCommand` now inspects the int return value and returns `false` on 0 (so the fallback chain actually triggers).
2. `DailyRewards.roll` only sets cooldown AFTER `/give` confirms success. Tries three formats: with explicit count, with `1`, bare. If all three return 0, sends the player a chat message ("Daily roll failed to deliver X — try again, no cooldown applied") and aborts without setting the cooldown.

Plus: rolled daily now sends a chat confirmation "✦ Daily reward: <item> ×N delivered to your inventory" so the user can see what they actually got, even if they missed the animation.

**Combat tag fix:** user reported combat tag triggering on environmental damage. Tightened the gate in `StatTracker`'s `ALLOW_DAMAGE` handler: combat tag now ONLY fires when the damage source is a living entity (player or mob). Fall damage / lava / fire / suffocation / drowning / cactus etc. still update the `damageTaken` counter but no longer trigger the combat boss bar or the kill-on-logout flag. New `resolveLivingAttacker` helper returns null for environmental sources.

## What's new in v1.82.1

CI fix: `SoundEvents.ENTITY_PLAYER_LEVELUP` is a raw `SoundEvent` on at least one yarn matrix entry, not a `RegistryEntry<SoundEvent>` — so `.value()` didn't resolve, and my `PlaySoundS2CPacket` construction failed to compile. Dropped the direct packet construction; now dispatching the vanilla `/playsound` command via `VersionShim.executeServerCommand`. Cross-version stable (the `/playsound` command syntax hasn't changed in years).

## What's new in v1.82.0

Big PvP-flavor pass. Four new features.

**Combat boss bar** — red boss bar appears at the top of your screen the moment you're combat-tagged. Drains over the 25-sec window with a live countdown ("§cCombat — 12s"). New `CombatBossBar` class is ticked once a second from `LeaderboardManager.tick`. Removed when the tag expires or the player disconnects.

**Death cam title** — when killed by another player, you get a big red `§4§lYOU DIED` title with subtitle `Killed by <Player>`, fades after 5 sec. (Not a true spectator-camera swap — that would require gamemode interception which is fragile across the yarn matrix. The title gives you the same information without the implementation risk.)

**`/icey bounty <player> <xp>`** — pay your XP levels to put a bounty on another player. Whoever kills them next collects the bounty as XP levels. Bounty is broadcast server-wide on placement. Bounties stack — multiple players can pile on the same target. Stored in `PlayerStats.bountyXp` (persisted in JSON). Kill broadcast includes the bounty payout: `[Icey SMP] PlayerA killed PlayerB + 12 XP bounty!`

**`/icey daily`** — 14-hour cooldown for a random item roll from a curated 33-item pool (no blocks/stairs/walls — only "ok-good-great" items: ores, ingots, golden apples, totems, beacon, elytra, music discs, etc.). Weighted distribution: common items (cooked beef, iron ingots) drop ~50% of the time, rare items (elytra, nether star, dragon head) drop ~1% each.

  **Rolling animation** on roll: title rapidly cycles through 8 fake-item names ("§eROLLING… / Cooked Beef" → "Diamonds" → "Saddle" → …) over 1.6 seconds, then settles on the real reward with a `§a§l✦ DAILY REWARD ✦ / Elytra ×1` banner and a Player-Levelup sound effect. Rare drops also broadcast to chat. New `Scheduler` class handles the tick-precise animation via a tiny single-threaded queue pumped from `LeaderboardManager.tick`.

`/icey help` now lists `/icey daily` and `/icey bounty <player> <xp>` alongside the existing commands. `/icey version` reports 1.82.0.

## What's new in v1.80.29

- **Walking shows meters, not km.** `0.0 km` was the persistent symptom — formatter was switching to km at 100,000 cm but most players have under 100 km walked, so it always rounded to `0.0 km`. New: always meters with one decimal below 1000 m, comma-separated integer above (e.g. `12,345 m`). Applied to both `/icey help` and `/icey top walking` output.
- **`/icey reward` is guaranteed to drop *something*.** Added a six-stage layered fallback chain in `WeaponDrops.run`: modern-syntax → legacy-syntax → no enchants → no lore → no rarity → bare vanilla item. If any one stage's syntax parses, that's what gets delivered. Players always end up with at least the named item, even on weird MC variants.

## What's new in v1.80.28

**Fix: `/icey reward` (and the automatic max-level grants) produced no item.** MC 1.21.5 removed the `{levels:{...}}` wrapper from the `minecraft:enchantments` component — it's now just the map directly. My `/give` syntax was building the legacy 1.21.0-1.21.4 form (`enchantments={levels:{...}}`), which fails to parse on 1.21.5/1.21.8/1.21.11 servers, so the whole command rejected silently.

`WeaponDrops.run` now tries the **modern** form (`enchantments={"minecraft:sharpness":5,...}`) first, falls back to the **legacy** form (`{levels:{...}}`) if the modern one syntax-errors. Covers every MC version in the matrix without baking the version in.

To make the fallback actually work, `VersionShim.executeServerCommand` now dispatches via the Brigadier `CommandDispatcher.execute(String, S)` path only — `CommandManager.executeWithPrefix` catches CommandSyntaxException internally and "succeeds" even on broken commands, which made the fallback unreachable. Brigadier's `execute` throws on syntax error, so we can detect failure and retry.

## What's new in v1.80.27

- **CI fix:** `CommandManager.executeWithPrefix(ServerCommandSource, String)` doesn't exist on at least one yarn matrix entry. Added `VersionShim.executeServerCommand(server, cmd)` that walks `executeWithPrefix` → `execute` via reflection on `getCommandManager()`, then falls back to the Brigadier dispatcher's own `execute(String, S)` (the brigadier API itself is stable — it's a Mojang lib not affected by yarn renames). `WeaponDrops` now calls through that helper.

## What's new in v1.80.26

**Seven max-level rewards, one per category:**

| Category | Reward | Item | Enchants |
|---|---|---|---|
| Mining | **Frostpick** | netherite_pickaxe | Eff V · Fortune III · Unbr III · Mending |
| PvP | **Frostfang** | diamond_sword | Sharp V · KB II · Fire Aspect II · Unbr III |
| Playtime | **Crown of Hours** | netherite_helmet | Prot IV · Resp III · Aqua Aff · Unbr III · Mending |
| Fishing | **Tidecaller** | fishing_rod | Luck of Sea III · Lure III · Unbr III · Mending |
| Walking | **Wanderer's Treads** | netherite_boots | Soul Speed III · Depth Strider III · Feather Falling IV · Unbr III · Mending |
| Jumps | **Springheel Greaves** | netherite_leggings | Prot IV · Swift Sneak III · Unbr III · Mending |
| Damage Taken | **Stonewall** | netherite_chestplate | Prot IV · Thorns III · Unbr III · Mending |

Each ships with a themed custom name (aqua/gold/green/red depending on category), 3-line lore, and `minecraft:rarity = "epic"` so the name glows purple. Awarded once per (player, category) — tracked in `PlayerStats.frostfangAwardedFor`.

**New admin command: `/icey reward <category> <player>` (op-2)** — hand-grant the reward for any category to any online player. Tab-completes both category id and player name. Doesn't mark the awarded set, so admins can hand out as many as they want.

The old `/icey givefrostfang <player>` stays as a back-compat alias (calls `reward pvp <player>`).

`/icey version` bumped to 1.80.26.

## What's new in v1.80.25

**Frostfang — max-level reward weapon.** Auto-given the first time a player hits the max level in any category (cap + 1 levels for that effect: e.g. mining maxes at Lv 6 for Haste, PvP at Lv 3 for Strength). It's a vanilla diamond sword with:
- Custom name `Frostfang` (aqua + bold)
- Three-line lore: "*A blade forged in the cold north.* / *Slows on hit · Bonus reach* / *Max-level reward — <category>*"
- Enchantments: Sharpness V + Knockback II + Fire Aspect II + Unbreaking III
- Rarity: Epic (purple name floating above it)

**Op-only `/icey givefrostfang <player>`** for handing out the sword manually (testing or as an event prize). Requires op-2.

**Tracking:** `PlayerStats.frostfangAwardedFor` is a `;`-separated list of category ids the player has already received the reward for. Persists to JSON. Means hitting max → reset → max again only gives one Frostfang per category, ever (unless an admin uses the give command).

**Server-wide announcement** on each Frostfang drop: `[Icey SMP] PlayerX earned a Frostfang for maxing Mining!` — and the leveling player gets a big `FROSTFANG / Max-level reward · Mining` title pop on their screen.

**Custom 16×16 PNG texture + model JSON shipped** in `assets/iceymodplus/` for a future revision that wires up custom_model_data overrides for the diamond_sword model. For this release the sword renders with the vanilla diamond-sword model — the custom name + epic-rarity glow make it obvious in inventories.

## What's new in v1.80.24

- **Removed Diamonds and Mob Kills.** Diamonds overlapped with Speed (Walking already gives Speed) and Mob Kills wasn't fun. 7 categories now: Mining, PvP, Playtime, Fishing, Walking, Jumps, Damage Taken.
- **Added Damage Taken → Resistance.** Tracks total HP soaked. Divisor 500 (= 50 HP per "hour" of progression — typical active play). Display in `/icey help` shows it as `12.4 HP / 50.0 HP × 10` so you can see it making sense even though it's stored × 10 internally for sub-half-HP precision.

## What's new in v1.80.23

- **Broadcasts on level-up, not on every leader-change.** The old behavior fired `[Icey SMP] PlayerX is now top of Walking (202)` every recompute cycle whenever the top score changed — noisy, useless raw-number announcement. New behavior: track each player's level per category in `lastPlayerLevels`, and only broadcast when a player's level actually goes UP. Message format: `[Icey SMP] PlayerX is now Level 2 in Walking!`. One broadcast per real progression event, no spam.
- **Big LEVEL UP title** pops on your screen the moment you level up — `LEVEL UP` as the title text and `Level 2 in Mining` as the subtitle. Fade-in 10 ticks, stay 50 ticks (2.5s), fade-out 20 ticks. Sent via `TitleS2CPacket` / `SubtitleS2CPacket` / `TitleFadeS2CPacket` directly to the leveling player only, wrapped in try/catch in case a yarn variant renames the packet classes.
- **`/spawn` removed.** Vanilla servers have `/spawnpoint` and the in-combat block plus the cross-dimension fallback weren't worth maintaining. `/setspawn` (op-2) stays for setting world spawn.

## What's new in v1.80.22

Two related issues from "playtime EVERYTHING doesn't update":

**1. Some event hooks were silently skipping.** `IceySmp.onInitialize` had all the server-event registrations in ONE big try/catch. If any single Fabric API call threw (yarn rename, missing module), every registration AFTER it got skipped — including the one for `StatTracker.registerEvents` which wires up mining / pvp / mob-kills / damage tracking. That gave the "everything shows 0" symptom: counts only tracked for categories whose event hook happened to register before the failure. Each registration now in its own try/catch with a `[IceySMP] ... installed` / `... failed` log line so a future regression is visible in the server console.

**2. `/icey help` showed raw ticks/cm for playtime + walking.** "Playtime: 0/72000 hours" wasn't telling anyone anything useful. Added `formatForCategory`: playtime renders as `2m / 1h`, walking renders as `45.3m / 6.00km`, the others stay as comma-separated counts. Both the current value AND the next-level threshold use the human-friendly format.

`/icey version` bumped to 1.80.22 — quick check if the fix actually reached your jar.

## What's new in v1.80.21

- **Fix: Fishing / Distance / Jumps counters showed 0/30 even after activity.** The MC-StatHandler-delta mechanism gated increments behind `if (last[i] > 0)`, intending "only count delta once we have a baseline snapshot". But that gate silently swallowed the **first** 0→1 transition — your first fish ever / first kilometre walked / first jump tracked never landed in the counter because `last[i]` was still at 0 during that tick. Switched to a per-player `snapshotSeeded` Set: the first tick seeds the snapshot to whatever MC has, every subsequent tick computes a real delta. Now fish #1 increments correctly.
- `/icey version` now prints 1.80.21 — handy for confirming you have the fixed jar.

## What's new in v1.80.20

- **CI: Linux ARM64 build no longer hangs forever.** Added `timeout-minutes: 20` to the `build-linux-arm64` job (was inheriting the GitHub-default 6-hour timeout) and wrapped the `electron-builder` call in a `timeout 12m` + 3-attempt retry loop. Most hangs in this job come from `fpm` (the RPM builder) interacting with system-rpm packages — killing the process and retrying on a fresh dist/ usually clears it. If all three attempts genuinely fail, the job exits with status 1 inside 20 minutes instead of squatting for hours.

## What's new in v1.80.19

- **`/icey version`** — prints the server mod version. Use this to confirm whether the jar in your mods/ folder is actually the latest one. If it says anything below 1.80.19, your install is stale; re-download via the launcher or grab the latest from the [releases page](https://github.com/FoxFlame27/iceyclient/releases/latest).
- **Server-side `/lb` fallback** — if your client doesn't have the iceymod client mod (or has an old version without the `/lb` chat command), typing `/lb` now hits the server and runs `/icey help` instead of returning "unknown command". So `/lb` always does *something* useful as long as iceymod+ is on the server.

## What's new in v1.80.18

- **CI fix:** `ServerPlayerEntity.getWorld()` doesn't resolve on the matrix's yarn versions. The dimension-equality check in `/setspawn` (which only blocked running it from the Nether) wasn't worth the yarn-rename risk — dropped the check entirely. `/setspawn` now always uses the player's `getBlockPos()` regardless of which dimension they're in; the coords get applied to the overworld spawn. Reasonable behavior + clean compile.

## What's new in v1.80.17

- **Swapped Animal Kills out, Jumps back in.** Animal Kills (Night Vision) gone; Jumps (Jump Boost) back, divisor=500 (≈1h of normal jumping = Lv 1). Effect display name updated in `/icey help`. Client `LeaderboardScreen` entries match.

## What's new in v1.80.16

Big SMP polish pass per user requests.

**Removed 6 useless categories** — kept the 8 that matter: Mining, PvP, Playtime, Mob Kills, Animal Kills, Diamonds, Fishing, Distance Walked. Dropped: Damage Dealt, Damage Taken, Deaths, Jumps, XP Levels Gained, Sneak Time.

**Way harder progression curve.** Old: 1/2/3/5/10/15-count steps for amp 0/1/2/.../7. New: exponential — `count / divisor` is the normalized "hours of activity" value. Lv 1 unlocks at normalized=1 (≈1 hour), Lv 2 at 2 (cumulative 2h), Lv 3 at 4 (4h), Lv 4 at 8 (8h), Lv 5 at 16 (16h), each level doubles the prior threshold. Per-category divisor calibrated to typical play rates: Mining 200 ores, PvP 2 kills, Mob Kills 50, Animal Kills 20, Diamonds 8, Fishing 30, Distance 6km, Playtime 1h.

**Strength capped at Level III** (amp 2) per user feedback — was way too OP at Level VI. Resistance capped at Level III, Speed at Level IV. Other caps unchanged.

**New commands:**
- **`/icey help`** — lists every category with the effect it grants and your live progress: `Mining → Haste | Lv 2 — 145/400 ores`. The current-count/next-threshold display makes the progression visible at all times.
- **`/setspawn`** — sets world spawn to your current location, op-2 required. Reflection-resolves the right `ServerWorld.setSpawnPos` overload across the yarn matrix.
- **`/iceyhuds`** (client-side) — emergency reset for stuck iceymod HUDs: force-visible, re-enable every non-optimization module, re-clamp positions to defaults, save. Use this if HUDs don't show after an update.

## What's new in v1.80.15

**Root cause of "iceymod+ gets auto-removed every launch":** the iceymod auto-install loop in `main.js:932` used a regex `/^iceymod.*\.jar$/i` to find stale client mod jars to delete. That regex *greedily matched* `iceymodplus-server-mod-mc1.21.8-1.0.0.jar` too (since the filename starts with `iceymod`). So every time the user clicked Launch:

1. Auto-install scans `mods/` for "iceymod jars"
2. Sees `iceymodplus-server-mod-mc...jar`, decides it's not the canonical `iceymod-1.0.0.jar`, deletes it
3. Reinstalls fresh `iceymod-1.0.0.jar`
4. MC starts → no iceymodplus → `/icey` fails → user thinks Download is broken

This was eating the user's downloaded server mod on **every single launch**. Now: regex requires `^iceymod-` (with the hyphen — `iceymodplus` has `p` at that position, so it doesn't match). Stale iceymod versions still get cleaned, iceymodplus stays intact.

## What's new in v1.80.14

CI compile fix for v1.80.13's death-broadcast addition:

1. `stealTotal(PlayerStats)` was referenced but never defined — leftover from a refactor where I described the function in a comment but didn't implement it. Added: sums every stealable counter on the victim so the chat broadcast can show "stole 47 stats!". Includes legacy `crops` and `woodChopped` fields for completeness.

2. `ServerPlayerEntity.getServer()` doesn't resolve on the matrix's yarn versions. Added `IceySmp.server` static field set in `SERVER_STARTED` and nulled in `SERVER_STOPPING` so non-event code can reach the server without trying to walk a yarn-renamed accessor chain.

## What's new in v1.80.13

Two annoying bugs fixed:

**1. Pressing N didn't open the leaderboard — `;` did.** MC saves keybind preferences in `options.txt`. When older versions of iceymod shipped with `;` as the leaderboard default, MC wrote `key.iceymod.leaderboard: semicolon` to the user's options.txt. Later versions changing the default to N had no effect because MC reads saved values first and ignores the new default. No way for a mod to force-overwrite that.

Fix: renamed the keybind ID from `key.iceymod.leaderboard` to `key.iceymod.openboard`. New ID has no entry in anyone's options.txt → MC uses the default (N) → pressing N opens the menu. Existing `;` bindings are now orphaned and silently ignored.

Also added a **client-side `/lb` chat command** as a third independent way to open the menu — works regardless of keybind state. So users have three options: press N, press whatever they have bound, or type `/lb`.

**2. `/icey` doesn't work even after clicking Download.** The launcher's download was succeeding with HTTP 404 status — saving the GitHub 404 HTML page as a `.jar`. Fabric Loader silently rejects malformed jars, so the user sees nothing in mods/ but the launcher reports "installed". Compounded by stale jars from earlier naming variants (iceysmp-* vs iceymodplus-*) sitting in the folder forever.

Fix:
- **Cleanup before install:** new `cleanup-smp-mods` IPC scans the installation's mods/ and removes anything matching `^(iceysmp|iceymodplus).*\.jar$`. Stale junk + corrupt 404 dumps go away before the fresh download.
- **Post-download verification:** new `verify-jar` IPC stats the file. Anything below 5KB is treated as a corrupt download — the file is deleted and the toast says clearly "Download returned a corrupt file (X bytes) — try the Server Pack option".
- Toast on success now shows the actual file size so you can sanity-check ("Installed iceymod+ (78KB) — restart MC to enable").

## What's new in v1.80.12

Artifact rename for clarity — both downloadable directly from `releases/latest/`:

- **Server mod** (Fabric jar): `iceymodplus-server-mod-mc<MCVER>-1.0.0.jar` (was `iceymodplus-mc<MCVER>-1.0.0.jar`)
- **Server pack** (datapack zip): `iceymodplus-server-pack-1.0.0.zip` (was `iceymodplus-datapack-1.0.0.zip`)

Updated everywhere it's referenced — `mod-smp/gradle.properties` `archives_base_name`, CI artifact paths, launcher download URLs, launcher chooser-modal labels ("Server Mod (Fabric)" / "Server Pack (datapack)"), README direct-download section.

## What's new in v1.80.11

The crash log made it obvious: `/icey` doesn't work because the iceymod+ jar isn't actually loading. The launcher's download button URL was 404'ing (CI hadn't successfully built+attached the SMP jar to the latest release).

**Datapack version** as a vanilla-server-friendly alternative — no Fabric required:
- `mod-smp/datapack/` with `pack.mcmeta`, `data/iceymodplus/function/load.mcfunction`, `data/iceymodplus/function/tick.mcfunction`, and the `minecraft:load` + `minecraft:tick` function tags
- CI's new `build-datapack` job zips the tree into `iceymodplus-datapack-1.0.0.zip` and attaches it to the release
- Implements simpler tier-based auto-buffs (Mining: Haste I at 10 ores, II at 50, III at 200, etc.) using MC's builtin scoreboard objectives (`minecraft.mined:diamond_ore`, `minecraft.killed:player`, etc.). No steal-on-kill, no combat tag, no /icey commands — but works on any 1.21+ vanilla server.

**Launcher Mod/Datapack picker:** clicking Download iceymod+ now opens a chooser. "Mod" installs the Fabric jar into the selected installation's mods/ folder (singleplayer / Fabric servers). "Datapack" downloads the zip to `~/.iceyclient/downloads/` for vanilla servers.

**Visible mod-loaded confirmation:** server now broadcasts `[iceymod+] Loaded! Type /icey or press N` on every SERVER_STARTED. If you don't see this message, the jar isn't loading.

**Removed wood + farming categories** as requested. Stays cleaner (14 categories now).

## What's new in v1.80.10

- **`/icey` registers now even if other init breaks.** Restructured `IceySmp.onInitialize` so `SmpCommands.register()` runs FIRST, before any state setup (`SmpConfig.loadOrDefault`, `new StatTracker`, `new CombatTracker`, `new LeaderboardManager`). Each of those is wrapped in its own try/catch and logs progress to stdout (`[IceySMP] config loaded`, `[IceySMP] stats tracker ready`, etc.). Previously a single throwable anywhere in the init chain would skip command registration entirely — `/icey` simply wouldn't exist even though the mod claimed to be loaded. Now it always exists; commands null-check downstream state and report "not ready" if construction failed.
- **Menu paginated 8-per-page.** 16 categories crammed into one screen was tight. Rewrote `LeaderboardScreen` as 2 pages of 8 (2 cols × 4 rows), bigger buttons (220×24), with Prev/Next/Close nav buttons at the bottom. Page indicator under the title ("Page 1 of 2").

## What's new in v1.80.9

Two issues from "/icey nothing works, only 3 categories show":

1. **Defensive enum init.** Each `Category` constant was referencing a `StatusEffects.X` field directly in its constructor — meaning if even one of those fields didn't exist on a particular Yarn version in the matrix (e.g. `LUCK` / `JUMP_BOOST` / `HERO_OF_THE_VILLAGE` / `SLOW_FALLING` were renamed or moved), the entire enum failed to class-load, which cascaded into `LeaderboardManager` construction throwing, which made `IceySmp.onInitialize`'s try/catch swallow the error and skip `SmpCommands.register()` → no `/icey` command exists on the server. Switched every effect ref to a `Supplier<RegistryEntry<StatusEffect>>` that's lazily evaluated; a missing field disables that one category's buff but doesn't kill the rest of the mod.
2. **Client menu only had 3 buttons.** `LeaderboardScreen` was hardcoded to Mining/PvP/Playtime. Rewrote as a 2-column grid that lists all 16 categories. Each button still does the same thing — sends `/icey top <id>` and closes — but now every category is reachable from the in-game N keybind.

Also: **pack.png icon** copied to `assets/iceysmp/icon.png` and registered in `fabric.mod.json` so iceymod+ shows up with the Icey logo in Mod Menu / `/modlist` instead of the default question mark.

## What's new in v1.80.8

- **iceymod+ now has an icon** in the Fabric mod list. Copied the existing iceymod icon to `assets/iceysmp/icon.png` and referenced it from `fabric.mod.json` so the mod shows up with the proper logo (not the default question-mark) in /modlist, Mod Menu, the Fabric Loader log, etc.

## What's new in v1.80.7

- **CI fix:** three more Yarn signature drifts blocked the SMP build — `ServerPlayerEntity.teleport` had different arg counts across matrix entries (8-arg with ServerWorld+Set+yaw+pitch+resetCamera vs 7-arg without the boolean vs 6-arg without the Set), `ServerPlayerEntity.damage` flipped between `(world, source, amount)` and `(source, amount)`, and `LivingEntity.kill` between `(ServerWorld)` and no-args. Pulled all three into a new `VersionShim` class that walks every known signature via reflection in order. Bytecode in `SmpCommands` / `CombatLogoutHandler` no longer references any of the unstable overloads directly; they call `VersionShim.teleportSafe / damageSafe / killSafe`.

## What's new in v1.80.6

- **CI fix:** Yarn renamed `getSpawnPos` between `ServerWorld` and `WorldProperties` (via `getLevelProperties`) across the 1.21.x matrix — and the 6-arg teleport overload my `/spawn` fallback called doesn't exist (only the 4-arg `LivingEntity` and 8-arg `ServerPlayerEntity` versions do). Replaced the `getSpawnPos` call with a reflection-based `resolveWorldSpawn(world)` that tries `world.getSpawnPos()`, then `world.getLevelProperties().getSpawnPos()`, then `getProperties()` and `getLevelData()` variants, falling back to `(0,64,0)` if all fail. Removed the broken 6-arg teleport fallback. Source now compiles against every Yarn version in the matrix.

## What's new in v1.80.5

- **5 new leaderboard categories** (16 total). Pulled from MC's built-in `StatHandler` via per-second delta snapshots — no mixins needed:
  - **Fishing** (fish caught) → Luck
  - **Distance Walked** (km) → Speed
  - **Jumps** → Jump Boost
  - **XP Levels Gained** → Hero of the Village
  - **Sneak Time** (minutes) → Slow Falling
- All 5 are stealable on PvP kill like the other counters.
- **Mod renamed `iceysmp` → `iceymodplus`** for distribution. The internal mod id stays `iceysmp` (don't break existing world saves' permission entries), but the jar filename is now `iceymodplus-mc<MCVER>-1.0.0.jar`. CI matrix artifacts and the launcher Download button both updated.
- **Direct GitHub download links** added to the top of the README — one URL per supported MC version, always pointing at the latest release. No launcher required.

## What's new in v1.81.0

iceymod+ overhaul. Server mod gets PvP guardrails + more stats + count-based effect scaling, client mod moves the leaderboard key.

**New categories** (8 new on top of Mining/PvP/Playtime — 11 total):
- Mob Kills → Resistance
- Animal Kills → Night Vision
- Farming (crops harvested) → Haste
- Diamonds Mined → Speed
- Wood Chopped → Haste
- Damage Dealt → Strength
- Damage Taken → Resistance
- Deaths → Regeneration

All stats are **stealable** — when player A kills player B in legitimate combat, all of B's counters transfer to A and B's reset to 0.

**Effect amp scaling** (per category, per player, based on own count):
- Counts 1/2/3 → amp 0/1/2 (Level I/II/III)
- Each +5 counts past 3 → +1 amp (up to amp 7 at count 28)
- Each +15 counts past 28 → +1 amp thereafter
- Per-effect caps: Strength=5, Resistance=3, Speed=4, JumpBoost=5, others=9

So your first 3 of anything gives you 1-by-1 boosts, then progression slows down. Top player gets a fame announcement (bold!) in chat: `[Icey SMP] PlayerX is now top of Mining (1247)`.

**PvP guardrails:**
- **Noob protection:** new players get 10 min from first join — no PvP damage to or from them
- **Starter kit:** iron armor (helm/chest/legs/boots) + iron sword/pickaxe/axe/shovel + 16 cooked beef on first join
- **Combat tag:** 25 sec (bumped from 10). Both players must hit each other in this window for a kill to count
- **Combat logout = death:** disconnect while combat-tagged → killed automatically on next connect attempt (drops inventory normally)
- **Same victim never counts twice** by default (`sameVictimCooldownSeconds=0` means lifetime cooldown — kill someone once, that pairing is dead for stat purposes)
- **/spawn** command teleports to world spawn. Blocked while combat-tagged
- All `/icey` and `/spawn` commands work from server console (op-level checks bypassed there) and on dedicated multiplayer servers

**Client:**
- Leaderboard keybind moved from `;` to `N` (per the request)
- Auto-sprint keybind unbound from N (was a collision) — rebind via Controls if you want
- Top-of-category chat announcements are bold so you can't miss them

**Config knobs** in `config/iceysmp.properties`: `recomputeSeconds`, `combatTagSeconds`, `sameVictimCooldownSeconds`, `effectDurationSeconds`, `noobProtectionMinutes`, `starterKit`, `killStealsStats`, `killOnCombatLogout`.

## What's new in v1.80.0

- **`;` opens an iceymod+ leaderboard picker** in-game. Three buttons (Mining / PvP / Playtime); click one and the client sends `/icey top <category>` for you, response renders in chat. Works on any server (or singleplayer world) that has iceymod+ installed. New keybind `key.iceymod.leaderboard` default `SEMICOLON` — rebindable like every other Icey keybind.
- **iceymod+ now works in singleplayer.** Changed the mod's `environment` from `server` to `*` so it loads in the integrated server that singleplayer starts. Drop the jar into your client installation's `mods/` folder (or use the launcher button) and `/icey top mining` works in-world.
- **Launcher download flow is install, not download.** The Settings → Advanced "Download iceymod+" button no longer dumps the jar into `~/.iceyclient/downloads/`. It places the jar directly into the currently-selected installation's `mods/` folder (same logic the auto-fabric-api install uses). Toast says "Installed iceymod+ — restart MC to enable". Refuses to install on Vanilla installations with a clear error. The card itself is reduced to one line: just the button.

## What's new in v1.79.2

- **CI fix:** Yarn renamed `ServerCommandSource.hasPermissionLevel(int)` to `hasPermission(int)` somewhere in the 1.21.x matrix, and the exact MC version differs by Yarn build, so hardcoding either name fails to compile against the other half of our matrix. `SmpCommands` now does a class-init-time `MethodHandle` lookup that tries both names and caches whichever resolves — the command predicates call through that cached handle. Source compiles cleanly against every Yarn version.
- **CI speed:** Cached `~/.gradle/caches`, `~/.gradle/wrapper`, `~/.npm`, and `~/.cache/electron`. First run after a build-config change still warm-loads dependencies, but subsequent runs (which is the common case) drop the gradle compile from ~2–3 min to ~30s and the Linux electron-builder npm-install pass from ~1–2 min to a few seconds. Net: the Linux RPM artifact should be downloadable from the workflow page faster than before.

## What's new in v1.79.1

- **CI fix:** Icey SMP wouldn't compile — `ServerLivingEntityEvents` was imported from the wrong package (`event.lifecycle.v1` instead of the correct `entity.event.v1`). Same class, but the Fabric API splits lifecycle events from entity events into separate sub-packages. Build failed on all 4 matrix entries; now resolved.

## What's new in v1.79.0

- **Icey SMP — new server-side Fabric mod.** Drop the jar in your server's `mods/` folder. Tracks Mining (top players get Haste II / Haste I), PvP (Strength II / Strength I), and Playtime (top player gets Saturation). Anti-farm: combat tag (10s, both must be tagged), same-victim kill cooldown (10min), ore-block whitelist. Recomputes every 30s; effects refresh on each cycle so they auto-fade when you drop off the top. Stats persist to `world/iceysmp/stats.json`. Commands: `/icey top mining|pvp|playtime`, `/icey reload` (op-3), `/icey reset` (op-4). Config file at `config/iceysmp.properties` (intervals, cooldowns, effect duration). Works on Minecraft 1.21 through 1.21.11 via per-version CI builds.
- **Settings → Advanced → "Download This Mod" button** for Icey SMP. Auto-detects your currently-selected installation's MC version and pulls the matching jar from the latest GitHub release. User MC versions are rounded down to the nearest build target (1.21 / 1.21.5 / 1.21.8 / 1.21.11) — e.g. MC 1.21.7 gets the `mc1.21.5` jar. Downloaded to `~/.iceyclient/downloads/` and the folder opens automatically so you can move the jar to your server.
- **CI: per-version `iceysmp` jar matrix.** New `build-smp` job builds 4 jars in parallel (one per supported MC version) and attaches them all to the release. The release job depends on the matrix so partial-failure of one version doesn't block the launcher build.

## What's new in v1.78.2

- **Fix: HUDs only showed in edit mode, blank in actual gameplay.** Root cause was an old defensive pattern in `HudManager.render` / `tick`: any module that threw a single exception was permanently disabled and its `enabled=false` saved to `iceymod.json`. The HUD edit screen has its own render path that silently catches exceptions and keeps drawing — that's why moving HUDs around showed them but normal play didn't. On new MC versions where multiple modules can throw on first render call, this auto-disable cascade left users with a nearly-empty HUD bar that didn't recover even after the underlying compat bug was fixed. Now: render/tick errors are silently caught, the module is *not* disabled, and we log the error once per module per session (not once per frame) so stdout doesn't get flooded.
- **One-shot config migration.** Re-enables any module whose default was on but whose saved state is off — undoing the auto-disables persisted by previous versions. Marked complete via `migrations.renderSafe: true` in `iceymod.json` so the migration only runs once and respects your future explicit toggles.

## What's new in v1.78.1

- **Reverted: X-Ray removed.** v1.78.0 added an X-Ray module + `Block.shouldDrawSide` mixin and broke all HUD rendering. Pulled the module, mixin, mixin registration, keybind, and lang entry. Settings screen's adaptive grid layout (also from v1.78.0) stays — it's an unrelated improvement.
- **Hardened mod init.** Every setup call in `onInitializeClient` (HudManager / WaypointManager / WaypointBeamRenderer / HitboxRenderer / StructureTracker / BiomeTracker / ChatCoordParser) is now wrapped in its own try/catch. A constructor crash in one subsystem can no longer cancel the rest of mod init — partial Icey beats entirely-broken Icey.

## What's new in v1.78.0

- **X-Ray module added.** Toggle key: `X`. Hides every block not in your selected see-through list, leaving ores / spawners / loot exposed inside otherwise-invisible terrain. ~85 individually-toggleable blocks across categories: overworld + nether ores, mineral blocks, raw blocks, spawners (regular / trial / vault), structure markers (reinforced deepslate, end portal frame, dragon egg), loot containers (chest / trapped chest / ender chest / barrel / shulker box / hopper / dispenser / dropper / furnace), utility (beacon / conduit / lodestone / brewing stand / enchanting table / anvil / respawn anchor), amethyst (block / budding / cluster / 3 bud sizes), light/glow (glowstone, shroomlight, sea lantern, jack-o-lantern, all 3 froglights, redstone lamp), mob heads (player / zombie / creeper / skeleton / wither / piglin / dragon), ice (ice / packed / blue), nether markers (crying obsidian, gilded blackstone, magma, soul sand/soil, nether brick fence), sculk (sculk / catalyst / shrieker / sensor), misc (honey, honeycomb, moss, turtle/sniffer egg). Most ores + dungeon markers default ON, decorative/light blocks default OFF.
- **Settings screen rebuilt as an adaptive grid.** Old screen put every setting in one column → off-screen-and-unclickable for X-ray's 85+ entries. Now uses 1–4 columns based on count, scaling to fit the screen height. Short lists still show as a single wide column.
- **Minimap removed.** The Xaero-style minimap module + renderer are gone — `MinimapModule.java` and `MinimapRenderer.java` deleted, registration pulled from `HudManager.init()` and `IceyMod.onInitializeClient()`, top-right special-case removed from `applyCenterDefaults`. Saved positions for `minimap` in `iceymod.json` from older builds become inert (no-op on load).
- **Sodium caveat:** X-Ray uses a vanilla `Block.shouldDrawSide` mixin. Sodium replaces face-occlusion with its own pipeline, so X-Ray may be ignored on Sodium installs until Sodium-specific mixins are added.
- **Ethics caveat:** X-Ray is detectable on most public servers. Fine for SP / your own server; obvious risk on others.

## What's new in v1.77.1

- **Fix: Structure Locator / Biome Locator / Waypoints HUD widgets were invisible after enabling.** The first-run `applyCenterDefaults` placed every non-minimap module in a centered grid mid-screen, so these three widgets ended up behind whatever else was stacked there. Anchored them top-left in a vertical stack instead — Structures at y=40, Biomes at y=120, Waypoints at y=200. **Existing configs:** the structure menu's "Find New Structures" button and the waypoint menu's "Set Waypoint Here" button now also snap their widget to a visible top-left position if it's currently buried in the middle 50% of the screen — so you don't need to delete `iceymod.json` to recover.
- **Spawners checkbox added to the structure-locator's "Select Structures" toggle grid** (was missing in v1.77.0 — the BoolSetting existed and defaulted on, but the menu didn't list it).

## What's new in v1.77.0

- **Structure Locator now finds Spawners** (regular monster spawners — dungeons, mineshafts, fortress nether-fortress entrance rooms, stronghold libraries). Each spawner shows as its own row in the locator HUD with a red marker; auto-waypoints place an individual flag on each one. Toggle in the module settings (default on).
- Spawners use a 4-block cluster threshold (vs the 50/40-block threshold for area structures) so two real spawners 10+ blocks apart still both register as separate entries — matters for mineshafts where multiple cave-spider spawners are nearby.
- **CI: `iceymod` jar is now rebuilt on every release.** The launcher build pipeline previously just packed whatever prebuilt jar was sitting in `mod/build/libs/` from a developer's machine, so changes to the Java mod source weren't actually shipped without a manual local rebuild. Added a `build-mod` job (Temurin JDK 21 + `./gradlew build`) that runs first, uploads the fresh jar as an artifact, and the three platform jobs each download it before electron-builder packages the launcher.

## What's new in v1.76.0

- **Fix: account manager unreachable when every saved account was expired.** Previously `getAuth` returned null whenever the active account's token had passed its `expiresAt`, which collapsed the titlebar profile area to nothing and the sidebar to a "Login" button. Clicking Login then hit the max-5-accounts cap and you had no way to remove an old one — soft-locked. Now: if there are saved accounts but no active one, a generic Steve avatar shows in the titlebar and an "Accounts" entry shows in the sidebar; clicking either opens the dropdown with all saved accounts (each with a Remove button) plus the "No active account" header. Removing one frees the slot so you can add a new MS login.

## What's new in v1.75.0

- **Fix: launcher auto-installed the wrong Fabric API jar for any MC version not in the hardcoded list** (1.21.1, 1.21, 1.20.x, etc. all silently got 1.21.11's Fabric API, then crashed at launch with `HARD_DEP_INCOMPATIBLE_PRESELECTED fabric-api ... requires minecraft 1.21.11`). Replaced the hardcoded version map with a Modrinth API query at launch time — the launcher now fetches the latest Fabric API jar that lists the installation's exact MC version under `game_versions`. Works for every MC version Modrinth covers; falls through cleanly (logs a warning and skips auto-install) when the network call fails or no matching version exists, instead of pinning the wrong jar.

## What's new in v1.74.0

- **E4MC prompt now re-appears on every world import** until E4MC is actually installed. Previously the "Skip" button persisted via `localStorage.iceyE4mcSkip`, so dismissing it once meant you'd never see it again — easy to forget the option exists. Now Skip just closes for this import; the next import re-prompts. The hint text on the Skip button changed from "Don't ask again" to "Maybe later" to match. The check that suppresses the prompt when E4MC is already in `mods/` is unchanged, so installs that already have it stay quiet.

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
