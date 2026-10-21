package org.fourz.rvnkcore.api.controller;

import org.fourz.rvnkcore.service.sightings.SightingStore;

import java.util.Map;

/** Test access to the controller's package-private helpers. */
public final class NetSightingsControllerAccess {

    private NetSightingsControllerAccess() { }

    public static Map<String, Object> body(String server, long after, SightingStore.Page p) {
        return NetSightingsController.body(server, after, p);
    }

    public static String json(com.google.gson.Gson gson, Map<String, Object> data) {
        return NetSightingsController.json(gson, data);
    }

    public static long parseCursor(String raw) {
        return NetSightingsController.parseCursor(raw);
    }
}
