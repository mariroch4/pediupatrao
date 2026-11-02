package com.umc.pediupatrao.dto.dashboard;

import java.time.LocalDateTime;

public record RespostaDashboard<T>(
        FiltroDashboard filtros,
        LocalDateTime atualizadoEm,
        T dados) {
}