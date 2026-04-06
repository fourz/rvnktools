package org.fourz.rvnktools.announceManager;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.command.CommandSender;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletableFuture;
import org.fourz.rvnkcore.RVNKCore;
import org.fourz.rvnkcore.api.model.AnnouncementDTO;
import org.fourz.rvnkcore.api.model.AnnouncementTypeDTO;
import org.fourz.rvnkcore.api.service.AnnouncementService;
import org.fourz.rvnkcore.service.announcement.DefaultAnnouncementService;
import org.fourz.rvnkcore.service.registry.ServiceRegistry;
import org.fourz.rvnkcore.util.chat.ChatServiceInterface;
import org.fourz.rvnkcore.util.log.LogManager;
import org.fourz.rvnkcore.util.chat.ChatService;

import net.md_5.bungee.api.ChatMessageType;

import java.util.logging.Level;

public class AnnounceManager {
    private static final String CLASS_NAME = "AnnounceManager";
    private final LogManager logger;

    private final RVNKCore plugin;
    private final AnnounceConfig announceConfig;
    private final AnnounceScheduler announceScheduler;
    private final ChatServiceInterface chatService;
    private final Map<String, Announcement> announcements = new ConcurrentHashMap<>();
    private boolean usingPlaceholderAPI;
    private AnnouncementService announcementService;
    private AnnouncementFeeCollector feeCollector;

    public AnnounceManager(RVNKCore plugin) {
        this.logger = LogManager.getInstance(plugin, getClass());
        logger.info("Enabling AnnounceManager.");
        this.plugin = plugin;
        this.chatService = new ChatService();
        this.usingPlaceholderAPI = plugin.getServer().getPluginManager().getPlugin("PlaceholderAPI") != null;
        this.announceConfig = new AnnounceConfig(plugin, this);

        // Resolve AnnouncementService (required)
        resolveAnnouncementService();
        loadFromDatabase();

        // Initialize MOTD after announcements are loaded
        announceConfig.initializeMotd(getAnnouncements("motd"));

        this.announceScheduler = new AnnounceScheduler(plugin, this);
        announceScheduler.scheduleAnnouncements();

        // Initialize weekly fee collector if Vault economy is available
        initializeFeeCollector();
    }

    private void initializeFeeCollector() {
        try {
            org.bukkit.plugin.RegisteredServiceProvider<net.milkbowl.vault.economy.Economy> rsp =
                plugin.getServer().getServicesManager().getRegistration(net.milkbowl.vault.economy.Economy.class);
            if (rsp == null) {
                logger.info("Vault economy not found — fee collector disabled");
                return;
            }
            net.milkbowl.vault.economy.Economy economy = rsp.getProvider();
            this.feeCollector = new AnnouncementFeeCollector(announcementService, economy, logger);

            // Run weekly: 20 ticks/sec * 60 * 60 * 24 * 7 = 12,096,000 ticks
            // Initial delay: 5 minutes (6000 ticks) to let server stabilize
            long weeklyTicks = 20L * 60 * 60 * 24 * 7;
            feeCollector.runTaskTimerAsynchronously(plugin, 6000L, weeklyTicks);

            // Register login listener for payment notifications
            AnnouncementFeeLoginListener loginListener = new AnnouncementFeeLoginListener(feeCollector, logger);
            plugin.getServer().getPluginManager().registerEvents(loginListener, plugin);

            logger.info("AnnouncementFeeCollector initialized (weekly cycle, Vault economy)");
        } catch (Exception e) {
            logger.warning("Failed to initialize fee collector: " + e.getMessage());
        }
    }

    private void resolveAnnouncementService() {
        try {
            ServiceRegistry registry = plugin.getServiceRegistry();
            if (registry != null) {
                this.announcementService = registry.getService(AnnouncementService.class);
                if (this.announcementService != null) {
                    logger.info("AnnounceManager connected to AnnouncementService (DB-backed)");
                    return;
                }
            }
        } catch (Exception e) {
            logger.warning("Failed to resolve AnnouncementService: " + e.getMessage());
        }
        logger.error("AnnouncementService unavailable — AnnounceManager requires database");
    }

    private void loadFromDatabase() {
        if (announcementService == null) {
            logger.error("Cannot load announcements — AnnouncementService is null");
            return;
        }

        try {
            // Ensure announce types are available from YAML seed
            Map<String, AnnounceType> yamlTypes = announceConfig.getYmlTypes();
            if (yamlTypes != null && !yamlTypes.isEmpty()) {
                announceConfig.setAnnounceTypes(yamlTypes);
            }

            List<AnnouncementDTO> dtos = announcementService.getAllAnnouncements().join();

            if (dtos.isEmpty()) {
                // DB is empty — run one-time migration from YAML seed
                migrateYamlToDatabase();
                dtos = announcementService.getAllAnnouncements().join();
            }

            List<Announcement> loaded = new ArrayList<>();
            for (AnnouncementDTO dto : dtos) {
                loaded.add(convertFromDTO(dto));
            }
            setAnnouncements(loaded);
            logger.info("Loaded " + loaded.size() + " announcements from database");
        } catch (Exception e) {
            logger.error("Failed to load from database", e);
        }
    }

    /**
     * Reloads announcements from the database.
     * Called when a webhook notification indicates announcements have changed.
     */
    public void reloadFromDatabase() {
        if (announcementService == null) {
            logger.warning("Cannot reload from database — service unavailable");
            return;
        }

        try {
            List<AnnouncementDTO> dtos = announcementService.getAllAnnouncements().join();
            List<Announcement> loaded = new ArrayList<>();
            for (AnnouncementDTO dto : dtos) {
                loaded.add(convertFromDTO(dto));
            }
            setAnnouncements(loaded);
            announceScheduler.scheduleAnnouncements();
            logger.info("Reloaded " + loaded.size() + " announcements from database (webhook trigger)");
        } catch (Exception e) {
            logger.error("Failed to reload from database", e);
        }
    }

    public void registerCommands() {
        if (plugin.getCommand("announce") != null) {
            plugin.getCommand("announce").setExecutor(new AnnounceCommand(this, plugin));
            plugin.getCommand("announce").setTabCompleter(new AnnounceTabCompleter(this));
            logger.info("Announce command registered successfully");
        } else {
            logger.warning("Announce command not found in plugin.yml");
        }
    }

    public boolean addAnnouncement(Announcement announcement) {
        if (announcement == null || announcement.getId() == null) {
            logger.warning("Cannot add invalid announcement");
            return false;
        }

        String id = announcement.getId().toLowerCase();
        if (announcements.containsKey(id)) {
            logger.warning("Announcement with ID '" + id + "' already exists in memory");
            return false;
        }

        try {
            if (announcementService != null && !announcement.isImported()) {
                AnnouncementDTO dto = convertToDTO(announcement);
                announcementService.createAnnouncement(dto).join();
                announcement.setImported();
                logger.debug("Saved announcement to DB: " + id);
            }

            announcements.put(id, announcement);
            logger.debug("Added announcement to memory: " + id);
            return true;
        } catch (Exception e) {
            logger.error("Failed to add announcement: " + id, e);
            return false;
        }
    }

    public boolean addAnnouncement(CommandSender sender, String input) {
        String[] args = input.split(" ", 3);
        Player player = null;

        if (sender instanceof Player) {
            player = (Player) sender;
        }

        if (args.length < 3) {
            if (player != null) {
                chatService.sendMessage(player, "Invalid announcement format. Usage: <type> <id> <message>");
            } else {
                plugin.getLogger().warning("Invalid announcement format. Usage: <type> <id> <message>");
            }
            return false;
        }

        String type = args[0];
        String id = args[1];
        String text = args[2];

        if (announcementExists(id)) {
            if (player != null) {
                chatService.sendMessage(player, "An announcement with ID '" + id + "' already exists");
            } else {
                plugin.getLogger().warning("An announcement with ID '" + id + "' already exists");
            }
            return false;
        }

        if (!validateAnnounceType(type)) {
            plugin.getLogger().warning("Invalid announcement type: " + type);
            return false;
        }

        if (player != null) {
            if (!player.hasPermission("rvnktools.command.announce.add." + type)) {
                player.sendMessage("You do not have permission to add announcements.");
                return false;
            }
            player.sendMessage("Announcement added: " + id + " (" + type + ")");
            boolean result = announceConfig.parseAnnouncement(id, type, text, player.getName());
            // Set ownerUuid on the newly created announcement
            if (result) {
                Announcement ann = announcements.get(id.toLowerCase());
                if (ann != null) {
                    ann.setOwnerUuid(player.getUniqueId().toString());
                    // Persist ownerUuid to DB
                    if (announcementService != null) {
                        try {
                            AnnouncementDTO dto = convertToDTO(ann);
                            announcementService.updateAnnouncement(dto).join();
                        } catch (Exception e) {
                            logger.warning("Failed to persist ownerUuid for announcement: " + id);
                        }
                    }
                }
            }
            return result;
        }

        return announceConfig.parseAnnouncement(id, type, text);
    }

    public void broadcastAnnouncement(Announcement announcement) {
        if (announcement == null) {
            logger.warning("Cannot broadcast null announcement");
            return;
        }

        String announcementType = announcement.getType();
        if (announcementType == null || announcementType.isEmpty()) {
            logger.warning("Announcement has null or empty type");
            return;
        }

        AnnounceType type = announceConfig.getAnnounceTypes().get(announcementType);
        if (type == null) {
            logger.warning("Could not find announcement type '" + announcementType +
                "' in config. Available types: " + String.join(", ", announceConfig.getAnnounceTypes().keySet()));
            return;
        }

        if ("webonly".equals(type.getDisplayContext())) {
            return; // webonly types are not broadcast in-game
        }

        String prefix = type.getPrefix() != null ? type.getPrefix() : "";
        String suffix = type.getSuffix() != null ? type.getSuffix() : "";
        String message = prefix + announcement.getMessage() + suffix;

        Map<Player, PlayerPreferences> playerPrefs = new HashMap<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (this.shouldReceiveAnnouncement(player, announcement)) {
                String locationPref = announceConfig.getPreference(player.getUniqueId(), "location");
                String soundPref = announceConfig.getPreference(player.getUniqueId(), "sound");
                playerPrefs.put(player, new PlayerPreferences(
                    locationPref != null ? locationPref : "chat",
                    soundPref != null ? soundPref : "none"
                ));
            }
        }

        for (Map.Entry<Player, PlayerPreferences> entry : playerPrefs.entrySet()) {
            Player player = entry.getKey();
            PlayerPreferences prefs = entry.getValue();

            switch (prefs.location.toLowerCase()) {
                case "title":
                    player.sendTitle(chatService.parseTitle(message), "", 10, 100, 20);
                    break;
                case "action-bar":
                    player.spigot().sendMessage(ChatMessageType.ACTION_BAR, chatService.parseActionBar(message));
                    break;
                default:
                    chatService.sendMessage(player, message, plugin.getLinkMaker());
                    break;
            }

            if (!prefs.sound.equalsIgnoreCase("none")) {
                try {
                    org.bukkit.Sound sound = org.bukkit.Sound.valueOf(prefs.sound.toUpperCase());
                    player.playSound(player.getLocation(), sound, 1.0f, 1.0f);
                } catch (IllegalArgumentException e) {
                    logger.warning("Invalid sound preference for player " + player.getName() + ": " + prefs.sound);
                }
            }

            logger.debug("Broadcasting to " + player.getName() + ": " + message + " (location: " + prefs.location + ")");
        }
    }

    private static class PlayerPreferences {
        final String location;
        final String sound;

        PlayerPreferences(String location, String sound) {
            this.location = location;
            this.sound = sound;
        }
    }

    public void cleanup() {
        announceScheduler.cleanup();
        Runtime.getRuntime().gc();
    }

    public boolean deleteAnnouncement(String id) {
        if (id == null) {
            plugin.getLogger().warning("Cannot delete announcement with null ID");
            return false;
        }

        try {
            if (announcementService != null) {
                announcementService.deleteAnnouncement(id).join();
            }
            announcements.remove(id);
            logger.debug("Deleted announcement: " + id);
            return true;
        } catch (Exception e) {
            logger.info("Failed to delete announcement: " + id);
            return false;
        }
    }

    public void toggleAnnouncementType(Player player, String type) {
        UUID playerId = player.getUniqueId();
        type = type.toLowerCase();
        if (player.hasPermission("rvnktools.command.announce.toggle." + type)) {
            Set<String> disabledTypes = announceConfig.getPlayerDisabledTypes().getOrDefault(playerId, new HashSet<>());

            if (disabledTypes.contains(type)) {
                disabledTypes.remove(type);
                announceConfig.removePlayerDisabledType(playerId, type);
                chatService.sendMessage(player, "Announcements of type '" + type + "' enabled.");
            } else {
                disabledTypes.add(type);
                announceConfig.addPlayerDisabledType(playerId, type);
                chatService.sendMessage(player, "Announcements of type '" + type + "' disabled.");
            }
            announceConfig.getPlayerDisabledTypes().put(playerId, disabledTypes);
        } else {
            chatService.sendMessage(player, "You do not have permission to toggle announcements of this type.");
        }
    }

    public void reloadConfig() {
        if (announcementService != null) {
            reloadFromDatabase();
        } else {
            announceConfig.reloadConfig();
            announceScheduler.scheduleAnnouncements();
        }
    }

    public void shutdown() {
        announceScheduler.shutdown();
        if (feeCollector != null) {
            try {
                feeCollector.cancel();
                logger.info("AnnouncementFeeCollector cancelled");
            } catch (Exception e) {
                logger.debug("Fee collector already cancelled");
            }
        }
        logger.info("Saving preferences before shutdown...");
        announceConfig.shutdown();
        logger.info("AnnounceManager shutdown complete.");
    }

    public boolean validateAnnounceType(String type) {
        return announceConfig.getAnnounceTypes().containsKey(type);
    }

    public boolean shouldReceiveAnnouncement(Player player, Announcement announcement) {
        if (!player.hasPermission("rvnktools.announce.type." + announcement.getType().toLowerCase())) {
            return false;
        }

        if (announcement.getPermission() != null && !player.hasPermission(announcement.getPermission())) {
            return false;
        }

        Set<String> disabledTypes = announceConfig.getPlayerDisabledTypes().get(player.getUniqueId());
        if (disabledTypes != null && disabledTypes.contains(announcement.getType().toLowerCase())) {
            return false;
        }

        return true;
    }

    public boolean getPlayerDisabledTypes(Player player, String type) {
        Set<String> disabledTypes = announceConfig.getPlayerDisabledTypes().get(player.getUniqueId());
        return disabledTypes != null && disabledTypes.contains(type);
    }

    public String[] getPlayerDisabledAnnouncementTypes(Player player) {
        Set<String> disabledTypes = announceConfig.getPlayerDisabledTypes().get(player.getUniqueId());
        if (disabledTypes == null) {
            return new String[0];
        }
        return disabledTypes.toArray(new String[0]);
    }

    public Set<String> getAnnounceTypes() {
        return announceConfig.getAnnounceTypes().keySet();
    }

    public Set<String> getAnnouncementIds() {
        return announcements.keySet();
    }

    public List<Announcement> getAnnouncements() {
        return new ArrayList<>(announcements.values());
    }

    public List<Announcement> getAnnouncements(String type) {
        List<Announcement> typeAnnouncements = new ArrayList<>();
        for (Announcement announcement : announcements.values()) {
            if (type.equalsIgnoreCase(announcement.getType())) {
                typeAnnouncements.add(announcement);
            }
        }
        return typeAnnouncements;
    }

    public void setAnnouncements(List<Announcement> announcementList) {
        if (announcementList == null) {
            logger.warning("Skipping null announcement list");
            return;
        }
        announcements.clear();
        for (Announcement announcement : announcementList) {
            if (announcement.getId() == null) {
                logger.warning("Skipping announcement with null ID");
                continue;
            }
            announcements.put(announcement.getId(), announcement);
        }
        logger.debug("Set " + announcements.size() + " announcements");
    }

    public void savePlayerDisabledTypes() {
        announceConfig.savePlayerDisabledTypes();
    }

    public boolean sendAnnouncementNow(CommandSender sender, String id) {
        if (sender instanceof Player) {
            Player player = (Player) sender;
            if (!player.hasPermission("rvnktools.command.announce.now")) {
                chatService.sendMessage(player, "You do not have permission to send announcements now.");
                return false;
            }
        }

        Announcement announcement = announcements.get(id);
        if (announcement == null) {
            if (sender instanceof Player) {
                chatService.sendMessage((Player)sender, "Invalid announcement ID: " + id);
            } else {
                plugin.getLogger().warning("Cannot send announcement: No announcement found with ID " + id);
            }
            return false;
        }
        broadcastAnnouncement(announcement);
        return true;
    }

    public AnnounceType getAnnounceType(String type) {
        return announceConfig.getAnnounceTypes().get(type);
    }

    public boolean announcementExists(String id) {
        return announcements.containsKey(id);
    }

    public void setAnnouncementImported(String id) {
        Announcement announcement = announcements.get(id);
        if (announcement != null) {
            announcement.setImported();
        }
    }

    public void setAnnouncementsImported() {
        for (Announcement announcement : announcements.values()) {
            announcement.setImported();
        }
    }

    public boolean isAnnouncementImported(String id) {
        Announcement announcement = announcements.get(id);
        return announcement != null && announcement.isImported();
    }

    public Announcement getAnnouncement(String id) {
        if (id == null) {
            plugin.getLogger().warning("Cannot get announcement with null ID");
            return null;
        }
        return announcements.get(id);
    }

    public AnnounceConfig getConfig() {
        return announceConfig;
    }

    public boolean isDatabaseAvailable() {
        return announcementService != null;
    }

    public AnnouncementService getAnnouncementService() {
        return announcementService;
    }

    public void setPreference(UUID playerId, String property, String value) {
        announceConfig.setPreference(playerId, property, value);
    }

    public String getPreference(UUID playerId, String property) {
        return announceConfig.getPreference(playerId, property);
    }

    public Map<String, String> getPreferences(UUID playerId) {
        return announceConfig.getAllPreferences(playerId);
    }

    public boolean updateAnnouncement(String id, String newMessage) {
        if (id == null || newMessage == null) {
            logger.warning("Cannot update announcement: ID or message is null");
            return false;
        }

        Announcement oldAnnouncement = announcements.get(id);
        if (oldAnnouncement == null) {
            logger.warning("Cannot update announcement: No announcement found with ID " + id);
            return false;
        }

        try {
            announceScheduler.unscheduleAnnouncement(oldAnnouncement);

            Announcement updatedAnnouncement = new Announcement();
            updatedAnnouncement.setId(oldAnnouncement.getId());
            updatedAnnouncement.setType(oldAnnouncement.getType());
            updatedAnnouncement.setMessage(newMessage);
            updatedAnnouncement.setPermission(oldAnnouncement.getPermission());
            updatedAnnouncement.setOwner(oldAnnouncement.getOwner());
            updatedAnnouncement.setOwnerUuid(oldAnnouncement.getOwnerUuid());
            updatedAnnouncement.setDate(oldAnnouncement.getDate());
            updatedAnnouncement.setTime(oldAnnouncement.getTime());
            updatedAnnouncement.setExpiration(oldAnnouncement.getExpiration());
            updatedAnnouncement.setRecurrence(oldAnnouncement.getRecurrence());

            if (announcementService != null) {
                AnnouncementDTO dto = convertToDTO(updatedAnnouncement);
                announcementService.updateAnnouncement(dto).join();
            }

            announcements.remove(id);
            announcements.put(id, updatedAnnouncement);
            announceScheduler.scheduleAnnouncement(updatedAnnouncement);

            logger.debug("Updated and rescheduled announcement: " + id);
            return true;
        } catch (Exception e) {
            logger.error("Failed to update announcement: " + id, e);
            if (!announcements.containsKey(id)) {
                announcements.put(id, oldAnnouncement);
                announceScheduler.scheduleAnnouncement(oldAnnouncement);
            }
            return false;
        }
    }

    // ========== DTO Conversion Methods ==========

    static Announcement convertFromDTO(AnnouncementDTO dto) {
        Announcement ann = new Announcement();
        ann.setId(dto.getId());
        ann.setMessage(dto.getMessage());
        ann.setType(dto.getType());
        ann.setEnabled(dto.isActive());
        ann.setImported();

        if (dto.getIntervalSeconds() > 0) {
            ann.setRecurrence((long) dto.getIntervalSeconds());
        }

        if (dto.getScheduledFor() != null) {
            ann.setDate(dto.getScheduledFor().toLocalDateTime().toLocalDate());
            ann.setTime(dto.getScheduledFor().toLocalDateTime().toLocalTime());
        }

        if (dto.getExpiresAt() != null) {
            ann.setExpiration(dto.getExpiresAt().toLocalDateTime());
        }

        // Set ownerUuid from first-class column
        if (dto.getOwnerUuid() != null) {
            ann.setOwnerUuid(dto.getOwnerUuid());
        }

        Map<String, Object> metadata = dto.getMetadata();
        if (metadata != null) {
            Object permission = metadata.get("permission");
            if (permission != null) {
                ann.setPermission(permission.toString());
            }
            Object owner = metadata.get("owner");
            if (owner != null) {
                ann.setOwner(owner.toString());
            }
        }

        return ann;
    }

    static AnnouncementDTO convertToDTO(Announcement ann) {
        AnnouncementDTO.Builder builder = new AnnouncementDTO.Builder()
            .id(ann.getId())
            .message(ann.getMessage())
            .type(ann.getType())
            .active(ann.isEnabled())
            .title(ann.getId());

        if (ann.getRecurrence() != null && ann.getRecurrence() > 0) {
            builder.intervalSeconds(ann.getRecurrence().intValue());
        }

        if (ann.getDate() != null) {
            String dateStr = ann.getOriginalDateString() != null
                ? ann.getOriginalDateString()
                : ann.getDate().toString();
            builder.metadata("scheduleDate", dateStr);
            if (ann.getTime() != null) {
                builder.scheduledFor(Timestamp.valueOf(LocalDateTime.of(ann.getDate(), ann.getTime())));
            }
        }

        if (ann.getExpiration() != null) {
            builder.expiresAt(Timestamp.valueOf(ann.getExpiration()));
        }

        if (ann.getOwnerUuid() != null) {
            builder.ownerUuid(ann.getOwnerUuid());
        }

        if (ann.getPermission() != null) {
            builder.metadata("permission", ann.getPermission());
        }
        if (ann.getOwner() != null) {
            builder.metadata("owner", ann.getOwner());
        }

        return builder.build();
    }

    // ========== YAML -> DB Migration ==========

    private void migrateYamlToDatabase() {
        logger.info("Starting one-time YAML -> DB migration for announcements...");

        try {
            List<Announcement> yamlAnnouncements = announceConfig.getYmlAnnouncements();

            if (yamlAnnouncements == null || yamlAnnouncements.isEmpty()) {
                logger.info("No YAML announcements to migrate");
                return;
            }

            List<AnnouncementDTO> dtos = new ArrayList<>();
            for (Announcement ann : yamlAnnouncements) {
                dtos.add(convertToDTO(ann));
            }

            int imported = announcementService.bulkImportAnnouncements(dtos).join();
            logger.info("Migrated " + imported + " announcements from YAML to database");

            migrateTypesToDatabase();

        } catch (Exception e) {
            logger.error("YAML -> DB migration failed", e);
        }
    }

    private void migrateTypesToDatabase() {
        if (!(announcementService instanceof DefaultAnnouncementService)) {
            return;
        }

        DefaultAnnouncementService defaultService = (DefaultAnnouncementService) announcementService;
        var typeRepo = defaultService.getTypeRepository();
        if (typeRepo == null) {
            return;
        }

        Map<String, AnnounceType> yamlTypes = announceConfig.getAnnounceTypes();
        if (yamlTypes == null || yamlTypes.isEmpty()) {
            return;
        }

        List<AnnouncementTypeDTO> typeDTOs = new ArrayList<>();
        Timestamp now = Timestamp.valueOf(LocalDateTime.now());
        for (AnnounceType type : yamlTypes.values()) {
            Integer listFee = type.getListingFee() != null ? type.getListingFee().intValue() : 0;
            AnnouncementTypeDTO dto = new AnnouncementTypeDTO.Builder()
                .id(type.getId().toLowerCase())
                .name(type.getId().substring(0, 1).toUpperCase() + type.getId().substring(1))
                .prefix(type.getPrefix())
                .suffix(type.getSuffix())
                .permission(type.getPermission())
                .displayContext(type.getDisplayContext())
                .listFee(listFee)
                .weeklyFee(0)
                .active(true)
                .createdAt(now)
                .updatedAt(now)
                .build();
            typeDTOs.add(dto);
        }

        try {
            int count = typeRepo.insertAll(typeDTOs).join();
            logger.info("Migrated " + count + " announcement types from YAML to database");
        } catch (Exception e) {
            logger.error("Failed to migrate announcement types to database", e);
        }
    }
}
