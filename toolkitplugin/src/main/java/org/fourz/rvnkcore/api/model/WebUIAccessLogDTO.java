package org.fourz.rvnkcore.api.model;

import java.sql.Timestamp;

/**
 * Data transfer object for a WebUI access-log entry.
 * Table: {@code rvnk_webui_access_log}
 *
 * <p>Records a single WebUI page visit / login / admin action. The country code is
 * resolved upstream by fourzorg-api (ip-api lookup); RVNKCore stores it as-is.</p>
 *
 * @since 1.5.9
 */
public class WebUIAccessLogDTO {

    private Long id;
    private String ign;
    private String uuid;
    private String ipAddress;
    private String countryCode;
    private String pagePath;
    private String actionType;
    private Timestamp createdAt;

    public WebUIAccessLogDTO() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getIgn() { return ign; }
    public void setIgn(String ign) { this.ign = ign; }

    public String getUuid() { return uuid; }
    public void setUuid(String uuid) { this.uuid = uuid; }

    public String getIpAddress() { return ipAddress; }
    public void setIpAddress(String ipAddress) { this.ipAddress = ipAddress; }

    public String getCountryCode() { return countryCode; }
    public void setCountryCode(String countryCode) { this.countryCode = countryCode; }

    public String getPagePath() { return pagePath; }
    public void setPagePath(String pagePath) { this.pagePath = pagePath; }

    public String getActionType() { return actionType; }
    public void setActionType(String actionType) { this.actionType = actionType; }

    public Timestamp getCreatedAt() { return createdAt; }
    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }

    /**
     * @return a new fluent builder for {@link WebUIAccessLogDTO}
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Fluent builder for {@link WebUIAccessLogDTO}.
     */
    public static final class Builder {
        private final WebUIAccessLogDTO dto = new WebUIAccessLogDTO();

        public Builder id(Long id) { dto.id = id; return this; }
        public Builder ign(String ign) { dto.ign = ign; return this; }
        public Builder uuid(String uuid) { dto.uuid = uuid; return this; }
        public Builder ipAddress(String ipAddress) { dto.ipAddress = ipAddress; return this; }
        public Builder countryCode(String countryCode) { dto.countryCode = countryCode; return this; }
        public Builder pagePath(String pagePath) { dto.pagePath = pagePath; return this; }
        public Builder actionType(String actionType) { dto.actionType = actionType; return this; }
        public Builder createdAt(Timestamp createdAt) { dto.createdAt = createdAt; return this; }

        public WebUIAccessLogDTO build() { return dto; }
    }
}
