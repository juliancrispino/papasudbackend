-- Vistas de stock v2: el ledger pasa a ser item-based y consciente de la unidad.
--
-- Antes: v_registered_stock sumaba stock_movements.quantity_kg (un lote por movimiento).
-- Ahora: suma movement_items, que es lo unico que permite representar un remito multi-lote.
--
-- Se mantiene la regla original: solo cuentan los movimientos CONFIRMED, destino suma y
-- origen resta. Nada de esto muta filas: el stock se calcula, no se guarda.

-- Las vistas de V3 se reemplazan, no se parchean: CREATE OR REPLACE VIEW no puede
-- agregar la columna `unit` en el medio de la lista (Postgres solo permite agregar
-- columnas al final y nunca renombrar). Se dropean en orden de dependencia inversa.
DROP VIEW IF EXISTS v_lot_traceability CASCADE;
DROP VIEW IF EXISTS v_stock_overview CASCADE;
DROP VIEW IF EXISTS v_effective_verified_stock CASCADE;
DROP VIEW IF EXISTS v_latest_stock_count CASCADE;
DROP VIEW IF EXISTS v_registered_stock CASCADE;
DROP VIEW IF EXISTS v_ledger_deltas CASCADE;

-- ---------------------------------------------------------------------------
-- Deltas elementales del ledger, por (lote, ubicacion, unidad)
-- ---------------------------------------------------------------------------
CREATE VIEW v_ledger_deltas AS
SELECT
    mi.lot_id,
    m.destination_location_id AS location_id,
    mi.unit,
    mi.dispatched_quantity AS delta_qty,
    COALESCE(m.confirmed_at, m.movement_date) AS effective_at
FROM movement_items mi
JOIN stock_movements m ON m.id = mi.movement_id
WHERE m.status = 'CONFIRMED'
  AND m.destination_location_id IS NOT NULL
UNION ALL
SELECT
    mi.lot_id,
    m.origin_location_id AS location_id,
    mi.unit,
    -mi.dispatched_quantity AS delta_qty,
    COALESCE(m.confirmed_at, m.movement_date) AS effective_at
FROM movement_items mi
JOIN stock_movements m ON m.id = mi.movement_id
WHERE m.status = 'CONFIRMED'
  AND m.origin_location_id IS NOT NULL;

-- ---------------------------------------------------------------------------
-- Stock registrado (lo que dice el ledger)
-- ---------------------------------------------------------------------------
CREATE VIEW v_registered_stock AS
SELECT
    lot_id,
    location_id,
    unit,
    SUM(delta_qty) AS registered_quantity_kg
FROM v_ledger_deltas
GROUP BY lot_id, location_id, unit;

-- ---------------------------------------------------------------------------
-- Ultimo conteo fisico por posicion
-- ---------------------------------------------------------------------------
CREATE VIEW v_latest_stock_count AS
SELECT DISTINCT ON (lot_id, location_id, unit)
    id,
    lot_id,
    location_id,
    unit,
    quantity_kg,
    observed_quantity,
    counted_at,
    notes,
    verified_by,
    source_type,
    created_at
FROM stock_counts
ORDER BY lot_id, location_id, unit, counted_at DESC, created_at DESC;

-- ---------------------------------------------------------------------------
-- Verificado efectivo = ultimo conteo + movimientos confirmados posteriores
-- ---------------------------------------------------------------------------
CREATE VIEW v_effective_verified_stock AS
SELECT
    c.lot_id,
    c.location_id,
    c.unit,
    COALESCE(c.observed_quantity, c.quantity_kg) + COALESCE(post.delta_qty, 0) AS verified_quantity_kg,
    c.counted_at AS last_verified_at
FROM v_latest_stock_count c
LEFT JOIN LATERAL (
    SELECT SUM(d.delta_qty) AS delta_qty
    FROM v_ledger_deltas d
    WHERE d.lot_id = c.lot_id
      AND d.location_id = c.location_id
      AND d.unit = c.unit
      AND d.effective_at > c.counted_at
) post ON TRUE;

-- ---------------------------------------------------------------------------
-- Vista principal del snapshot.
--
-- Se ancla en stock_positions para que cada fila tenga un id ESTABLE y persistido
-- (el stockRecordId que el frontend devuelve en /api/stock/verify y assign-shelf)
-- junto con su token de concurrencia (version).
--
-- registered_quantity_kg es la autoridad operativa cuando todavia no hubo conteo
-- fisico: verificationPending = true, pero la validacion de disponibilidad NUNCA
-- se saltea por eso (ver MovementValidationService).
-- ---------------------------------------------------------------------------
CREATE VIEW v_stock_overview AS
SELECT
    sp.id AS stock_position_id,
    sp.lot_id,
    l.code AS lot_code,
    v.name AS variety,
    sp.location_id,
    loc.name AS location_name,
    sp.unit,
    sp.shelf_id,
    sp.version,
    COALESCE(rs.registered_quantity_kg, 0) AS registered_quantity_kg,
    ev.verified_quantity_kg,
    CASE
        WHEN ev.verified_quantity_kg IS NULL THEN NULL
        ELSE ev.verified_quantity_kg - COALESCE(rs.registered_quantity_kg, 0)
    END AS difference_kg,
    papastock_has_discrepancy(ev.verified_quantity_kg, COALESCE(rs.registered_quantity_kg, 0))
        AS has_discrepancy,
    (ev.verified_quantity_kg IS NULL) AS verification_pending,
    ev.last_verified_at,
    sp.updated_at
FROM stock_positions sp
JOIN lots l ON l.id = sp.lot_id
JOIN varieties v ON v.id = l.variety_id
JOIN locations loc ON loc.id = sp.location_id
LEFT JOIN v_registered_stock rs
    ON rs.lot_id = sp.lot_id AND rs.location_id = sp.location_id AND rs.unit = sp.unit
LEFT JOIN v_effective_verified_stock ev
    ON ev.lot_id = sp.lot_id AND ev.location_id = sp.location_id AND ev.unit = sp.unit;

-- ---------------------------------------------------------------------------
-- Trazabilidad unificada: movimientos + eventos explicitos
-- ---------------------------------------------------------------------------
CREATE VIEW v_lot_traceability AS
SELECT
    sm.id,
    mi.lot_id,
    sm.movement_date AS event_date,
    sm.movement_type AS event_type,
    COALESCE(sm.destination_location_id, sm.origin_location_id) AS location_id,
    COALESCE(sm.notes, sm.remito_number) AS description,
    COALESCE(sm.source_type, 'MOVEMENT') AS source,
    COALESCE(sm.source_raw, '{}'::jsonb) AS data
FROM stock_movements sm
JOIN movement_items mi ON mi.movement_id = sm.id
UNION ALL
SELECT
    te.id,
    te.lot_id,
    te.event_date,
    te.event_type,
    te.location_id,
    te.description,
    COALESCE(te.source_type, 'TRACEABILITY_EVENT') AS source,
    te.data
FROM traceability_events te;
