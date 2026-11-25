package org.fourz.rvnkcore.service.chatrelay;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

/**
 * The scoped inbound token for the Matrix bridge (#2377). It opens ONLY {@code POST /v1/chat/inbound},
 * and the controller then accepts only {@code originServerId} = the configured Matrix origin with
 * {@code room=MATRIX}. It never opens anything else: the bridge must not hold the tier's general
 * {@code X-API-Key}, which opens the whole REST API.
 *
 * <p>The server holds only SHA-256 hashes ({@code chat-relay.matrix.inbound-token-sha256}), never a
 * token. Same shape as the net-sightings read token (#2351).</p>
 */
public final class ChatInboundToken {

    /** Request header that carries the token. */
    public static final String HEADER = "X-Chat-Inbound-Token";
    /** Request attribute AuthFilter sets when the request was opened by this token. */
    public static final String ATTRIBUTE = "rvnkcore.chat.scopedInbound";

    private static volatile List<byte[]> hashes = List.of();

    private ChatInboundToken() { }

    /** Replaces the accepted hashes. Entries that are not 64 hex digits are skipped. */
    public static int configure(List<String> hexHashes) {
        HexFormat hex = HexFormat.of();
        hashes = hexHashes == null ? List.of() : hexHashes.stream()
                .filter(h -> h != null && h.matches("[0-9a-fA-F]{64}"))
                .map(h -> hex.parseHex(h.toLowerCase(java.util.Locale.ROOT)))
                .toList();
        return hashes.size();
    }

    /** True when the token's SHA-256 equals an accepted hash. Constant time per comparison. */
    public static boolean matches(String token) {
        List<byte[]> accepted = hashes;
        if (token == null || token.isEmpty() || accepted.isEmpty()) return false;
        byte[] digest;
        try {
            digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
        boolean ok = false;
        for (byte[] h : accepted) ok |= MessageDigest.isEqual(h, digest);
        return ok;
    }
}
