package org.fourz.rvnkcore.plugin;

import org.bukkit.Server;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.generator.BiomeProvider;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginDescriptionFile;
import org.bukkit.plugin.PluginLoader;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.InputStream;
import java.util.List;
import java.util.logging.Logger;

/**
 * Virtual plugin object for RVNKCore to provide clean separation from RVNKTools.
 * 
 * This class acts as a "virtual plugin" that represents RVNKCore within the RVNKTools
 * plugin ecosystem. It delegates most operations to the parent RVNKTools plugin while
 * providing a distinct identity for logging, configuration, and service management.
 * 
 * This pattern enables:
 * - Clean separation between RVNKCore and RVNKTools logging and services
 * - Easy migration path when RVNKCore becomes a separate plugin
 * - Distinct plugin identity for debugging and monitoring
 * - Independent lifecycle management within the same JAR
 * 
 * @since 1.0.0
 */
public class VirtualRVNKCorePlugin implements Plugin {
    
    private final JavaPlugin parentPlugin;
    private final String virtualName = "RVNKCore";
    private final String virtualVersion = "1.0.0-alpha";
    
    /**
     * Creates a virtual RVNKCore plugin instance.
     * 
     * @param parentPlugin The actual RVNKTools plugin instance
     */
    public VirtualRVNKCorePlugin(JavaPlugin parentPlugin) {
        this.parentPlugin = parentPlugin;
    }
    
    @Override
    public @NotNull File getDataFolder() {
        // Use a subdirectory within the parent plugin's data folder
        File coreDataFolder = new File(parentPlugin.getDataFolder(), "rvnkcore");
        if (!coreDataFolder.exists()) {
            coreDataFolder.mkdirs();
        }
        return coreDataFolder;
    }
    
    @Override
    public @NotNull PluginDescriptionFile getDescription() {
        return parentPlugin.getDescription();
    }
    
    @Override
    public @NotNull FileConfiguration getConfig() {
        return parentPlugin.getConfig();
    }
    
    @Override
    public @Nullable InputStream getResource(@NotNull String filename) {
        return parentPlugin.getResource(filename);
    }
    
    @Override
    public void saveConfig() {
        parentPlugin.saveConfig();
    }
    
    @Override
    public void saveDefaultConfig() {
        parentPlugin.saveDefaultConfig();
    }
    
    @Override
    public void saveResource(@NotNull String resourcePath, boolean replace) {
        parentPlugin.saveResource(resourcePath, replace);
    }
    
    @Override
    public void reloadConfig() {
        parentPlugin.reloadConfig();
    }
    
    @Override
    public @NotNull PluginLoader getPluginLoader() {
        return parentPlugin.getPluginLoader();
    }
    
    @Override
    public @NotNull Server getServer() {
        return parentPlugin.getServer();
    }
    
    @Override
    public boolean isEnabled() {
        return parentPlugin.isEnabled();
    }
    
    @Override
    public void onDisable() {
        // Virtual plugin doesn't handle lifecycle directly
    }
    
    @Override
    public void onLoad() {
        // Virtual plugin doesn't handle lifecycle directly
    }
    
    @Override
    public void onEnable() {
        // Virtual plugin doesn't handle lifecycle directly
    }
    
    @Override
    public boolean isNaggable() {
        return parentPlugin.isNaggable();
    }
    
    @Override
    public void setNaggable(boolean canNag) {
        parentPlugin.setNaggable(canNag);
    }
    
    @Override
    public @Nullable ChunkGenerator getDefaultWorldGenerator(@NotNull String worldName, @Nullable String id) {
        return parentPlugin.getDefaultWorldGenerator(worldName, id);
    }
    
    @Override
    public @Nullable BiomeProvider getDefaultBiomeProvider(@NotNull String worldName, @Nullable String id) {
        return parentPlugin.getDefaultBiomeProvider(worldName, id);
    }
    
    @Override
    public @NotNull Logger getLogger() {
        return parentPlugin.getLogger();
    }
    
    @Override
    public @NotNull String getName() {
        return virtualName;
    }
    
    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        return parentPlugin.onCommand(sender, command, label, args);
    }
    
    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        return parentPlugin.onTabComplete(sender, command, alias, args);
    }
    
    /**
     * Gets the virtual plugin version.
     * 
     * @return The RVNKCore version string
     */
    public String getVersion() {
        return virtualVersion;
    }
    
    /**
     * Gets the parent RVNKTools plugin instance.
     * 
     * @return The parent plugin
     */
    public JavaPlugin getParentPlugin() {
        return parentPlugin;
    }
    
    /**
     * Gets a display name for logging and debugging.
     * 
     * @return Display name in format "RVNKCore[via RVNKTools]"
     */
    public String getDisplayName() {
        return virtualName + "[via " + parentPlugin.getName() + "]";
    }
}
