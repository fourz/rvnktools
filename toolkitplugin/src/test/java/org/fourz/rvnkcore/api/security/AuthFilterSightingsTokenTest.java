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
import org.fourz.rvnkcore.service.sightings.SightingsReadToken;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.EnumSet;
import java.util.HexFormat;
import java.util.List;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * {@code /v1/net-sightings} opens with the read token only (#2351): not with the shared API key, and
 * the token opens nothing else. The IP allowlist does not apply to it. Local embedded Jetty only.
 */
@DisplayName("AuthFilter sightings read token")
class AuthFilterSightingsTokenTest {

    private static final String KEY = "test-key-0123456789abcdef";
    private static final String TOKEN = "z-office-read-token-for-tests";
    private static Server server;
    private static int port;

    @BeforeAll
    static void start() throws Exception {
        SightingsReadToken.configure(List.of(HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(TOKEN.getBytes(StandardCharsets.UTF_8)))));
        ApiConfig config = mock(ApiConfig.class);
        when(config.getApiKey()).thenReturn(KEY);
        // An allowlist that admits nobody: the sightings path must still open with the token.
        when(config.getAllowedIPs()).thenReturn(new String[] {"203.0.113.250"});
        when(config.isRateLimitEnabled()).thenReturn(false);
        Plugin plugin = mock(Plugin.class);
        when(plugin.getName()).thenReturn("RVNKCore");
        when(plugin.getLogger()).thenReturn(Logger.getLogger("AuthFilterSightingsTokenTest"));

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
        context.addServlet(new ServletHolder(new Ok()), "/v1/net-sightings");   // as in ServletFactory
        context.addServlet(new ServletHolder(new Ok()), "/v1/players/*");
        server.setHandler(context);
        server.start();
        port = connector.getLocalPort();
    }

    @AfterAll
    static void stop() throws Exception {
        SightingsReadToken.configure(List.of());
        if (server != null) server.stop();
    }

    @Test
    void tokenOpensSightingsDespiteTheIpAllowlist() throws IOException {
        assertEquals(200, status("/api/v1/net-sightings?after=0", "X-Net-Sightings-Token", TOKEN));
    }

    @Test
    void apiKeyDoesNotOpenSightings() throws IOException {
        assertEquals(401, status("/api/v1/net-sightings", "X-API-Key", KEY));
        assertEquals(401, status("/api/v1/net-sightings", null, null));
        assertEquals(401, status("/api/v1/net-sightings", "X-Net-Sightings-Token", TOKEN + "x"));
    }

    @Test
    void tokenOpensNothingElse() throws IOException {
        assertEquals(401, status("/api/v1/players/abc", "X-Net-Sightings-Token", TOKEN));
        assertEquals(401, status("/api/v1/net-sightings/../players/abc", "X-Net-Sightings-Token", TOKEN));
    }

    @Test
    void noHashConfiguredRefusesEveryone() throws Exception {
        SightingsReadToken.configure(List.of());
        try {
            assertEquals(401, status("/api/v1/net-sightings", "X-Net-Sightings-Token", TOKEN));
            assertFalse(SightingsReadToken.matches(""));
        } finally {
            SightingsReadToken.configure(List.of(HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(TOKEN.getBytes(StandardCharsets.UTF_8)))));
        }
    }

    @Test
    void rotationAcceptsEitherHashAndSkipsJunk() throws Exception {
        String other = "second-token";
        int n = SightingsReadToken.configure(List.of(
                "not-a-hash",
                HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(TOKEN.getBytes(StandardCharsets.UTF_8))),
                HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(other.getBytes(StandardCharsets.UTF_8))).toUpperCase()));
        assertEquals(2, n);
        assertTrue(SightingsReadToken.matches(TOKEN));
        assertTrue(SightingsReadToken.matches(other));
        assertFalse(SightingsReadToken.matches("third"));
    }

    private static int status(String rawPath, String header, String value) throws IOException {
        try (Socket socket = new Socket("127.0.0.1", port)) {
            socket.setSoTimeout(5000);
            StringBuilder req = new StringBuilder().append("GET ").append(rawPath).append(" HTTP/1.1\r\n")
                    .append("Host: 127.0.0.1\r\nConnection: close\r\n");
            if (header != null) req.append(header).append(": ").append(value).append("\r\n");
            req.append("\r\n");
            OutputStream out = socket.getOutputStream();
            out.write(req.toString().getBytes(StandardCharsets.US_ASCII));
            out.flush();
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.US_ASCII));
            return Integer.parseInt(in.readLine().split(" ")[1]);
        }
    }

    private static final class Ok extends HttpServlet {
        @Override
        protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
            resp.setStatus(200);
            resp.getWriter().write("ok");
        }
    }
}
