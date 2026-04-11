# RVNKTools Command Reference

**Version**: 1.4.10-alpha
**Last Updated**: 2026-04-11

---

## Overview

RVNKTools is the RVNKCore toolkit plugin providing teleportation, messaging, announcements, preferences, account linking, and admin utilities. Most commands support console execution; player-only restrictions are noted per command.

**Base Permission**: `rvnktools.command`

---

## Quick Reference

| Command | Description | Permission |
|---------|-------------|------------|
| `/tp <args>` | Smart teleport (vanilla override) | `rvnktools.command.tp` |
| `/teleport <subcommand>` | Teleport utilities | `rvnktools.command.tp` |
| `/worldswap [world]` | Swap worlds preserving location | `rvnktools.command.worldswap` |
| `/event [world]` | Alias for worldswap | `rvnktools.command.worldswap` |
| `/tpa <player>` | Request teleport to player | `rvnktools.command.tpa` |
| `/tpahere <player>` | Request player to teleport to you | `rvnktools.command.tpahere` |
| `/tpaccept` | Accept a teleport request | `rvnktools.command.tpaccept` |
| `/tpdeny` | Deny a teleport request | `rvnktools.command.tpdeny` |
| `/back [player]` | Return to previous location | `rvnktools.command.back` |
| `/broadcast <message>` | Broadcast to all players | `rvnktools.command.broadcast` |
| `/announce <subcommand>` | Manage announcements | `rvnktools.command.announce` |
| `/pref [plugin] [action]` | Manage notification preferences | `rvnkcore.prefs` |
| `/link <service>` | Link account to external service | `rvnktools.link` |
| `/ping` | Server performance info (TPS, memory) | _(none — public)_ |
| `/events` | Info about scheduled events | _(none — public)_ |
| `/discord` | Discord server link | _(none — public)_ |
| `/trains [subcommand]` | TrainCarts settings and help | `rvnktools.command.trains` |
| `/puthat <modelData>` | Apply custom hat to nearest mob | `rvnktools.command.puthat` |
| `/logfilter <subcommand>` | Manage server log filtering | `rvnktools.command.logfilter` |
| `/rvnktools <subcommand>` | Plugin admin command | `rvnktools.command` |
| `/rvnkcore <subcommand>` | RVNKCore diagnostics | `rvnktools.admin.test` |
| `/pstest <subcommand>` | PlayerService test utilities | `rvnktools.admin.pstest` |

---

## Teleportation

### /tp

Smart teleport command. Active only when `commands.override-vanilla-tp: true` in config (default: false). When disabled, vanilla Bukkit `/tp` is used instead.

**Usage patterns**:

| Usage | Effect | Extra Permission |
|-------|--------|------------------|
| `/tp <player>` | Teleport self to player | — |
| `/tp <x> <y> <z>` | Teleport self to coordinates | — |
| `/tp <player1> <player2>` | Teleport player1 to player2 | `rvnktools.command.tp.others` |
| `/tp <player> <x> <y> <z>` | Teleport player to coordinates | `rvnktools.command.tp.others` |
| `/tp here <player>` | Teleport player to sender | `rvnktools.command.tp.others` |

**Permission**: `rvnktools.command.tp`

**Coordinate format**:
- Absolute: `100` `-64` `200`
- Relative: `~` (current), `~5` (current + 5), `~-5` (current − 5)

**Console**: Partial — coordinate and player modes require explicit arguments.

---

### /teleport

Explicit teleport subcommand router. Always available regardless of the vanilla-override config.

**Usage**: `/teleport <subcommand> [args]`

**Permission**: `rvnktools.command.tp`

**Subcommands**:

| Subcommand | Usage | Description |
|------------|-------|-------------|
| `player` | `/teleport player <target>` | Teleport to a player |
| `coords` | `/teleport coords <x> <y> <z>` | Teleport to coordinates |
| `here` | `/teleport here <player>` | Teleport player to sender (player-only) |
| `worldswap` | `/teleport worldswap [world]` | World swap (see `/worldswap`) |

**Alias**: `/tools teleport`

---

### /worldswap

Teleports to the last known location in a target world, preserving position history across swaps.

**Usage**: `/worldswap [world]`

**Parameters**:
- `world` — Destination world name (optional; defaults to `event`)

**Aliases**: `/ws`, `/event`

**Permission**: `rvnktools.command.worldswap`

**Player-only**: Yes

**Behavior**:
- First-time swap to a world teleports to world spawn
- Subsequent swaps restore the player's last saved position in that world
- Integrates with RVNKWorlds `IWorldService` when present; falls back to Bukkit world lookup
- Checks `rvnkworlds.world.access.<world>` or `rvnkworlds.world.access.*` for world access

**Note**: `rvnktools.command.tp.worldswap` is accepted as an alias permission node (both nodes are valid via `ITeleportService` constants).

---

### /tpa

Sends a teleport request to another player. If accepted, the sender teleports to the target.

**Usage**: `/tpa <player>`

**Parameters**:
- `player` — Online player name (required)

**Permission**: `rvnktools.command.tpa`

**Player-only**: Yes

**Bypass permissions**:
- `rvnktools.tpa.bypass.cooldown` — Skip cooldown timer
- `rvnktools.tpa.bypass.warmup` — Skip warmup timer

**Notes**: Target receives clickable accept/deny buttons. Cross-world teleportation is supported via `ITeleportService`.

---

### /tpahere

Sends a request for another player to teleport to you. If accepted, the target teleports to the sender.

**Usage**: `/tpahere <player>`

**Parameters**:
- `player` — Online player name (required)

**Permission**: `rvnktools.command.tpahere`

**Player-only**: Yes

---

### /tpaccept

Accepts a pending teleport request. Applies the configured warmup delay before teleportation.

**Usage**: `/tpaccept`

**Permission**: `rvnktools.command.tpaccept`

**Player-only**: Yes

**Bypass permission**: `rvnktools.tpa.bypass.warmup`

---

### /tpdeny

Denies a pending teleport request.

**Usage**: `/tpdeny`

**Permission**: `rvnktools.command.tpdeny`

**Player-only**: Yes

---

### /back

Returns the player to their location before the most recent teleport. Each use saves the current location as the new back point, enabling back-and-forth toggling.

**Usage**:
- `/back` — Return self (player-only)
- `/back <player>` — Return named player (console-compatible)

**Permission**: `rvnktools.command.back`

**Console**: Yes (requires `<player>` argument)

**Cooldown/warmup**: Shared with the TPA cooldown system; configurable.

---

## Messaging

### /broadcast

Broadcasts a message to all online players.

**Usage**: `/broadcast <message>`

**Parameters**:
- `message` — Message text (multi-word; required)

**Permission**: `rvnktools.command.broadcast`

**Console**: Yes

**Output format**: `[Broadcast] <message>` in gold.

---

## Announcements

### /announce

Manages the announcement system — add, delete, schedule, and deliver server announcements.

**Usage**: `/announce <subcommand> [args]`

**Aliases**: `/announcement`

**Base permission**: `rvnktools.command.announce`

**Wildcard**: `rvnktools.announce.*` grants all announce sub-permissions.

**Subcommands**:

| Subcommand | Usage | Permission | Description |
|------------|-------|------------|-------------|
| `help` | `/announce help` | `rvnktools.command.announce` | Show help |
| `types` | `/announce types` | `rvnktools.command.announce` | List announcement types |
| `toggle <type>` | `/announce toggle <type>` | `rvnktools.command.announce` | Toggle announcement type |
| `list` | `/announce list` | `rvnktools.command.announce` | List all announcements |
| `status` | `/announce status` | `rvnktools.command.announce` | View scheduler status |
| `pref <type>` | `/announce pref <type>` | `rvnktools.command.announce` | Set personal preference |
| `add <message>` | `/announce add <message>` | `rvnktools.command.announce.add` | Add new announcement |
| `delete <id>` | `/announce delete <id>` | `rvnktools.command.announce.delete` | Remove announcement |
| `now <id>` | `/announce now <id>` | `rvnktools.command.announce.now` | Send announcement immediately |
| `set <id> <field> <value>` | `/announce set <id> <field> <value>` | `rvnktools.command.announce.set` | Modify announcement field |
| `update <id> <message>` | `/announce update <id> <message>` | `rvnktools.command.announce.update` | Replace announcement message |
| `reload` | `/announce reload` | `rvnktools.command.announce.reload` | Reload announcements config |

---

## Preferences

### /pref

Manages per-player notification preferences for all RVNK plugins.

**Usage**: `/pref [plugin] [action] [args]`

**Aliases**: `/prefs`, `/preferences`

**Base permission**: `rvnkcore.prefs`

**Admin permission**: `rvnkcore.prefs.admin` (required for `admin` subcommand)

**Player-only**: Yes (except `admin` subcommands)

**Subcommands**:

| Usage | Description |
|-------|-------------|
| `/pref` | Show all plugin preferences |
| `/pref <plugin>` | Show preferences for one plugin |
| `/pref <plugin> toggle` | Toggle master on/off for plugin |
| `/pref <plugin> enable <type>` | Enable a notification type |
| `/pref <plugin> disable <type>` | Disable a notification type |
| `/pref <plugin> quiet <hour1> <hour2>` | Set quiet hours (0–23) |
| `/pref <plugin> quiet disable` | Disable quiet hours |
| `/pref <plugin> channel <type> <channel> <on\|off>` | Toggle a delivery channel |
| `/pref <plugin> reset` | Reset plugin preferences to defaults |
| `/pref admin types [plugin]` | List registered notification types (admin) |
| `/pref admin defaults <plugin> <type> <on\|off>` | Set server-wide defaults (admin) |

**Notification channels**: `TITLE`, `ACTION_BAR`, `CHAT`, `SOUND`, `BOSS_BAR`, `DISCORD`

---

## Account Linking

### /link

Links a player account to external services.

**Usage**: `/link <service>`

**Parameters**:
- `service` — One of: `login`, `matrix`, `discord`

**Permission**: `rvnktools.link`

**Console**: Partial — `/link login <player>` is supported from console.

**Services**:

| Service | Usage | Description |
|---------|-------|-------------|
| `login` | `/link login` | Generate a one-time magic link for the web portal |
| `matrix` | `/link matrix` | Link to Matrix account |
| `discord` | `/link discord` | Link to Discord account |

**`login` behavior**:
- Generates a one-time, 15-minute token
- Rate-limited per player via `AuthTokenStore`
- Sends player a clickable URL
- Resolves LuckPerms groups automatically for role assignment on the web portal
- Callback URL from `auth.callback-url` config or derived from `webui.host` + `webui.port`

---

## Utility

### /ping

Displays server performance information. No permission required.

**Usage**: `/ping`

**Aliases**: `/tps`

**Console**: Yes

**Displays**:
- TPS (1m / 5m / 15m averages) — color-coded: green >18, yellow >16, red <16
- Online player count
- Memory usage
- CPU usage
- Server version, Java version, OS

---

### /events

Shows information about currently scheduled events.

**Usage**: `/events`

**Player-only**: Yes

**Permission**: None (public)

---

### /discord

Sends the server's Discord invite link as a clickable chat message.

**Usage**: `/discord`

**Player-only**: Yes

**Permission**: None (public)

---

### /trains

Manages TrainCarts settings and provides help pages.

**Usage**: `/trains [subcommand]`

**Aliases**: `/train-help`

**Permission**: `rvnktools.command.trains`

**Player-only**: Yes

**Subcommands**:

| Subcommand | Permission | Description |
|------------|------------|-------------|
| _(none)_ | `rvnktools.command.trains` | Show help overview |
| `enable` | `rvnktools.command.trains.enable` | Enable TrainCarts rail system |
| `disable` | `rvnktools.command.trains.disable` | Disable (use vanilla carts) |
| `modes` | `rvnktools.command.trains` | Show modes help page |
| `examples` | `rvnktools.command.trains` | Show command examples |
| `links` | `rvnktools.command.trains` | Show wiki and video links |

**Notes**: `enable` and `disable` dispatch LuckPerms group commands (`group.rail-trains`) for permission management.

---

### /puthat

Applies a custom model hat (Jack-o-Lantern base) to the nearest mob within 10 blocks.

**Usage**: `/puthat <customModelData>`

**Parameters**:
- `customModelData` — Integer CustomModelData value for the head item

**Permission**: `rvnktools.command.puthat`

**Player-only**: Yes

**Range**: 10 blocks

---

### /logfilter

Manages runtime log filtering — suppress noisy plugin log output by keyword.

**Usage**: `/logfilter <subcommand> [args]`

**Permission**: `rvnktools.command.logfilter`

**Console**: Yes

**Subcommands**:

| Subcommand | Usage | Description |
|------------|-------|-------------|
| `status` | `/logfilter status` | Show filter config and enabled state |
| `reload` | `/logfilter reload` | Reload filter configuration |
| `enable` | `/logfilter enable` | Enable log filtering |
| `disable` | `/logfilter disable` | Disable log filtering |
| `add <keyword>` | `/logfilter add <keyword>` | Add keyword to filter list |
| `remove <keyword>` | `/logfilter remove <keyword>` | Remove keyword from filter list |
| `list` | `/logfilter list` | List all active keyword filters |

---

## Administration

### /rvnktools

Main administrative command for the RVNKTools plugin.

**Usage**: `/rvnktools <subcommand> [args]`

**Aliases**: `/tools`

**Base permission**: `rvnktools.command`

**Subcommands**:

| Subcommand | Permission | Description |
|------------|------------|-------------|
| `reload` | `rvnktools.admin.reload` | Reload plugin configuration |
| `debug` | `rvnktools.admin.debug` | Show debug information |
| `createtestdata [all\|types\|announcements]` | `rvnktools.admin.debug` | Seed test data |
| `links reload` | `rvnktools.links.reload` | Reload links configuration |
| `cycle reload` | `rvnktools.cycle.reload` | Reload cycle commands |
| `migration` | `rvnktools.admin.migration` | Run migration utilities |
| `teleport worldswap [world]` | `rvnktools.command.worldswap` | World swap shortcut |

**Wildcard**: `rvnktools.admin.*` grants all admin sub-permissions.

---

### /rvnkcore

RVNKCore diagnostics, service inspection, and system testing. Console-first design.

**Usage**: `/rvnkcore <subcommand> [args]`

**Permission**: `rvnktools.admin.test`

**Console**: Yes

**Subcommands**:

| Subcommand | Usage | Description |
|------------|-------|-------------|
| `debug` | `/rvnkcore debug` | Full system diagnostic dump |
| `services` | `/rvnkcore services` | List all registered services in ServiceRegistry |
| `db` / `database` | `/rvnkcore db` | Test database connectivity |
| `version` | `/rvnkcore version` | Show RVNKCore version info |
| `reload` | `/rvnkcore reload` | Reload RVNKCore configuration |
| `plugins` | `/rvnkcore plugins` | List loaded RVNK plugins |
| `commands` | `/rvnkcore commands` | Show CommandManager status |
| `health` | `/rvnkcore health` | Full health check (services + DB + plugins) |
| `test [all\|services\|db]` | `/rvnkcore test` | Run test suite |
| `mojang name <username>` | `/rvnkcore mojang name Steve` | Resolve username to UUID |
| `mojang uuid <uuid>` | `/rvnkcore mojang uuid <uuid>` | Resolve UUID to username |
| `mojang verify <name\|uuid>` | `/rvnkcore mojang verify Steve` | Verify player exists in Mojang API |
| `mojang stats` | `/rvnkcore mojang stats` | Show Mojang API rate limiter stats |

---

### /pstest

Test harness for `PlayerService`. Used for verifying database operations and LuckPerms sync from console during development.

**Usage**: `/pstest <subcommand> [args]`

**Permission**: `rvnktools.admin.pstest`

**Console**: Yes

**Subcommands**:

| Subcommand | Usage | Description |
|------------|-------|-------------|
| `all` | `/pstest all` | Get all players from database |
| `recent [hours]` | `/pstest recent 48` | Show recent players (default: 24h) |
| `online` | `/pstest online` | Get currently online players |
| `uuid <uuid>` | `/pstest uuid <uuid>` | Get player by UUID |
| `name <name>` | `/pstest name Steve` | Get player by name |
| `groups <groupname>` | `/pstest groups moderator` | Show players in LuckPerms group |
| `search <pattern>` | `/pstest search Ste` | Search players by name pattern |
| `count` | `/pstest count` | Total player count in database |
| `location <player> <world> <x> <y> <z>` | `/pstest location Steve world 0 64 0` | Update player location record |
| `updategroups <player> <primary> [groups...]` | `/pstest updategroups Steve default vip` | Update group assignments |
| `syncgroups [player]` | `/pstest syncgroups Steve` | Sync LuckPerms groups to database |
| `create <player> <world> <x> <y> <z>` | `/pstest create Steve world 0 64 0` | Create player record manually |
| `exists <uuid>` | `/pstest exists <uuid>` | Check if player record exists |

---

## Permission Nodes

### Teleportation

| Permission | Purpose |
|-----------|---------|
| `rvnktools.command.tp` | `/tp` and `/teleport` (self) |
| `rvnktools.command.tp.others` | Teleport other players |
| `rvnktools.command.tp.worldswap` | World swap via `/tp` or `/teleport` |
| `rvnktools.command.worldswap` | `/worldswap` and `/event` |
| `rvnktools.command.tpa` | Send `/tpa` requests |
| `rvnktools.command.tpahere` | Send `/tpahere` requests |
| `rvnktools.command.tpaccept` | Accept requests |
| `rvnktools.command.tpdeny` | Deny requests |
| `rvnktools.command.back` | `/back` |
| `rvnktools.tpa.bypass.cooldown` | Bypass TPA cooldown |
| `rvnktools.tpa.bypass.warmup` | Bypass TPA warmup |

### Messaging and Announcements

| Permission | Purpose |
|-----------|---------|
| `rvnktools.command.broadcast` | `/broadcast` |
| `rvnktools.command.announce` | Base `/announce` access |
| `rvnktools.command.announce.add` | Add announcements |
| `rvnktools.command.announce.delete` | Delete announcements |
| `rvnktools.command.announce.set` | Modify announcement fields |
| `rvnktools.command.announce.update` | Update announcement message |
| `rvnktools.command.announce.now` | Send announcement immediately |
| `rvnktools.command.announce.reload` | Reload announcements config |
| `rvnktools.announce.*` | All announcement permissions (wildcard) |

### Preferences and Linking

| Permission | Purpose |
|-----------|---------|
| `rvnkcore.prefs` | Manage own preferences |
| `rvnkcore.prefs.admin` | Manage all players' preferences |
| `rvnktools.link` | Use `/link` |

### Utility

| Permission | Purpose |
|-----------|---------|
| `rvnktools.command.trains` | `/trains` base access |
| `rvnktools.command.trains.enable` | Enable TrainCarts |
| `rvnktools.command.trains.disable` | Disable TrainCarts |
| `rvnktools.command.puthat` | `/puthat` |
| `rvnktools.command.logfilter` | `/logfilter` |

### Administration

| Permission | Purpose |
|-----------|---------|
| `rvnktools.command` | Base `/rvnktools` access |
| `rvnktools.admin.*` | All admin permissions (wildcard) |
| `rvnktools.admin.reload` | Reload configs |
| `rvnktools.admin.debug` | Debug commands and test data |
| `rvnktools.admin.migration` | Migration utilities |
| `rvnktools.admin.test` | `/rvnkcore` diagnostics |
| `rvnktools.admin.pstest` | `/pstest` utilities |
| `rvnktools.links.reload` | Reload links config |
| `rvnktools.cycle.reload` | Reload cycle commands |

---

## Console Support

Commands that require a player by default accept a `<player>` argument from the console:

```
back Steve
broadcast Server restart in 5 minutes
rvnkcore health
rvnkcore db
pstest online
pstest syncgroups Steve
link login Steve
```

Commands that are strictly player-only (no console support): `/tpa`, `/tpahere`, `/tpaccept`, `/tpdeny`, `/worldswap`, `/event`, `/teleport here`, `/events`, `/discord`, `/trains`, `/puthat`, `/pref`
