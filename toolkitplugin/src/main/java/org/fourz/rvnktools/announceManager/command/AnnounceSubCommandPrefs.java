package org.fourz.rvnktools.announceManager.command;

import org.bukkit.Sound;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.fourz.rvnkcore.RVNKCore;
import org.fourz.rvnktools.announceManager.AnnounceManager;
import org.fourz.rvnktools.announceManager.preferences.PreferenceProperty;

public class AnnounceSubCommandPrefs extends AnnounceSubCommand {
    
    public AnnounceSubCommandPrefs(AnnounceManager announceManager, RVNKCore plugin) {
        super(announceManager, plugin);
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        java.util.UUID targetUUID;
        int argOffset;

        if (!(sender instanceof Player)) {
            // Console: /announce prefs <player> [property] [value]
            if (args.length < 2) {
                messageSender(sender, "&cUsage: /announce prefs <player> [property] [value]");
                return false;
            }
            Player target = plugin.getServer().getPlayer(args[1]);
            if (target == null) {
                messageSender(sender, "&cPlayer not found or not online: " + args[1]);
                return false;
            }
            targetUUID = target.getUniqueId();
            argOffset = 1;
        } else {
            targetUUID = ((Player) sender).getUniqueId();
            argOffset = 0;
        }

        int propertyArgIdx = 1 + argOffset;
        int valueArgIdx = 2 + argOffset;

        if (args.length <= propertyArgIdx) {
            showCurrentPrefs(sender, targetUUID);
            return true;
        }

        String property = args[propertyArgIdx].toLowerCase();
        String value = args.length > valueArgIdx ? args[valueArgIdx].toLowerCase() : null;

        if (value == null) {
            showPropertyValue(sender, targetUUID, property);
            return true;
        }

        switch (property) {
            case "location":
                return setLocation(sender, targetUUID, value);
            case "sound":
                return setSound(sender, targetUUID, value);
            default:
                messageSender(sender, "&cUnknown preference: " + property);
                return false;
        }
    }

    private void showPropertyValue(CommandSender sender, java.util.UUID targetUUID, String property) {
        switch (property) {
            case "location":
                announceManager.getConfig().getPreferenceAsync(targetUUID,
                    PreferenceProperty.LOCATION.getKey())
                    .thenAccept(value -> {
                        messageSender(sender, "&7Current location: &f" + value);
                        messageSender(sender, "&7To change: &f/announce prefs location <chat|title|action-bar|none>");
                    })
                    .exceptionally(ex -> {
                        messageSender(sender, "&cFailed to load preference: " + ex.getMessage());
                        return null;
                    });
                break;
            case "sound":
                announceManager.getConfig().getPreferenceAsync(targetUUID,
                    PreferenceProperty.SOUND.getKey())
                    .thenAccept(value -> {
                        messageSender(sender, "&7Current sound: &f" + (value.isEmpty() ? "none" : value));
                        messageSender(sender, "&7To change: &f/announce prefs sound <sound_name|none>");
                    })
                    .exceptionally(ex -> {
                        messageSender(sender, "&cFailed to load preference: " + ex.getMessage());
                        return null;
                    });
                break;
            default:
                messageSender(sender, "&cUnknown preference: " + property);
        }
    }

    private void showCurrentPrefs(CommandSender sender, java.util.UUID targetUUID) {
        announceManager.getConfig().getPreferenceAsync(targetUUID,
            PreferenceProperty.LOCATION.getKey())
            .thenCombine(
                announceManager.getConfig().getPreferenceAsync(targetUUID,
                    PreferenceProperty.SOUND.getKey()),
                (location, sound) -> {
                    messageSender(sender, "&6=== Announcement Preferences ===");
                    messageSender(sender, "&7Location: &f" + location);
                    messageSender(sender, "&7Sound: &f" + (sound.isEmpty() ? "none" : sound));
                    messageSender(sender, "");
                    messageSender(sender, "&7To change: &f/announce prefs <location|sound> <value>");
                    messageSender(sender, "&7Example: &f/announce prefs location title-top");
                    return null;
                }
            )
            .exceptionally(ex -> {
                messageSender(sender, "&cFailed to load preferences: " + ex.getMessage());
                return null;
            });
    }

    private boolean setLocation(CommandSender sender, java.util.UUID targetUUID, String location) {
        if (location.equals("none")) {
            announceManager.getConfig().setPreferenceAsync(targetUUID,
                PreferenceProperty.LOCATION.getKey(), "none")
                .thenAccept(v -> messageSender(sender, "&aAnnouncement location reset to default"))
                .exceptionally(ex -> {
                    messageSender(sender, "&cFailed to reset location: " + ex.getMessage());
                    return null;
                });
            return true;
        }

        if (!location.matches("chat|title|action-bar")) {
            messageSender(sender, "&cInvalid location. Use: chat, title, action-bar, or none");
            return false;
        }

        announceManager.getConfig().setPreferenceAsync(targetUUID,
            PreferenceProperty.LOCATION.getKey(), location)
            .thenAccept(v -> messageSender(sender, "&aAnnouncement location set to: &f" + location))
            .exceptionally(ex -> {
                messageSender(sender, "&cFailed to set location: " + ex.getMessage());
                return null;
            });
        return true;
    }

    private boolean setSound(CommandSender sender, java.util.UUID targetUUID, String soundName) {
        if (soundName.equals("none")) {
            announceManager.getConfig().setPreferenceAsync(targetUUID,
                PreferenceProperty.SOUND.getKey(), "none")
                .thenAccept(v -> messageSender(sender, "&aAnnouncement sound reset to default"))
                .exceptionally(ex -> {
                    messageSender(sender, "&cFailed to reset sound: " + ex.getMessage());
                    return null;
                });
            return true;
        }

        try {
            Sound.valueOf(soundName.toUpperCase());
            announceManager.getConfig().setPreferenceAsync(targetUUID,
                PreferenceProperty.SOUND.getKey(), soundName.toUpperCase())
                .thenAccept(v -> messageSender(sender, "&aAnnouncement sound set to: &f" + soundName))
                .exceptionally(ex -> {
                    messageSender(sender, "&cFailed to set sound: " + ex.getMessage());
                    return null;
                });
            return true;
        } catch (IllegalArgumentException e) {
            messageSender(sender, "&cInvalid sound name. Use a valid Minecraft sound or 'none'");
            return false;
        }
    }
}
