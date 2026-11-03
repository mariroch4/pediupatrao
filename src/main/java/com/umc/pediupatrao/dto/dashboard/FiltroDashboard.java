package com.umc.pediupatrao.dto.dashboard;

import com.umc.pediupatrao.entity.StatusPedido;
import com.umc.pediupatrao.entity.TipoEntrega;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;

public record FiltroDashboard(
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataInicio,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataFim,
        TipoEntrega tipoEntrega,
        StatusPedido status,
        String responsavel) {

    public static FiltroDashboard vazio() {
        return new FiltroDashboard(null, null, null, null, null);
    }
}