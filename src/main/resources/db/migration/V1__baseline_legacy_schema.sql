-- Baseline of the original Hibernate-managed schema.
-- Uses IF NOT EXISTS so existing Render/PostgreSQL data is never dropped.

CREATE TABLE IF NOT EXISTS ubicaciones (
    id BIGSERIAL PRIMARY KEY,
    nombre VARCHAR(255) NOT NULL UNIQUE,
    tipo VARCHAR(50) NOT NULL
);

CREATE TABLE IF NOT EXISTS lotes (
    id BIGSERIAL PRIMARY KEY,
    variedad VARCHAR(255) NOT NULL UNIQUE,
    ubicacion_id BIGINT NOT NULL REFERENCES ubicaciones (id),
    stock_declarado NUMERIC(10, 2) NOT NULL,
    stock_verificado NUMERIC(10, 2) NOT NULL
);

CREATE TABLE IF NOT EXISTS movimientos (
    id BIGSERIAL PRIMARY KEY,
    lote_id BIGINT NOT NULL REFERENCES lotes (id),
    origen_id BIGINT REFERENCES ubicaciones (id),
    destino_id BIGINT REFERENCES ubicaciones (id),
    cantidad NUMERIC(10, 2) NOT NULL,
    fecha TIMESTAMP NOT NULL,
    tipo VARCHAR(50) NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_lotes_ubicacion ON lotes (ubicacion_id);
CREATE INDEX IF NOT EXISTS idx_movimientos_lote ON movimientos (lote_id);
