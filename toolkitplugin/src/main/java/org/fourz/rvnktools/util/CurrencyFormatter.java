package org.fourz.rvnktools.util;

import java.text.DecimalFormat;
import org.bukkit.ChatColor;
import org.fourz.rvnkcore.RVNKCore;

public class CurrencyFormatter {
    private static final DecimalFormat DECIMAL_FORMAT = new DecimalFormat("#,##0.##");

    public static String format(double amount, RVNKCore plugin) {
        String currencyName = amount == 1 ?
            plugin.getEconomy().currencyNameSingular() :
            plugin.getEconomy().currencyNamePlural();

        return ChatColor.GOLD + DECIMAL_FORMAT.format(amount) + " " + currencyName;
    }

    public static String format(double amount, RVNKCore plugin, boolean useSymbol) {

        if (!useSymbol) {
            return format(amount, plugin);
        }
        String currencySymbol = plugin.getEconomy().currencyNameSingular().substring(0, 1).toUpperCase();
        return ChatColor.GOLD + DECIMAL_FORMAT.format(amount) + currencySymbol;
    }
}
