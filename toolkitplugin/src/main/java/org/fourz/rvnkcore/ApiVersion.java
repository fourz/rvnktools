package org.fourz.rvnkcore;

/**
 * RVNKCore REST API contract version.
 *
 * Bump this constant when making breaking API changes — independent of the
 * plugin version in pom.xml. Exposed via /v1/health as "apiVersion".
 * fourzorg-api mirrors this value manually after each breaking change.
 */
public final class ApiVersion {
    public static final String API_VERSION = "1.0";

    private ApiVersion() {}
}
