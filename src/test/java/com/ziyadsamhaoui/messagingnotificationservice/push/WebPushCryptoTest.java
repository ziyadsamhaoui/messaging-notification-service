package com.ziyadsamhaoui.messagingnotificationservice.push;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.util.Arrays;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.KeyAgreement;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;

class WebPushCryptoTest {

    @Test
    void encryptedPayloadDecryptsBackToThePlainText() throws Exception {
        KeyPair userKeys = keyPair();
        byte[] userPublic = WebPushCrypto.encodePublicKey((ECPublicKey) userKeys.getPublic());
        byte[] authSecret = random(16);

        String plainText = "hello push";
        byte[] body = WebPushCrypto.encrypt(plainText, base64(userPublic), base64(authSecret));

        byte[] salt = Arrays.copyOfRange(body, 0, 16);
        int keyIdLength = body[20] & 0xFF;
        byte[] serverPublic = Arrays.copyOfRange(body, 21, 21 + keyIdLength);
        byte[] cipherText = Arrays.copyOfRange(body, 21 + keyIdLength, body.length);

        byte[] sharedSecret = sharedSecret((ECPrivateKey) userKeys.getPrivate(),
                WebPushCrypto.decodePublicKey(serverPublic));
        byte[] authPrk = WebPushCrypto.hkdfExtract(authSecret, sharedSecret);
        byte[] keyInfo = concat(nullTerminated("WebPush: info"), userPublic, serverPublic);
        byte[] ikm = WebPushCrypto.hkdfExpand(authPrk, keyInfo, 32);
        byte[] prk = WebPushCrypto.hkdfExtract(salt, ikm);
        byte[] contentKey = WebPushCrypto.hkdfExpand(prk, nullTerminated("Content-Encoding: aes128gcm"), 16);
        byte[] nonce = WebPushCrypto.hkdfExpand(prk, nullTerminated("Content-Encoding: nonce"), 12);

        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(contentKey, "AES"), new GCMParameterSpec(128, nonce));
        byte[] record = cipher.doFinal(cipherText);

        assertThat(record[record.length - 1]).isEqualTo((byte) 0x02);
        assertThat(new String(record, 0, record.length - 1, StandardCharsets.UTF_8)).isEqualTo(plainText);
    }

    @Test
    void vapidTokenIsAVerifiableEs256Jws() throws Exception {
        KeyPair vapidKeys = keyPair();
        String publicKey = base64(WebPushCrypto.encodePublicKey((ECPublicKey) vapidKeys.getPublic()));
        String privateKey = base64(toFixed(((ECPrivateKey) vapidKeys.getPrivate()).getS().toByteArray()));

        String token = WebPushCrypto.vapidToken(publicKey, privateKey, "mailto:admin@badrlink.local",
                "https://fcm.googleapis.com/fcm/send/abc");

        String[] parts = token.split("\\.");
        assertThat(parts).hasSize(3);

        String claims = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
        assertThat(claims).contains("\"aud\":\"https://fcm.googleapis.com\"").contains("mailto:admin@badrlink.local");

        Signature verifier = Signature.getInstance("SHA256withECDSA");
        verifier.initVerify(WebPushCrypto.decodePublicKey(Base64.getUrlDecoder().decode(publicKey)));
        verifier.update((parts[0] + "." + parts[1]).getBytes(StandardCharsets.US_ASCII));
        assertThat(verifier.verify(rawToDer(Base64.getUrlDecoder().decode(parts[2]), 32))).isTrue();
    }

    @Test
    void audienceDropsPathAndQuery() {
        assertThat(WebPushCrypto.audience("https://fcm.googleapis.com/fcm/send/abc?x=1"))
                .isEqualTo("https://fcm.googleapis.com");
    }

    private KeyPair keyPair() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
        generator.initialize(new ECGenParameterSpec("secp256r1"));
        return generator.generateKeyPair();
    }

    private byte[] sharedSecret(ECPrivateKey privateKey, ECPublicKey publicKey) throws Exception {
        KeyAgreement agreement = KeyAgreement.getInstance("ECDH");
        agreement.init(privateKey);
        agreement.doPhase(publicKey, true);
        return agreement.generateSecret();
    }

    private byte[] random(int length) {
        byte[] value = new byte[length];
        new SecureRandom().nextBytes(value);
        return value;
    }

    private byte[] toFixed(byte[] value) {
        byte[] fixed = new byte[32];
        int start = value.length > 32 ? value.length - 32 : 0;
        int length = Math.min(value.length, 32);
        System.arraycopy(value, start, fixed, 32 - length, length);
        return fixed;
    }

    private byte[] rawToDer(byte[] raw, int coordinateLength) {
        byte[] r = derInteger(Arrays.copyOfRange(raw, 0, coordinateLength));
        byte[] s = derInteger(Arrays.copyOfRange(raw, coordinateLength, coordinateLength * 2));
        byte[] body = concat(r, s);
        byte[] der = new byte[body.length + 2];
        der[0] = 0x30;
        der[1] = (byte) body.length;
        System.arraycopy(body, 0, der, 2, body.length);
        return der;
    }

    private byte[] derInteger(byte[] value) {
        int start = 0;
        while (start < value.length - 1 && value[start] == 0) {
            start++;
        }
        int length = value.length - start;
        boolean needsSignByte = value[start] < 0;
        byte[] body = new byte[length + (needsSignByte ? 1 : 0)];
        System.arraycopy(value, start, body, needsSignByte ? 1 : 0, length);

        byte[] result = new byte[body.length + 2];
        result[0] = 0x02;
        result[1] = (byte) body.length;
        System.arraycopy(body, 0, result, 2, body.length);
        return result;
    }

    private byte[] nullTerminated(String value) {
        return concat(value.getBytes(StandardCharsets.UTF_8), new byte[] { 0 });
    }

    private byte[] concat(byte[]... parts) {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        for (byte[] part : parts) {
            output.writeBytes(part);
        }
        return output.toByteArray();
    }

    private String base64(byte[] value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }
}
