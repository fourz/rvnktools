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
        if (!(sender instanceof Player)) {
            messageSender(sender, "&cThis command can only be used by players");
            return true;
        }
        // Cast sender to Player
        Player player = (Player) sender;
        if (args.length < 2) {
            showCurrentPrefs(player);
            return true;
        }

        // Handle both formats: /announce prefs <value> and /announce preference <property> <value>
        String property;
        String value;

        if (args.length == 2) {
            // Format: /announce prefs <value>
            property = args[1].toLowerCase();
            value = null;
        } else {
            // Format: /announce preference <property> <value>
            property = args[1].toLowerCase();
            value = args[2].toLowerCase();
        }

        if (value == null) {
            // Show current value for specific property
            showPropertyValue(player, property);
            return true;
        }

        switch (property) {
            case "location":
                return setLocation(player, value);
            case "sound":
                return setSound(player, value);
            default:
                messageSender(player, "&cUnknown preference: " + property);
                return false;
        }
    }

    private void showPropertyValue(Player player, String property) {
        switch (property) {
            case "location":
                announceManager.getConfig().getPreferenceAsync(player.getUniqueId(),
                    PreferenceProperty.LOCATION.getKey())
                    .thenAccept(value -> {
                        messageSender(player, "&7Current location: &f" + value);
                        messageSender(player, "&7To change: &f/announce prefs location <chat|title|action-bar|none>");
                    })
                    .exceptionally(ex -> {
                        messageSender(player, "&cFailed to load preference: " + ex.getMessage());
                        return null;
                    });
                break;
            case "sound":
                announceManager.getConfig().getPreferenceAsync(player.getUniqueId(),
                    PreferenceProperty.SOUND.getKey())
                    .thenAccept(value -> {
                        messageSender(player, "&7Current sound: &f" + (value.isEmpty() ? "none" : value));
                        messageSender(player, "&7To change: &f/announce prefs sound <sound_name|none>");
                    })
                    .exceptionally(ex -> {
                        messageSender(player, "&cFailed to load preference: " + ex.getMessage());
                        return null;
                    });
                break;
            default:
                messageSender(player, "&cUnknown preference: " + property);
        }
    }

    private void showCurrentPrefs(Player player) {
        announceManager.getConfig().getPreferenceAsync(player.getUniqueId(),
            PreferenceProperty.LOCATION.getKey())
            .thenCombine(
                announceManager.getConfig().getPreferenceAsync(player.getUniqueId(),
                    PreferenceProperty.SOUND.getKey()),
                (location, sound) -> {
                    messageSender(player, "&6=== Your Announcement Preferences ===");
                    messageSender(player, "&7Location: &f" + location);
                    messageSender(player, "&7Sound: &f" + (sound.isEmpty() ? "none" : sound));
                    messageSender(player, "");
                    messageSender(player, "&7To change: &f/announce prefs <location|sound> <value>");
                    messageSender(player, "&7Example: &f/announce prefs location title-top");
                    return null;
                }
            )
            .exceptionally(ex -> {
                messageSender(player, "&cFailed to load preferences: " + ex.getMessage());
                return null;
            });
    }

    private boolean setLocation(Player player, String location) {
        if (location.equals("none")) {
            announceManager.getConfig().setPreferenceAsync(player.getUniqueId(),
                PreferenceProperty.LOCATION.getKey(), "none")
                .thenAccept(v -> messageSender(player, "&aAnnouncement location reset to default"))
                .exceptionally(ex -> {
                    messageSender(player, "&cFailed to reset location: " + ex.getMessage());
                    return null;
                });
            return true;
        }

        if (!location.matches("chat|title|action-bar")) {
            messageSender(player, "&cInvalid location. Use: chat, title, action-bar, or none");
            return false;
        }

        announceManager.getConfig().setPreferenceAsync(player.getUniqueId(),
            PreferenceProperty.LOCATION.getKey(), location)
            .thenAccept(v -> messageSender(player, "&aAnnouncement location set to: &f" + location))
            .exceptionally(ex -> {
                messageSender(player, "&cFailed to set location: " + ex.getMessage());
                return null;
            });
        return true;
    }

    private boolean setSound(Player player, String soundName) {
        if (soundName.equals("none")) {
            announceManager.getConfig().setPreferenceAsync(player.getUniqueId(),
                PreferenceProperty.SOUND.getKey(), "none")
                .thenAccept(v -> messageSender(player, "&aAnnouncement sound reset to default"))
                .exceptionally(ex -> {
                    messageSender(player, "&cFailed to reset sound: " + ex.getMessage());
                    return null;
                });
            return true;
        }

        try {
            Sound.valueOf(soundName.toUpperCase());
            announceManager.getConfig().setPreferenceAsync(player.getUniqueId(),
                PreferenceProperty.SOUND.getKey(), soundName.toUpperCase())
                .thenAccept(v -> messageSender(player, "&aAnnouncement sound set to: &f" + soundName))
                .exceptionally(ex -> {
                    messageSender(player, "&cFailed to set sound: " + ex.getMessage());
                    return null;
                });
            return true;
        } catch (IllegalArgumentException e) {
            messageSender(player, "&cInvalid sound name. Use a valid Minecraft sound or 'none'");
            return false;
        }
    }
}
