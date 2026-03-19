package org.fourz.rvnktools.announceManager.command;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.fourz.rvnkcore.RVNKCore;
import org.fourz.rvnktools.announceManager.AnnounceManager;
import org.fourz.rvnktools.announceManager.Announcement;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

public class AnnounceSubCommandSet extends AnnounceSubCommand {
    
    public AnnounceSubCommandSet(AnnounceManager announceManager, RVNKCore plugin) {
        super(announceManager, plugin);
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (args.length < 4) {
            messageSender(sender, "&cUsage: /announce set <id> <property> <value>");
            return false;
        }

        String id = args[1];
        String property = args[2].toLowerCase();
        String value = String.join(" ", java.util.Arrays.copyOfRange(args, 3, args.length));

        Announcement announcement = announceManager.getAnnouncement(id);
        if (announcement == null) {
            messageSender(sender, "&cAnnouncement with ID '" + id + "' not found");
            return false;
        }

        switch (property) {
            case "recurrence":
                // Allow owners to modify recurrence without permission check
                boolean isOwner = false;
                if (sender instanceof Player) {
                    isOwner = announcement.getOwner().equalsIgnoreCase(((Player)sender).getName());
                }
                if (isOwner || checkPermission(sender, "rvnktools.command.announce.set")) {
                    if (isValidRecurrence(value)) {
                        announcement.setRecurrence(convertRecurrenceToSecondsLong(value));
                        announcement.setRecurrenceString(value);                    
                        messageSender(sender, "&aSet recurrence to: " + value + " for announcement, " + id + ".");
                    } else {
                        messageSender(sender, "&cInvalid recurrence value. Use: daily or none, or time values like 90m, 2h");
                        return false;
                    }
                } else {
                    messageSender(sender, "&cYou don't have permission to set recurrence");
                    return false;
                }             

                break;

            case "date":
                if (!checkPermission(sender, "rvnktools.command.announce.set")) {
                    return false;
                }
                try {
                    LocalDate date = LocalDate.parse(value, DateTimeFormatter.ofPattern("yyyy-MM-dd"));
                    announcement.setDate(date);
                    messageSender(sender, "&aSet date to: " + value);
                } catch (DateTimeParseException e) {
                    messageSender(sender, "&cInvalid date format. Use: YYYY-MM-DD");
                    return false;
                }
                break;

            case "type":
                if (!checkPermission(sender, "rvnktools.command.announce.set")) return false;
                if (announceManager.validateAnnounceType(value)) {
                    announcement.setType(value);
                    messageSender(sender, "&aSet type to: " + value);
                } else {
                    messageSender(sender, "&cInvalid announcement type: " + value);
                    return false;
                }
                break;

            case "permission":
                if (!checkPermission(sender, "rvnktools.command.announce.set")) return false;
                if (value.equalsIgnoreCase("none")) {
                    announcement.setPermission(null);
                    messageSender(sender, "&aRemoved permission requirement");
                } else {
                    announcement.setPermission(value);
                    messageSender(sender, "&aSet permission to: " + value);
                }
                break;

            default:
                messageSender(sender, "&cUnknown property: " + property);
                messageSender(sender, "&cValid properties: recurrence, date, type, permission");
                return false;
        }

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

    private Long convertRecurrenceToSecondsLong (String recurrence) {
        if (recurrence.equalsIgnoreCase("none")) {
            return null;
        }
        if (recurrence.equalsIgnoreCase("daily")) {
            return 86400L;
        }
        String number = recurrence.substring(0, recurrence.length() - 1);
        char unit = recurrence.charAt(recurrence.length() - 1);
        int value = Integer.parseInt(number);
        if (unit == 'm') {
            return (long) value * 60;
        }
        if (unit == 'h') {
            return (long) value * 3600;
        }
        return null;
    }
}