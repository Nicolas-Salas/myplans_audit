package com.myplans.audit.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "HISTORIAL")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Historial {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_historial")
    private Long idHistorial;

    @NotNull
    @Column(name = "id_tag", nullable = false)
    private Integer idTag;

    @NotNull
    @Column(name = "id_usuario", nullable = false)
    private Integer idUsuario;

    @Size(max = 50)
    @Column(name = "estado_anterior", length = 50)
    private String estadoAnterior;

    @NotBlank
    @Size(max = 50)
    @Column(name = "estado_nuevo", nullable = false, length = 50)
    private String estadoNuevo;

    @Column(name = "observaciones", columnDefinition = "TEXT")
    private String observaciones;

    @NotNull
    @Column(name = "fecha_actualizado", nullable = false, updatable = false)
    private LocalDateTime fechaActualizado;

    @Column(name = "por_ia", nullable = false)
    private Boolean porIa = false;
}
