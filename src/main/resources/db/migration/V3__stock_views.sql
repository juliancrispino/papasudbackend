-- Registered stock: only CONFIRMED movements. destination +, origin -.
CREATE OR REPLACE VIEW v_registered_stock AS
SELECT
    lot_id,
    location_id,
    SUM(delta_kg) AS registered_quantity_kg
FROM (
    SELECT
        lot_id,
        destination_location_id AS location_id,
        quantity_kg AS delta_kg
    FROM stock_movements
    WHERE status = 'CONFIRMED'
      AND destination_location_id IS NOT NULL
    UNION ALL
    SELECT
        lot_id,
        origin_location_id AS location_id,
        -quantity_kg AS delta_kg
    FROM stock_movements
    WHERE status = 'CONFIRMED'
      AND origin_location_id IS NOT NULL
) deltas
WHERE location_id IS NOT NULL
GROUP BY lot_id, location_id;

CREATE OR REPLACE VIEW v_latest_stock_count AS
SELECT DISTINCT ON (lot_id, location_id)
    id,
    lot_id,
    location_id,
    quantity_kg,
    counted_at,
    notes,
    verified_by,
    source_type,
    created_at
FROM stock_counts
ORDER BY lot_id, location_id, counted_at DESC, created_at DESC;

-- Effective verified = last physical count + confirmed movements after that count.
CREATE OR REPLACE VIEW v_effective_verified_stock AS
SELECT
    c.lot_id,
    c.location_id,
    c.quantity_kg + COALESCE(post.delta_kg, 0) AS verified_quantity_kg,
    c.counted_at AS last_verified_at
FROM v_latest_stock_count c
LEFT JOIN LATERAL (
    SELECT SUM(d.delta_kg) AS delta_kg
    FROM (
        SELECT m.quantity_kg AS delta_kg
        FROM stock_movements m
        WHERE m.status = 'CONFIRMED'
          AND m.lot_id = c.lot_id
          AND m.destination_location_id = c.location_id
          AND COALESCE(m.confirmed_at, m.movement_date) > c.counted_at
        UNION ALL
        SELECT -m.quantity_kg AS delta_kg
        FROM stock_movements m
        WHERE m.status = 'CONFIRMED'
          AND m.lot_id = c.lot_id
          AND m.origin_location_id = c.location_id
          AND COALESCE(m.confirmed_at, m.movement_date) > c.counted_at
    ) d
) post ON TRUE;

CREATE OR REPLACE FUNCTION papastock_has_discrepancy(
    verified_quantity_kg NUMERIC,
    registered_quantity_kg NUMERIC
) RETURNS BOOLEAN
LANGUAGE sql
IMMUTABLE
AS $$
    SELECT verified_quantity_kg IS NOT NULL
       AND registered_quantity_kg IS DISTINCT FROM verified_quantity_kg;
$$;

CREATE OR REPLACE VIEW v_stock_overview AS
WITH pairs AS (
    SELECT lot_id, location_id FROM v_registered_stock
    UNION
    SELECT lot_id, location_id FROM v_effective_verified_stock
)
SELECT
    p.lot_id,
    l.code AS lot_code,
    v.name AS variety,
    p.location_id,
    loc.name AS location_name,
    COALESCE(rs.registered_quantity_kg, 0) AS registered_quantity_kg,
    ev.verified_quantity_kg,
    CASE
        WHEN ev.verified_quantity_kg IS NULL THEN NULL
        ELSE ev.verified_quantity_kg - COALESCE(rs.registered_quantity_kg, 0)
    END AS difference_kg,
    papastock_has_discrepancy(ev.verified_quantity_kg, COALESCE(rs.registered_quantity_kg, 0)) AS has_discrepancy,
    (ev.verified_quantity_kg IS NULL) AS verification_pending,
    ev.last_verified_at
FROM pairs p
JOIN lots l ON l.id = p.lot_id
JOIN varieties v ON v.id = l.variety_id
JOIN locations loc ON loc.id = p.location_id
LEFT JOIN v_registered_stock rs
    ON rs.lot_id = p.lot_id AND rs.location_id = p.location_id
LEFT JOIN v_effective_verified_stock ev
    ON ev.lot_id = p.lot_id AND ev.location_id = p.location_id;

CREATE OR REPLACE VIEW v_lot_traceability AS
SELECT
    sm.id,
    sm.lot_id,
    sm.movement_date AS event_date,
    sm.movement_type AS event_type,
    COALESCE(sm.destination_location_id, sm.origin_location_id) AS location_id,
    COALESCE(sm.notes, sm.remito_number) AS description,
    COALESCE(sm.source_type, 'MOVEMENT') AS source,
    COALESCE(sm.source_raw, '{}'::jsonb) AS data
FROM stock_movements sm
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
