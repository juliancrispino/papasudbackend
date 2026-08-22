package com.hackaton.papasud.operaciones.service;

import com.hackaton.papasud.domain.entity.Lote;
import com.hackaton.papasud.domain.entity.Movimiento;
import com.hackaton.papasud.domain.enums.TipoMovimiento;
import com.hackaton.papasud.exception.InsufficientStockException;
import com.hackaton.papasud.exception.StockMismatchException;
import com.hackaton.papasud.operaciones.dto.DespachoRequestDto;
import com.hackaton.papasud.repository.LoteRepository;
import com.hackaton.papasud.repository.MovimientoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class OperacionesService {

    private final LoteRepository loteRepository;
    private final MovimientoRepository movimientoRepository;

    @Transactional
    public void registrarDespacho(DespachoRequestDto request) {
        Lote lote = loteRepository.findById(request.loteId())
                .orElseThrow(() -> new IllegalArgumentException("Lote no encontrado"));

        if (lote.getStockDeclarado().compareTo(lote.getStockVerificado()) != 0) {
            throw new StockMismatchException("Bloqueo de despacho: El stock declarado no coincide con el verificado para el lote " + lote.getId());
        }

        if (request.cantidad().compareTo(lote.getStockVerificado()) > 0) {
            throw new InsufficientStockException("Stock insuficiente para el lote " + lote.getId() + ". Solicitado: " + request.cantidad() + ", Disponible: " + lote.getStockVerificado());
        }

        // Actualizar stock
        lote.setStockDeclarado(lote.getStockDeclarado().subtract(request.cantidad()));
        lote.setStockVerificado(lote.getStockVerificado().subtract(request.cantidad()));
        loteRepository.save(lote);

        // Registrar movimiento
        Movimiento movimiento = Movimiento.builder()
                .lote(lote)
                .origen(lote.getUbicacion()) // El origen es donde estaba el lote
                .destino(null) // Para despacho no hay destino interno, o podríamos tener ubicacion "CLIENTE"
                .cantidad(request.cantidad())
                .fecha(LocalDateTime.now())
                .tipo(TipoMovimiento.DESPACHO)
                .build();
        
        movimientoRepository.save(movimiento);
    }
}
