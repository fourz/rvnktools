package org.fourz.rvnkcore.service.sightings;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

/**
 * The read token for {@code /v1/net-sightings} (#2351). The server holds only SHA-256 hashes of
 * accepted tokens, never a token. AuthFilter asks {@link #matches}; config load and
 * {@code /rvnkcore reload} call {@link #configure}.
 */
public final class SightingsReadToken {

    /** Request header that carries the token. */
    public static final String HEADER = "X-Net-Sightings-Token";

    private static volatile List<byte[]> hashes = List.of();

    private SightingsReadToken() { }

    /** Replaces the accepted hashes. Entries that are not 64 hex digits are skipped. */
    public static int configure(List<String> hexHashes) {
        HexFormat hex = HexFormat.of();
        hashes = hexHashes.stream()
                .filter(h -> h != null && h.matches("[0-9a-fA-F]{64}"))
                .map(h -> hex.parseHex(h.toLowerCase(java.util.Locale.ROOT)))
                .toList();
        return hashes.size();
    }

    /** True when the token's SHA-256 equals an accepted hash. Constant time per comparison. */
    public static boolean matches(String token) {
        List<byte[]> accepted = hashes;
        if (token == null || token.isEmpty() || accepted.isEmpty()) return false;
        byte[] digest = sha256(token);
        boolean ok = false;
        for (byte[] h : accepted) ok |= MessageDigest.isEqual(h, digest);
        return ok;
    }

    static byte[] sha256(String token) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
