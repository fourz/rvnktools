package org.fourz.rvnktools.api.security;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.KeyFactory;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Collection;
import org.fourz.rvnkcore.util.log.LogManager;
import java.io.File;

public class KeyStoreImporter {

    private static X509Certificate[] loadCertificateChain(String chainPath, LogManager logger) {
        if (chainPath == null || !Files.exists(Paths.get(chainPath))) {
            logger.info("No chain file specified or found, continuing with single certificate");
            return null;
        }

        try {
            CertificateFactory cf = CertificateFactory.getInstance("X.509");
            Collection<? extends Certificate> chain = cf.generateCertificates(Files.newInputStream(Paths.get(chainPath)));
            return chain.stream()
                       .map(cert -> (X509Certificate) cert)
                       .toArray(X509Certificate[]::new);
        } catch (CertificateException e) {
            logger.error("Invalid certificate chain format: " + e.getMessage());
        } catch (IOException e) {
            logger.error("Failed to read certificate chain file: " + e.getMessage());
        }
        return null;
    }

    public static boolean importKeyStore(String certPath, String keyPath, String chainPath,
                                       String keystorePath, String keystorePassword,
                                       String keyManagerPassword, LogManager logger) {
        if (!validateInputs(certPath, keyPath, keystorePath, keystorePassword, keyManagerPassword, logger)) {
            return false;
        }

        try {
            // Load certificate and validate
            X509Certificate cert = loadCertificate(certPath, logger);
            if (cert == null) return false;
            cert.checkValidity();

            // Load private key
            PrivateKey privateKey = loadPrivateKey(keyPath, logger);
            if (privateKey == null) return false;

            // Load certificate chain if available
            X509Certificate[] chain = loadCertificateChain(chainPath, logger);

            // Create or load keystore
            KeyStore keyStore = createOrLoadKeystore(keystorePath, keystorePassword, logger);
            if (keyStore == null) return false;

            // Prepare full certificate chain
            X509Certificate[] fullChain;
            if (chain != null && chain.length > 0) {
                fullChain = new X509Certificate[chain.length + 1];
                fullChain[0] = cert;
                System.arraycopy(chain, 0, fullChain, 1, chain.length);
                logger.info("Using certificate chain with " + fullChain.length + " certificates");
            } else {
                fullChain = new X509Certificate[]{cert};
                logger.info("Using single certificate without chain");
            }

            // Store key and certificate chain
            keyStore.setKeyEntry("importedKey", privateKey, keyManagerPassword.toCharArray(), fullChain);

            // Save keystore
            saveKeystore(keyStore, keystorePath, keystorePassword, logger);
            logger.info("Successfully imported certificate and chain into keystore");
            return true;

        } catch (CertificateException e) {
            logger.error("Certificate validation failed: " + e.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error during import: " + e.getMessage());
        }

        return false;
    }

    private static boolean validateInputs(String certPath, String keyPath, String keystorePath,
                                        String keystorePassword, String keyManagerPassword, LogManager logger) {
        if (certPath == null || keyPath == null || keystorePath == null ||
            keystorePassword == null || keyManagerPassword == null) {
            logger.error("One or more required parameters are null");
            return false;
        }

        if (!Files.exists(Paths.get(certPath))) {
            logger.error("Certificate file not found: " + certPath);
            return false;
        }

        if (!Files.exists(Paths.get(keyPath))) {
            logger.error("Private key file not found: " + keyPath);
            return false;
        }

        File keystoreFile = new File(keystorePath);
        if (!keystoreFile.exists()) {
            File parentDir = keystoreFile.getParentFile();
            if (parentDir != null && !parentDir.exists() && !parentDir.mkdirs()) {
                logger.error("Failed to create directory for keystore: " + parentDir);
                return false;
            }
        }

        return true;
    }

    private static X509Certificate loadCertificate(String certPath, LogManager logger) {
        try {
            CertificateFactory cf = CertificateFactory.getInstance("X.509");
            return (X509Certificate) cf.generateCertificate(Files.newInputStream(Paths.get(certPath)));
        } catch (CertificateException e) {
            logger.error("Invalid certificate format: " + e.getMessage());
        } catch (IOException e) {
            logger.error("Failed to read certificate file: " + e.getMessage());
        }
        return null;
    }

    private static PrivateKey loadPrivateKey(String keyPath, LogManager logger) {
        try {
            byte[] keyBytes = Files.readAllBytes(Paths.get(keyPath));
            PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(keyBytes);
            return KeyFactory.getInstance("RSA").generatePrivate(keySpec);
        } catch (IOException e) {
            logger.error("Failed to read private key file: " + e.getMessage());
        } catch (NoSuchAlgorithmException e) {
            logger.error("RSA algorithm not available: " + e.getMessage());
        } catch (InvalidKeySpecException e) {
            logger.error("Invalid private key format (must be PKCS8): " + e.getMessage());
        }
        return null;
    }

    private static KeyStore createOrLoadKeystore(String keystorePath, String keystorePassword, LogManager logger) {
        try {
            KeyStore keyStore = KeyStore.getInstance("JKS");
            if (Files.exists(Paths.get(keystorePath))) {
                try (java.io.FileInputStream fis = new java.io.FileInputStream(keystorePath)) {
                    keyStore.load(fis, keystorePassword.toCharArray());
                    logger.info("Loaded existing keystore: " + keystorePath);
                }
            } else {
                keyStore.load(null, null);
                logger.info("Created new keystore: " + keystorePath);
            }
            return keyStore;
        } catch (KeyStoreException e) {
            logger.error("Failed to create keystore: " + e.getMessage());
        } catch (IOException e) {
            logger.error("Failed to read/write keystore: " + e.getMessage());
        } catch (NoSuchAlgorithmException | CertificateException e) {
            logger.error("Failed to load keystore: " + e.getMessage());
        }
        return null;
    }

    private static void saveKeystore(KeyStore keyStore, String keystorePath, String keystorePassword, LogManager logger)
            throws IOException, KeyStoreException, NoSuchAlgorithmException, CertificateException {
        File tempFile = null;
        try {
            // Save to temporary file first
            tempFile = File.createTempFile("keystore", ".tmp");
            try (java.io.FileOutputStream fos = new java.io.FileOutputStream(tempFile)) {
                keyStore.store(fos, keystorePassword.toCharArray());
            }

            // If successful, move to final location
            Files.move(tempFile.toPath(), Paths.get(keystorePath),
                      java.nio.file.StandardCopyOption.REPLACE_EXISTING);

        } finally {
            // Cleanup temp file if it exists
            if (tempFile != null && tempFile.exists()) {
                tempFile.delete();
            }
        }
    }
}
