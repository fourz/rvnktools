package org.fourz.rvnkcore;

/**
 * RVNKCore REST API contract version.
 *
 * Bump this constant when making additive or breaking API changes — independent of the
 * plugin version in pom.xml. Exposed via /v1/health as "apiVersion".
 * fourzorg-api mirrors this value manually after each contract change.
 *
 * <p>Version history:
 * <ul>
 *   <li><b>1.2</b> (May 2026, RVNKCore 1.5.x)
 *     <ul>
 *       <li>{@code POST /v1/whitelist/add} — add player to whitelist</li>
 *       <li>{@code DELETE /v1/whitelist/{ign}} — remove player from whitelist</li>
 *       <li>{@code GET /v1/whitelist/{ign}} — check whitelist status</li>
 *     </ul>
 *   </li>
 *   <li><b>1.1</b> (Apr 2026, RVNKCore 1.5.0-alpha)
 *     <ul>
 *       <li>Trade responses include new {@code itemType} field (material name extracted from {@code itemStackData})</li>
 *       <li>Shop responses include new {@code groupId} field (nullable Int — shop group membership)</li>
 *       <li>Group coowners stored and returned as JSON array (was CSV in pre-#675 installs)</li>
 *       <li>{@code tradeSource} field formally documented (PLAYER, ADMIN_OVERRIDE, SYSTEM)</li>
 *     </ul>
 *   </li>
 *   <li><b>1.0</b> (initial — RVNKCore 1.4.x): base API surface established</li>
 * </ul>
 * </p>
 */
public final class ApiVersion {
    public static final String API_VERSION = "1.2";

    private ApiVersion() {}
}
