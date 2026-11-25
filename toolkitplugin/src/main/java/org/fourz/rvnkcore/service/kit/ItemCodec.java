package org.fourz.rvnkcore.service.kit;

import org.bukkit.inventory.ItemStack;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * Turns one {@link ItemStack} into bytes and back, with every data component.
 *
 * <p>RVNKCore compiles against spigot-api, but every tier runs Paper. {@link #PAPER} calls Paper's
 * {@code ItemStack#serializeAsBytes} / {@code ItemStack.deserializeBytes} by reflection: that is the
 * item's full component NBT with its data version, so Paper upgrades it on a later Minecraft. {@link
 * #BUKKIT} is the fallback for a Spigot server: Bukkit's {@code ConfigurationSerializable} stream. The
 * kit row names its codec, so a kit is always read with the codec that wrote it.</p>
 *
 * <p>Call both directions on the main thread: item (de)serialisation reads server registries.</p>
 */
public interface ItemCodec {

    String id();

    byte[] encode(ItemStack item);

    ItemStack decode(byte[] bytes);

    /** Paper's byte format, or null when this server is not Paper. */
    ItemCodec PAPER = Paper.create();

    ItemCodec BUKKIT = new ItemCodec() {
        @Override public String id() { return "bukkit"; }

        @Override public byte[] encode(ItemStack item) {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (BukkitObjectOutputStream out = new BukkitObjectOutputStream(bytes)) {
                out.writeObject(item);
            } catch (IOException e) {
                throw new IllegalStateException("Item encode failed: " + e.getMessage(), e);
            }
            return bytes.toByteArray();
        }

        @Override public ItemStack decode(byte[] bytes) {
            try (BukkitObjectInputStream in = new BukkitObjectInputStream(new ByteArrayInputStream(bytes))) {
                return (ItemStack) in.readObject();
            } catch (IOException | ClassNotFoundException | ClassCastException e) {
                throw new IllegalStateException("Item decode failed: " + e.getMessage(), e);
            }
        }
    };

    /** The codec new kits are saved with: Paper when present. */
    static ItemCodec best() {
        return PAPER != null ? PAPER : BUKKIT;
    }

    /** @throws IllegalArgumentException when the codec is unknown or not available on this server */
    static ItemCodec forId(String id) {
        if ("paper".equals(id)) {
            if (PAPER == null) throw new IllegalArgumentException("Kit was saved on Paper; this server is not Paper");
            return PAPER;
        }
        if ("bukkit".equals(id)) return BUKKIT;
        throw new IllegalArgumentException("Unknown item codec: " + id);
    }

    /** Reflection over Paper's item byte API. */
    final class Paper implements ItemCodec {
        private final Method serialize;
        private final Method deserialize;

        private Paper(Method serialize, Method deserialize) {
            this.serialize = serialize;
            this.deserialize = deserialize;
        }

        static ItemCodec create() {
            try {
                return new Paper(ItemStack.class.getMethod("serializeAsBytes"),
                        ItemStack.class.getMethod("deserializeBytes", byte[].class));
            } catch (NoSuchMethodException | SecurityException e) {
                return null;
            }
        }

        @Override public String id() { return "paper"; }

        @Override public byte[] encode(ItemStack item) {
            try {
                return (byte[]) serialize.invoke(item);
            } catch (IllegalAccessException | InvocationTargetException e) {
                throw new IllegalStateException("Item encode failed: " + rootMessage(e), e);
            }
        }

        @Override public ItemStack decode(byte[] bytes) {
            try {
                return (ItemStack) deserialize.invoke(null, (Object) bytes);
            } catch (IllegalAccessException | InvocationTargetException e) {
                throw new IllegalStateException("Item decode failed: " + rootMessage(e), e);
            }
        }

        private static String rootMessage(Throwable e) {
            Throwable t = e;
            while (t.getCause() != null) t = t.getCause();
            return t.getClass().getSimpleName() + ": " + t.getMessage();
        }
    }
}
