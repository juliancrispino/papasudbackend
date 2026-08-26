package com.hackaton.papasud.auth;

/**
 * Equivalente Java de `npm run auth:hash` de Express.
 *
 * <p>Uso: {@code java -cp target/classes com.hackaton.papasud.auth.HashPasswordTool <password>}
 * El resultado va en PAPASTOCK_AUTH_PASSWORD_HASH.
 */
public final class HashPasswordTool {

    private static final int MIN_LENGTH = 12;

    private HashPasswordTool() {
    }

    public static void main(String[] args) {
        if (args.length != 1) {
            System.err.println("Uso: HashPasswordTool <password>");
            System.exit(2);
            return;
        }
        if (args[0].length() < MIN_LENGTH) {
            System.err.println("La password debe tener al menos " + MIN_LENGTH + " caracteres.");
            System.exit(2);
            return;
        }
        System.out.println(ScryptPasswordHasher.hash(args[0]));
    }
}
