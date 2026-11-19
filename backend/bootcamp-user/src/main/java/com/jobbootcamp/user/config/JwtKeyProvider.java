package com.jobbootcamp.user.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

@Component
public class JwtKeyProvider {

    private static final Logger log = LoggerFactory.getLogger(JwtKeyProvider.class);

    private final RSAPrivateKey privateKey;
    private final RSAPublicKey publicKey;

    public JwtKeyProvider(
            @Value("${app.jwt.private-key-path}") String privateKeyPath,
            @Value("${app.jwt.public-key-path}") String publicKeyPath,
            @Value("${app.jwt.allow-generate:true}") boolean allowGenerate) {
        Path privatePath = Paths.get(privateKeyPath).toAbsolutePath().normalize();
        Path publicPath = Paths.get(publicKeyPath).toAbsolutePath().normalize();
        try {
            if (Files.exists(privatePath) && Files.exists(publicPath)) {
                this.privateKey = readPrivateKey(privatePath);
                this.publicKey = readPublicKey(publicPath);
            } else if (allowGenerate) {
                KeyPair keyPair = generateKeyPair();
                writePem(privatePath, "PRIVATE KEY", keyPair.getPrivate().getEncoded());
                writePem(publicPath, "PUBLIC KEY", keyPair.getPublic().getEncoded());
                this.privateKey = (RSAPrivateKey) keyPair.getPrivate();
                this.publicKey = (RSAPublicKey) keyPair.getPublic();
                log.warn("DEV JWT key pair generated at {} and {}. Do not use these keys in production.", privatePath, publicPath);
            } else {
                throw new IllegalStateException("JWT key files are missing: " + privatePath + " / " + publicPath
                        + ". Provide JWT_PRIVATE_KEY_PATH and JWT_PUBLIC_KEY_PATH, or enable generation for local development.");
            }
        } catch (IOException | GeneralSecurityException e) {
            throw new IllegalStateException("Failed to load JWT keys", e);
        }
    }

    public RSAPrivateKey getPrivateKey() {
        return privateKey;
    }

    public RSAPublicKey getPublicKey() {
        return publicKey;
    }

    private KeyPair generateKeyPair() throws GeneralSecurityException {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        return generator.generateKeyPair();
    }

    private RSAPrivateKey readPrivateKey(Path path) throws IOException, GeneralSecurityException {
        byte[] der = decodePem(Files.readString(path, StandardCharsets.UTF_8));
        return (RSAPrivateKey) KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(der));
    }

    private RSAPublicKey readPublicKey(Path path) throws IOException, GeneralSecurityException {
        byte[] der = decodePem(Files.readString(path, StandardCharsets.UTF_8));
        return (RSAPublicKey) KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(der));
    }

    private byte[] decodePem(String content) {
        String base64 = content
                .replaceAll("-----BEGIN [^-]+-----", "")
                .replaceAll("-----END [^-]+-----", "")
                .replaceAll("\\s", "");
        return Base64.getDecoder().decode(base64);
    }

    private void writePem(Path path, String type, byte[] der) throws IOException {
        Files.createDirectories(path.getParent());
        String base64 = Base64.getMimeEncoder(64, System.lineSeparator().getBytes(StandardCharsets.UTF_8)).encodeToString(der);
        String pem = "-----BEGIN " + type + "-----" + System.lineSeparator()
                + base64 + System.lineSeparator()
                + "-----END " + type + "-----" + System.lineSeparator();
        Files.writeString(path, pem, StandardCharsets.UTF_8);
    }
}