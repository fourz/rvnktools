package org.fourz.rvnkcore.command;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.fourz.rvnkcore.RVNKCore;
import org.fourz.rvnkcore.service.kit.ItemCodec;
import org.fourz.rvnkcore.service.kit.Kit;
import org.fourz.rvnkcore.service.kit.KitGrantPlan;
import org.fourz.rvnkcore.service.kit.KitService;
import org.fourz.rvnkcore.service.kit.KitSlots;
import org.fourz.rvnktools.command.manager.BaseSubCommand;
import org.fourz.rvnktools.command.manager.RVNKCommand;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.Consumer;

/**
 * {@code /rvnk kit <save|give|list|show|delete>} (#2415): named item sets granted by command.
 *
 * <p>{@code give} is console-safe, so a quest {@code COMMAND} reward or the Trace QA session can run
 * {@code rvnk kit give qa-survival <player>}. It grants into the player's live inventory, which is the
 * current world group's inventory under RVNKWorlds. {@code save} captures the sender's own inventory,
 * so it needs a player. Database work runs off the main thread; item encoding and the grant run on it.</p>
 *
 * <p>Permissions: {@code rvnkcore.kit.admin} for save, delete and giving to others;
 * {@code rvnkcore.kit.<name>} to give yourself that kit. See {@link KitArgs#allowed}.</p>
 */
public class KitSubCommand extends BaseSubCommand {

    private static final List<String> VERBS = List.of("save", "give", "list", "show", "delete");

    public KitSubCommand(RVNKCore plugin, RVNKCommand parent) {
        super(plugin, parent, "kit",
                "Save, give, list, show or delete a named item set (kit)",
                "/rvnk kit <save <name> [--clear]|give <kit> [player]|list|show <kit>|delete <kit>>",
                null, false);
    }

    @Override
    public List<String> getExamples() {
        return List.of(
                "/rvnk kit save qa-survival",
                "  capture your inventory, armour and off-hand as kit qa-survival (adds on give)",
                "/rvnk kit save event-basic --clear",
                "  as above, but a give clears the inventory first",
                "/rvnk kit give qa-survival Shad0melt",
                "  console-safe; needs rvnkcore.kit.admin to give another player",
                "/rvnk kit list",
                "/rvnk kit show qa-survival",
                "/rvnk kit delete qa-survival");
    }

    @Override
    protected boolean executeSubCommand(CommandSender sender, String[] args) {
        KitArgs parsed;
        try {
            parsed = KitArgs.parse(args);
        } catch (IllegalArgumentException e) {
            sendErrorMessage(sender, e.getMessage());
            sendErrorMessage(sender, getUsage());
            return true;
        }
        KitService kits = RVNKCore.getServiceSafe(KitService.class);
        if (kits == null) {
            sendErrorMessage(sender, "Kits are not available: the database is not ready");
            return true;
        }
        boolean admin = sender.hasPermission(KitArgs.PERM_ADMIN);
        try {
            switch (parsed.verb()) {
                case LIST -> list(sender, kits, admin);
                case SHOW -> {
                    if (!allowed(sender, parsed, admin, true)) return true;
                    withKit(sender, kits, parsed.kit(), kit -> show(sender, kit));
                }
                case DELETE -> {
                    if (!allowed(sender, parsed, admin, true)) return true;
                    reply(sender, kits.delete(parsed.kit()), gone -> {
                        if (gone) sendSuccessMessage(sender, "Kit " + parsed.kit() + " deleted");
                        else sendErrorMessage(sender, "No kit named " + parsed.kit());
                    });
                }
                case SAVE -> save(sender, kits, parsed, admin);
                case GIVE -> give(sender, kits, parsed, admin);
            }
        } catch (RejectedExecutionException e) {
            sendErrorMessage(sender, "Kit service busy; try again");
        }
        return true;
    }

    private boolean allowed(CommandSender sender, KitArgs parsed, boolean admin, boolean self) {
        boolean kitPerm = parsed.kit() != null && sender.hasPermission(Kit.permission(parsed.kit()));
        if (KitArgs.allowed(parsed.verb(), admin, kitPerm, self)) return true;
        sendNoPermissionMessage(sender);
        return false;
    }

    private void list(CommandSender sender, KitService kits, boolean admin) {
        reply(sender, kits.list(), all -> {
            List<String> rows = new ArrayList<>();
            for (Kit k : all) {
                if (!admin && !sender.hasPermission(Kit.permission(k.name()))) continue;
                rows.add("&f" + k.name() + " &7- " + k.itemCount() + " slot(s), "
                        + (k.clearFirst() ? "clear-first" : "add") + ", v" + k.version());
            }
            if (rows.isEmpty()) {
                sendInfoMessage(sender, "No kits" + (admin ? "" : " you can use"));
                return;
            }
            sendInfoMessage(sender, "Kits (" + rows.size() + "):");
            rows.forEach(r -> sendMessage(sender, "  " + r));
        });
    }

    private void show(CommandSender sender, Kit kit) {
        sendInfoMessage(sender, "Kit " + kit.name() + ": " + kit.itemCount() + " slot(s), "
                + (kit.clearFirst() ? "clear-first" : "add") + ", v" + kit.version()
                + ", saved by " + (kit.savedBy().isEmpty() ? "?" : kit.savedBy()) + ", codec " + kit.codec());
        SortedMap<Integer, ItemStack> items;
        try {
            items = decode(kit);
        } catch (IllegalArgumentException | IllegalStateException e) {
            sendErrorMessage(sender, "Cannot read the items: " + e.getMessage());
            return;
        }
        items.forEach((slot, item) -> sendMessage(sender, "  &7" + KitSlots.label(slot) + ": &f" + describe(item)));
    }

    private void save(CommandSender sender, KitService kits, KitArgs parsed, boolean admin) {
        if (!allowed(sender, parsed, admin, true)) return;
        if (!(sender instanceof Player player)) {
            sendErrorMessage(sender, "save captures your own inventory, so it needs a player");
            return;
        }
        // Encode on the main thread: item serialisation reads server registries.
        ItemCodec codec = ItemCodec.best();
        SortedMap<Integer, byte[]> items = new TreeMap<>();
        PlayerInventory inv = player.getInventory();
        try {
            for (int slot = 0; slot < KitSlots.SIZE; slot++) {
                ItemStack item = get(inv, slot);
                if (!isEmpty(item)) items.put(slot, codec.encode(item));
            }
        } catch (IllegalStateException e) {
            sendErrorMessage(sender, "Could not encode your inventory: " + e.getMessage());
            return;
        }
        if (items.isEmpty()) {
            sendErrorMessage(sender, "Your inventory is empty; nothing to save");
            return;
        }
        Kit kit = new Kit(parsed.kit(), parsed.clearFirst(), codec.id(), items.size(), items, 0,
                "player:" + player.getName(), System.currentTimeMillis());
        reply(sender, kits.save(kit), version -> {
            sendSuccessMessage(sender, "Kit " + kit.name() + (version > 1 ? " replaced (v" + version + ")" : " saved")
                    + ": " + items.size() + " slot(s), " + (kit.clearFirst() ? "clear-first" : "add")
                    + ". Players need " + Kit.permission(kit.name()) + " to give it to themselves.");
            logger.info("Kit " + kit.name() + " v" + version + " saved by " + player.getName()
                    + " (" + items.size() + " slots, codec " + codec.id() + ")");
        });
    }

    private void give(CommandSender sender, KitService kits, KitArgs parsed, boolean admin) {
        Player target;
        if (parsed.player() == null) {
            if (!(sender instanceof Player p)) {
                sendErrorMessage(sender, "Name a player: /rvnk kit give " + parsed.kit() + " <player>");
                return;
            }
            target = p;
        } else {
            target = online(parsed.player());
            if (target == null) {
                sendErrorMessage(sender, "Player not online: " + parsed.player());
                return;
            }
        }
        boolean self = sender instanceof Player p && p.getUniqueId().equals(target.getUniqueId());
        if (!allowed(sender, parsed, admin, self)) return;

        final UUID targetId = target.getUniqueId();
        withKit(sender, kits, parsed.kit(), kit -> {
            Player now = Bukkit.getPlayer(targetId);
            if (now == null) {
                sendErrorMessage(sender, "Player left before the kit arrived");
                return;
            }
            int dropped;
            try {
                dropped = grant(now, kit);
            } catch (IllegalArgumentException | IllegalStateException e) {
                sendErrorMessage(sender, "Kit " + kit.name() + " not given: " + e.getMessage());
                return;
            }
            String extra = dropped > 0 ? " (" + dropped + " stack(s) did not fit and dropped at their feet)" : "";
            if (!now.equals(sender)) {
                sendSuccessMessage(sender, "Gave kit " + kit.name() + " to " + now.getName() + extra);
            }
            now.sendMessage("§aYou received kit §f" + kit.name() + "§a" + extra);
            logger.info("Kit " + kit.name() + " v" + kit.version() + " given to " + now.getName()
                    + " by " + sender.getName() + (kit.clearFirst() ? " (clear-first)" : ""));
        });
    }

    /**
     * Grants a kit into the player's live inventory. Decodes every item first, so a bad item aborts
     * the whole grant before anything is touched. Main thread only.
     *
     * @return number of stacks dropped at the player's feet
     */
    static int grant(Player player, Kit kit) {
        SortedMap<Integer, ItemStack> items = decode(kit);
        PlayerInventory inv = player.getInventory();
        KitGrantPlan<ItemStack> plan = KitGrantPlan.of(items, slot -> isEmpty(get(inv, slot)), kit.clearFirst());
        if (plan.clear()) {
            inv.clear();
            inv.setArmorContents(new ItemStack[4]);
            inv.setItemInOffHand(null);
        }
        plan.place().forEach((slot, item) -> set(inv, slot, item));
        if (plan.add().isEmpty()) return 0;
        Map<Integer, ItemStack> left = inv.addItem(plan.add().toArray(new ItemStack[0]));
        for (ItemStack item : left.values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), item);
        }
        return left.size();
    }

    private static SortedMap<Integer, ItemStack> decode(Kit kit) {
        ItemCodec codec = ItemCodec.forId(kit.codec());
        SortedMap<Integer, ItemStack> items = new TreeMap<>();
        for (Map.Entry<Integer, byte[]> e : kit.items().entrySet()) {
            try {
                items.put(e.getKey(), codec.decode(e.getValue()));
            } catch (IllegalStateException ex) {
                throw new IllegalStateException(KitSlots.label(e.getKey()) + ": " + ex.getMessage(), ex);
            }
        }
        return items;
    }

    private static ItemStack get(PlayerInventory inv, int slot) {
        return switch (slot) {
            case KitSlots.FEET -> inv.getBoots();
            case KitSlots.LEGS -> inv.getLeggings();
            case KitSlots.CHEST -> inv.getChestplate();
            case KitSlots.HEAD -> inv.getHelmet();
            case KitSlots.OFF_HAND -> inv.getItemInOffHand();
            default -> inv.getItem(slot);
        };
    }

    private static void set(PlayerInventory inv, int slot, ItemStack item) {
        switch (slot) {
            case KitSlots.FEET -> inv.setBoots(item);
            case KitSlots.LEGS -> inv.setLeggings(item);
            case KitSlots.CHEST -> inv.setChestplate(item);
            case KitSlots.HEAD -> inv.setHelmet(item);
            case KitSlots.OFF_HAND -> inv.setItemInOffHand(item);
            default -> inv.setItem(slot, item);
        }
    }

    private static boolean isEmpty(ItemStack item) {
        return item == null || item.getType().isAir() || item.getAmount() <= 0;
    }

    private static String describe(ItemStack item) {
        StringBuilder sb = new StringBuilder(item.getType().name().toLowerCase(Locale.ROOT)).append(" x").append(item.getAmount());
        ItemMeta meta = item.getItemMeta();
        if (meta != null && meta.hasDisplayName()) sb.append(" \"").append(meta.getDisplayName()).append("§f\"");
        int enchants = item.getEnchantments().size();
        if (enchants > 0) sb.append(" +").append(enchants).append(" enchant(s)");
        return sb.toString();
    }

    private static Player online(String arg) {
        Player p = Bukkit.getPlayerExact(arg);
        if (p != null) return p;
        try {
            return Bukkit.getPlayer(UUID.fromString(arg));
        } catch (IllegalArgumentException ignored) {
            return null; // not a UUID
        }
    }

    private void withKit(CommandSender sender, KitService kits, String name, Consumer<Kit> action) {
        reply(sender, kits.get(name), found -> {
            if (found.isEmpty()) sendErrorMessage(sender, "No kit named " + name + " (see /rvnk kit list)");
            else action.accept(found.get());
        });
    }

    /** Runs {@code onResult} on the main thread, or reports the failure there. */
    private <T> void reply(CommandSender sender, CompletableFuture<T> future, Consumer<T> onResult) {
        future.whenComplete((value, ex) -> Bukkit.getScheduler().runTask(plugin, () -> {
            if (ex != null) {
                Throwable t = ex;
                while (t.getCause() != null) t = t.getCause();
                sendErrorMessage(sender, "Kit request failed: " + t.getClass().getSimpleName() + ": " + t.getMessage());
                logger.warning("Kit request failed: " + t);
            } else {
                onResult.accept(value);
            }
        }));
    }

    @Override
    protected List<String> getTabCompletions(CommandSender sender, String[] args) {
        if (args.length == 1) return VERBS;
        KitService kits = RVNKCore.getServiceSafe(KitService.class);
        String verb = args[0].toLowerCase(Locale.ROOT);
        if (args.length == 2 && kits != null && !verb.equals("list")) return kits.cachedNames();
        if (args.length == 3 && verb.equals("give")) {
            return Bukkit.getOnlinePlayers().stream().map(Player::getName).toList();
        }
        if (args.length == 3 && verb.equals("save")) return List.of("--clear", "--add");
        return Collections.emptyList();
    }
}
