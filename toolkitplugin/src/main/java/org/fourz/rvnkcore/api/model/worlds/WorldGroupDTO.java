package org.fourz.rvnkcore.api.model.worlds;

import java.util.ArrayList;
import java.util.List;

/**
 * One world group (World Forge control plane, #2218).
 *
 * <p>Returned by every group write route ({@code POST /rvnkworlds/groups},
 * {@code POST|DELETE /rvnkworlds/groups/{name}/worlds...}, {@code PUT .../default},
 * {@code PUT .../permission}, {@code DELETE /rvnkworlds/groups/{name}}). From RVNKWorlds 1.6.233 the
 * read routes {@code GET /rvnkworlds/groups[/{name}]} return the same shape. It is a superset of the
 * older {@code {name, worlds, isDefault, description}} record, so a client of the old shape keeps
 * working.</p>
 *
 * @since 1.5.103
 */
public class WorldGroupDTO {

    private String name;
    private List<String> worlds = new ArrayList<>();
    private boolean isDefault;
    /** Always null today; kept so the old record shape is a subset. */
    private String description;
    /** {@code inventoryLinkEmulator}: inventories shared inside the group ({@code /world group create --link}). */
    private boolean inventoryLink;
    /** {@code portalEmulation} ({@code /world group create --portal}). */
    private boolean portalEmulation;
    /** Permission gate {@code rvnkworlds.group.<name>} ({@code /world group permission <g> set|clear}). */
    private boolean requiresPermission;
    /** True when the group carries a {@code skyStack} config block (enabled or not). */
    private boolean skyStack;

    public WorldGroupDTO() {
    }

    public WorldGroupDTO(String name, List<String> worlds, boolean isDefault, boolean inventoryLink,
                         boolean portalEmulation, boolean requiresPermission, boolean skyStack) {
        this.name = name;
        setWorlds(worlds);
        this.isDefault = isDefault;
        this.inventoryLink = inventoryLink;
        this.portalEmulation = portalEmulation;
        this.requiresPermission = requiresPermission;
        this.skyStack = skyStack;
    }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public List<String> getWorlds() { return worlds; }
    public void setWorlds(List<String> worlds) { this.worlds = worlds != null ? new ArrayList<>(worlds) : new ArrayList<>(); }

    public boolean isDefault() { return isDefault; }
    public void setDefault(boolean isDefault) { this.isDefault = isDefault; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public boolean isInventoryLink() { return inventoryLink; }
    public void setInventoryLink(boolean inventoryLink) { this.inventoryLink = inventoryLink; }

    public boolean isPortalEmulation() { return portalEmulation; }
    public void setPortalEmulation(boolean portalEmulation) { this.portalEmulation = portalEmulation; }

    public boolean isRequiresPermission() { return requiresPermission; }
    public void setRequiresPermission(boolean requiresPermission) { this.requiresPermission = requiresPermission; }

    public boolean isSkyStack() { return skyStack; }
    public void setSkyStack(boolean skyStack) { this.skyStack = skyStack; }
}
