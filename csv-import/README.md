# CSV de importación — `Planilla de movimientos 2026.xls`

Archivos generados desde la planilla real, con las columnas exactas de las tablas
PostgreSQL del modelo PapaStock (`V2__papastock_stock_model.sql`). Los `id` son UUID
determinísticos derivados del código natural, así que las claves foráneas ya vienen
resueltas y no hace falta ningún lookup durante la importación.

Regenerar es idempotente: los mismos datos producen los mismos UUID.

## Orden de importación

El orden importa porque hay FKs con `ON DELETE RESTRICT`.

| # | Archivo | Tabla destino | Filas |
| --- | --- | --- | --- |
| 1 | `01_locations.csv` | `locations` | 9 |
| 2 | `02_varieties.csv` | `varieties` | 39 |
| 3 | `03_customers.csv` | `customers` | 61 |
| 4 | `04_lots.csv` | `lots` | 116 |
| 5 | `05_stock_movements.csv` | `stock_movements` | 436 |

`99_filas_para_revision.csv` **no se importa**: lista lo que quedó fuera y por qué.

## Archivo plano de revisión

`papastock_movimientos.csv` es el mismo contenido de `05_stock_movements.csv` pero
denormalizado: 436 filas con nombres legibles en lugar de UUIDs, ordenadas por fecha.

```
fecha, tipo_movimiento, estado, remito, variedad, lote, cantidad_kg,
origen, destino, cliente, observaciones, hoja_origen, fila_origen, movement_number
```

Sirve para revisar los datos en Excel/DBeaver antes de cargarlos, o para importarlos a
una tabla de staging propia. **No** se puede importar directamente a `stock_movements`,
porque esa tabla espera UUIDs en las FKs; para eso están los cinco archivos numerados.
`movement_number` es la clave que permite cruzar ambos archivos fila a fila.

## Configuración en DBeaver

En *Import data → CSV → Importer settings*:

- **Encoding**: `UTF-8`
- **Column delimiter**: `,`
- **Quote char**: `"`
- **Header position**: `top`
- **NULL value mark**: dejar vacío, y activar **"Set empty strings to NULL"**

Ese último punto es el único imprescindible: sin él, las columnas UUID vacías
(`origin_location_id` en un `INBOUND`, `customer_id` sin cliente) llegan como cadena
vacía y PostgreSQL rechaza la fila.

Las columnas con valor por defecto en la tabla (`created_at`, `updated_at`,
`metadata`, `confirmed_at`) no están en los CSV salvo `metadata`, que viene como `{}`.

## Cómo se mapeó cada hoja

| Hoja | Movimientos | Tipo | Criterio |
| --- | --- | --- | --- |
| De campo a Frío | 20 | `INBOUND` | Destino = columna *Destino*; origen nulo (viene del campo) |
| Ingreso Tolvas Santa Ana | 65 | `INBOUND` | Destino fijo `SANTA-ANA` (la hoja no tiene columna destino) |
| Env a Frio | 71 | `INBOUND` | Destino = columna *Destino* |
| Ingreso Trevelin | 88 | `INBOUND` | Destino fijo `TREVELIN` |
| Ret Frio | 75 | `DISPATCH` | Origen = columna *Origen*; sin destino interno |
| Entregas a clientes 2026 | 117 | `DISPATCH` | Origen deducido del texto de *Observaciones*; cliente = *Destino* |

Todos los movimientos entran como `status = 'CONFIRMED'`, que es lo que hace que
`v_registered_stock` los tome como stock registrado.

Otras decisiones:

- **Lotes**: la planilla repite el mismo número de lote entre variedades (lote 50 de
  spunta y lote 55 b de yona), así que el código es `VARIEDAD-LOTE`, por ejemplo
  `AGATA-241`. La campaña se fija en `2026` por el nombre del archivo; productor,
  origen y fecha de cosecha quedan vacíos porque la planilla no los trae.
- **Cantidades**: el `.` de la planilla es separador de miles y la `,` es decimal, así
  que `35.160` se importa como `35160.000`.
- **Ubicaciones**: se unificaron variantes de escritura (`galpon-galpon` → `GALPON`,
  `sasula balcarce` → `SASULA`). Son `COLD_STORAGE` las que aparecen como origen de
  *Ret Frio* y `WAREHOUSE` las plantas y galpones.
- **Trazabilidad**: los movimientos no se duplican en `traceability_events`; la vista
  `v_lot_traceability` ya los une con los eventos.
- **Provenance**: cada fila conserva `source_file`, `source_sheet` y `source_row`, así
  que cualquier movimiento se puede rastrear hasta su celda original.

## Limitaciones reales

1. **50 de 170 pares lote+ubicación quedan con saldo negativo** (18 en `PLANTA`, 11 en
   `DOSPANCA`, 11 en `CECIVE`). Es esperable: la planilla registra salidas cuyo ingreso
   correspondiente vive en otra planilla o en el stock inicial de campaña, que no está
   en este archivo. Antes de tratar estos números como stock real hace falta cargar los
   saldos iniciales como `OPENING_BALANCE`.
2. **`P.Chica` (119 filas) no se importó.** Tiene encabezado en la fila 6 y una columna
   por destino en vez de una fila por movimiento; necesita un mapeo manual.
3. **El origen de los despachos a clientes es inferido** del texto libre de
   *Observaciones* (`en planta`, `en cecive`, `directo de santa ana`). Se importaron los
   117 casos que sí tenían un sitio identificable.
4. **`Stocks`, `DJ Panc` y `SP` son resúmenes con fórmulas**, no movimientos, y quedaron
   fuera a propósito.
5. Las ubicaciones sembradas por `V5__seed_generic_locations.sql` (Frigorífico Norte,
   Sur, Central, Galpón Principal) **no** son las de la planilla y conviven con estas
   nueve sin pisarse.
