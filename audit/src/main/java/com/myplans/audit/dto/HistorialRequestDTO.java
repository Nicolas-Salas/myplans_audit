package com.myplans.audit.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record HistorialRequestDTO(

        @NotNull(message = "idTag es obligatorio")
        Integer idTag,

        @NotNull(message = "idUsuario es obligatorio")
        Integer idUsuario,

        @Size(max = 50)
        String estadoAnterior,

        @NotBlank(message = "estadoNuevo es obligatorio")
        @Size(max = 50, message = "estadoNuevo debe tener máximo 50 caracteres")
        String estadoNuevo,

        String observaciones,

        Boolean porIa
) {
}