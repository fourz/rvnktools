package org.fourz.rvnkcore.api.model.worlds;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A named set of generator settings (World Forge, #2199 / #2200).
 *
 * <p>Both a response ({@code GET /rvnkworlds/presets[/{name}]}) and the request body of
 * {@code POST /rvnkworlds/presets}. On a POST, RVNKCore clears the server-owned fields
 * {@link #builtIn} (forced {@code false}) and {@link #createdAt} ({@code null}) before calling
 * the service, so a client cannot mint a fake built-in; the implementation stamps
 * {@code createdAt} itself.</p>
 *
 * <p>{@link #settings} is opaque here — RVNKWorlds validates it against the generator schema.
 * Numbers in a parsed request arrive as {@link Long} (integral) or {@link Double}.</p>
 *
 * @since 1.5.96
 */
public class PresetDTO {

    private String name;
    /** Generator id this preset applies to, e.g. {@code "archipelago"}. */
    private String generator;
    private String displayName;
    private String description;
    /** True for presets shipped in the RVNKWorlds jar; these cannot be deleted (409). */
    private boolean builtIn;
    /** Free-text author label; REST has one shared key, so this is whatever the client sends. */
    private String author;
    private Instant createdAt;
    private Map<String, Object> settings = new LinkedHashMap<>();

    public PresetDTO() {
    }

    public PresetDTO(String name, String generator, String displayName, String description,
                     boolean builtIn, String author, Instant createdAt, Map<String, Object> settings) {
        this.name = name;
        this.generator = generator;
        this.displayName = displayName;
        this.description = description;
        this.builtIn = builtIn;
        this.author = author;
        this.createdAt = createdAt;
        setSettings(settings);
    }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getGenerator() { return generator; }
    public void setGenerator(String generator) { this.generator = generator; }

    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public boolean isBuiltIn() { return builtIn; }
    public void setBuiltIn(boolean builtIn) { this.builtIn = builtIn; }

    public String getAuthor() { return author; }
    public void setAuthor(String author) { this.author = author; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Map<String, Object> getSettings() { return settings; }
    public void setSettings(Map<String, Object> settings) {
        this.settings = settings != null ? settings : new LinkedHashMap<>();
    }
}
