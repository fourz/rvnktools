package org.fourz.rvnktools.linkMaker;

import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.TextComponent;
import java.io.File;
import java.util.HashMap;
import java.util.Map;

public class LinkMaker {
    private final JavaPlugin plugin;
    private final File configFile;
    private FileConfiguration config;
    private Map<String, LinkData> linksMap;
    boolean usingPlaceholderAPI;

    // initialize the plugin and load the configuration
    public LinkMaker(JavaPlugin plugin) {
        this.plugin = plugin;
        this.usingPlaceholderAPI = (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null);
        this.configFile = new File(plugin.getDataFolder(), "links.yml");

        loadConfig();
    }

    // load the configuration file and populates the links map
    private void loadConfig() {
        if (!configFile.exists()) {
            configFile.getParentFile().mkdirs();
            plugin.saveResource("links.yml", false);
        }

        this.config = YamlConfiguration.loadConfiguration(configFile);
        linksMap = new HashMap<>();
        for (String key : config.getConfigurationSection("links").getKeys(false)) {
            String placeholder = config.getString("links." + key + ".placeholder");
            String url = config.getString("links." + key + ".url");
            String text = config.getString("links." + key + ".text");
            if (placeholder != null && url != null && text != null) {
                linksMap.put(placeholder, new LinkData(url, text));
            }
        }
    }

    // replaces placeholders in the message with clickable links
    public TextComponent replacePlaceholders(String message) {
        TextComponent constructedMessage = new TextComponent();
        String[] parts = message.split("\\{");
        for (String part : parts) {
            if (part.contains("}")) {
                String[] split = part.split("}");
                String placeholder = "{" + split[0] + "}";
                LinkData linkData = linksMap.get(placeholder);

                if (linkData != null) {
                    TextComponent linkComponent = makeLink(linkData.getUrl(), linkData.getText());
                    constructedMessage.addExtra(linkComponent);
                } else {
                    constructedMessage.addExtra(new TextComponent(placeholder));
                }

                if (split.length > 1) {
                    constructedMessage.addExtra(new TextComponent(split[1]));
                }
            } else {
                constructedMessage.addExtra(new TextComponent(part));
            }
        }
        return constructedMessage;
    }

    // create a clickable text component with the specified URL and text
    public TextComponent makeLink(String url, String text) {
        TextComponent linkComponent = new TextComponent(text);
        linkComponent.setClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, url));
        linkComponent.setColor(net.md_5.bungee.api.ChatColor.AQUA);
        linkComponent.setUnderlined(true);
        return linkComponent;
    }

    // reload the configuration file
    public void reloadConfig() {
        loadConfig();
    }

    // inner class to store URL and text for links
    private static class LinkData {
        private final String url;
        private final String text;

        public LinkData(String url, String text) {
            this.url = url;
            this.text = text;
        }

        public String getUrl() {
            return url;
        }

        public String getText() {
            return text;
        }
    }
}