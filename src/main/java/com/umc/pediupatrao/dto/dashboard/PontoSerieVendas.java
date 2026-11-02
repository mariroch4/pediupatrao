package com.umc.pediupatrao.dto.dashboard;

import java.math.BigDecimal;

public record PontoSerieVendas(
        String rotulo,
        long pedidos,
        BigDecimal faturamento) {
}