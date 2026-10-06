package org.fourz.rvnkcore.command;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.fourz.rvnkcore.RVNKCore;
import org.fourz.rvnkcore.api.model.NpcRef;
import org.fourz.rvnkcore.api.service.INpcService;
import org.fourz.rvnkcore.api.service.INpcService.TagResult;
import org.fourz.rvnkcore.service.npc.NpcKeys;
import org.fourz.rvnkcore.service.npc.UnavailableNpcService;
import org.fourz.rvnktools.command.manager.BaseSubCommand;
import org.fourz.rvnktools.command.manager.RVNKCommand;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * {@code /rvnk npc <tag|untag|list|info>} — staff tooling for the NPC bridge (#2213).
 *
 * <p>Every verb runs from the console. {@code tag} without an NPC id uses the sender's Citizens
 * selection ({@code /npc sel}); the console has to give the id.</p>
 *
 * <ul>
 *   <li>{@code tag <key> [npcId]} — {@code rvnkcore.npc.tag}</li>
 *   <li>{@code untag <key>} — {@code rvnkcore.npc.untag}</li>
 *   <li>{@code list} — {@code rvnkcore.npc.list}</li>
 *   <li>{@code info <key>} — {@code rvnkcore.npc.info}</li>
 * </ul>
 *
 * <p>The service is resolved on every invocation, so the command keeps working if RVNKCore's
 * registry is rebuilt.</p>
 *
 * @since 1.5.99-alpha
 */
public class NpcSubCommand extends BaseSubCommand {

    static final String PERM_TAG = "rvnkcore.npc.tag";
    static final String PERM_UNTAG = "rvnkcore.npc.untag";
    static final String PERM_LIST = "rvnkcore.npc.list";
    static final String PERM_INFO = "rvnkcore.npc.info";

    private static final List<String> VERBS = List.of("tag", "untag", "list", "info");

    public NpcSubCommand(RVNKCore plugin, RVNKCommand parent) {
        super(plugin, parent, "npc",
                "Tag Citizens NPCs with an RVNK key for quests and events",
                "/rvnk npc <tag <key> [npcId]|untag <key>|list|info <key>>",
                null, false);
    }

    @Override
    public List<String> getExamples() {
        return List.of(
                "/rvnk npc tag harbour_master 12",
                "  tags Citizens NPC #12; omit the id to use your /npc sel selection",
                "/rvnk npc info harbour_master",
                "/rvnk npc list",
                "/rvnk npc untag harbour_master",
                "  keys: lower-case a-z 0-9 _ - , 1-48 characters, unique per server");
    }

    @Override
    protected boolean executeSubCommand(CommandSender sender, String[] args) {
        if (args.length == 0) {
            sendUsage(sender);
            return true;
        }
        String verb = args[0].toLowerCase(Locale.ROOT);
        String[] rest = Arrays.copyOfRange(args, 1, args.length);
        switch (verb) {
            case "tag":
                return guarded(sender, PERM_TAG, () -> handleTag(sender, rest));
            case "untag":
                return guarded(sender, PERM_UNTAG, () -> handleUntag(sender, rest));
            case "list":
                return guarded(sender, PERM_LIST, () -> handleList(sender));
            case "info":
                return guarded(sender, PERM_INFO, () -> handleInfo(sender, rest));
            default:
                sendUsage(sender);
                return true;
        }
    }

    private boolean guarded(CommandSender sender, String permission, Runnable action) {
        if (!sender.hasPermission(permission)) {
            sendNoPermissionMessage(sender);
            return true;
        }
        action.run();
        return true;
    }

    // ── verbs ──────────────────────────────────────────────────────────────────

    private void handleTag(CommandSender sender, String[] args) {
        if (args.length < 1 || args.length > 2) {
            sendErrorMessage(sender, "Usage: /rvnk npc tag <key> [npcId]");
            return;
        }
        INpcService service = availableService(sender);
        if (service == null) {
            return;
        }
        String key = NpcKeys.normalize(args[0]);
        if (key == null) {
            sendInvalidKey(sender, args[0]);
            return;
        }

        int npcId;
        if (args.length == 2) {
            try {
                npcId = Integer.parseInt(args[1]);
            } catch (NumberFormatException e) {
                sendErrorMessage(sender, "NPC id must be a number: " + args[1]);
                return;
            }
        } else {
            OptionalInt selected = service.selectedBackingId(sender);
            if (selected.isEmpty()) {
                sendErrorMessage(sender, "No NPC id given and no NPC selected. Select one with "
                        + "/npc sel, or give the id: /rvnk npc tag " + key + " <npcId>");
                return;
            }
            npcId = selected.getAsInt();
        }

        TagResult result = service.tag(npcId, key);
        switch (result) {
            case TAGGED -> sendSuccessMessage(sender, "Tagged NPC #" + npcId + " with key '" + key + "'.");
            case RETAGGED -> sendSuccessMessage(sender, "NPC #" + npcId + " now carries key '" + key
                    + "' (its previous key was replaced).");
            case KEY_IN_USE -> {
                String holder = service.findByKey(key).map(ref -> "NPC #" + ref.getBackingId()).orElse("another NPC");
                sendErrorMessage(sender, "Key '" + key + "' is already on " + holder
                        + ". Untag it first: /rvnk npc untag " + key);
            }
            case NPC_NOT_FOUND -> sendErrorMessage(sender, "No Citizens NPC has id " + npcId + ".");
            default -> sendErrorMessage(sender, "Tag failed: " + result);
        }
    }

    private void handleUntag(CommandSender sender, String[] args) {
        if (args.length != 1) {
            sendErrorMessage(sender, "Usage: /rvnk npc untag <key>");
            return;
        }
        INpcService service = availableService(sender);
        if (service == null) {
            return;
        }
        TagResult result = service.untag(args[0]);
        switch (result) {
            case UNTAGGED -> sendSuccessMessage(sender, "Removed key '" + NpcKeys.normalize(args[0]) + "'.");
            case INVALID_KEY -> sendInvalidKey(sender, args[0]);
            case KEY_NOT_FOUND -> sendErrorMessage(sender, "No NPC carries key '" + args[0] + "'.");
            default -> sendErrorMessage(sender, "Untag failed: " + result);
        }
    }

    private void handleList(CommandSender sender) {
        INpcService service = availableService(sender);
        if (service == null) {
            return;
        }
        List<String> keys = service.listKeys();
        sender.sendMessage(ChatColor.GOLD + "RVNK NPC keys" + ChatColor.GRAY + " (" + keys.size()
                + ", provider " + service.getProviderName() + ")");
        if (keys.isEmpty()) {
            sender.sendMessage(ChatColor.GRAY + "  none - tag one with /rvnk npc tag <key> [npcId]");
            return;
        }
        for (String key : keys) {
            Optional<NpcRef> ref = service.findByKey(key);
            sender.sendMessage("  " + ChatColor.WHITE + key + ChatColor.GRAY
                    + ref.map(r -> "  #" + r.getBackingId() + " " + r.getDisplayName()
                            + (r.getWorld() != null ? "  " + r.getWorld() : "")
                            + (r.isSpawned() ? "" : "  (despawned)")).orElse(""));
        }
    }

    private void handleInfo(CommandSender sender, String[] args) {
        if (args.length != 1) {
            sendErrorMessage(sender, "Usage: /rvnk npc info <key>");
            return;
        }
        INpcService service = availableService(sender);
        if (service == null) {
            return;
        }
        String key = NpcKeys.normalize(args[0]);
        if (key == null) {
            sendInvalidKey(sender, args[0]);
            return;
        }
        Optional<NpcRef> found = service.findByKey(key);
        if (found.isEmpty()) {
            sendErrorMessage(sender, "No NPC carries key '" + key + "'.");
            return;
        }
        NpcRef ref = found.get();
        sender.sendMessage(ChatColor.GOLD + "NPC '" + ref.getKey() + "'");
        sender.sendMessage(ChatColor.YELLOW + "  Name: " + ChatColor.WHITE + ref.getDisplayName());
        sender.sendMessage(ChatColor.YELLOW + "  " + service.getProviderName() + " id: " + ChatColor.WHITE
                + ref.getBackingId());
        sender.sendMessage(ChatColor.YELLOW + "  Spawned: " + ChatColor.WHITE + ref.isSpawned());
        Location location = ref.getLocation();
        sender.sendMessage(ChatColor.YELLOW + "  Location: " + ChatColor.WHITE + (location == null
                ? "unknown"
                : (ref.getWorld() != null ? ref.getWorld() : "?") + " " + location.getBlockX() + ", "
                        + location.getBlockY() + ", " + location.getBlockZ()));
    }

    // ── helpers ────────────────────────────────────────────────────────────────

    /** @return the service when available, else null after telling the sender why */
    private INpcService availableService(CommandSender sender) {
        INpcService service = RVNKCore.getServiceSafe(INpcService.class);
        if (service == null) {
            sendErrorMessage(sender, "NPC bridge is not registered.");
            return null;
        }
        if (!service.isAvailable()) {
            String reason = service instanceof UnavailableNpcService u ? u.getReason() : "no NPC plugin";
            sendErrorMessage(sender, "NPC bridge unavailable: " + reason + ". Install Citizens to use /rvnk npc.");
            return null;
        }
        return service;
    }

    private void sendInvalidKey(CommandSender sender, String raw) {
        sendErrorMessage(sender, "Invalid key '" + raw + "'. Use lower-case a-z, 0-9, _ or -, 1-"
                + NpcKeys.MAX_LENGTH + " characters.");
    }

    private void sendUsage(CommandSender sender) {
        sendInfoMessage(sender, "Usage: " + getUsage());
        sendMessage(sender, "&7Examples: /rvnk help npc");
    }

    @Override
    protected List<String> getTabCompletions(CommandSender sender, String[] args) {
        if (args.length == 1) {
            List<String> matches = new ArrayList<>();
            for (String verb : VERBS) {
                if (verb.startsWith(args[0].toLowerCase(Locale.ROOT))) {
                    matches.add(verb);
                }
            }
            return matches;
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("untag") || args[0].equalsIgnoreCase("info"))) {
            INpcService service = RVNKCore.getServiceSafe(INpcService.class);
            if (service == null || !service.isAvailable()) {
                return Collections.emptyList();
            }
            String prefix = args[1].toLowerCase(Locale.ROOT);
            List<String> matches = new ArrayList<>();
            for (String key : service.listKeys()) {
                if (key.startsWith(prefix)) {
                    matches.add(key);
                }
            }
            return matches;
        }
        return Collections.emptyList();
    }
}
