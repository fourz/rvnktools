package org.fourz.rvnkcore.api.config;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.fourz.rvnkcore.util.log.LogManager;

/**
 * Configuration for cross-server portals.
 *
 * <p>Mirrors {@link TransferConfig} / {@link ChatRelayConfig}'s {@code fromConfigurationSection} +
 * {@code validate} style. A portal is a trigger block (default {@code DIAMOND_BLOCK}) plus a sign
 * whose first line is the {@code sign-header} and which names a target server; stepping on the
 * block transfers the player via the existing {@code TransferService}.</p>
 *
 * <p><b>Shipped default is disabled.</b> Seed this section on each live server's config.</p>
 *
 * @since 1.5.24
 */
public class PortalConfig {

    /** Default trigger block when none is configured or the configured value is invalid. */
    public static final String DEFAULT_TRIGGER_BLOCK = "DIAMOND_BLOCK";

    private final boolean enabled;
    private final String triggerBlock;
    private final String signHeader;
    private final String permissionCreate;
    private final String permissionDelete;
    private final String permissionUse;

    /** Resolved trigger material, or null when the configured name does not resolve to a block. */
    private final Material triggerMaterial;

    private PortalConfig(boolean enabled, String triggerBlock, String signHeader,
                         String permissionCreate, String permissionDelete, String permissionUse) {
        this.enabled = enabled;
        this.triggerBlock = (triggerBlock != null && !triggerBlock.trim().isEmpty())
                ? triggerBlock.trim() : DEFAULT_TRIGGER_BLOCK;
        this.signHeader = (signHeader != null && !signHeader.trim().isEmpty())
                ? signHeader.trim() : "[server]";
        this.permissionCreate = (permissionCreate != null && !permissionCreate.trim().isEmpty())
                ? permissionCreate.trim() : "rvnkcore.portal.create";
        this.permissionDelete = (permissionDelete != null && !permissionDelete.trim().isEmpty())
                ? permissionDelete.trim() : "rvnkcore.portal.delete";
        this.permissionUse = (permissionUse != null && !permissionUse.trim().isEmpty())
                ? permissionUse.trim() : "rvnkcore.portal.use";
        this.triggerMaterial = Material.matchMaterial(this.triggerBlock);
    }

    /**
     * Creates a PortalConfig from a ConfigurationSection.
     *
     * @param section The {@code portal} configuration section (may be null)
     * @return PortalConfig instance (disabled defaults when section is null)
     */
    public static PortalConfig fromConfigurationSection(ConfigurationSection section) {
        if (section == null) {
            return new PortalConfig(false, DEFAULT_TRIGGER_BLOCK, "[server]",
                    "rvnkcore.portal.create", "rvnkcore.portal.delete", "rvnkcore.portal.use");
        }

        ConfigurationSection perms = section.getConfigurationSection("permissions");
        String createPerm = perms != null ? perms.getString("create", "rvnkcore.portal.create") : "rvnkcore.portal.create";
        String deletePerm = perms != null ? perms.getString("delete", "rvnkcore.portal.delete") : "rvnkcore.portal.delete";
        String usePerm = perms != null ? perms.getString("use", "rvnkcore.portal.use") : "rvnkcore.portal.use";

        return new PortalConfig(
                section.getBoolean("enabled", false),
                section.getString("trigger-block", DEFAULT_TRIGGER_BLOCK),
                section.getString("sign-header", "[server]"),
                createPerm,
                deletePerm,
                usePerm
        );
    }

    /**
     * Validates the portal configuration.
     *
     * @param logger Logger for reporting validation issues
     * @return true if valid (or disabled), false if the trigger block does not resolve to a block
     */
    public boolean validate(LogManager logger) {
        if (!enabled) {
            return true;
        }
        boolean valid = true;
        if (triggerMaterial == null) {
            logger.error("Portal trigger-block '" + triggerBlock
                    + "' did not resolve to a Material — portals cannot be triggered");
            valid = false;
        } else if (!triggerMaterial.isBlock()) {
            logger.error("Portal trigger-block '" + triggerBlock
                    + "' resolved to " + triggerMaterial + " which is not a placeable block");
            valid = false;
        }
        if (signHeader.isEmpty()) {
            logger.warning("Portal sign-header is empty — signs cannot be recognised as portals");
        }
        return valid;
    }

    public boolean isEnabled() {
        return enabled;
    }

    /**
     * @return the raw configured trigger-block name (e.g. {@code "DIAMOND_BLOCK"})
     */
    public String getTriggerBlock() {
        return triggerBlock;
    }

    /**
     * @return the resolved trigger {@link Material}, or null when the configured name is invalid
     */
    public Material getTriggerMaterial() {
        return triggerMaterial;
    }

    /**
     * @return the sign header that marks a sign as a portal target (e.g. {@code "[server]"})
     */
    public String getSignHeader() {
        return signHeader;
    }

    public String getPermissionCreate() {
        return permissionCreate;
    }

    public String getPermissionDelete() {
        return permissionDelete;
    }

    public String getPermissionUse() {
        return permissionUse;
    }
}
