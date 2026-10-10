# RVNKCore NPC Harness and Region Tool

**Since**: 1.5.100-alpha (#2248, epic #2239). Builds on the NPC bridge (#2213, 1.5.99-alpha).
**1.5.101-alpha**: standable-Y snap and settle tolerance, settle-tolerant zone export, no false
skin FAILED (Dev QA of #2248).

The harness places, edits and checks Citizens NPCs **from the console**, keyed by RVNK key. NPCs can
be declared in a YAML spec file and applied idempotently, and the spec doubles as the QA check
(`verify`). The region tool defines WorldGuard regions from explicit corners, so the console can
protect an area without a WorldEdit selection.

It removes the four console blockers from the TFAH build on Event (#2243):

| Blocker in #2243 | Harness answer |
|------------------|----------------|
| Console `/npc create` throws an NPE in `Location.getChunk()` without `--at` | `create` uses `CitizensAPI.getNPCRegistry().createNPC(PLAYER, name)` and `spawn(location)` with an explicit location |
| Console `/rg define` needs a WorldEdit selection | `/rvnk region define <name> <world> x1 y1 z1 x2 y2 z2` |
| NPC skins were never set | `skin <key> <player\|url>`, and `skin:` in the spec |
| Writing `regions.yml` directly was refused | Regions go through the WorldGuard API and `RegionManager.saveChanges()` |

Command reference and permissions: [commands.md](commands.md#rvnk-npc) and
[commands.md](commands.md#rvnk-region).

---

## Commands

All verbs run from the console. Quote a value that has spaces: `"Warden Tolla"`.

```
/rvnk npc create <key> <name> <world> <x> <y> <z> [yaw] [pitch]
/rvnk npc move <key> <world> <x> <y> <z> [yaw] [pitch]
/rvnk npc rename <key> <name>
/rvnk npc remove <key>
/rvnk npc skin <key> <playerName|url>
/rvnk npc lookclose <key> on|off
/rvnk npc pose <key> stand|sit|sneak
/rvnk npc hold <key> <material|none>
/rvnk npc protected <key> true|false
/rvnk npc nameplate <key> on|off|hover
/rvnk npc protect <key> [radius=2] [height=3]
/rvnk npc apply <spec> [--dry-run]
/rvnk npc verify [spec]
/rvnk npc export <spec> [--force]
/rvnk npc click <key> <player> [right|left]      (QA, #2255; see "Click simulator" below)

/rvnk region define <name> <world> <x1> <y1> <z1> <x2> <y2> <z2> [flag=value ...] [priority=N]
/rvnk region flag <name> <world> <flag> <value|clear>
/rvnk region remove <name> <world>
/rvnk region info <name> <world>
```

| Permission | Covers | Default |
|------------|--------|---------|
| `rvnkcore.npc.admin` | every `/rvnk npc` verb above (also a child of `rvnkcore.npc.*`) | op |
| `rvnkcore.npc.tag` / `untag` / `list` / `info` | the #2213 bridge verbs | op |
| `rvnkcore.qa.subject` | held by the TARGET player of `click`; needed on every non-Dev tier | **false** |
| `rvnkcore.region.admin` | every `/rvnk region` verb (child of `rvnkcore.region.*`) | op |

Tab completion: verbs, keys, spec names, worlds, the player's own coordinates, `on`/`off`,
poses, materials, online player names (skins and `click`), `right`/`left`, region ids, flag names and `allow`/`deny`/`clear`.

**Without Citizens** every NPC verb says the harness is unavailable. **Without WorldGuard**,
`protect` and every region verb print `WorldGuard not installed`; `apply` and `verify` still run and
note "zone skipped" / ZONE_UNCHECKED for zones.

---

## Spec files

Location: `plugins/RVNKCore/npc/<spec>.yml`. A spec is named without the path and without `.yml`
(`A-Z a-z 0-9 _ -`, 1-64 characters); no command can read or write outside that folder.

### Schema

```yaml
description: optional free text          # optional
version: 1                               # optional, ignored
npcs:
  <key>:                                 # RVNK key, lower-case a-z 0-9 _ -, 1-48 characters
    name: "Warden Tolla"                 # required; plain text recommended (no colour codes)
    world: sotw_city                     # required; must be loaded or have a world folder
    pos: [-92.5, 67, 2.5]                # required; [x, y, z], y = feet
    yaw: 180                             # optional; -360..360, stored as (-180, 180]
    pitch: 0                             # optional; -90..90
    skin: Notch                          # optional; player name (1-16 of A-Z a-z 0-9 _) or http(s) URL
    lookclose: true                      # optional; true/false or on/off
    protected: true                      # optional; Citizens "protected" (invulnerable)
    pose: stand                          # optional; stand | sit | sneak
    hold: lantern                        # optional; item material key, or none
    nameplate: on                        # optional; on | off | hover
    zone: { radius: 2, height: 3 }       # optional; or `zone: true` for the defaults
```

| Field | Required | Values | Citizens side |
|-------|----------|--------|---------------|
| `name` | yes | text, 1-64 characters | `NPC.setName` |
| `world` | yes | world name | spawn location |
| `pos` | yes | `[x, y, z]`, \|x\|,\|z\| <= 30,000,000, \|y\| <= 2048 | `spawn` / `teleport` |
| `yaw`, `pitch` | no | -360..360, -90..90 | location rotation |
| `skin` | no | player name or URL | `SkinTrait` |
| `lookclose` | no | bool | `LookClose` trait |
| `protected` | no | bool | `NPC.setProtected` |
| `pose` | no | `stand`, `sit`, `sneak` | `SitTrait`, `SneakTrait` |
| `hold` | no | material key or `none` | `Equipment` HAND |
| `nameplate` | no | `on`, `off`, `hover` | `nameplate-visible` metadata |
| `zone` | no | `true`, `false`, `{ radius: 0-16, height: 1-32 }` | WorldGuard region `npc_<key>` |

### Rules

- **A field left out is unmanaged.** `apply` does not touch it and `verify` does not compare it.
- **Strict validation.** An unknown field (for example `lookClose:`), a bad value, an unknown world
  or a bad material is an error, and a spec with any error is not applied at all. Every error is
  listed with its YAML path, such as `npcs.guide_ruins.hold: unknown or non-item material 'lantren'`.
- **YAML on/off.** YAML 1.1 reads a bare `on`/`off`/`yes`/`no` as a boolean. Every on/off field,
  and `nameplate`, accepts both forms, so `nameplate: on` works.
- **World check.** Parse time: the world must be loaded or have a folder (`<container>/<name>`, or
  the Paper 26 layout `<container>/<primary>/dimensions/<ns>/<name>`). Apply time: a key whose world
  is not loaded is BLOCKED; the other keys still apply. Load it with `/world load <name>` first.

---

## apply

`/rvnk npc apply <spec> [--dry-run]` plans every key, prints the plan, and (without `--dry-run`)
carries it out. Output per key:

| Action | Meaning |
|--------|---------|
| `CREATE` | No NPC carries the key: create it at `pos`, then write every managed field and the zone |
| `UPDATE` | The NPC exists; only the differing fields are written, each listed as `field: live -> spec` |
| `NOOP` | The NPC matches the spec |
| `BLOCKED` | World not loaded, or the key is on two NPCs (remove the extra by hand) |

- **Idempotent.** A second apply of the same spec is all NOOP and writes nothing.
- **Adoption.** A key tagged by hand (`/rvnk npc tag <key> <npcId>`) is updated in place, never
  created again. This is how Event's existing 6 NPCs come under the spec.
- **Write order** per key: create or rename, one move (world, position, rotation), skin, lookclose,
  protected, pose, hold, nameplate, zone.
- **Standable Y.** Create and move snap the target Y to the ground (see [Standable Y](#standable-y)).
  The step result says so: `position: snapped 68 -> 67`.
- **Skins finish later.** See [Skins](#skins) for the result lines.

## verify

`/rvnk npc verify <spec>` compares the spec with the server. `/rvnk npc verify` with no spec checks
that every tagged key resolves to exactly one NPC with a location in a loaded world.

| Kind | Problem? | Meaning |
|------|----------|---------|
| `MISSING` | yes | Spec key with no NPC |
| `FIELD` | yes | A field differs: `position`, `world`, `name`, `skin`, `lookclose`, `protected`, `pose`, `hold`, `nameplate`, `zone` |
| `DUPLICATE` | yes | Key on more than one NPC |
| `NO_LOCATION` | yes | NPC has no stored location (no-spec mode) |
| `WORLD_UNLOADED` | yes | World not loaded; nothing else checked for that key |
| `ORPHAN` | yes | Tagged key not declared in the spec |
| `DESPAWNED` | info | Not spawned; normally just an unloaded chunk |
| `ZONE_UNCHECKED` | info | Spec has a zone but WorldGuard is missing |

The summary line is `clean` when there are no problem kinds, else `DRIFT - N problem(s)`.

Comparison rules, shared with `apply` so they cannot disagree:

- **Position**: drift when the X/Z distance is over **0.5** blocks, or the live Y is none of: within
  0.5 of the spec Y, within 0.5 of the standable Y, or up to **1.5** below the spec Y (settled).
  See [Standable Y](#standable-y).
- **Rotation**: yaw/pitch are compared (1 degree tolerance) only when the spec sets them **and**
  LookClose is off. With LookClose on, Citizens turns the NPC toward players all the time.
- **Name**: compared without colour codes.
- **Skin**: player names ignore case; URLs must match exactly. The harness stores the source it set
  as NPC metadata `rvnk-skin`; a skin set by hand with `/npc skin` is read from `SkinTrait`.
- **Zone**: region `npc_<key>` must exist and carry `interact=allow`, `use=allow`,
  `mob-spawning=deny`. Its bounds must be the zone shape around the spec position, around the
  standable position, or around the live NPC (when the live position is in sync) with the zone's
  feet up to 1.5 blocks above the NPC's feet. A missing or wrong zone is rebuilt around the
  standable position.

## export

`/rvnk npc export <spec> [--force]` writes every tagged NPC to a spec. Positions are the live
(standing) positions, rounded to 2 decimals, and angles are rounded to 1; this is inside verify's
tolerance, so applying an export at once is all NOOP. A zone is written when `npc_<key>` has the
zone shape around the NPC: radius and height come from the region bounds, the box is centred on
the NPC's block, and the zone's feet may sit up to 1.5 blocks above the NPC's feet (a zone built
at the spec Y before the NPC settled). Otherwise the note `zone left out` is printed. NPCs without
a location are skipped with a note. It refuses to overwrite an existing file without `--force`.

---

## Standable Y

**Since 1.5.101.** A Citizens player NPC falls under gravity. In 1.5.100 a spec Y one block above
the ground (`pos: [-95.5, 68, 2.5]` over ground whose top block is 66) made the NPC settle at 67;
every `apply` then moved it back to 68 and `verify` reported `(1.00 off)` forever.

**Snap rule** (`NpcGround`, on `create`, `move` and `apply`):

1. A Y is **standable** at a feet block when the block below is solid (it has collision) and the
   feet and head blocks are passable (not lava).
2. Search at the requested X/Z: the requested feet block first, then **4 blocks down**, then
   **2 blocks up**. The first standable feet block wins.
3. If the requested feet block is standable, the requested Y is kept as it is. Otherwise the Y
   becomes the found feet block, and the result says `snapped 68 -> 67`.
4. If nothing in the window is standable, the requested Y is kept and the result says
   `WARNING no standable ground within 4 below / 2 above y 68; kept the requested y`. The step
   does not fail.
5. The zone is built around the snapped position.

A `--dry-run` shows the snap ahead: `position: missing -> journey -95.5,68,2.5 (stands at y 67)`.

**Tolerance** (apply, verify and export share it): X/Z within **0.5**; Y within 0.5 of the spec
Y, or within 0.5 of the standable Y, or up to **1.5 below** the spec Y. A live Y above the spec Y
(beyond 0.5) is always drift.

The spec file is not rewritten: it can keep `68`. To make it exact, run `export` and use the
exported Y.

> An NPC meant to float (Citizens gravity off) more than 4 blocks above the ground is outside the
> search window and keeps its Y. One within 4 blocks of the ground is snapped down to it.

---

## Skins

`skin <key> <player>` and `skin:` in a spec set Citizens' `SkinTrait`. Citizens fetches a skin only
for a **spawned** NPC.

| Situation | Result line |
|-----------|-------------|
| NPC not spawned (no player near, chunk unloaded) | `skin set to player 'Notch'; Citizens fetches it when the NPC spawns`. No 5 s check runs. |
| Spawned, texture arrived in 5 s | `skin <key>: texture for 'Notch' loaded` |
| Spawned, no texture after 5 s | `skin <key>: texture for 'Notch' not yet loaded after 5 s (Citizens retries) ...` (warning, not a failure) |
| The check itself failed | `skin <key>: FAILED - ...` |
| URL skin | `skin <key>: URL skin applied`, or `FAILED to generate a skin from the URL: ...` |

---

## Protect zone

`/rvnk npc protect <key> [radius] [height]` and `zone:` in a spec both create or update the
WorldGuard region **`npc_<key>`** in the NPC's world:

- **Shape**: x and z from `block - radius` to `block + radius`; y from the feet block up `height`
  blocks. Radius 2, height 3 is 5 x 3 x 5. For Tolla at -92,67,2: `-94,67,0 -> -90,69,4`.
- **Flags**: `interact=allow`, `use=allow`, `mob-spawning=deny`.
- **Priority**: a new zone gets priority **10**, so it wins over a priority-0 region around it
  (at equal priority WorldGuard lets `deny` win). An existing zone keeps its priority, flags,
  owners and members; only the bounds and the three flags are rewritten.
- `protect` uses the NPC's **live** position; a spec zone uses the **standable** position (the
  spec position snapped to the ground, see [Standable Y](#standable-y)).
- `remove <key>` leaves the zone in place and prints the `/rvnk region remove` line.

---

## Sample spec: TFAH guides (6 NPCs)

`plugins/RVNKCore/npc/tfah.yml`. Positions are the operator's coordinates (#2243, #2248).

> Before the first apply on Event, run `/rvnk npc export tfah-live` and compare it with this file.
> The sotw positions here are whole numbers; if Citizens placed an NPC at a block centre, the first
> apply moves it by about 0.7 blocks. Copy the live values into this file if they are the ones you
> want. Skins are left out (no skins chosen yet): add `skin:` lines when they are.

```yaml
description: TFAH Secrets of the Worlds guides (#2243). Zones only on the sotw guides.
npcs:
  wayfarer_greeter:
    name: "Wayfarer Ines"
    world: skyblock
    pos: [10.9, 65, 31]
    protected: true

  guide_koz:
    name: "Warden Halvard"
    world: world
    pos: [-133.4, 78, -4.1]
    protected: true

  guide_sol:
    name: "Keeper Soli"
    world: alphac
    pos: [-328.3, 144, 453.4]
    protected: true

  guide_ruins:
    name: "Warden Tolla"
    world: sotw_city
    pos: [-92, 67, 2]
    lookclose: true
    protected: true
    zone: { radius: 2, height: 3 }

  guide_aether:
    name: "Skywarden Rell"
    world: sotw_sky_0
    pos: [-15, 86, -1]
    lookclose: true
    protected: true
    zone: { radius: 2, height: 3 }

  guide_cavern:
    name: "Hollowkeeper Vance"
    world: sotw_deep_0
    pos: [6, 243, 8]
    lookclose: true
    protected: true
    zone: { radius: 2, height: 3 }
```

### The five sotw site regions (#2243 section 9)

These protect the sites, not one NPC, so they are plain regions. Corners and flags are from the
#2243 build log. `sotw_spire_top` has **no** `build` flag, so the ascend trigger block can still be
placed.

```
rvnk region define sotw_tolla sotw_city -94 65 0 -90 69 4 interact=allow use=allow mob-spawning=deny
rvnk region define sotw_spire_top sotw_city -19 316 -17 -13 319 -11 interact=allow use=allow mob-spawning=deny
rvnk region define sotw_well sotw_city 6 62 6 11 65 10 interact=allow use=allow mob-spawning=deny
rvnk region define sotw_rell sotw_sky_0 -17 83 -3 -13 88 1 interact=allow use=allow mob-spawning=deny
rvnk region define sotw_vance sotw_deep_0 4 241 6 10 246 12 interact=allow use=allow mob-spawning=deny
```

---

## Click simulator (QA, #2255, since 1.5.102-alpha)

```
/rvnk npc click <key> <player> [right|left]      # default right
```

Fires `RvnkNpcInteractEvent` for an **online** player as if that player had clicked the NPC. It
lets an operator walk NPC quest beats and dialogue from the console.

**Same path as a real click.** The Citizens listener and `click` both fire the event through
`NpcClickDispatcher`. The event has the same fields: player, key, the NPC's name, the click type,
and the NPC's live location (its stored location when despawned). It fires on the main thread.
When no listener cancels it, the click is recorded as the player's last interaction
(`%rvnknpc_last_key%`). So RVNKQuests' `NpcInteractionCoordinator`, `NPC_INTERACT` triggers and
`TALK_TO` objectives cannot tell a simulated click from a real one. `SimulatedClickParityTest`
proves the two events and the two records are equal.

**What does not run.** Citizens' own `NPCRightClickEvent` / `NPCLeftClickEvent` and the NPC's
`/npc command` actions do not fire, because no entity was clicked. The player's distance to the
NPC is not checked. No RVNK consumer checks it for a real click either.

**Output.** The sender gets one line:

```
[guide_cavern] Simulated RIGHT click by Shadowmelt on 'Warden Tolla' (#12): fired, not cancelled.
  Gate: tier 'dev' is Dev. Any dialogue goes to Shadowmelt, not to you.
```

A listener that cancels the event gives `fired, CANCELLED by a listener. Not recorded as the last
interaction.` Any NPC dialogue goes to the **target player's** chat, not to the console. RVNKQuests
reads the line from RVNKLore asynchronously, so it arrives a moment after the command.

### Gate

The gate is a pure function, `NpcClickGate.evaluate(tier, senderIsAdmin, targetIsQaSubject)`.
`NpcClickGateTest` covers the matrix.

| Tier (`server-id`) | Target has `rvnkcore.qa.subject` | Result |
|--------------------|----------------------------------|--------|
| `dev` or `test` | either | allowed |
| `event`, `nations`, any other set id | yes | allowed |
| `event`, `nations`, any other set id | no | refused: names the tier and the permission |
| unset (`local`), blank, or unreadable | either | refused: the tier is unknown |
| any | either, but the sender lacks `rvnkcore.npc.admin` | refused |

- **Tier source.** RVNKCore's `ConfigLoader.getServerId()`: `chat-relay.server-id`, then
  `webhook.server-id`, else `local`. RVNKQuests' `ServerTier` reads the same value for its
  `quest debug` gates. RVNK Dev's id is `dev`. Its chat room is `test`, but that is a different
  setting.
- **The target's permission counts, not the sender's.** The console has every permission, so a
  sender check alone would let any console push quest state on Event.
- `rvnkcore.qa.subject` has `default: false`, so an op is not a QA subject by accident. It is not a
  child of `rvnkcore.npc.*`. A LuckPerms `*` grant still matches it, so do not give `*` to a
  player group.

LuckPerms QA group (Event):

```
lp creategroup qa
lp group qa permission set rvnkcore.qa.subject true
lp user <player> parent add qa
lp user <player> parent remove qa        # when the QA walk ends
```

### Audit

Every attempt past argument parsing writes one INFO line to the server log:

```
[RVNKCore] [npc click] sender=CONSOLE target=Shadowmelt key=guide_cavern click=RIGHT tier=dev npc=#12 gate="tier 'dev' is Dev" result=fired
[RVNKCore] [npc click] sender=CONSOLE target=Bob key=guide_cavern click=RIGHT tier=event REFUSED: tier 'event' is not Dev and the target player lacks rvnkcore.qa.subject (...)
```

Refusals, an unknown key (`NOT RUN: no NPC carries the key`) and an unavailable bridge are logged
too.

---

## Architecture

| Package | Holds | Loads without |
|---------|-------|---------------|
| `service.npc.harness` | `NpcSpec`, `NpcSpecParser`, `NpcDiff`, `NpcGround` (snap; `Terrain` interface), `NpcApplyPlanner`, `NpcVerifier`, `NpcSpecExecutor`, `NpcSpecExporter`, `NpcSpecStore`, `NpcSkins`, `NpcHarness` (interface) | Citizens, WorldGuard |
| `service.npc.citizens` | `CitizensNpcHarness` (and the #2213 adapter); it also supplies the Bukkit block reads for `NpcGround.Terrain` | - (Citizens only) |
| `service.region` | `IRegionService`, `UnavailableRegionService`, `RegionBridge`, `RegionArgs`, `Cuboid` | WorldGuard |
| `service.region.worldguard` | `WorldGuardRegionService`, `WorldGuardRegionAdapter` | - (WorldGuard only) |
| `service.npc` | `NpcClickDispatcher` (the one event path, shared by the Citizens listener and `click`), `NpcClickSimulator` (#2255) | Citizens |
| `command` | `NpcSubCommand`, `NpcAdminVerbs`, `RegionSubCommand`, `NpcArgs`, `NpcClickGate`, `QuotedArgs` | Citizens, WorldGuard |

- `NpcHarness` and `NpcClickSimulator` are registered in the ServiceRegistry only when Citizens is
  available.
  `IRegionService` is always registered; without WorldGuard it is `UnavailableRegionService`.
- The planner, diff, verifier, parser and executor take Citizens and WorldGuard only through
  `NpcHarness` and `IRegionService`, so the tests run them against in-memory fakes, including a
  full apply followed by a second all-NOOP plan.
- `NpcBridgeClassLoadingTest` hides `net.citizensnpcs`, `me.clip`, `com.sk89q` and the adapter
  packages and proves the startup path, the commands and the harness classes still load.
- WorldGuard is compiled against 7.0.9 / WorldEdit 7.2.14 (`provided`, transitive deps excluded);
  the servers run 7.0.18. Flag parse errors are caught as `Exception`, so both the 7.0.9
  `InvalidFlagFormat` and the later `InvalidFlagFormatException` are handled.
