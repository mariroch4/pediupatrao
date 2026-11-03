package com.umc.pediupatrao.dto.dashboard;

import java.math.BigDecimal;

public record ProdutoMaisVendido(
        int posicao,
        String produtoId,
        String nome,
        long quantidade,
        BigDecimal faturamento,
        long pedidos,
        BigDecimal participacaoFaturamento) {
}
