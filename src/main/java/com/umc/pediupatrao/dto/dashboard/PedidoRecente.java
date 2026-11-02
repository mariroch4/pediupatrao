package com.umc.pediupatrao.dto.dashboard;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record PedidoRecente(
        String id,
        String clienteNome,
        String tipoEntrega,
        String tipoEntregaDescricao,
        String status,
        String statusDescricao,
        String corBadge,
        BigDecimal valorTotal,
        LocalDateTime dataHoraEntrada,
        String responsavelEntrada) {
}