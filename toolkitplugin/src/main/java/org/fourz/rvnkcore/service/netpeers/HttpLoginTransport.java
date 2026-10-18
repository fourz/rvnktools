package org.fourz.rvnkcore.service.netpeers;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.time.Duration;
import java.util.Collection;

/**
 * POSTs a login event to z-office (#2351). TLS verification stays on. With {@code ca-cert} set, the
 * client trusts that CA only (z-office has a certificate from the internal CA); without it, the JVM
 * truststore applies. Hostname verification is the HttpClient default.
 */
public class HttpLoginTransport implements LoginEventReporter.Transport {

    static final String SECRET_HEADER = "X-Net-Peers-Event-Secret";

    private final HttpClient client;
    private final URI uri;
    private final String secret;
    private final Duration timeout;

    /**
     * @param caCert resolved CA file, or null for the JVM truststore
     * @throws GeneralSecurityException when the CA file holds no usable certificate
     */
    public HttpLoginTransport(NetPeersConfig config, Path caCert) throws IOException, GeneralSecurityException {
        this.uri = URI.create(config.url());
        this.secret = config.secret();
        this.timeout = Duration.ofMillis(config.timeoutMs());
        HttpClient.Builder b = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .followRedirects(HttpClient.Redirect.NEVER)
                .connectTimeout(timeout);
        if (caCert != null) b.sslContext(trusting(caCert));
        this.client = b.build();
    }

    @Override
    public LoginEventReporter.Response post(String json) throws IOException {
        HttpRequest req = HttpRequest.newBuilder(uri)
                .timeout(timeout)
                .header("Content-Type", "application/json")
                .header(SECRET_HEADER, secret)
                .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                .build();
        try {
            HttpResponse<String> res = client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            return new LoginEventReporter.Response(res.statusCode(), res.body());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("interrupted", e);
        }
    }

    static SSLContext trusting(Path pem) throws IOException, GeneralSecurityException {
        Collection<? extends Certificate> certs;
        try (InputStream in = Files.newInputStream(pem)) {
            certs = CertificateFactory.getInstance("X.509").generateCertificates(in);
        }
        if (certs.isEmpty()) throw new GeneralSecurityException("no certificate in " + pem.getFileName());
        KeyStore ks = KeyStore.getInstance(KeyStore.getDefaultType());
        ks.load(null, null);
        int i = 0;
        for (Certificate c : certs) ks.setCertificateEntry("net-peers-ca-" + i++, c);
        TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        tmf.init(ks);
        SSLContext ctx = SSLContext.getInstance("TLS");
        ctx.init(null, tmf.getTrustManagers(), null);
        return ctx;
    }
}
