package org.fourz.rvnkcore.service.qa.bkc;

import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.fourz.rvnkcore.service.qa.QaPacketText;
import org.fourz.rvnkcore.service.qa.QaTapService;
import org.fourz.rvnkcore.util.log.LogManager;

/**
 * The BKCommonLib side of the QA tap (#2359), by reflection through BKCommonLib's own class loader.
 *
 * <p>There is no compile dependency: BKCommonLib's published pom imports a snapshot BOM that no
 * repository serves, so it cannot be a Maven dependency. Everything here is looked up at runtime, and
 * this class is reached from {@link QaTapService#install} only after BKCommonLib is confirmed enabled.</p>
 *
 * <ul>
 *   <li><b>Packet types.</b> BKCommonLib names no constants for the chat and title packets. Each is made
 *       through {@code PacketType}'s protected {@code (String name, Class<?> type)} constructor with a
 *       null class: it resolves {@code net.minecraft.network.protocol.game.<name>} and registers the type
 *       in BKCommonLib's class lookup, which is what routes a sent packet to its monitors.</li>
 *   <li><b>Monitor.</b> A {@link Proxy} implements {@code PacketMonitor}. A monitor observes a packet after
 *       the send decision and cannot change or drop it.</li>
 * </ul>
 */
public final class BkcQaPacketTap {

    static final String PKG = "com.bergerkiller.bukkit.common.";
    static final String PACKET_TYPE = PKG + "protocol.PacketType";
    static final String PACKET_MONITOR = PKG + "protocol.PacketMonitor";
    static final String COMMON_PACKET = PKG + "protocol.CommonPacket";
    static final String PACKET_UTIL = PKG + "utils.PacketUtil";

    private BkcQaPacketTap() {
    }

    /**
     * Registers the monitor for every packet in {@link QaPacketText#KINDS} that resolves on this server;
     * returns the uninstall action.
     *
     * @throws IllegalStateException when BKCommonLib's API is not as expected, or no packet type resolves
     */
    public static Runnable install(Plugin plugin, QaTapService service, LogManager logger) {
        Plugin bkc = Bukkit.getPluginManager().getPlugin("BKCommonLib");
        if (bkc == null) throw new IllegalStateException("BKCommonLib is not loaded");
        ClassLoader loader = bkc.getClass().getClassLoader();
        try {
            Class<?> packetType = Class.forName(PACKET_TYPE, true, loader);
            Class<?> monitorType = Class.forName(PACKET_MONITOR, true, loader);
            Class<?> commonPacket = Class.forName(COMMON_PACKET, true, loader);
            Class<?> packetUtil = Class.forName(PACKET_UTIL, true, loader);

            Constructor<?> make = packetType.getDeclaredConstructor(String.class, Class.class);
            make.setAccessible(true);
            Method typeClass = packetType.getMethod("getType");
            List<Object> types = new ArrayList<>();
            for (String name : QaPacketText.KINDS.keySet()) {
                try {
                    Object type = make.newInstance(name, null);
                    if (typeClass.invoke(type) == null) {
                        logger.warning("QA inbox: packet " + name + " not found on this server - not recorded");
                        continue;
                    }
                    types.add(type);
                } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
                    logger.warning("QA inbox: packet " + name + " could not be declared - not recorded: " + e);
                }
            }
            if (types.isEmpty()) throw new IllegalStateException("no QA packet type resolved");

            Method getHandle = commonPacket.getMethod("getHandle");
            InvocationHandler handler = (proxy, method, args) -> switch (method.getName()) {
                case "onMonitorPacketSend" -> {
                    if (args != null && args.length == 2 && args[0] != null && args[1] instanceof Player p) {
                        service.onPacket(p.getUniqueId(), getHandle.invoke(args[0]));
                    }
                    yield null;
                }
                case "onMonitorPacketReceive" -> null; // incoming packets are not recorded
                case "hashCode" -> System.identityHashCode(proxy);
                case "equals" -> proxy == args[0];
                case "toString" -> "RVNKCore QA packet monitor";
                default -> null;
            };
            Object monitor = Proxy.newProxyInstance(loader, new Class<?>[] {monitorType}, handler);

            Object typeArray = Array.newInstance(packetType, types.size());
            for (int i = 0; i < types.size(); i++) Array.set(typeArray, i, types.get(i));
            Method add = packetUtil.getMethod("addPacketMonitor", Plugin.class, monitorType, typeArray.getClass());
            add.invoke(null, plugin, monitor, typeArray);
            Method remove = packetUtil.getMethod("removePacketMonitor", monitorType);
            logger.debug("QA inbox: monitoring " + types.size() + " packet type(s) through BKCommonLib");
            return () -> {
                try {
                    remove.invoke(null, monitor);
                } catch (ReflectiveOperationException e) {
                    // BKCommonLib may already be disabled at shutdown.
                }
            };
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("BKCommonLib packet API not as expected: " + e, e);
        }
    }
}
