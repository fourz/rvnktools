package org.fourz.rvnkcore.api.model.worlds;

/**
 * Parsed body of {@code POST /rvnkworlds/groups} (#2218). Mirrors
 * {@code /world group create <name> [--link] [--portal]}.
 *
 * <p>Already checked by RVNKCore: {@code name} is a non-blank string, and the two flags are booleans
 * when present (absent = false). The name rule ({@code [a-zA-Z0-9_-]+}) and the duplicate check are
 * RVNKWorlds' (the same code the console runs).</p>
 *
 * @since 1.5.103
 */
public class CreateGroupRequest {

    private String name;
    /** {@code --link}: share inventories inside the group. */
    private boolean inventoryLink;
    /** {@code --portal}: portal emulation for the group. */
    private boolean portalEmulation;

    public CreateGroupRequest() {
    }

    public CreateGroupRequest(String name, boolean inventoryLink, boolean portalEmulation) {
        this.name = name;
        this.inventoryLink = inventoryLink;
        this.portalEmulation = portalEmulation;
    }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public boolean isInventoryLink() { return inventoryLink; }
    public void setInventoryLink(boolean inventoryLink) { this.inventoryLink = inventoryLink; }

    public boolean isPortalEmulation() { return portalEmulation; }
    public void setPortalEmulation(boolean portalEmulation) { this.portalEmulation = portalEmulation; }
}
