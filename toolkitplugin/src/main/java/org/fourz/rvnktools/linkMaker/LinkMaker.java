package org.fourz.rvnktools.linkMaker;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.fourz.rvnktools.RVNKTools;

public class LinkMaker {
  private RVNKTools plugin;
  private FileConfiguration linksConfig;
  private Map<String, String> placeholderToKeyMap;

  public LinkMaker(RVNKTools plugin) {
    this.plugin = plugin;
    loadLinksConfig();
    loadPlaceholderToKeyMap();
  }

  private void loadLinksConfig() {
    File configFile = new File(plugin.getDataFolder(), "links.yml");
    if (!configFile.exists()) {
      configFile.getParentFile().mkdirs();
      plugin.saveResource("links.yml", false);
    }

    linksConfig = YamlConfiguration.loadConfiguration(configFile);
  }

  private void loadPlaceholderToKeyMap() {
    placeholderToKeyMap = new HashMap<>();
    for (String key : linksConfig.getConfigurationSection("links").getKeys(false)) {
      String placeholder = linksConfig.getString("links." + key + ".placeholder");
      if (placeholder != null) {
        placeholderToKeyMap.put(placeholder, key);
      }
    }
  }

  public TextComponent replacePlaceholders(String message) {
    TextComponent constructedMessage = new TextComponent();
    String[] parts = message.split("\\{");
    for (String part : parts) {
      if (part.contains("}")) {
        String[] split = part.split("}");
        String placeholder = "{" + split[0] + "}";
        String parentKey = placeholderToKeyMap.get(placeholder);

        if (parentKey != null) {
          String url = linksConfig.getString("links." + parentKey + ".url");
          String text = linksConfig.getString("links." + parentKey + ".text");

          if (url != null && text != null) {
            TextComponent linkComponent = makeLink(url, text);
            constructedMessage.addExtra(linkComponent);
          } else {
            constructedMessage.addExtra(new TextComponent(placeholder));
          }
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

  public TextComponent makeLink(String url, String text) {
    TextComponent linkComponent = new TextComponent(text);
    linkComponent.setClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, url));
    linkComponent.setColor(net.md_5.bungee.api.ChatColor.AQUA);
    linkComponent.setUnderlined(true);
    return linkComponent;
  }

  public void reloadLinksConfig() {
    loadLinksConfig();
  }
}