package com.myplans.audit.repository;

import com.myplans.audit.entity.Historial;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface HistorialRepository extends JpaRepository<Historial, Long> {
    List<Historial> findByIdTagOrderByFechaActualizadoDesc(Integer idTag);

    Page<Historial> findByIdUsuarioOrderByFechaActualizadoDesc(Integer idUsuario, Pageable pageable);

    long countByIdTag(Integer idTag);
}