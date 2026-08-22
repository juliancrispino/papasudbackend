package com.hackaton.papasud.ingesta.service;

import com.hackaton.papasud.domain.entity.Lote;
import com.hackaton.papasud.domain.entity.Movimiento;
import com.hackaton.papasud.domain.entity.Ubicacion;
import com.hackaton.papasud.domain.enums.TipoMovimiento;
import com.hackaton.papasud.domain.enums.TipoUbicacion;
import com.hackaton.papasud.ingesta.dto.IngestaCsvDto;
import com.hackaton.papasud.repository.LoteRepository;
import com.hackaton.papasud.repository.MovimientoRepository;
import com.hackaton.papasud.repository.UbicacionRepository;
import com.opencsv.bean.CsvToBean;
import com.opencsv.bean.CsvToBeanBuilder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.Reader;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class IngestaService {

    private final UbicacionRepository ubicacionRepository;
    private final LoteRepository loteRepository;
    private final MovimientoRepository movimientoRepository;

    @Transactional
    public void procesarArchivo(MultipartFile archivo) {
        try (Reader reader = new BufferedReader(new InputStreamReader(archivo.getInputStream()))) {
            CsvToBean<IngestaCsvDto> csvToBean = new CsvToBeanBuilder<IngestaCsvDto>(reader)
                    .withType(IngestaCsvDto.class)
                    .withIgnoreLeadingWhiteSpace(true)
                    .build();

            List<IngestaCsvDto> lineas = csvToBean.parse();

            for (IngestaCsvDto linea : lineas) {
                // 1. Ubicacion
                Ubicacion ubicacion = ubicacionRepository.findByNombre(linea.getUbicacionNombre())
                        .orElseGet(() -> {
                            Ubicacion nuevaUbicacion = Ubicacion.builder()
                                    .nombre(linea.getUbicacionNombre())
                                    .tipo(TipoUbicacion.valueOf(linea.getUbicacionTipo().toUpperCase()))
                                    .build();
                            return ubicacionRepository.save(nuevaUbicacion);
                        });

                // 2. Lote
                BigDecimal declarado = new BigDecimal(linea.getStockDeclarado());
                BigDecimal verificado = new BigDecimal(linea.getStockVerificado());
                
                Lote lote = Lote.builder()
                        .variedad(linea.getLoteVariedad())
                        .ubicacion(ubicacion)
                        .stockDeclarado(declarado)
                        .stockVerificado(verificado)
                        .build();
                lote = loteRepository.save(lote);

                // 3. Movimiento inicial (AJUSTE)
                Movimiento movimiento = Movimiento.builder()
                        .lote(lote)
                        .destino(ubicacion)
                        .cantidad(verificado)
                        .fecha(LocalDateTime.now())
                        .tipo(TipoMovimiento.AJUSTE)
                        .build();
                movimientoRepository.save(movimiento);
            }

            log.info("Archivo procesado exitosamente. Líneas procesadas: {}", lineas.size());
        } catch (Exception e) {
            log.error("Error al procesar el archivo CSV", e);
            throw new RuntimeException("Error al procesar el archivo CSV: " + e.getMessage(), e);
        }
    }
}
