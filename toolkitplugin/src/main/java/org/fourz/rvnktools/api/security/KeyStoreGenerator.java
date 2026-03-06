package org.fourz.rvnktools.api.security;

import java.io.File;
import java.io.FileOutputStream;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.cert.X509Certificate;
import java.security.cert.Certificate;
import java.math.BigInteger;
import java.util.Date;
import java.util.Calendar;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import java.security.SecureRandom;

public class KeyStoreGenerator {
    /**
     * Generates a self-signed keystore for localhost only (legacy).
     */
    public static void generateKeyStore(String keystorePath, String keystorePassword, String keyAlias) throws Exception {
        generateKeyStore(keystorePath, keystorePassword, keyAlias, new String[0]);
    }

    /**
     * Generates a self-signed keystore with optional extra hostnames in the SAN list.
     *
     * @param keystorePath      Path to write the JKS keystore
     * @param keystorePassword  Keystore password
     * @param keyAlias          Key alias inside the store
     * @param extraHostnames    Additional DNS names to include as SANs (e.g. internal hostnames)
     */
    public static void generateKeyStore(String keystorePath, String keystorePassword, String keyAlias, String[] extraHostnames) throws Exception {
        // Generate key pair
        KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");
        keyPairGenerator.initialize(2048);
        KeyPair keyPair = keyPairGenerator.generateKeyPair();

        // Generate self-signed certificate with SAN extensions
        X500Name dnName = new X500Name("CN=localhost, O=RVNKCore, OU=API Server");
        BigInteger certSerialNumber = BigInteger.valueOf(System.currentTimeMillis());

        Calendar calendar = Calendar.getInstance();
        Date startDate = calendar.getTime();
        calendar.add(Calendar.YEAR, 1);
        Date endDate = calendar.getTime();

        X509v3CertificateBuilder certBuilder = new JcaX509v3CertificateBuilder(
            dnName, certSerialNumber, startDate, endDate, dnName, keyPair.getPublic());

        // Add Subject Alternative Names for better compatibility
        try {
            // Build SAN list: localhost + loopback IPs + any extra hostnames
            java.util.List<org.bouncycastle.asn1.x509.GeneralName> sanList = new java.util.ArrayList<>();
            sanList.add(new org.bouncycastle.asn1.x509.GeneralName(org.bouncycastle.asn1.x509.GeneralName.dNSName, "localhost"));
            sanList.add(new org.bouncycastle.asn1.x509.GeneralName(org.bouncycastle.asn1.x509.GeneralName.iPAddress, "127.0.0.1"));
            sanList.add(new org.bouncycastle.asn1.x509.GeneralName(org.bouncycastle.asn1.x509.GeneralName.iPAddress, "::1"));
            for (String hostname : extraHostnames) {
                if (hostname != null && !hostname.trim().isEmpty() && !"localhost".equals(hostname.trim())) {
                    sanList.add(new org.bouncycastle.asn1.x509.GeneralName(org.bouncycastle.asn1.x509.GeneralName.dNSName, hostname.trim()));
                }
            }
            org.bouncycastle.asn1.x509.GeneralNames subjectAltNames = new org.bouncycastle.asn1.x509.GeneralNames(
                sanList.toArray(new org.bouncycastle.asn1.x509.GeneralName[0])
            );
            certBuilder.addExtension(org.bouncycastle.asn1.x509.Extension.subjectAlternativeName, false, subjectAltNames);
        } catch (Exception sanEx) {
            // SAN extension is optional, continue without it
            System.err.println("Warning: Could not add Subject Alternative Names to certificate: " + sanEx.getMessage());
        }

        ContentSigner contentSigner = new JcaContentSignerBuilder("SHA256WithRSA")
            .build(keyPair.getPrivate());

        X509Certificate cert = new JcaX509CertificateConverter()
            .getCertificate(certBuilder.build(contentSigner));

        // Create keystore and store keys
        KeyStore keyStore = KeyStore.getInstance("JKS");
        keyStore.load(null, null);
        keyStore.setKeyEntry(keyAlias, keyPair.getPrivate(), 
            keystorePassword.toCharArray(), new Certificate[]{cert});

        // Save to file
        try (FileOutputStream fos = new FileOutputStream(keystorePath)) {
            keyStore.store(fos, keystorePassword.toCharArray());
        }
    }

    public static String generateSecurePassword() {
        SecureRandom random = new SecureRandom();
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return bytesToHex(bytes);
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder result = new StringBuilder();
        for (byte b : bytes) {
            result.append(String.format("%02x", b));
        }
        return result.toString();
    }
}
