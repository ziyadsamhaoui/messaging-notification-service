package com.ziyadsamhaoui.messagingnotificationservice.push;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.AlgorithmParameters;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECParameterSpec;
import java.security.spec.ECPoint;
import java.security.spec.ECPrivateKeySpec;
import java.security.spec.ECPublicKeySpec;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.KeyAgreement;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

final class WebPushCrypto {

    private static final int P256_KEY_LENGTH = 65;
    private static final int COORDINATE_LENGTH = 32;
    private static final int RECORD_SIZE = 4096;
    private static final int GCM_TAG_BITS = 128;
    private static final long VAPID_TTL_SECONDS = 12 * 3600L;

    private static final byte[] AES_GCM_INFO = nullTerminated("Content-Encoding: aes128gcm");
    private static final byte[] NONCE_INFO = nullTerminated("Content-Encoding: nonce");
    private static final byte[] KEY_INFO_PREFIX = nullTerminated("WebPush: info");

    private static final SecureRandom RANDOM = new SecureRandom();

    private WebPushCrypto() {
    }

    private static byte[] nullTerminated(String value) {
        byte[] text = value.getBytes(StandardCharsets.UTF_8);
        byte[] result = new byte[text.length + 1];
        System.arraycopy(text, 0, result, 0, text.length);
        return result;
    }

    static byte[] encrypt(String payload, String p256dh, String authSecret) {
        try {
            byte[] userPublicBytes = Base64.getUrlDecoder().decode(p256dh);
            byte[] auth = Base64.getUrlDecoder().decode(authSecret);

            KeyPair ephemeral = generateKeyPair();
            byte[] ephemeralPublicBytes = encodePublicKey((ECPublicKey) ephemeral.getPublic());
            byte[] sharedSecret = deriveSharedSecret((ECPrivateKey) ephemeral.getPrivate(),
                    decodePublicKey(userPublicBytes));

            byte[] authPrk = hkdfExtract(auth, sharedSecret);
            byte[] keyInfo = concat(KEY_INFO_PREFIX, userPublicBytes, ephemeralPublicBytes);
            byte[] ikm = hkdfExpand(authPrk, keyInfo, 32);

            byte[] salt = new byte[16];
            RANDOM.nextBytes(salt);
            byte[] prk = hkdfExtract(salt, ikm);
            byte[] contentEncryptionKey = hkdfExpand(prk, AES_GCM_INFO, 16);
            byte[] nonce = hkdfExpand(prk, NONCE_INFO, 12);

            byte[] record = concat(payload.getBytes(StandardCharsets.UTF_8), new byte[] { 0x02 });
            byte[] cipherText = gcmEncrypt(record, contentEncryptionKey, nonce);

            ByteArrayOutputStream body = new ByteArrayOutputStream();
            body.write(salt);
            body.write(new byte[] {
                    (byte) (RECORD_SIZE >>> 24),
                    (byte) (RECORD_SIZE >>> 16),
                    (byte) (RECORD_SIZE >>> 8),
                    (byte) RECORD_SIZE });
            body.write(P256_KEY_LENGTH);
            body.write(ephemeralPublicBytes);
            body.write(cipherText);
            return body.toByteArray();
        } catch (GeneralSecurityException | IOException exception) {
            throw new IllegalStateException("web push payload encryption failed", exception);
        }
    }

    static String vapidToken(String publicKey, String privateKey, String subject, String endpoint) {
        try {
            String audience = audience(endpoint);
            String header = base64Url("{\"typ\":\"JWT\",\"alg\":\"ES256\"}".getBytes(StandardCharsets.UTF_8));
            String claims = "{\"aud\":\"" + audience + "\",\"exp\":" + (Instant.now().getEpochSecond()
                    + VAPID_TTL_SECONDS) + ",\"sub\":\"" + subject + "\"}";
            String signingInput = header + "." + base64Url(claims.getBytes(StandardCharsets.UTF_8));

            Signature signature = Signature.getInstance("SHA256withECDSA");
            signature.initSign(decodePrivateKey(Base64.getUrlDecoder().decode(privateKey.trim())));
            signature.update(signingInput.getBytes(StandardCharsets.US_ASCII));

            return signingInput + "." + base64Url(derToRaw(signature.sign(), COORDINATE_LENGTH));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("vapid token signing failed", exception);
        }
    }

    static String audience(String endpoint) {
        URI uri = URI.create(endpoint);
        int port = uri.getPort();
        return uri.getScheme() + "://" + uri.getHost() + (port > 0 ? ":" + port : "");
    }

    static KeyPair generateKeyPair() throws GeneralSecurityException {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
        generator.initialize(new ECGenParameterSpec("secp256r1"));
        return generator.generateKeyPair();
    }

    private static ECParameterSpec p256Parameters() throws GeneralSecurityException {
        AlgorithmParameters parameters = AlgorithmParameters.getInstance("EC");
        parameters.init(new ECGenParameterSpec("secp256r1"));
        return parameters.getParameterSpec(ECParameterSpec.class);
    }

    static ECPublicKey decodePublicKey(byte[] raw) throws GeneralSecurityException {
        BigInteger x = new BigInteger(1, Arrays.copyOfRange(raw, 1, 1 + COORDINATE_LENGTH));
        BigInteger y = new BigInteger(1, Arrays.copyOfRange(raw, 1 + COORDINATE_LENGTH, P256_KEY_LENGTH));
        return (ECPublicKey) KeyFactory.getInstance("EC")
                .generatePublic(new ECPublicKeySpec(new ECPoint(x, y), p256Parameters()));
    }

    static ECPrivateKey decodePrivateKey(byte[] raw) throws GeneralSecurityException {
        return (ECPrivateKey) KeyFactory.getInstance("EC")
                .generatePrivate(new ECPrivateKeySpec(new BigInteger(1, raw), p256Parameters()));
    }

    static byte[] encodePublicKey(ECPublicKey publicKey) {
        byte[] encoded = new byte[P256_KEY_LENGTH];
        encoded[0] = 0x04;
        writeCoordinate(encoded, 1, publicKey.getW().getAffineX());
        writeCoordinate(encoded, 1 + COORDINATE_LENGTH, publicKey.getW().getAffineY());
        return encoded;
    }

    private static void writeCoordinate(byte[] target, int offset, BigInteger value) {
        byte[] coordinate = value.toByteArray();
        int length = coordinate.length;
        int start = length > COORDINATE_LENGTH ? length - COORDINATE_LENGTH : 0;
        int copyLength = Math.min(length, COORDINATE_LENGTH);
        System.arraycopy(coordinate, start, target, offset + COORDINATE_LENGTH - copyLength, copyLength);
    }

    private static byte[] deriveSharedSecret(ECPrivateKey privateKey, ECPublicKey publicKey)
            throws GeneralSecurityException {

        KeyAgreement agreement = KeyAgreement.getInstance("ECDH");
        agreement.init(privateKey);
        agreement.doPhase(publicKey, true);
        return agreement.generateSecret();
    }

    private static byte[] gcmEncrypt(byte[] plainText, byte[] key, byte[] nonce) throws GeneralSecurityException {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(GCM_TAG_BITS, nonce));
        return cipher.doFinal(plainText);
    }

    static byte[] hkdfExtract(byte[] salt, byte[] ikm) throws GeneralSecurityException {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(salt.length == 0 ? new byte[32] : salt, "HmacSHA256"));
        return mac.doFinal(ikm);
    }

    static byte[] hkdfExpand(byte[] prk, byte[] info, int length) throws GeneralSecurityException {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(prk, "HmacSHA256"));

        byte[] output = new byte[length];
        byte[] previous = new byte[0];
        int offset = 0;
        byte counter = 1;

        while (offset < length) {
            mac.reset();
            mac.update(previous);
            mac.update(info);
            mac.update(counter);
            previous = mac.doFinal();

            int copyLength = Math.min(previous.length, length - offset);
            System.arraycopy(previous, 0, output, offset, copyLength);
            offset += copyLength;
            counter++;
        }

        return output;
    }

    private static byte[] derToRaw(byte[] der, int coordinateLength) {
        int offset = 0;
        if (der[offset++] != 0x30) {
            throw new IllegalStateException("unexpected ECDSA signature encoding");
        }
        int sequenceLength = der[offset++] & 0xFF;
        if ((sequenceLength & 0x80) != 0) {
            offset += sequenceLength & 0x7F;
        }

        byte[] raw = new byte[coordinateLength * 2];

        if (der[offset++] != 0x02) {
            throw new IllegalStateException("unexpected ECDSA signature encoding");
        }
        int rLength = der[offset++] & 0xFF;
        writeDerInteger(der, offset, rLength, raw, 0, coordinateLength);
        offset += rLength;

        if (der[offset++] != 0x02) {
            throw new IllegalStateException("unexpected ECDSA signature encoding");
        }
        int sLength = der[offset++] & 0xFF;
        writeDerInteger(der, offset, sLength, raw, coordinateLength, coordinateLength);

        return raw;
    }

    private static void writeDerInteger(byte[] der, int start, int length, byte[] target, int targetOffset,
            int coordinateLength) {

        int leadingZeros = 0;
        while (leadingZeros < length - 1 && der[start + leadingZeros] == 0) {
            leadingZeros++;
        }
        int significant = length - leadingZeros;
        int copyLength = Math.min(significant, coordinateLength);
        System.arraycopy(der, start + leadingZeros + (significant - copyLength), target,
                targetOffset + coordinateLength - copyLength, copyLength);
    }

    private static byte[] concat(byte[]... parts) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        for (byte[] part : parts) {
            output.write(part);
        }
        return output.toByteArray();
    }

    private static String base64Url(byte[] value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }
}
