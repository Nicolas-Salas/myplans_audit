package com.myplans.audit.controller;

import com.myplans.audit.dto.HistorialRequestDTO;
import com.myplans.audit.dto.HistorialResponseDTO;
import com.myplans.audit.service.HistorialService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/historial")
@RequiredArgsConstructor
@Tag(name = "Auditoría", description = "Registro inmutable de cambios. APPEND-ONLY: solo POST y GET, jamás PUT ni DELETE (RF-22).")
public class HistorialController {

    private final HistorialService historialService;

    @Operation(summary = "Registrar evento de auditoría (interno)", description = "Solo lo invoca el Core con el header X-Internal-Token. "
            +
            "Los usuarios humanos no pueden POSTear acá.", security = @SecurityRequirement(name = "internalToken"))
    @PreAuthorize("hasRole('CORE_SERVICE')")
    @PostMapping
    public ResponseEntity<HistorialResponseDTO> create(@Valid @RequestBody HistorialRequestDTO request) {
        HistorialResponseDTO created = historialService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @Operation(summary = "Todos los eventos de auditoría", description = "Devuelve todos los registros ordenados del más reciente al más antiguo.")
    @PreAuthorize("hasAnyRole('AUDITOR', 'ADMIN')")
    @GetMapping
    public ResponseEntity<List<HistorialResponseDTO>> getAll() {
        return ResponseEntity.ok(historialService.findAll());
    }

    @Operation(summary = "Historial de cambios de un TAG", description = "Devuelve todos los registros del TAG ordenados del más reciente al más antiguo.")
    @PreAuthorize("hasAnyRole('AUDITOR', 'ADMIN')")
    @GetMapping("/tag/{idTag}")
    public ResponseEntity<List<HistorialResponseDTO>> getByTag(@PathVariable Integer idTag) {
        return ResponseEntity.ok(historialService.findByTag(idTag));
    }

    @Operation(summary = "Conteo de eventos de un TAG")
    @PreAuthorize("hasAnyRole('AUDITOR', 'ADMIN')")
    @GetMapping("/tag/{idTag}/count")
    public ResponseEntity<Map<String, Object>> countByTag(@PathVariable Integer idTag) {
        long total = historialService.countByTag(idTag);
        return ResponseEntity.ok(Map.of("idTag", idTag, "total", total));
    }
}