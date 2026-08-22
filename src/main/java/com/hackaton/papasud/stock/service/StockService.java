package com.hackaton.papasud.stock.service;

import com.hackaton.papasud.domain.entity.Lote;
import com.hackaton.papasud.domain.entity.Movimiento;
import com.hackaton.papasud.repository.LoteRepository;
import com.hackaton.papasud.repository.MovimientoRepository;
import com.hackaton.papasud.stock.dto.DashboardResponseDto;
import com.hackaton.papasud.stock.dto.LoteDto;
import com.hackaton.papasud.stock.dto.MovimientoDto;
import com.hackaton.papasud.stock.dto.UbicacionStockDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class StockService {

    private final LoteRepository loteRepository;
    private final MovimientoRepository movimientoRepository;

    @Transactional(readOnly = true)
    public DashboardResponseDto obtenerDashboard() {
        List<Lote> lotes = loteRepository.findAll();

        Map<Long, List<Lote>> lotesPorUbicacion = lotes.stream()
                .collect(Collectors.groupingBy(lote -> lote.getUbicacion().getId()));

        boolean tieneDiscrepanciasGlobal = false;
        
        List<UbicacionStockDto> ubicacionesDto = lotesPorUbicacion.entrySet().stream()
                .map(entry -> {
                    List<Lote> lotesUbicacion = entry.getValue();
                    var ubicacion = lotesUbicacion.get(0).getUbicacion();
                    
                    List<LoteDto> lotesDto = lotesUbicacion.stream().map(lote -> {
                        boolean discrepancia = lote.getStockDeclarado().compareTo(lote.getStockVerificado()) != 0;
                        return new LoteDto(
                                lote.getId(),
                                lote.getVariedad(),
                                lote.getStockDeclarado(),
                                lote.getStockVerificado(),
                                discrepancia
                        );
                    }).toList();

                    return new UbicacionStockDto(
                            ubicacion.getId(),
                            ubicacion.getNombre(),
                            ubicacion.getTipo().name(),
                            lotesDto
                    );
                }).toList();

        tieneDiscrepanciasGlobal = ubicacionesDto.stream()
                .flatMap(u -> u.lotes().stream())
                .anyMatch(LoteDto::tieneDiscrepancia);

        return new DashboardResponseDto(tieneDiscrepanciasGlobal, ubicacionesDto);
    }

    @Transactional(readOnly = true)
    public List<MovimientoDto> obtenerTrazabilidad(Long loteId) {
        if (!loteRepository.existsById(loteId)) {
            throw new IllegalArgumentException("Lote no encontrado con ID: " + loteId);
        }

        return movimientoRepository.findByLoteIdOrderByFechaDesc(loteId).stream()
                .map(mov -> new MovimientoDto(
                        mov.getId(),
                        mov.getTipo().name(),
                        mov.getCantidad(),
                        mov.getFecha(),
                        mov.getOrigen() != null ? mov.getOrigen().getNombre() : "N/A",
                        mov.getDestino() != null ? mov.getDestino().getNombre() : "N/A"
                )).toList();
    }
}
