package com.academia.users;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Genera el token que viaja en el enlace de correo y calcula el hash que se guarda en
 * {@code password_reset_tokens.token_hash} (docs/modelo-datos.md sección 2).
 *
 * SHA-256 y no BCrypt: a diferencia de una contraseña, el token ya tiene entropía completa
 * (32 bytes de {@link SecureRandom}), así que no hace falta un hash lento con coste
 * configurable para frenar fuerza bruta. Un hash rápido es suficiente y más barato de
 * verificar en cada intento de canje.
 */
final class TokenGenerator {

    private static final SecureRandom RANDOM = new SecureRandom();

    private TokenGenerator() {
    }

    static String newRawToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    static String hash(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashBytes);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 no disponible en esta JVM.", e);
        }
    }
}
