package org.fourz.rvnkcore.service.kit;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Base64;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * The stored form of a kit's items: one Base64 text holding every slot's encoded item.
 *
 * <p>Layout: magic {@code RVK1}, a slot count, then per slot a slot byte, a length and the bytes the
 * {@link ItemCodec} produced. The envelope knows nothing about items, so it is tested without a
 * server. Text, not a BLOB, so MySQL and SQLite store it the same way.</p>
 */
public final class KitPayload {

    private static final int MAGIC = 0x52564B31; // "RVK1"
    /** Guards a corrupt length from allocating a huge array. */
    static final int MAX_ITEM_BYTES = 4 * 1024 * 1024;

    private KitPayload() { }

    public static String encode(Map<Integer, byte[]> items) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (DataOutputStream out = new DataOutputStream(bytes)) {
                out.writeInt(MAGIC);
                out.writeShort(items.size());
                for (Map.Entry<Integer, byte[]> e : new TreeMap<>(items).entrySet()) {
                    if (!KitSlots.valid(e.getKey())) throw new IllegalArgumentException("Invalid kit slot: " + e.getKey());
                    out.writeByte(e.getKey());
                    out.writeInt(e.getValue().length);
                    out.write(e.getValue());
                }
            }
            return Base64.getEncoder().encodeToString(bytes.toByteArray());
        } catch (IOException e) {
            throw new IllegalStateException("Kit payload encode failed", e); // in-memory stream: not expected
        }
    }

    /** @throws IllegalArgumentException when the text is not a kit payload */
    public static SortedMap<Integer, byte[]> decode(String payload) {
        SortedMap<Integer, byte[]> items = new TreeMap<>();
        if (payload == null || payload.isEmpty()) return items;
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(Base64.getDecoder().decode(payload)))) {
            if (in.readInt() != MAGIC) throw new IllegalArgumentException("Not a kit payload");
            int count = in.readUnsignedShort();
            for (int i = 0; i < count; i++) {
                int slot = in.readUnsignedByte();
                int len = in.readInt();
                if (!KitSlots.valid(slot) || len < 0 || len > MAX_ITEM_BYTES) {
                    throw new IllegalArgumentException("Corrupt kit payload at entry " + i);
                }
                byte[] item = new byte[len];
                in.readFully(item);
                items.put(slot, item);
            }
            return items;
        } catch (IOException e) {
            throw new IllegalArgumentException("Truncated kit payload", e);
        }
    }
}
