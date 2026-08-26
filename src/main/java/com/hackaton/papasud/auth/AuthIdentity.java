package com.hackaton.papasud.auth;

import java.util.List;

/** Cuerpo exacto que espera DemoSessionContext del frontend dentro de {data}. */
public record AuthIdentity(
        String username,
        String name,
        String role,
        String plant,
        List<String> permissions) {
}
