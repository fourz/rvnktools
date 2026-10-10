package org.fourz.rvnkcore.api.model.worlds;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * One terrain generator RVNKWorlds can create worlds with (World Forge, #2196 / #2200).
 *
 * <p>Served by {@code GET /rvnkworlds/generators} and {@code GET /rvnkworlds/generators/{id}}.
 * The {@link #schema} is opaque to RVNKCore: RVNKWorlds builds it from its {@code @GenOption}
 * annotations (#2197) and the WebUI renders controls from it. Core only carries it.</p>
 *
 * @since 1.5.96
 */
public class GeneratorInfoDTO {

    /** Generator id as used in a create request, e.g. {@code "archipelago"}. */
    private String id;
    private String displayName;
    private String description;
    /** Version of {@link #schema}; bumps when an option is added, renamed or re-ranged. */
    private int schemaVersion;
    /** Option schema as JSON-compatible maps/lists/scalars. Never null on the wire. */
    private Map<String, Object> schema = new LinkedHashMap<>();

    public GeneratorInfoDTO() {
    }

    public GeneratorInfoDTO(String id, String displayName, String description,
                            int schemaVersion, Map<String, Object> schema) {
        this.id = id;
        this.displayName = displayName;
        this.description = description;
        this.schemaVersion = schemaVersion;
        setSchema(schema);
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public int getSchemaVersion() { return schemaVersion; }
    public void setSchemaVersion(int schemaVersion) { this.schemaVersion = schemaVersion; }

    public Map<String, Object> getSchema() { return schema; }
    public void setSchema(Map<String, Object> schema) {
        this.schema = schema != null ? schema : new LinkedHashMap<>();
    }
}
