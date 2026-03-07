package org.fourz.rvnkcore.config.dto;

/**
 * DTO for SQLite connection configuration.
 * Transfers SQLite config from each plugin's ConfigManager to the connection provider.
 *
 * <p>Shared across all RVNK plugins via RVNKCore.</p>
 */
public class SQLiteSettingsDTO {
    private final String filePath;
    private final String tablePrefix;

    public SQLiteSettingsDTO(String filePath, String tablePrefix) {
        this.filePath = filePath;
        this.tablePrefix = tablePrefix;
    }

    public String getFilePath() { return filePath; }
    public String getTablePrefix() { return tablePrefix != null ? tablePrefix : ""; }
}
