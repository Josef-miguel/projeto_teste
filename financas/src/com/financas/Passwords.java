package com.financas;

import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.HexFormat;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/**
 * Hash de senhas com PBKDF2 (HmacSHA256) e salt aleatório por usuário.
 * Formato armazenado: pbkdf2$iterações$salt$hash (em hexadecimal).
 */
public final class Passwords {
    private static final int ITERATIONS = 120_000;
    private static final int KEY_BITS = 256;
    private static final SecureRandom RNG = new SecureRandom();

    private Passwords() {}

    public static String hash(String password) {
        byte[] salt = new byte[16];
        RNG.nextBytes(salt);
        byte[] dk = pbkdf2(password, salt, ITERATIONS);
        return "pbkdf2$" + ITERATIONS + "$"
                + HexFormat.of().formatHex(salt) + "$"
                + HexFormat.of().formatHex(dk);
    }

    public static boolean verify(String password, String stored) {
        try {
            String[] parts = stored.split("\\$");
            if (parts.length != 4 || !parts[0].equals("pbkdf2")) return false;
            int iterations = Integer.parseInt(parts[1]);
            byte[] salt = HexFormat.of().parseHex(parts[2]);
            byte[] expected = HexFormat.of().parseHex(parts[3]);
            byte[] actual = pbkdf2(password, salt, iterations);
            return MessageDigest.isEqual(expected, actual);
        } catch (Exception e) {
            return false;
        }
    }

    private static byte[] pbkdf2(String password, byte[] salt, int iterations) {
        try {
            PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, iterations, KEY_BITS);
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                    .generateSecret(spec)
                    .getEncoded();
        } catch (Exception e) {
            throw new IllegalStateException("Erro ao calcular hash da senha", e);
        }
    }

}
