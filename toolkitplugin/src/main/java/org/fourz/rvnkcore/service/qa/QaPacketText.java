package org.fourz.rvnkcore.service.qa;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.RecordComponent;
import java.util.Map;
import java.util.function.Predicate;

/**
 * Plain text out of an outgoing vanilla packet (#2359), by reflection only, so RVNKCore keeps
 * compiling against spigot-api. Paper runs Mojang-mapped, so the names below are the runtime names.
 *
 * <p>No library types here: the BKCommonLib adapter hands over the raw packet object. Any failure
 * returns null; the caller counts it and moves on, and a packet is never changed.</p>
 */
public final class QaPacketText {

    /** The vanilla chat component interface; every text field in these packets has this type. */
    static final String COMPONENT = "net.minecraft.network.chat.Component";

    /** Packet simple name to line kind. Player chat is left out: public chat is already in /v1/chat/recent. */
    public static final Map<String, String> KINDS = Map.of(
            "ClientboundSystemChatPacket", "chat",
            "ClientboundDisguisedChatPacket", "chat",
            "ClientboundSetTitleTextPacket", "title",
            "ClientboundSetSubtitleTextPacket", "subtitle",
            "ClientboundSetActionBarTextPacket", "actionbar");

    private QaPacketText() {
    }

    /** The line kind for this packet, or null when it is not one the tap records. */
    public static String kindOf(Object packet) {
        if (packet == null) return null;
        String kind = KINDS.get(packet.getClass().getSimpleName());
        // A system chat packet with overlay=true is drawn above the hotbar, not in the chat box.
        if ("chat".equals(kind) && Boolean.TRUE.equals(booleanComponent(packet, "overlay"))) return "actionbar";
        return kind;
    }

    /** The packet's text, or null. */
    public static String textOf(Object packet) {
        return textOf(packet, QaPacketText::isComponentType);
    }

    /** As {@link #textOf(Object)}, with the component test injectable for unit tests. */
    static String textOf(Object packet, Predicate<Class<?>> isComponent) {
        if (packet == null) return null;
        try {
            Class<?> type = packet.getClass();
            if (type.isRecord()) {
                for (RecordComponent rc : type.getRecordComponents()) {
                    if (!isComponent.test(rc.getType())) continue;
                    Method accessor = rc.getAccessor();
                    accessor.setAccessible(true);
                    String text = flatten(accessor.invoke(packet));
                    if (text != null) return text;
                }
                return null;
            }
            for (Field f : type.getDeclaredFields()) {
                if (Modifier.isStatic(f.getModifiers()) || !isComponent.test(f.getType())) continue;
                f.setAccessible(true);
                String text = flatten(f.get(packet));
                if (text != null) return text;
            }
            return null;
        } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
    }

    /** {@code component.getString()}: the vanilla flattening to plain text, colours and styles dropped. */
    static String flatten(Object component) throws ReflectiveOperationException {
        if (component == null) return null;
        Method getString = component.getClass().getMethod("getString");
        getString.setAccessible(true);
        Object text = getString.invoke(component);
        return text instanceof String s ? s : null;
    }

    static boolean isComponentType(Class<?> type) {
        if (type == null) return false;
        if (type.getName().equals(COMPONENT)) return true;
        for (Class<?> i : type.getInterfaces()) {
            if (i.getName().equals(COMPONENT)) return true;
        }
        return false;
    }

    private static Boolean booleanComponent(Object packet, String name) {
        try {
            if (!packet.getClass().isRecord()) return null;
            for (RecordComponent rc : packet.getClass().getRecordComponents()) {
                if (rc.getName().equals(name) && rc.getType() == boolean.class) {
                    Method accessor = rc.getAccessor();
                    accessor.setAccessible(true);
                    return (Boolean) accessor.invoke(packet);
                }
            }
        } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
        return null;
    }
}
