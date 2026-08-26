-- PapaStock: paridad de modelo con el frontend React y con las garantias que hoy da Express.
--
-- Principio que NO cambia: el stock sigue derivandose del ledger append-only
-- (stock_movements + movement_items + stock_counts). Esta migracion NO introduce
-- una columna de cantidad mutable como fuente de verdad.
--
-- Lo que si agrega:
--   1. movement_items      -> multi-lote real (un remito, N lotes)
--   2. stock_positions     -> identidad estable + token de version + fila que se bloquea
--   3. idempotency_records -> recepcion idempotente
--   4. auth_sessions       -> sesiones persistentes
--   5. transporters / shelf_units / shelves -> catalogos del snapshot
--   6. columnas de paridad en stock_movements, stock_counts y stock_discrepancies

-- ---------------------------------------------------------------------------
-- 1. Catalogos operativos (FASE 11)
-- ---------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS transporters (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    company_name VARCHAR(255) NOT NULL,
    trade_name VARCHAR(255),
    cuit VARCHAR(32) NOT NULL,
    contact_name VARCHAR(255) NOT NULL DEFAULT '',
    phone VARCHAR(64) NOT NULL DEFAULT '',
    email VARCHAR(255) NOT NULL DEFAULT '',
    address VARCHAR(255) NOT NULL DEFAULT '',
    city VARCHAR(128) NOT NULL DEFAULT '',
    province VARCHAR(128) NOT NULL DEFAULT '',
    license_plate VARCHAR(32) NOT NULL DEFAULT '',
    vehicle_type VARCHAR(64) NOT NULL DEFAULT '',
    capacity_kg NUMERIC(14, 3) NOT NULL DEFAULT 0,
    insurance_policy VARCHAR(128),
    notes TEXT,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_transporter_capacity CHECK (capacity_kg >= 0)
);

CREATE INDEX IF NOT EXISTS idx_transporters_company ON transporters (company_name);

CREATE TABLE IF NOT EXISTS shelf_units (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    location_id UUID NOT NULL REFERENCES locations (id) ON DELETE RESTRICT,
    code VARCHAR(64) NOT NULL,
    label VARCHAR(255) NOT NULL,
    grid_row INTEGER NOT NULL,
    grid_col INTEGER NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_shelf_units_location_code UNIQUE (location_id, code),
    CONSTRAINT chk_shelf_unit_grid CHECK (grid_row >= 0 AND grid_col >= 0)
);

CREATE TABLE IF NOT EXISTS shelves (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    location_id UUID NOT NULL REFERENCES locations (id) ON DELETE RESTRICT,
    shelf_unit_id UUID NOT NULL REFERENCES shelf_units (id) ON DELETE CASCADE,
    code VARCHAR(64) NOT NULL,
    label VARCHAR(255) NOT NULL,
    level INTEGER NOT NULL,
    capacity_kg NUMERIC(14, 3),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_shelves_unit_level UNIQUE (shelf_unit_id, level),
    CONSTRAINT chk_shelf_level CHECK (level >= 0),
    CONSTRAINT chk_shelf_capacity CHECK (capacity_kg IS NULL OR capacity_kg >= 0)
);

CREATE INDEX IF NOT EXISTS idx_shelf_units_location ON shelf_units (location_id);
CREATE INDEX IF NOT EXISTS idx_shelves_location ON shelves (location_id);

-- ---------------------------------------------------------------------------
-- 2. Unidades: kg y bolsas conviven. Sin factor confiable NO se convierte.
-- ---------------------------------------------------------------------------

ALTER TABLE lots ADD COLUMN IF NOT EXISTS avg_kg_per_bag NUMERIC(10, 3);
ALTER TABLE lots DROP CONSTRAINT IF EXISTS chk_lots_avg_kg_per_bag;
ALTER TABLE lots ADD CONSTRAINT chk_lots_avg_kg_per_bag
    CHECK (avg_kg_per_bag IS NULL OR avg_kg_per_bag > 0);

-- ---------------------------------------------------------------------------
-- 3. stock_positions: identidad estable de una posicion de stock.
--    NO guarda cantidades. Guarda el id que el frontend usa en round-trips
--    (stockRecordId) y el token de concurrencia optimista (version).
--    Es ademas la unica fila que se bloquea con FOR UPDATE al mover stock,
--    lo que serializa a los escritores sin bloquear el ledger append-only.
-- ---------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS stock_positions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    lot_id UUID NOT NULL REFERENCES lots (id) ON DELETE RESTRICT,
    location_id UUID NOT NULL REFERENCES locations (id) ON DELETE RESTRICT,
    unit VARCHAR(8) NOT NULL DEFAULT 'kg',
    shelf_id UUID REFERENCES shelves (id) ON DELETE SET NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_stock_positions UNIQUE (lot_id, location_id, unit),
    CONSTRAINT chk_stock_position_unit CHECK (unit IN ('kg', 'bags')),
    CONSTRAINT chk_stock_position_version CHECK (version >= 0)
);

CREATE INDEX IF NOT EXISTS idx_stock_positions_lot ON stock_positions (lot_id);
CREATE INDEX IF NOT EXISTS idx_stock_positions_location ON stock_positions (location_id);

-- ---------------------------------------------------------------------------
-- 4. stock_movements: el header deja de ser la autoridad de lote/cantidad.
--    lot_id y quantity_kg quedan como compatibilidad legacy (nullable).
-- ---------------------------------------------------------------------------

ALTER TABLE stock_movements ALTER COLUMN lot_id DROP NOT NULL;
ALTER TABLE stock_movements ALTER COLUMN quantity_kg DROP NOT NULL;

ALTER TABLE stock_movements DROP CONSTRAINT IF EXISTS chk_movement_qty;
ALTER TABLE stock_movements ADD CONSTRAINT chk_movement_qty
    CHECK (quantity_kg IS NULL OR quantity_kg > 0);

ALTER TABLE stock_movements ADD COLUMN IF NOT EXISTS kind VARCHAR(32) NOT NULL DEFAULT 'transfer';
ALTER TABLE stock_movements ADD COLUMN IF NOT EXISTS unit VARCHAR(8);
ALTER TABLE stock_movements ADD COLUMN IF NOT EXISTS corrects_movement_id UUID
    REFERENCES stock_movements (id) ON DELETE RESTRICT;
ALTER TABLE stock_movements ADD COLUMN IF NOT EXISTS reception_status VARCHAR(32)
    NOT NULL DEFAULT 'not_applicable';
ALTER TABLE stock_movements ADD COLUMN IF NOT EXISTS received_total NUMERIC(14, 3);
ALTER TABLE stock_movements ADD COLUMN IF NOT EXISTS received_unit VARCHAR(8);
ALTER TABLE stock_movements ADD COLUMN IF NOT EXISTS received_at TIMESTAMPTZ;
ALTER TABLE stock_movements ADD COLUMN IF NOT EXISTS transporter_id UUID
    REFERENCES transporters (id) ON DELETE RESTRICT;

ALTER TABLE stock_movements DROP CONSTRAINT IF EXISTS chk_movement_kind;
ALTER TABLE stock_movements ADD CONSTRAINT chk_movement_kind CHECK (kind IN (
    'transfer', 'correction', 'import', 'opening_balance', 'reception_adjustment'
));

ALTER TABLE stock_movements DROP CONSTRAINT IF EXISTS chk_movement_unit;
ALTER TABLE stock_movements ADD CONSTRAINT chk_movement_unit
    CHECK (unit IS NULL OR unit IN ('kg', 'bags'));

ALTER TABLE stock_movements DROP CONSTRAINT IF EXISTS chk_movement_received_unit;
ALTER TABLE stock_movements ADD CONSTRAINT chk_movement_received_unit
    CHECK (received_unit IS NULL OR received_unit IN ('kg', 'bags'));

ALTER TABLE stock_movements DROP CONSTRAINT IF EXISTS chk_movement_reception_status;
ALTER TABLE stock_movements ADD CONSTRAINT chk_movement_reception_status CHECK (
    reception_status IN ('not_applicable', 'pending', 'received', 'needs_reconciliation')
);

-- Una correccion reclasifica stock DENTRO de una ubicacion y siempre apunta al original.
-- Nunca se hace UPDATE del movimiento historico: se agrega uno nuevo.
--
-- En un ledger la correccion se materializa como dos filas espejo (sale del lote
-- equivocado, entra al correcto), cada una con UNA sola punta. Poner origen y destino
-- iguales en la misma fila haria que los deltas se cancelen y el stock no se movería.
-- Lo que el invariante exige es que la correccion no cruce ubicaciones: eso seria una
-- transferencia, no una correccion.
ALTER TABLE stock_movements DROP CONSTRAINT IF EXISTS chk_correction_semantics;
ALTER TABLE stock_movements ADD CONSTRAINT chk_correction_semantics CHECK (
    kind <> 'correction'
    OR (
        corrects_movement_id IS NOT NULL
        AND COALESCE(origin_location_id, destination_location_id) IS NOT NULL
        AND (
            origin_location_id IS NULL
            OR destination_location_id IS NULL
            OR origin_location_id = destination_location_id
        )
    )
);

-- chk_transfer_locations exige origen <> destino para movement_type = 'TRANSFER'.
-- Las correcciones usan movement_type = 'ADJUSTMENT', asi que no colisionan.

CREATE INDEX IF NOT EXISTS idx_stock_movements_kind ON stock_movements (kind);
CREATE INDEX IF NOT EXISTS idx_stock_movements_remito ON stock_movements (remito_number)
    WHERE remito_number IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_stock_movements_corrects ON stock_movements (corrects_movement_id)
    WHERE corrects_movement_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_stock_movements_reception ON stock_movements (reception_status)
    WHERE reception_status = 'pending';

-- ---------------------------------------------------------------------------
-- 5. movement_items: la autoridad real de "que lote y cuanto" (FASE 5)
-- ---------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS movement_items (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    movement_id UUID NOT NULL REFERENCES stock_movements (id) ON DELETE CASCADE,
    lot_id UUID NOT NULL REFERENCES lots (id) ON DELETE RESTRICT,
    dispatched_quantity NUMERIC(14, 3) NOT NULL,
    received_quantity NUMERIC(14, 3),
    received_at TIMESTAMPTZ,
    unit VARCHAR(8) NOT NULL DEFAULT 'kg',
    sort_order INTEGER NOT NULL DEFAULT 0,
    data JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_movement_item_dispatched CHECK (dispatched_quantity > 0),
    CONSTRAINT chk_movement_item_received CHECK (received_quantity IS NULL OR received_quantity >= 0),
    CONSTRAINT chk_movement_item_unit CHECK (unit IN ('kg', 'bags')),
    CONSTRAINT uq_movement_items_order UNIQUE (movement_id, sort_order)
);

CREATE INDEX IF NOT EXISTS idx_movement_items_movement ON movement_items (movement_id);
CREATE INDEX IF NOT EXISTS idx_movement_items_lot ON movement_items (lot_id);

-- Backfill: todo movimiento historico con lot_id + quantity_kg pasa a tener su linea.
-- Sin esto las vistas item-based perderian el stock ya cargado.
INSERT INTO movement_items (id, movement_id, lot_id, dispatched_quantity, unit, sort_order, data)
SELECT
    gen_random_uuid(),
    m.id,
    m.lot_id,
    m.quantity_kg,
    'kg',
    0,
    jsonb_build_object('backfilled_from', 'stock_movements.header')
FROM stock_movements m
WHERE m.lot_id IS NOT NULL
  AND m.quantity_kg IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM movement_items mi WHERE mi.movement_id = m.id);

UPDATE stock_movements SET unit = 'kg' WHERE unit IS NULL AND quantity_kg IS NOT NULL;
UPDATE stock_movements SET kind = 'opening_balance' WHERE movement_type = 'OPENING_BALANCE';
UPDATE stock_movements SET kind = 'import' WHERE source_type = 'legacy_migration' AND kind = 'transfer';

-- ---------------------------------------------------------------------------
-- 6. stock_counts: paridad con StockCount del frontend (FASE 9)
--    quantity_kg se conserva porque las vistas de verificado lo usan; a partir
--    de ahora siempre vale lo mismo que observed_quantity.
-- ---------------------------------------------------------------------------

ALTER TABLE stock_counts ADD COLUMN IF NOT EXISTS unit VARCHAR(8) NOT NULL DEFAULT 'kg';
ALTER TABLE stock_counts ADD COLUMN IF NOT EXISTS expected_quantity NUMERIC(14, 3);
ALTER TABLE stock_counts ADD COLUMN IF NOT EXISTS observed_quantity NUMERIC(14, 3);
ALTER TABLE stock_counts ADD COLUMN IF NOT EXISTS difference NUMERIC(14, 3);
ALTER TABLE stock_counts ADD COLUMN IF NOT EXISTS discrepancy_id UUID
    REFERENCES stock_discrepancies (id) ON DELETE SET NULL;
ALTER TABLE stock_counts ADD COLUMN IF NOT EXISTS stock_position_id UUID
    REFERENCES stock_positions (id) ON DELETE RESTRICT;

ALTER TABLE stock_counts DROP CONSTRAINT IF EXISTS chk_stock_count_unit;
ALTER TABLE stock_counts ADD CONSTRAINT chk_stock_count_unit CHECK (unit IN ('kg', 'bags'));

UPDATE stock_counts SET observed_quantity = quantity_kg WHERE observed_quantity IS NULL;
UPDATE stock_counts SET expected_quantity = quantity_kg WHERE expected_quantity IS NULL;
UPDATE stock_counts SET difference = COALESCE(observed_quantity, 0) - COALESCE(expected_quantity, 0)
    WHERE difference IS NULL;

-- ---------------------------------------------------------------------------
-- 7. stock_discrepancies: paridad con Discrepancy del frontend (FASE 10)
-- ---------------------------------------------------------------------------

ALTER TABLE stock_discrepancies ADD COLUMN IF NOT EXISTS movement_item_id UUID
    REFERENCES movement_items (id) ON DELETE RESTRICT;
ALTER TABLE stock_discrepancies ADD COLUMN IF NOT EXISTS stock_position_id UUID
    REFERENCES stock_positions (id) ON DELETE RESTRICT;
ALTER TABLE stock_discrepancies ADD COLUMN IF NOT EXISTS type VARCHAR(48)
    NOT NULL DEFAULT 'physical_count';
ALTER TABLE stock_discrepancies ADD COLUMN IF NOT EXISTS unit VARCHAR(8) NOT NULL DEFAULT 'kg';
ALTER TABLE stock_discrepancies ADD COLUMN IF NOT EXISTS expected_quantity NUMERIC(14, 3);
ALTER TABLE stock_discrepancies ADD COLUMN IF NOT EXISTS observed_quantity NUMERIC(14, 3);
ALTER TABLE stock_discrepancies ADD COLUMN IF NOT EXISTS cause TEXT;
ALTER TABLE stock_discrepancies ADD COLUMN IF NOT EXISTS created_at TIMESTAMPTZ;

ALTER TABLE stock_discrepancies DROP CONSTRAINT IF EXISTS chk_discrepancy_type;
ALTER TABLE stock_discrepancies ADD CONSTRAINT chk_discrepancy_type CHECK (
    type IN ('reception_shortfall', 'reception_unallocated', 'physical_count')
);

ALTER TABLE stock_discrepancies DROP CONSTRAINT IF EXISTS chk_discrepancy_unit;
ALTER TABLE stock_discrepancies ADD CONSTRAINT chk_discrepancy_unit CHECK (unit IN ('kg', 'bags'));

UPDATE stock_discrepancies SET expected_quantity = registered_quantity_kg WHERE expected_quantity IS NULL;
UPDATE stock_discrepancies SET observed_quantity = verified_quantity_kg WHERE observed_quantity IS NULL;
UPDATE stock_discrepancies SET created_at = opened_at WHERE created_at IS NULL;
UPDATE stock_discrepancies SET cause = probable_cause WHERE cause IS NULL;

ALTER TABLE stock_discrepancies ALTER COLUMN created_at SET DEFAULT NOW();

CREATE INDEX IF NOT EXISTS idx_discrepancies_movement ON stock_discrepancies (related_movement_id)
    WHERE related_movement_id IS NOT NULL;

-- ---------------------------------------------------------------------------
-- 8. Idempotencia de recepcion (FASE 7)
--    Guarda key + fingerprint del payload normalizado + la respuesta exacta,
--    para poder repetir la MISMA respuesta sin re-ejecutar el efecto.
-- ---------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS idempotency_records (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    scope VARCHAR(64) NOT NULL,
    idempotency_key VARCHAR(200) NOT NULL,
    target_id UUID,
    payload_fingerprint VARCHAR(64) NOT NULL,
    status_code INTEGER NOT NULL,
    response_body JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_idempotency_scope_key UNIQUE (scope, idempotency_key),
    CONSTRAINT chk_idempotency_fingerprint CHECK (payload_fingerprint ~ '^[0-9a-f]{64}$'),
    CONSTRAINT chk_idempotency_key_length CHECK (char_length(idempotency_key) BETWEEN 8 AND 200)
);

CREATE INDEX IF NOT EXISTS idx_idempotency_target ON idempotency_records (target_id)
    WHERE target_id IS NOT NULL;

-- ---------------------------------------------------------------------------
-- 9. Sesiones persistentes (FASE 3)
--    Express las guarda en memoria y las pierde en cada deploy. Aca no.
--    Solo se guarda el fingerprint HMAC del token, nunca el token.
-- ---------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS auth_sessions (
    token_fingerprint VARCHAR(128) PRIMARY KEY,
    username VARCHAR(128) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    expires_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_auth_sessions_expiry ON auth_sessions (expires_at);

-- ---------------------------------------------------------------------------
-- 10. Backfill de stock_positions desde el ledger existente.
--     Toda combinacion lote/ubicacion/unidad que ya tenga historia o conteo
--     necesita su identidad estable ANTES de que el snapshot la exponga.
-- ---------------------------------------------------------------------------

INSERT INTO stock_positions (lot_id, location_id, unit)
SELECT DISTINCT mi.lot_id, m.destination_location_id, mi.unit
FROM movement_items mi
JOIN stock_movements m ON m.id = mi.movement_id
WHERE m.destination_location_id IS NOT NULL
ON CONFLICT (lot_id, location_id, unit) DO NOTHING;

INSERT INTO stock_positions (lot_id, location_id, unit)
SELECT DISTINCT mi.lot_id, m.origin_location_id, mi.unit
FROM movement_items mi
JOIN stock_movements m ON m.id = mi.movement_id
WHERE m.origin_location_id IS NOT NULL
ON CONFLICT (lot_id, location_id, unit) DO NOTHING;

INSERT INTO stock_positions (lot_id, location_id, unit)
SELECT DISTINCT sc.lot_id, sc.location_id, sc.unit
FROM stock_counts sc
ON CONFLICT (lot_id, location_id, unit) DO NOTHING;

UPDATE stock_counts sc
SET stock_position_id = sp.id
FROM stock_positions sp
WHERE sc.stock_position_id IS NULL
  AND sp.lot_id = sc.lot_id
  AND sp.location_id = sc.location_id
  AND sp.unit = sc.unit;
