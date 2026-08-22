INSERT INTO locations (code, name, type, active)
SELECT 'COLD-01', 'Frigorífico 1', 'COLD_STORAGE', TRUE
WHERE NOT EXISTS (SELECT 1 FROM locations WHERE code = 'COLD-01');

INSERT INTO locations (code, name, type, active)
SELECT 'COLD-02', 'Frigorífico 2', 'COLD_STORAGE', TRUE
WHERE NOT EXISTS (SELECT 1 FROM locations WHERE code = 'COLD-02');

INSERT INTO locations (code, name, type, active)
SELECT 'COLD-03', 'Frigorífico 3', 'COLD_STORAGE', TRUE
WHERE NOT EXISTS (SELECT 1 FROM locations WHERE code = 'COLD-03');

INSERT INTO locations (code, name, type, active)
SELECT 'WH-01', 'Galpón', 'WAREHOUSE', TRUE
WHERE NOT EXISTS (SELECT 1 FROM locations WHERE code = 'WH-01');
