package com.umc.pediupatrao.dto.dashboard;

import java.math.BigDecimal;
import java.time.LocalDate;

public record IndicadoresDashboard(
        LocalDate dataInicio,
        LocalDate dataFim,
        BigDecimal faturamento,
        long totalPedidos,
        long pedidosValidos,
        BigDecimal ticketMedio,
        long itensVendidos,
        BigDecimal totalDescontos,
        long pedidosComDesconto,
        long pedidosCancelados,
        BigDecimal taxaCancelamento,
        Comparativo periodoAnterior) {

    public record Comparativo(
            LocalDate dataInicio,
            LocalDate dataFim,
            BigDecimal faturamento,
            long totalPedidos,
            BigDecimal ticketMedio,
            BigDecimal variacaoFaturamento,
            BigDecimal variacaoPedidos,
            BigDecimal variacaoTicketMedio) {
    }
}