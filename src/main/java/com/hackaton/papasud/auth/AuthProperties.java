package com.hackaton.papasud.auth;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "papasud.auth")
public class AuthProperties {

    /** Usuario unico habilitado, igual que en Express. */
    private String username = "operador";

    /** Hash scrypt en el formato de Express: scrypt$saltBase64Url$hashBase64Url. */
    private String passwordHash = "";

    /**
     * Password en texto plano, SOLO para desarrollo local. Se ignora cuando las cookies
     * son seguras (produccion), asi no puede quedar habilitado por accidente.
     */
    private String devPassword = "";

    /** Secreto del HMAC con el que se derivan los fingerprints de sesion. */
    private String sessionSecret = "";

    private boolean secureCookies = false;

    /** Lax es lo correcto para same-origin, que es la arquitectura objetivo. */
    private String sameSite = "Lax";

    private int sessionTtlMinutes = 480;

    private String plant = "Planta Balcarce";
}
