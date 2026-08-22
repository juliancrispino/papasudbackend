-- Safe legacy migration:
-- 1. Current lote.stock_declarado becomes OPENING_BALANCE (ledger truth).
-- 2. Current lote.stock_verificado becomes a stock_count at a clearly marked migration instant.
-- 3. Historical movimientos are copied as CANCELLED audit rows so they do not double-count the ledger.
-- 4. Original tables are not dropped.

INSERT INTO locations (id, code, name, type, active, legacy_numeric_id, created_at, updated_at)
SELECT
    gen_random_uuid(),
    'LEGACY-' || u.id,
    u.nombre,
    CASE
        WHEN UPPER(u.tipo) IN ('FRIGORIFICO', 'COLD_STORAGE') THEN 'COLD_STORAGE'
        WHEN UPPER(u.tipo) IN ('GALPON', 'WAREHOUSE') THEN 'WAREHOUSE'
        WHEN UPPER(u.tipo) = 'FIELD' THEN 'FIELD'
        ELSE 'EXTERNAL'
    END,
    TRUE,
    u.id,
    NOW(),
    NOW()
FROM ubicaciones u
WHERE NOT EXISTS (
    SELECT 1 FROM locations loc WHERE loc.legacy_numeric_id = u.id
);

INSERT INTO varieties (id, code, name, active)
SELECT
    gen_random_uuid(),
    UPPER(REGEXP_REPLACE(l.variedad, '\s+', '-', 'g')),
    l.variedad,
    TRUE
FROM (
    SELECT DISTINCT variedad FROM lotes WHERE variedad IS NOT NULL AND variedad <> ''
) l
WHERE NOT EXISTS (
    SELECT 1 FROM varieties v WHERE LOWER(v.name) = LOWER(l.variedad)
);

INSERT INTO lots (
    id, code, variety_id, campaign, producer, origin, harvest_date, metadata, active,
    legacy_numeric_id, stock_declarado, stock_verificado, created_at, updated_at
)
SELECT
    gen_random_uuid(),
    COALESCE(NULLIF(l.variedad, ''), 'L-' || l.id),
    v.id,
    NULL,
    NULL,
    NULL,
    NULL,
    jsonb_build_object(
        'legacy_migration', TRUE,
        'legacy_lote_id', l.id,
        'legacy_ubicacion_id', l.ubicacion_id
    ),
    TRUE,
    l.id,
    l.stock_declarado,
    l.stock_verificado,
    NOW(),
    NOW()
FROM lotes l
JOIN varieties v ON LOWER(v.name) = LOWER(l.variedad)
WHERE NOT EXISTS (
    SELECT 1 FROM lots x WHERE x.legacy_numeric_id = l.id
);

INSERT INTO stock_movements (
    id, movement_number, lot_id, movement_type, origin_location_id, destination_location_id,
    quantity_kg, movement_date, status, notes, source_type, source_raw, created_at, updated_at, confirmed_at
)
SELECT
    gen_random_uuid(),
    'LEGACY-OB-' || l.legacy_numeric_id,
    l.id,
    'OPENING_BALANCE',
    NULL,
    loc.id,
    GREATEST(l.stock_declarado, 0.001),
    NOW(),
    'CONFIRMED',
    'Saldo inicial migrado desde lotes.stock_declarado',
    'legacy_migration',
    jsonb_build_object(
        'legacy_lote_id', l.legacy_numeric_id,
        'stock_declarado', l.stock_declarado
    ),
    NOW(),
    NOW(),
    NOW()
FROM lots l
JOIN lotes old_l ON old_l.id = l.legacy_numeric_id
JOIN locations loc ON loc.legacy_numeric_id = old_l.ubicacion_id
WHERE l.stock_declarado IS NOT NULL
  AND l.stock_declarado > 0
  AND NOT EXISTS (
      SELECT 1 FROM stock_movements sm
      WHERE sm.movement_number = 'LEGACY-OB-' || l.legacy_numeric_id
  );

INSERT INTO stock_counts (
    id, lot_id, location_id, quantity_kg, counted_at, notes, verified_by, source_type, created_at
)
SELECT
    gen_random_uuid(),
    l.id,
    loc.id,
    GREATEST(l.stock_verificado, 0),
    TIMESTAMPTZ '2026-08-22 00:00:00+00',
    'Conteo migrado desde lotes.stock_verificado',
    'legacy_migration',
    'legacy_migration',
    NOW()
FROM lots l
JOIN lotes old_l ON old_l.id = l.legacy_numeric_id
JOIN locations loc ON loc.legacy_numeric_id = old_l.ubicacion_id
WHERE l.stock_verificado IS NOT NULL
  AND NOT EXISTS (
      SELECT 1 FROM stock_counts sc
      WHERE sc.lot_id = l.id
        AND sc.location_id = loc.id
        AND sc.source_type = 'legacy_migration'
  );

INSERT INTO stock_movements (
    id, movement_number, lot_id, movement_type, origin_location_id, destination_location_id,
    quantity_kg, movement_date, status, notes, source_type, source_raw, created_at, updated_at, confirmed_at
)
SELECT
    gen_random_uuid(),
    'LEGACY-MOV-' || m.id,
    l.id,
    CASE m.tipo
        WHEN 'TRASLADO' THEN 'TRANSFER'
        WHEN 'DESPACHO' THEN 'DISPATCH'
        WHEN 'AJUSTE' THEN 'ADJUSTMENT'
        ELSE 'ADJUSTMENT'
    END,
    orig.id,
    dest.id,
    GREATEST(m.cantidad, 0.001),
    COALESCE(m.fecha, NOW())::timestamptz,
    'CANCELLED',
    'Movimiento histórico conservado fuera del ledger para no duplicar stock_declarado actual',
    'legacy_migration',
    jsonb_build_object(
        'legacy_movimiento_id', m.id,
        'legacy_tipo', m.tipo,
        'ledger_excluded', TRUE
    ),
    NOW(),
    NOW(),
    NULL
FROM movimientos m
JOIN lots l ON l.legacy_numeric_id = m.lote_id
LEFT JOIN locations orig ON orig.legacy_numeric_id = m.origen_id
LEFT JOIN locations dest ON dest.legacy_numeric_id = m.destino_id
WHERE NOT EXISTS (
    SELECT 1 FROM stock_movements sm WHERE sm.movement_number = 'LEGACY-MOV-' || m.id
)
AND (
    CASE m.tipo
        WHEN 'TRASLADO' THEN orig.id IS NOT NULL AND dest.id IS NOT NULL AND orig.id <> dest.id
        WHEN 'DESPACHO' THEN orig.id IS NOT NULL
        ELSE TRUE
    END
);

INSERT INTO traceability_events (
    id, lot_id, event_type, event_date, location_id, description, data, source_type, created_at
)
SELECT
    gen_random_uuid(),
    l.id,
    CASE m.tipo
        WHEN 'TRASLADO' THEN 'STORAGE'
        WHEN 'DESPACHO' THEN 'DISPATCH'
        ELSE 'INSPECTION'
    END,
    COALESCE(m.fecha, NOW())::timestamptz,
    COALESCE(dest.id, orig.id),
    'Evento migrado desde movimientos.id=' || m.id,
    jsonb_build_object(
        'legacy_movimiento_id', m.id,
        'legacy_tipo', m.tipo,
        'cantidad', m.cantidad
    ),
    'legacy_migration',
    NOW()
FROM movimientos m
JOIN lots l ON l.legacy_numeric_id = m.lote_id
LEFT JOIN locations orig ON orig.legacy_numeric_id = m.origen_id
LEFT JOIN locations dest ON dest.legacy_numeric_id = m.destino_id
WHERE NOT EXISTS (
    SELECT 1 FROM traceability_events te
    WHERE te.source_type = 'legacy_migration'
      AND te.data->>'legacy_movimiento_id' = m.id::text
);
