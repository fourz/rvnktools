package org.fourz.rvnktools.announceManager;

public class AnnounceType {
    private String id;
    private String prefix;
    private String suffix;
    private String permission;
    private Double listingFee;
    private String displayContext = "both";
    private boolean defaultEnabled = true;
    private boolean imported = false;

    // Getters and setters
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getPrefix() {
        return prefix;
    }

    public void setPrefix(String prefix) {
        this.prefix = prefix;
    }

    public String getSuffix() {
        return suffix;
    }

    public void setSuffix(String suffix) {
        this.suffix = suffix;
    }

    public String getPermission() {
        return permission;
    }

    public void setPermission(String permission) {
        this.permission = permission;
    }

    public Double getListingFee() {
        return listingFee;
    }

    public void setListingFee(Double listingFee) {
        this.listingFee = listingFee;
    }

    public String getDisplayContext() {
        return displayContext;
    }

    public void setDisplayContext(String displayContext) {
        this.displayContext = displayContext;
    }

    public boolean isDefaultEnabled() {
        return defaultEnabled;
    }

    public void setDefaultEnabled(boolean defaultEnabled) {
        this.defaultEnabled = defaultEnabled;
    }

}
