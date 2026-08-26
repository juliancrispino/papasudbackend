package com.hackaton.papasud.auth;

/** Scopes replicados 1:1 de Express, para que el cutover no cambie el modelo de permisos. */
public final class Permission {

    private Permission() {
    }

    public static final String DATA_READ = "data:read";
    public static final String STOCK_WRITE = "stock:write";
    public static final String IMPORTS_WRITE = "imports:write";
    public static final String AI_USE = "ai:use";

    public static final java.util.List<String> OPERATOR = java.util.List.of(
            DATA_READ, STOCK_WRITE, IMPORTS_WRITE, AI_USE);
}
