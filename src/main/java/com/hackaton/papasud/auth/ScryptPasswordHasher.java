package com.hackaton.papasud.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import org.bouncycastle.crypto.generators.SCrypt;

/**
 * Verifica el formato de hash que ya usa Express: {@code scrypt$<salt>$<hash>} en
 * base64url, con los parametros por defecto de Node crypto.scryptSync
 * (N=16384, r=8, p=1, keylen=64).
 *
 * <p>Se mantiene ese formato a proposito: permite reutilizar el mismo
 * PAPASTOCK_AUTH_PASSWORD_HASH en ambos backends durante la migracion, sin que nadie
 * tenga que rotar la credencial el dia del cutover.
 */
public final class ScryptPasswordHasher {

    private static final int COST_N = 16384;
    private static final int BLOCK_SIZE_R = 8;
    private static final int PARALLELIZATION_P = 1;
    private static final int KEY_LENGTH = 64;
    private static final int MIN_SALT_LENGTH = 16;
    private static final String PREFIX = "scrypt";

    private ScryptPasswordHasher() {
    }

    public static String hash(String password) {
        byte[] salt = new byte[MIN_SALT_LENGTH];
        new SecureRandom().nextBytes(salt);
        return hash(password, salt);
    }

    public static String hash(String password, byte[] salt) {
        byte[] derived = derive(password, salt, KEY_LENGTH);
        Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();
        return PREFIX + "$" + encoder.encodeToString(salt) + "$" + encoder.encodeToString(derived);
    }

    public static boolean isValidFormat(String encoded) {
        return parse(encoded) != null;
    }

    /** Comparacion en tiempo constante: no filtra informacion por timing. */
    public static boolean matches(String password, String encoded) {
        Parsed parsed = parse(encoded);
        if (parsed == null || password == null) {
            return false;
        }
        byte[] actual = derive(password, parsed.salt(), parsed.expected().length);
        return MessageDigest.isEqual(actual, parsed.expected());
    }

    private static byte[] derive(String password, byte[] salt, int length) {
        return SCrypt.generate(
                password.getBytes(StandardCharsets.UTF_8),
                salt,
                COST_N,
                BLOCK_SIZE_R,
                PARALLELIZATION_P,
                length);
    }

    private static Parsed parse(String encoded) {
        if (encoded == null || encoded.isBlank()) {
            return null;
        }
        String[] parts = encoded.split("\\$");
        if (parts.length != 3 || !PREFIX.equals(parts[0])) {
            return null;
        }
        try {
            Base64.Decoder decoder = Base64.getUrlDecoder();
            byte[] salt = decoder.decode(parts[1]);
            byte[] expected = decoder.decode(parts[2]);
            if (salt.length < MIN_SALT_LENGTH || expected.length != KEY_LENGTH) {
                return null;
            }
            return new Parsed(salt, expected);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private record Parsed(byte[] salt, byte[] expected) {
    }
}
