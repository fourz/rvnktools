package org.fourz.rvnkcore.api.security;

import com.google.gson.Gson;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.bukkit.plugin.Plugin;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.servlet.FilterHolder;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.fourz.rvnkcore.api.config.ApiConfig;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.EnumSet;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * The public health exemption must not open any other endpoint. It used to match a substring of
 * the raw request URI, while Jetty routes on the normalised path, so a path that merely contained
 * "/v1/health" could reach a protected servlet with no API key.
 *
 * <p>Local only: an embedded Jetty on a loopback ephemeral port, requests sent as raw request lines
 * so dot-segments reach the server unaltered.</p>
 */
@DisplayName("AuthFilter health exemption")
class AuthFilterHealthBypassTest {

    private static final String KEY = "test-key-0123456789abcdef";
    private static Server server;
    private static int port;

    @BeforeAll
    static void start() throws Exception {
        ApiConfig config = mock(ApiConfig.class);
        when(config.getApiKey()).thenReturn(KEY);
        when(config.getAllowedIPs()).thenReturn(null);
        when(config.isRateLimitEnabled()).thenReturn(false);
        Plugin plugin = mock(Plugin.class);
        when(plugin.getName()).thenReturn("RVNKCore");
        when(plugin.getLogger()).thenReturn(Logger.getLogger("AuthFilterHealthBypassTest"));

        server = new Server();
        ServerConnector connector = new ServerConnector(server);
        connector.setHost("127.0.0.1");
        connector.setPort(0);
        server.addConnector(connector);

        ServletContextHandler context = new ServletContextHandler();
        context.setContextPath("/api");
        FilterHolder auth = new FilterHolder(new AuthFilter(config, plugin, new Gson()));
        for (String pattern : AuthPathPatterns.BLANKET_PATTERNS) {
            context.addFilter(auth, pattern, EnumSet.of(jakarta.servlet.DispatcherType.REQUEST));
        }
        context.addServlet(new ServletHolder(new Echo("HEALTH")), "/v1/health/*");  // as in ServletFactory
        context.addServlet(new ServletHolder(new Echo("PLAYERS")), "/v1/players/*");
        context.addServlet(new ServletHolder(new Echo("SHOPS")), "/bartershops/*");
        server.setHandler(context);
        server.start();
        port = connector.getLocalPort();
    }

    @AfterAll
    static void stop() throws Exception {
        if (server != null) server.stop();
    }

    @Test
    @DisplayName("Health stays public")
    void healthIsPublic() throws IOException {
        assertEquals(200, status("GET", "/api/v1/health", null));
    }

    @Test
    @DisplayName("Health sub-paths stay public; writes to health are not exempt")
    void healthSubPathsAndMethods() throws IOException {
        assertEquals(200, status("GET", "/api/v1/health/detailed", null));
        assertEquals(401, status("POST", "/api/v1/health", null));
    }

    @Test
    @DisplayName("A protected endpoint needs the key (control)")
    void protectedNeedsKey() throws IOException {
        assertEquals(401, status("GET", "/api/v1/players/abc", null));
        assertEquals(200, status("GET", "/api/v1/players/abc", KEY));
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {
            "/api/v1/health/../players/abc",
            "/api/v1/health/../players/abc/groups",
            "/api/v1/players/abc/v1/health",
            "/api/v1/players/abc/v1/health/",
            "/api/v1/players/abc;/v1/health",
            "/api/bartershops/shops/v1/health",
            "/api/v1/health/../../bartershops/shops",
    })
    @DisplayName("A path that only contains the health text is not exempt")
    void healthTextDoesNotOpenOtherEndpoints(String path) throws IOException {
        int code = status("GET", path, null);
        assertTrue(code == 401 || code == 400 || code == 404,
                "unauthenticated request to " + path + " was served with HTTP " + code);
    }

    private static int status(String method, String rawPath, String key) throws IOException {
        try (Socket socket = new Socket("127.0.0.1", port)) {
            socket.setSoTimeout(5000);
            StringBuilder req = new StringBuilder()
                    .append(method).append(' ').append(rawPath).append(" HTTP/1.1\r\n")
                    .append("Host: 127.0.0.1\r\n")
                    .append("Connection: close\r\n");
            if (key != null) req.append("X-API-Key: ").append(key).append("\r\n");
            req.append("\r\n");
            OutputStream out = socket.getOutputStream();
            out.write(req.toString().getBytes(StandardCharsets.US_ASCII));
            out.flush();
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.US_ASCII));
            String statusLine = in.readLine();
            return Integer.parseInt(statusLine.split(" ")[1]);
        }
    }

    private static final class Echo extends HttpServlet {
        private final String name;
        Echo(String name) { this.name = name; }
        @Override
        protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
            resp.setStatus(200);
            resp.getWriter().write(name + " " + req.getPathInfo());
        }
    }
}
