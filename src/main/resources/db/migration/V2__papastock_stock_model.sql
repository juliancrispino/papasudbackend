-- PapaStock core model. Legacy tables (ubicaciones, lotes, movimientos) are kept intact.

CREATE TABLE IF NOT EXISTS locations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code VARCHAR(64) NOT NULL UNIQUE,
    name VARCHAR(255) NOT NULL,
    type VARCHAR(32) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    legacy_numeric_id BIGINT UNIQUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_location_type CHECK (type IN ('COLD_STORAGE', 'WAREHOUSE', 'FIELD', 'EXTERNAL'))
);

CREATE TABLE IF NOT EXISTS varieties (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code VARCHAR(64) UNIQUE,
    name VARCHAR(255) NOT NULL UNIQUE,
    active BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE IF NOT EXISTS customers (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(255) NOT NULL,
    country_code VARCHAR(8),
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS lots (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code VARCHAR(128) NOT NULL UNIQUE,
    variety_id UUID NOT NULL REFERENCES varieties (id) ON DELETE RESTRICT,
    campaign VARCHAR(64),
    producer VARCHAR(255),
    origin VARCHAR(255),
    harvest_date DATE,
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    legacy_numeric_id BIGINT UNIQUE,
    -- Deprecated: not the source of truth. Preserved for audit of the Hibernate-era columns.
    stock_declarado NUMERIC(14, 3),
    stock_verificado NUMERIC(14, 3),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS stock_movements (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    movement_number VARCHAR(64) UNIQUE,
    lot_id UUID NOT NULL REFERENCES lots (id) ON DELETE RESTRICT,
    movement_type VARCHAR(32) NOT NULL,
    origin_location_id UUID REFERENCES locations (id) ON DELETE RESTRICT,
    destination_location_id UUID REFERENCES locations (id) ON DELETE RESTRICT,
    customer_id UUID REFERENCES customers (id) ON DELETE RESTRICT,
    quantity_kg NUMERIC(14, 3) NOT NULL,
    movement_date TIMESTAMPTZ NOT NULL,
    status VARCHAR(32) NOT NULL,
    remito_number VARCHAR(128),
    notes TEXT,
    source_type VARCHAR(64),
    source_file VARCHAR(512),
    source_sheet VARCHAR(128),
    source_row INTEGER,
    source_raw JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    confirmed_at TIMESTAMPTZ,
    CONSTRAINT chk_movement_qty CHECK (quantity_kg > 0),
    CONSTRAINT chk_movement_type CHECK (movement_type IN (
        'OPENING_BALANCE', 'INBOUND', 'TRANSFER', 'DISPATCH', 'ADJUSTMENT', 'RETURN'
    )),
    CONSTRAINT chk_movement_status CHECK (status IN ('DRAFT', 'PENDING', 'CONFIRMED', 'CANCELLED')),
    CONSTRAINT chk_transfer_locations CHECK (
        movement_type <> 'TRANSFER' OR (
            origin_location_id IS NOT NULL
            AND destination_location_id IS NOT NULL
            AND origin_location_id <> destination_location_id
        )
    ),
    CONSTRAINT chk_dispatch_origin CHECK (
        movement_type <> 'DISPATCH' OR origin_location_id IS NOT NULL
    ),
    CONSTRAINT chk_inbound_destination CHECK (
        movement_type NOT IN ('INBOUND', 'OPENING_BALANCE')
        OR destination_location_id IS NOT NULL
    )
);

CREATE TABLE IF NOT EXISTS stock_counts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    lot_id UUID NOT NULL REFERENCES lots (id) ON DELETE RESTRICT,
    location_id UUID NOT NULL REFERENCES locations (id) ON DELETE RESTRICT,
    quantity_kg NUMERIC(14, 3) NOT NULL,
    counted_at TIMESTAMPTZ NOT NULL,
    notes TEXT,
    verified_by VARCHAR(255),
    source_type VARCHAR(64),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_count_qty CHECK (quantity_kg >= 0)
);

CREATE TABLE IF NOT EXISTS stock_discrepancies (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    lot_id UUID NOT NULL REFERENCES lots (id) ON DELETE RESTRICT,
    location_id UUID NOT NULL REFERENCES locations (id) ON DELETE RESTRICT,
    registered_quantity_kg NUMERIC(14, 3),
    verified_quantity_kg NUMERIC(14, 3),
    difference_kg NUMERIC(14, 3),
    status VARCHAR(32) NOT NULL,
    probable_cause TEXT,
    related_movement_id UUID REFERENCES stock_movements (id) ON DELETE RESTRICT,
    ai_analysis JSONB,
    resolution_notes TEXT,
    opened_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    resolved_at TIMESTAMPTZ,
    CONSTRAINT chk_discrepancy_status CHECK (status IN ('OPEN', 'INVESTIGATING', 'RESOLVED', 'DISMISSED'))
);

CREATE TABLE IF NOT EXISTS traceability_events (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    lot_id UUID NOT NULL REFERENCES lots (id) ON DELETE RESTRICT,
    event_type VARCHAR(64) NOT NULL,
    event_date TIMESTAMPTZ NOT NULL,
    location_id UUID REFERENCES locations (id) ON DELETE RESTRICT,
    movement_id UUID REFERENCES stock_movements (id) ON DELETE RESTRICT,
    description TEXT,
    data JSONB NOT NULL DEFAULT '{}'::jsonb,
    source_type VARCHAR(64),
    source_file VARCHAR(512),
    source_sheet VARCHAR(128),
    source_row INTEGER,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS export_operations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    operation_number VARCHAR(64) UNIQUE,
    lot_id UUID NOT NULL REFERENCES lots (id) ON DELETE RESTRICT,
    customer_id UUID REFERENCES customers (id) ON DELETE RESTRICT,
    destination_country_code VARCHAR(8) NOT NULL,
    quantity_kg NUMERIC(14, 3) NOT NULL,
    status VARCHAR(32) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_export_status CHECK (status IN ('DRAFT', 'INCOMPLETE', 'READY', 'GENERATED', 'CANCELLED')),
    CONSTRAINT chk_export_qty CHECK (quantity_kg > 0)
);

CREATE TABLE IF NOT EXISTS export_requirement_sets (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    country_code VARCHAR(8) NOT NULL,
    document_type VARCHAR(64) NOT NULL,
    title VARCHAR(255),
    source_text TEXT,
    source_reference TEXT,
    is_mock BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS export_requirement_fields (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    requirement_set_id UUID NOT NULL REFERENCES export_requirement_sets (id) ON DELETE RESTRICT,
    data_key VARCHAR(128) NOT NULL,
    label VARCHAR(255) NOT NULL,
    required BOOLEAN NOT NULL DEFAULT TRUE,
    description TEXT,
    source_reference TEXT,
    sort_order INTEGER
);

CREATE TABLE IF NOT EXISTS generated_documents (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    export_operation_id UUID REFERENCES export_operations (id) ON DELETE RESTRICT,
    document_type VARCHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL,
    snapshot_data JSONB NOT NULL,
    storage_path VARCHAR(1024),
    generated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS data_imports (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    filename VARCHAR(512),
    import_type VARCHAR(32),
    status VARCHAR(32) NOT NULL,
    rows_total INTEGER,
    rows_imported INTEGER,
    rows_failed INTEGER,
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS import_errors (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    import_id UUID NOT NULL REFERENCES data_imports (id) ON DELETE RESTRICT,
    sheet VARCHAR(128),
    row_number INTEGER,
    raw_data JSONB,
    error_message TEXT NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_lots_code ON lots (code);
CREATE INDEX IF NOT EXISTS idx_lots_variety ON lots (variety_id);
CREATE INDEX IF NOT EXISTS idx_stock_movements_lot ON stock_movements (lot_id);
CREATE INDEX IF NOT EXISTS idx_stock_movements_origin ON stock_movements (origin_location_id);
CREATE INDEX IF NOT EXISTS idx_stock_movements_destination ON stock_movements (destination_location_id);
CREATE INDEX IF NOT EXISTS idx_stock_movements_date ON stock_movements (movement_date);
CREATE INDEX IF NOT EXISTS idx_stock_movements_status ON stock_movements (status);
CREATE INDEX IF NOT EXISTS idx_stock_counts_lot_loc_at ON stock_counts (lot_id, location_id, counted_at DESC);
CREATE INDEX IF NOT EXISTS idx_traceability_lot_date ON traceability_events (lot_id, event_date DESC);
CREATE INDEX IF NOT EXISTS idx_discrepancies_lot_loc_status ON stock_discrepancies (lot_id, location_id, status);
CREATE INDEX IF NOT EXISTS idx_export_operations_lot ON export_operations (lot_id);
CREATE INDEX IF NOT EXISTS idx_customers_name ON customers (name);
