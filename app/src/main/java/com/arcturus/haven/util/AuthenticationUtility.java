package com.arcturus.haven.util;

import android.content.Intent;

import java.nio.ByteBuffer;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;

import javax.crypto.KeyGenerator;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

// Opening access to actions across the profile boundary poses a security risk.
// The risk is that other applications might also be able to start our activities
// through system's IntentForwarderActivity
// That activity runs in the system process, thus normal limitations like "permissions"
// and "exported" will not work.
// This class adds a timestamp and a signature to our own Intents sent through the boundary.
// The exported activity is separately protected by a signature-level permission; this HMAC
// is defense in depth and must never be used as the sole authorization mechanism.
public class AuthenticationUtility {
    public static synchronized void signIntent(Intent intent) {
        String key = LocalStorageManager.getInstance().getString(
                LocalStorageManager.PREF_AUTH_KEY);
        if (!isValidKey(key)) {
            // Generate the key if we don't have one yet
            try {
                KeyGenerator keyGen = KeyGenerator.getInstance("HmacSHA256");
                keyGen.init(256);
                key = bytesToHex(keyGen.generateKey().getEncoded());
            } catch (NoSuchAlgorithmException e) {
                throw new RuntimeException("WTF?");
            }

            LocalStorageManager.getInstance().setString(
                    LocalStorageManager.PREF_AUTH_KEY, key);

            // If this is the first time, we just send the key to the other side
            intent.putExtra("auth_key", key);
        } else {
            long timestamp = new Date().getTime();
            String nonce = UUID.randomUUID().toString();
            intent.putExtra("timestamp", timestamp);
            intent.putExtra("nonce", nonce);
            intent.putExtra("signature", sign(key, intent.getAction(), timestamp, nonce));
        }
    }

    public static synchronized boolean checkIntent(Intent intent) {
        String key = LocalStorageManager.getInstance().getString(
                LocalStorageManager.PREF_AUTH_KEY);
        if (!isValidKey(key)) {
            // The activity's signature-level permission authenticates the sender during
            // bootstrap. Never accept malformed or missing keys into persistent storage.
            String receivedKey = intent.getStringExtra("auth_key");
            if (isValidKey(receivedKey)) {
                LocalStorageManager.getInstance().setString(
                        LocalStorageManager.PREF_AUTH_KEY, receivedKey);
                return true;
            } else {
                return false;
            }
        } else {
            long timestamp = new Date().getTime();
            long intentTimestamp = intent.getLongExtra("timestamp", 0);
            long age = timestamp - intentTimestamp;
            if (age < 0 || age > 30 * 1000) return false;

            String nonce = intent.getStringExtra("nonce");
            if (nonce == null || nonce.length() > 128) return false;

            String expected = sign(key, intent.getAction(), intentTimestamp, nonce);
            String received = intent.getStringExtra("signature");
            boolean valid = received != null && MessageDigest.isEqual(
                    expected.getBytes(StandardCharsets.UTF_8),
                    received.getBytes(StandardCharsets.UTF_8));
            return valid && consumeNonce(nonce, timestamp);
        }
    }

    public static synchronized void reset() {
        LocalStorageManager.getInstance().remove(LocalStorageManager.PREF_AUTH_KEY);
        LocalStorageManager.getInstance().remove(LocalStorageManager.PREF_AUTH_NONCES);
    }

    private static String sign(String hexKey, String action, long timestamp, String nonce) {
        try {
            SecretKeySpec keySpec = new SecretKeySpec(hexStringToByteArray(hexKey), "HmacSHA256");
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(keySpec);
            byte[] actionBytes = action == null ? new byte[0] : action.getBytes(StandardCharsets.UTF_8);
            byte[] nonceBytes = nonce == null ? new byte[0] : nonce.getBytes(StandardCharsets.UTF_8);
            ByteBuffer buffer = ByteBuffer.allocate(4 + actionBytes.length + Long.BYTES + 4 + nonceBytes.length);
            buffer.putInt(actionBytes.length);
            buffer.put(actionBytes);
            buffer.putLong(timestamp);
            buffer.putInt(nonceBytes.length);
            buffer.put(nonceBytes);
            return bytesToHex(mac.doFinal(buffer.array()));
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new RuntimeException("WTF?");
        }
    }

    final private static char[] hexArray = "0123456789ABCDEF".toCharArray();

    private static boolean isValidKey(String key) {
        return key != null && key.length() == 64 && key.matches("[0-9A-Fa-f]{64}");
    }

    private static boolean consumeNonce(String nonce, long now) {
        String stored = LocalStorageManager.getInstance().getString(
                LocalStorageManager.PREF_AUTH_NONCES);
        StringBuilder retained = new StringBuilder();
        boolean alreadySeen = false;
        if (stored != null && !stored.isEmpty()) {
            for (String entry : stored.split(",")) {
                int separator = entry.lastIndexOf(':');
                if (separator <= 0) continue;
                String savedNonce = entry.substring(0, separator);
                try {
                    long savedAt = Long.parseLong(entry.substring(separator + 1));
                    if (now - savedAt <= 30 * 1000) {
                        if (savedNonce.equals(nonce)) alreadySeen = true;
                        if (retained.length() > 0) retained.append(',');
                        retained.append(entry);
                    }
                } catch (NumberFormatException ignored) {
                    // Discard malformed cache entries.
                }
            }
        }
        if (alreadySeen) return false;
        if (retained.length() > 0) retained.append(',');
        retained.append(nonce).append(':').append(now);
        LocalStorageManager.getInstance().setString(
                LocalStorageManager.PREF_AUTH_NONCES, retained.toString());
        return true;
    }

    private static String bytesToHex(byte[] bytes) {
        char[] hexChars = new char[bytes.length * 2];
        for (int j = 0; j < bytes.length; j++) {
            int v = bytes[j] & 0xFF;
            hexChars[j * 2] = hexArray[v >>> 4];
            hexChars[j * 2 + 1] = hexArray[v & 0x0F];
        }
        return new String(hexChars);
    }


    private static byte[] hexStringToByteArray(String s) {
        try {
            int len = s.length();
            if (len > 1) {
                byte[] data = new byte[len / 2];
                for (int i = 0 ; i < len ; i += 2) {
                    data[i / 2] = (byte) ((Character.digit(s.charAt(i), 16) << 4)
                            + Character.digit(s.charAt(i + 1), 16));
                }
                return data;
            } else {
                return null;
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

}
