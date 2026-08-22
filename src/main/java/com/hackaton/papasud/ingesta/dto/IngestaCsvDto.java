package com.hackaton.papasud.ingesta.dto;

import com.opencsv.bean.CsvBindByName;
import lombok.Data;

@Data
public class IngestaCsvDto {
    @CsvBindByName(column = "ubicacion_nombre", required = true)
    private String ubicacionNombre;

    @CsvBindByName(column = "ubicacion_tipo", required = true)
    private String ubicacionTipo;

    @CsvBindByName(column = "lote_variedad", required = true)
    private String loteVariedad;

    @CsvBindByName(column = "stock_declarado", required = true)
    private String stockDeclarado;

    @CsvBindByName(column = "stock_verificado", required = true)
    private String stockVerificado;
}
