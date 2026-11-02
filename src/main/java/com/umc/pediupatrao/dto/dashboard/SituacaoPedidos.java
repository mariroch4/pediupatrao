package com.umc.pediupatrao.dto.dashboard;

import java.util.List;

public record SituacaoPedidos(
        long totalPedidos,
        long emAndamento,
        long finalizados,
        long cancelados,
        List<StatusQuantidade> porStatus,
        Double tempoMedioPreparoMinutos,
        Double tempoMedioAteSaidaMinutos,
        Double tempoMedioTotalMinutos) {
}