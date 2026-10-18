package org.fourz.rvnkcore.service.netpeers;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpServer;
import org.fourz.rvnkcore.service.netpeers.LoginEventReporter.Outcome;
import org.fourz.rvnkcore.service.netpeers.LoginEventReporter.Response;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/** z-office login events (#2351). */
@DisplayName("LoginEventReporter")
class LoginEventReporterTest {

    private static final String SECRET = "s3cret-value-never-logged";
    private static final UUID LINKED = UUID.fromString("57d37338-7c06-458c-8cbe-6d9abff5e9af");

    private final List<String> logs = new CopyOnWriteArrayList<>();
    private final LoginEventReporter.Log log = new LoginEventReporter.Log() {
        @Override public void info(String m) { logs.add("INFO " + m); }
        @Override public void warning(String m) { logs.add("WARN " + m); }
        @Override public void debug(String m) { logs.add("DEBUG " + m); }
    };
    private LoginEventReporter reporter;

    @AfterEach
    void stop() {
        if (reporter != null) reporter.shutdown();
    }

    private static NetPeersConfig config(boolean enabled, String secret, Set<String> ignore) {
        return new NetPeersConfig(enabled, NetPeersConfig.DEFAULT_URL, secret, 4000, "", ignore);
    }

    /** Replies from a script; an IOException entry is thrown. */
    private static final class Scripted implements LoginEventReporter.Transport {
        final Deque<Object> replies = new ArrayDeque<>();
        final List<String> bodies = new CopyOnWriteArrayList<>();
        Scripted then(Object reply) { replies.add(reply); return this; }
        @Override public Response post(String json) throws IOException {
            bodies.add(json);
            Object r = replies.isEmpty() ? new Response(200, "{}") : replies.poll();
            if (r instanceof IOException e) throw e;
            return (Response) r;
        }
    }

    private static InetAddress ip(String s) throws Exception {
        return InetAddress.getByName(s);
    }

    // ── public address rule ──

    @Test
    void onlyPublicIpv4IsSent() throws Exception {
        assertEquals("8.8.8.8", PublicIpv4.of(ip("8.8.8.8")).orElseThrow());
        assertEquals("172.32.0.1", PublicIpv4.of(ip("172.32.0.1")).orElseThrow());
        for (String a : List.of("10.70.10.102", "192.168.1.5", "172.16.0.1", "172.31.255.255", "100.64.0.1",
                "127.0.0.1", "169.254.1.1", "0.0.0.0", "203.0.113.9", "224.0.0.1", "255.255.255.255")) {
            assertTrue(PublicIpv4.of(ip(a)).isEmpty(), a);
        }
        assertTrue(PublicIpv4.of(ip("2001:4860:4860::8888")).isEmpty(), "IPv6 is out of scope");
        assertTrue(PublicIpv4.of(null).isEmpty());
    }

    // ── what is sent ──

    @Test
    void sendsTheContractBody() throws Exception {
        Scripted t = new Scripted().then(new Response(200, "{\"ok\":true,\"granted\":true,\"action\":\"add\"}"));
        reporter = new LoginEventReporter(config(true, SECRET, Set.of()), t, log);
        assertTrue(reporter.report(LINKED, "wizardoffice", ip("8.8.4.4")));
        waitFor(() -> logs.stream().anyMatch(l -> l.contains("granted, action=add")));

        JsonObject body = JsonParser.parseString(t.bodies.get(0)).getAsJsonObject();
        assertEquals("minecraft", body.get("kind").getAsString());
        assertEquals(LINKED.toString(), body.get("uuid").getAsString());
        assertEquals("wizardoffice", body.get("name").getAsString());
        assertEquals("8.8.4.4", body.get("address").getAsString());
        assertFalse(t.bodies.get(0).contains(SECRET));
    }

    @Test
    void nothingSentWhenInactiveOrAddressNotUsable() throws Exception {
        Scripted t = new Scripted();
        reporter = new LoginEventReporter(config(false, SECRET, Set.of()), t, log);
        assertFalse(reporter.report(LINKED, "a", ip("8.8.8.8")), "disabled");
        reporter.shutdown();

        reporter = new LoginEventReporter(config(true, "", Set.of()), t, log);
        assertFalse(reporter.report(LINKED, "a", ip("8.8.8.8")), "no secret");
        reporter.shutdown();

        reporter = new LoginEventReporter(config(true, SECRET, Set.of("8.8.8.8")), t, log);
        assertFalse(reporter.report(LINKED, "a", ip("192.168.1.20")), "LAN address");
        assertFalse(reporter.report(LINKED, "a", null), "unknown address");
        assertFalse(reporter.report(LINKED, "a", ip("8.8.8.8")), "proxy address on the ignore list");
        assertTrue(t.bodies.isEmpty());
    }

    // ── outcomes ──

    @Test
    void outcomesByStatus() {
        reporter = new LoginEventReporter(config(true, SECRET, Set.of()), new Scripted()
                .then(new Response(202, "{\"ok\":true,\"granted\":false,\"reason\":\"not linked\"}"))
                .then(new Response(400, "{\"error\":\"address is not a public IPv4\"}"))
                .then(new Response(401, "{\"error\":\"unauthorized\"}")), log);
        assertEquals(Outcome.NOT_LINKED, reporter.send("a", "{}"));
        assertTrue(logs.stream().anyMatch(l -> l.contains("reason=not linked")));
        assertEquals(Outcome.REJECTED, reporter.send("a", "{}"));
        assertEquals(Outcome.UNAUTHORIZED, reporter.send("a", "{}"));
        assertTrue(logs.stream().anyMatch(l -> l.startsWith("WARN") && l.contains("401")));
        assertTrue(logs.stream().noneMatch(l -> l.contains(SECRET)), "the secret is never logged");
    }

    @Test
    void retriesOnceOnFailureThenGivesUp() {
        Scripted t = new Scripted().then(new SocketTimeoutException("timed out")).then(new SocketTimeoutException("timed out"))
                .then(new Response(200, "{}"));
        reporter = new LoginEventReporter(config(true, SECRET, Set.of()), t, log);
        assertEquals(Outcome.FAILED, reporter.send("a", "{}"));
        assertEquals(2, t.bodies.size(), "one try plus one retry, no more");
    }

    @Test
    void retryAfter503CanSucceed() {
        Scripted t = new Scripted().then(new Response(503, "{}")).then(new Response(200, "{\"action\":\"renew\"}"));
        reporter = new LoginEventReporter(config(true, SECRET, Set.of()), t, log);
        assertEquals(Outcome.GRANTED, reporter.send("a", "{}"));
        assertEquals(2, t.bodies.size());
    }

    @Test
    void noRetryOnClientError() {
        Scripted t = new Scripted().then(new Response(400, "{}"));
        reporter = new LoginEventReporter(config(true, SECRET, Set.of()), t, log);
        assertEquals(Outcome.REJECTED, reporter.send("a", "{}"));
        assertEquals(1, t.bodies.size());
    }

    // ── never blocks a join, no growing queue ──

    @Test
    void reportReturnsAtOnceAndQueueIsBounded() throws Exception {
        CountDownLatch release = new CountDownLatch(1);
        List<String> sent = new CopyOnWriteArrayList<>();
        LoginEventReporter.Transport hung = json -> {
            try {
                release.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            sent.add(json);
            return new Response(200, "{}");
        };
        reporter = new LoginEventReporter(config(true, SECRET, Set.of()), hung, log);
        long start = System.nanoTime();
        for (int i = 0; i < 40; i++) reporter.report(UUID.randomUUID(), "p" + i, ip("8.8.8.8"));
        long ms = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
        assertTrue(ms < 500, "40 reports took " + ms + " ms while z-office hung");
        assertTrue(logs.stream().anyMatch(l -> l.contains("queue full")));
        release.countDown();
        waitFor(() -> sent.size() >= 1 + LoginEventReporter.QUEUE_CAPACITY);
        Thread.sleep(100);
        assertEquals(1 + LoginEventReporter.QUEUE_CAPACITY, sent.size(), "one in flight plus the queue; the rest dropped");
    }

    // ── the real transport ──

    @Test
    void httpTransportSendsSecretHeaderAndJson() throws Exception {
        AtomicReference<String> header = new AtomicReference<>();
        AtomicReference<String> body = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/net/peers/login-event", ex -> {
            header.set(ex.getRequestHeaders().getFirst(HttpLoginTransport.SECRET_HEADER));
            body.set(new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] out = "{\"ok\":true,\"granted\":false,\"reason\":\"not linked\"}".getBytes(StandardCharsets.UTF_8);
            ex.sendResponseHeaders(202, out.length);
            ex.getResponseBody().write(out);
            ex.close();
        });
        server.start();
        try {
            String url = "http://127.0.0.1:" + server.getAddress().getPort() + "/api/net/peers/login-event";
            HttpLoginTransport t = new HttpLoginTransport(
                    new NetPeersConfig(true, url, SECRET, 2000, "", Set.of()), null);
            Response r = t.post("{\"kind\":\"minecraft\"}");
            assertEquals(202, r.status());
            assertEquals(SECRET, header.get());
            assertEquals("{\"kind\":\"minecraft\"}", body.get());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void caFileWithoutCertificateIsRefused() throws Exception {
        Path empty = Files.createTempFile("netpeers-ca", ".pem");
        try {
            assertThrows(GeneralSecurityException.class, () -> HttpLoginTransport.trusting(empty));
        } finally {
            Files.deleteIfExists(empty);
        }
    }

    private static void waitFor(java.util.function.BooleanSupplier cond) throws InterruptedException {
        long end = System.currentTimeMillis() + 3000;
        while (!cond.getAsBoolean() && System.currentTimeMillis() < end) Thread.sleep(20);
        assertTrue(cond.getAsBoolean(), "condition not met in 3 s");
    }
}
