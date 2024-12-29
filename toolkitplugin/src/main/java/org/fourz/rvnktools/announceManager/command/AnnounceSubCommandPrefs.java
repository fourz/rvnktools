package org.fourz.rvnktools.announceManager.command;

import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.fourz.rvnktools.RVNKTools;
import org.fourz.rvnktools.announceManager.AnnounceManager;
import org.fourz.rvnktools.announceManager.preferences.PreferenceProperty;

public class AnnounceSubCommandPrefs extends AnnounceSubCommand {
    
    public AnnounceSubCommandPrefs(AnnounceManager announceManager, RVNKTools plugin) {
        super(announceManager, plugin);
    }

    @Override
    public boolean execute(Player player, String[] args) {
        if (args.length < 3) {
            showCurrentPrefs(player);
            return true;
        }

        String property = args[1].toLowerCase();
        String value = args[2].toLowerCase();

        switch (property) {
            case "location":
                return setLocation(player, value);
            case "sound":
                return setSound(player, value);
            default:
                messagePlayer(player, "&cUnknown preference: " + property);
                return false;
        }
    }

    private void showCurrentPrefs(Player player) {
        String location = announceManager.getConfig().getPreference(player.getUniqueId(), 
            PreferenceProperty.LOCATION.getKey());
        String sound = announceManager.getConfig().getPreference(player.getUniqueId(),
            PreferenceProperty.SOUND.getKey());

        messagePlayer(player, "&6=== Your Announcement Preferences ===");
        messagePlayer(player, "&7Location: &f" + location);
        messagePlayer(player, "&7Sound: &f" + (sound.isEmpty() ? "none" : sound));
        messagePlayer(player, "");
        messagePlayer(player, "&7To change: &f/announce prefs <location|sound> <value>");
        messagePlayer(player, "&7Example: &f/announce prefs location title-top");
    }

    private boolean setLocation(Player player, String location) {
        if (!location.matches("chat|title-top|title-right")) {
            messagePlayer(player, "&cInvalid location. Use: chat, title-top, or title-right");
            return false;
        }

        announceManager.getConfig().setPreference(player.getUniqueId(),
            PreferenceProperty.LOCATION.getKey(), location);
        messagePlayer(player, "&aAnnouncement location set to: &f" + location);
        return true;
    }

    private boolean setSound(Player player, String soundName) {
        if (soundName.equals("none")) {
            announceManager.getConfig().setPreference(player.getUniqueId(),
                PreferenceProperty.SOUND.getKey(), "");
            messagePlayer(player, "&aAnnouncement sound disabled");
            return true;
        }

        try {
            Sound.valueOf(soundName.toUpperCase());
            announceManager.getConfig().setPreference(player.getUniqueId(),
                PreferenceProperty.SOUND.getKey(), soundName.toUpperCase());
            messagePlayer(player, "&aAnnouncement sound set to: &f" + soundName);
            return true;
        } catch (IllegalArgumentException e) {
            messagePlayer(player, "&cInvalid sound name. Use a valid Minecraft sound or 'none'");
            return false;
        }
    }
}
