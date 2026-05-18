package com.myplans.audit.dto;

import java.time.LocalDateTime;

public record HistorialResponseDTO(
        Long idHistorial,
        Integer idTag,
        Integer idUsuario,
        String estadoAnterior,
        String estadoNuevo,
        String observaciones,
        LocalDateTime fechaActualizado
) {
}