package org.fourz.rvnktools.announceManager.subcommand;

import org.bukkit.entity.Player;
import org.fourz.rvnktools.RVNKTools;
import org.fourz.rvnktools.announceManager.AnnounceManager;
import org.fourz.rvnktools.announceManager.Announcement;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

public class AnnounceSubCommandSet extends AnnounceSubCommand {
    
    public AnnounceSubCommandSet(AnnounceManager announceManager, RVNKTools plugin) {
        super(announceManager, plugin);
    }

    @Override
    public boolean execute(Player player, String[] args) {
        if (!checkPermission(player, "rvnktools.command.announce.set")) {
            return false;
        }

        if (args.length < 4) {
            messagePlayer(player, "&cUsage: /announce set <id> <property> <value>");
            return false;
        }

        String id = args[1];
        String property = args[2].toLowerCase();
        String value = String.join(" ", java.util.Arrays.copyOfRange(args, 3, args.length));

        Announcement announcement = null;
        for (Announcement a : announceManager.getAnnouncements()) {
            if (a.getId().equalsIgnoreCase(id)) {
                announcement = a;
                break;
            }
        }

        if (announcement == null) {
            messagePlayer(player, "&cAnnouncement with ID '" + id + "' not found");
            return false;
        }

        switch (property) {
            case "recurrence":
                // Allow owners to modify recurrence without permission check
                boolean isOwner = announcement.getOwner() != null && 
                                announcement.getOwner().equalsIgnoreCase(player.getName());
                if (!isOwner || !checkPermission(player, "rvnktools.command.announce.set")) return false;
                
                if (isValidRecurrence(value)) {
                    announcement.setRecurrence(value);
                    messagePlayer(player, "&aSet recurrence to: " + value);
                } else {
                    messagePlayer(player, "&cInvalid recurrence value. Use: daily or none, or time values like 90m, 2h");
                    return false;
                }
                break;

            case "date":
                if (!checkPermission(player, "rvnktools.command.announce.set")) return false;
                try {
                    LocalDate date = LocalDate.parse(value, DateTimeFormatter.ofPattern("yyyy-MM-dd"));
                    announcement.setDate(date);
                    messagePlayer(player, "&aSet date to: " + value);
                } catch (DateTimeParseException e) {
                    messagePlayer(player, "&cInvalid date format. Use: YYYY-MM-DD");
                    return false;
                }
                break;

            case "type":
                if (!checkPermission(player, "rvnktools.command.announce.set")) return false;
                if (announceManager.validateAnnounceType(value)) {
                    announcement.setType(value);
                    messagePlayer(player, "&aSet type to: " + value);
                } else {
                    messagePlayer(player, "&cInvalid announcement type: " + value);
                    return false;
                }
                break;

            case "permission":
                if (!checkPermission(player, "rvnktools.command.announce.set")) return false;
                if (value.equalsIgnoreCase("none")) {
                    announcement.setPermission(null);
                    messagePlayer(player, "&aRemoved permission requirement");
                } else {
                    announcement.setPermission(value);
                    messagePlayer(player, "&aSet permission to: " + value);
                }
                break;

            default:
                messagePlayer(player, "&cUnknown property: " + property);
                messagePlayer(player, "&cValid properties: recurrence, date, type, permission");
                return false;
        }

        announceManager.saveConfig();
        return true;
    }

    private boolean isValidRecurrence(String recurrence) {
        if (recurrence == null || recurrence.isEmpty()) {
            return false;
        }
        
        // Support for named periods
        if (recurrence.equalsIgnoreCase("none") ||
            recurrence.equalsIgnoreCase("daily")) {
            return true;
        }

        // Support for time-based formats like "90m", "2h", "4h", etc.
        String pattern = "^\\d+[mh]$";
        if (recurrence.matches(pattern)) {
            // Extract number and unit
            String number = recurrence.substring(0, recurrence.length() - 1);
            char unit = recurrence.charAt(recurrence.length() - 1);
            
            try {
                int value = Integer.parseInt(number);
                if (unit == 'm' && value > 0 && value <= 1440) { // Up to 24 hours in minutes
                    return true;
                }
                if (unit == 'h' && value > 0 && value <= 24) { // Up to 24 hours
                    return true;
                }
            } catch (NumberFormatException e) {
                return false;
            }
        }        
        return false;
    }
}