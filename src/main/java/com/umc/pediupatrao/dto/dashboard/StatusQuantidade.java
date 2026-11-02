package com.umc.pediupatrao.dto.dashboard;

import java.math.BigDecimal;

public record StatusQuantidade(
        String status,
        String descricao,
        String corBadge,
        String icone,
        long quantidade,
        BigDecimal percentual) {
}