package org.fourz.rvnkcore.command;

import java.util.ArrayList;
import java.util.List;

/**
 * Re-splits Bukkit's space-split arguments so a double-quoted value stays one argument (#2248):
 * {@code create guide "Warden Tolla" world 1 2 3} gives {@code [create, guide, Warden Tolla, world, 1, 2, 3]}.
 * An unclosed quote runs to the end of the line. {@code \"} is a literal quote inside a value.
 *
 * @since 1.5.100-alpha
 */
public final class QuotedArgs {

    private QuotedArgs() {
    }

    public static String[] tokenize(String[] args) {
        String line = String.join(" ", args);
        List<String> out = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;
        boolean hasToken = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '\\' && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                current.append('"');
                hasToken = true;
                i++;
            } else if (c == '"') {
                inQuotes = !inQuotes;
                hasToken = true;
            } else if (c == ' ' && !inQuotes) {
                if (hasToken) {
                    out.add(current.toString());
                    current.setLength(0);
                    hasToken = false;
                }
            } else {
                current.append(c);
                hasToken = true;
            }
        }
        if (hasToken) {
            out.add(current.toString());
        }
        return out.toArray(new String[0]);
    }
}
