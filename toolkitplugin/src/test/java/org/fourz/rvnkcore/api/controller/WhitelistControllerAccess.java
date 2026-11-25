package org.fourz.rvnkcore.api.controller;

import com.google.gson.Gson;
import jakarta.servlet.http.HttpServletResponse;

/** Test access to the whitelist controller's package-private helpers (#2448). */
public final class WhitelistControllerAccess {

    private WhitelistControllerAccess() { }

    public static void sendLookupError(HttpServletResponse out, Gson gson, Throwable cause) {
        WhitelistController.sendLookupError(out, gson, cause);
    }
}
