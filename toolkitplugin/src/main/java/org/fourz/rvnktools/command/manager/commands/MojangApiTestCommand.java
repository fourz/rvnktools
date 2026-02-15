package org.fourz.rvnktools.command.manager.commands;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.fourz.rvnkcore.RVNKCore;
import org.fourz.rvnkcore.api.mojang.MojangAPI;
import org.fourz.rvnkcore.service.registry.ServiceRegistry;
import org.fourz.rvnktools.command.manager.BaseCommand;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Test command for MojangAPI functionality.
 * Uses the shared MojangAPI instance from ServiceRegistry.
 *
 * <p>Usage: /mojangtest &lt;name|uuid|verify|stats&gt; &lt;value&gt;</p>
 *
 * <p>This demonstrates how other plugins can access the shared MojangAPI:</p>
 * <pre>
 * MojangAPI api = RVNKCore.getInstance().getServiceRegistry().getService(MojangAPI.class);
 * api.getUuidByName("Player").thenAccept(uuid -> ...);
 * </pre>
 */
public class MojangApiTestCommand extends BaseCommand {

    private MojangAPI mojangAPI;

    public MojangApiTestCommand(RVNKCore plugin) {
        super(plugin, "mojangtest",
              "Test MojangAPI functionality",
              "/mojangtest <name|uuid|verify> <value>",
              "rvnktools.admin.mojangtest");
    }

    @Override
    protected boolean executeCommand(CommandSender sender, String[] args) {
        // Get shared MojangAPI from ServiceRegistry on first use
        if (mojangAPI == null) {
            ServiceRegistry registry = RVNKCore.getInstance().getServiceRegistry();
            mojangAPI = registry.getService(MojangAPI.class);

            if (mojangAPI == null) {
                sender.sendMessage("&c[MojangAPI] &fMojangAPI service not available!".replace("&", "\u00A7"));
                return true;
            }
            sender.sendMessage("&6[MojangAPI] &fUsing shared MojangAPI from ServiceRegistry".replace("&", "\u00A7"));
        }

        if (args.length < 2) {
            sender.sendMessage("&c[MojangAPI] &fUsage: /mojangtest <name|uuid|verify|stats> <value>".replace("&", "\u00A7"));
            sender.sendMessage("&7  name <username> - Resolve username to UUID".replace("&", "\u00A7"));
            sender.sendMessage("&7  uuid <uuid> - Resolve UUID to username".replace("&", "\u00A7"));
            sender.sendMessage("&7  verify <username|uuid> - Verify player exists".replace("&", "\u00A7"));
            sender.sendMessage("&7  stats - Show rate limiter stats".replace("&", "\u00A7"));
            return true;
        }

        String action = args[0].toLowerCase();
        String value = args[1];

        switch (action) {
            case "name":
                testNameToUuid(sender, value);
                break;
            case "uuid":
                testUuidToName(sender, value);
                break;
            case "verify":
                testVerify(sender, value);
                break;
            case "stats":
                showStats(sender);
                break;
            default:
                sender.sendMessage("&c[MojangAPI] &fUnknown action: " + action);
                break;
        }

        return true;
    }

    private void testNameToUuid(CommandSender sender, String username) {
        sender.sendMessage("&6[MojangAPI] &fResolving username: &e" + username.replace("&", "\u00A7"));

        long startTime = System.currentTimeMillis();

        mojangAPI.getUuidByName(username).thenAccept(optUuid -> {
            long elapsed = System.currentTimeMillis() - startTime;

            Bukkit.getScheduler().runTask(plugin, () -> {
                if (optUuid.isPresent()) {
                    sender.sendMessage(("&a[MojangAPI] &fResolved: &e" + username + " &f-> &b" + optUuid.get()).replace("&", "\u00A7"));
                } else {
                    sender.sendMessage(("&c[MojangAPI] &fPlayer not found: &e" + username).replace("&", "\u00A7"));
                }
                sender.sendMessage(("&7[MojangAPI] &7Took " + elapsed + "ms").replace("&", "\u00A7"));
            });
        }).exceptionally(ex -> {
            Bukkit.getScheduler().runTask(plugin, () -> {
                sender.sendMessage(("&c[MojangAPI] &fError: " + ex.getMessage()).replace("&", "\u00A7"));
            });
            return null;
        });
    }

    private void testUuidToName(CommandSender sender, String uuidStr) {
        // Validate UUID format
        if (!MojangAPI.isValidUuidFormat(uuidStr)) {
            sender.sendMessage(("&c[MojangAPI] &fInvalid UUID format: &e" + uuidStr).replace("&", "\u00A7"));
            return;
        }

        java.util.Optional<UUID> parsedUuid = MojangAPI.parseUuid(uuidStr);
        if (parsedUuid.isEmpty()) {
            sender.sendMessage(("&c[MojangAPI] &fFailed to parse UUID: &e" + uuidStr).replace("&", "\u00A7"));
            return;
        }

        UUID uuid = parsedUuid.get();
        sender.sendMessage(("&6[MojangAPI] &fResolving UUID: &b" + uuid).replace("&", "\u00A7"));

        long startTime = System.currentTimeMillis();

        mojangAPI.getNameByUuid(uuid).thenAccept(optName -> {
            long elapsed = System.currentTimeMillis() - startTime;

            Bukkit.getScheduler().runTask(plugin, () -> {
                if (optName.isPresent()) {
                    sender.sendMessage(("&a[MojangAPI] &fResolved: &b" + uuid + " &f-> &e" + optName.get()).replace("&", "\u00A7"));
                } else {
                    sender.sendMessage(("&c[MojangAPI] &fUUID not found: &b" + uuid).replace("&", "\u00A7"));
                }
                sender.sendMessage(("&7[MojangAPI] &7Took " + elapsed + "ms").replace("&", "\u00A7"));
            });
        }).exceptionally(ex -> {
            Bukkit.getScheduler().runTask(plugin, () -> {
                sender.sendMessage(("&c[MojangAPI] &fError: " + ex.getMessage()).replace("&", "\u00A7"));
            });
            return null;
        });
    }

    private void testVerify(CommandSender sender, String value) {
        // Check if it's a UUID or username
        if (MojangAPI.isValidUuidFormat(value)) {
            java.util.Optional<UUID> parsedUuid = MojangAPI.parseUuid(value);
            if (parsedUuid.isEmpty()) {
                sender.sendMessage(("&c[MojangAPI] &fFailed to parse UUID").replace("&", "\u00A7"));
                return;
            }

            UUID uuid = parsedUuid.get();
            sender.sendMessage(("&6[MojangAPI] &fVerifying UUID: &b" + uuid).replace("&", "\u00A7"));

            mojangAPI.verifyUuid(uuid).thenAccept(valid -> {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (valid) {
                        sender.sendMessage(("&a[MojangAPI] &fUUID is &aVALID &f(exists in Mojang database)").replace("&", "\u00A7"));
                    } else {
                        sender.sendMessage(("&c[MojangAPI] &fUUID is &cINVALID &f(not found in Mojang database)").replace("&", "\u00A7"));
                    }
                });
            });
        } else if (MojangAPI.isValidUsername(value)) {
            sender.sendMessage(("&6[MojangAPI] &fVerifying username: &e" + value).replace("&", "\u00A7"));

            mojangAPI.verifyUsername(value).thenAccept(valid -> {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (valid) {
                        sender.sendMessage(("&a[MojangAPI] &fUsername is &aVALID &f(exists in Mojang database)").replace("&", "\u00A7"));
                    } else {
                        sender.sendMessage(("&c[MojangAPI] &fUsername is &cINVALID &f(not found in Mojang database)").replace("&", "\u00A7"));
                    }
                });
            });
        } else {
            sender.sendMessage(("&c[MojangAPI] &fInvalid format. Provide a valid UUID or username (3-16 chars, alphanumeric + _)").replace("&", "\u00A7"));
        }
    }

    private void showStats(CommandSender sender) {
        sender.sendMessage("&6[MojangAPI] &fRate Limiter Stats:".replace("&", "\u00A7"));
        sender.sendMessage(("&7  Remaining requests: &f" + mojangAPI.getRemainingRequests()).replace("&", "\u00A7"));
        sender.sendMessage(("&7  Name cache size: &f" + mojangAPI.getNameCacheSize()).replace("&", "\u00A7"));
        sender.sendMessage(("&7  UUID cache size: &f" + mojangAPI.getUuidCacheSize()).replace("&", "\u00A7"));
        sender.sendMessage(("&7  Source: &fServiceRegistry (shared instance)").replace("&", "\u00A7"));
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        if (args.length == 1) {
            return Arrays.asList("name", "uuid", "verify", "stats");
        }
        return Collections.emptyList();
    }
}
