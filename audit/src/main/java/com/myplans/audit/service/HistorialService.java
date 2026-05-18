package com.myplans.audit.service;

import com.myplans.audit.dto.HistorialRequestDTO;
import com.myplans.audit.dto.HistorialResponseDTO;
import com.myplans.audit.entity.Historial;
import com.myplans.audit.repository.HistorialRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.domain.Sort;

@Service
@RequiredArgsConstructor
public class HistorialService {

    private final HistorialRepository historialRepository;

    @Transactional
    public HistorialResponseDTO create(HistorialRequestDTO request) {
        Historial historial = Historial.builder()
                .idTag(request.idTag())
                .idUsuario(request.idUsuario())
                .estadoAnterior(request.estadoAnterior())
                .estadoNuevo(request.estadoNuevo())
                .observaciones(request.observaciones())
                .fechaActualizado(LocalDateTime.now())
                .build();

        historial = historialRepository.save(historial);
        return toResponse(historial);
    }

    @Transactional(readOnly = true)
    public List<HistorialResponseDTO> findByTag(Integer idTag) {
        return historialRepository.findByIdTagOrderByFechaActualizadoDesc(idTag).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public long countByTag(Integer idTag) {
        return historialRepository.countByIdTag(idTag);
    }

    @Transactional(readOnly = true)
    public List<HistorialResponseDTO> findAll() {
        return historialRepository.findAll(Sort.by(Sort.Direction.DESC, "fechaActualizado")).stream()
                .map(this::toResponse)
                .toList();
    }

    private HistorialResponseDTO toResponse(Historial h) {
        return new HistorialResponseDTO(
                h.getIdHistorial(),
                h.getIdTag(),
                h.getIdUsuario(),
                h.getEstadoAnterior(),
                h.getEstadoNuevo(),
                h.getObservaciones(),
                h.getFechaActualizado());
    }
}