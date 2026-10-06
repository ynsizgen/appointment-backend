package com.appointmentplatform.identity.security;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.Instant;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.util.Locale;
import org.springframework.stereotype.Component;

@Component
public class Totp {
    public long verify(String base32Secret, String code, Instant now) {
        if (code == null || !code.matches("[0-9]{6}"))
            return -1;
        long current = now.getEpochSecond() / 30;
        for (long step = current - 1;
             step <= current + 1; step++) {
            String expected = code(base32Secret, step, 6);
            if (MessageDigest.isEqual(expected.getBytes(StandardCharsets.US_ASCII), code.getBytes(StandardCharsets.US_ASCII))) return step;
        }
        return -1;
    }
    public String code(String secret, long step, int digits) {
        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(decode(secret), "HmacSHA1"));
            byte[] h = mac.doFinal(ByteBuffer.allocate(8).putLong(step).array());
            int offset = h[h.length - 1] & 15;
            int binary = ByteBuffer.wrap(h, offset, 4).getInt() & 0x7fffffff;
            return String.format(Locale.ROOT, "%0" + digits + "d", binary % (int)Math.pow(10, digits));
        } catch (GeneralSecurityException e) { throw new IllegalStateException(e); }
    }
    public byte[] decode(String secret) {
        if (secret == null || !secret.matches("[A-Z2-7]{32,128}")) throw new IllegalArgumentException("TOTP secret must be unpadded uppercase Base32 (at least 160 bits)");
        byte[] out = new byte[secret.length() * 5 / 8]; int buffer=0, bits=0, at=0;
        for (char c : secret.toCharArray()) {
            int value = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567".indexOf(c);
            buffer = (buffer << 5) | value; bits += 5;
            if (bits >= 8) { bits -= 8; out[at++] = (byte)(buffer >> bits); }
        }
        return out;
    }
}
