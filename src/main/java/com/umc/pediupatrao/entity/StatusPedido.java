package com.umc.pediupatrao.entity;

import java.util.List;

public enum StatusPedido {

    RECEBIDO("Recebido", "bg-info text-dark", null, "bi-inbox"),
    EM_PREPARACAO("Em preparação", "bg-warning text-dark", "Enviar para a cozinha", "bi-fire"),
    PRONTO("Pronto", "bg-primary", "Marcar como pronto", "bi-check2-circle"),
    SAIU_PARA_ENTREGA("Saiu para entrega", "bg-secondary", "Registrar saída para entrega", "bi-scooter"),
    RETIRADO("Retirado", "bg-secondary", "Registrar retirada pelo cliente", "bi-bag-check"),
    FINALIZADO("Finalizado", "bg-success", "Finalizar atendimento", "bi-flag-fill"),
    CANCELADO("Cancelado", "bg-danger", null, "bi-x-octagon");

    private final String descricao;
    private final String corBadge;
    private final String acao;  
    private final String icone;  

    StatusPedido(String descricao, String corBadge, String acao, String icone) {
        this.descricao = descricao;
        this.corBadge = corBadge;
        this.acao = acao;
        this.icone = icone;
    }

    public String getDescricao() {
        return descricao;
    }

    public String getCorBadge() {
        return corBadge;
    }

    public String getAcao() {
        return acao;
    }

    public String getIcone() {
        return icone;
    }

    public List<StatusPedido> proximosPermitidos(TipoEntrega tipoEntrega) {
        return switch (this) {
            case RECEBIDO -> List.of(EM_PREPARACAO);
            case EM_PREPARACAO -> List.of(PRONTO);
            case PRONTO -> tipoEntrega == TipoEntrega.RETIRADA
                    ? List.of(RETIRADO)
                    : List.of(SAIU_PARA_ENTREGA);
            case SAIU_PARA_ENTREGA, RETIRADO -> List.of(FINALIZADO);
            case FINALIZADO, CANCELADO -> List.of(); // estados finais: nada mais é permitido
        };
    }

    public boolean podeIrPara(StatusPedido destino, TipoEntrega tipoEntrega) {
        return proximosPermitidos(tipoEntrega).contains(destino);
    }

    public boolean isSaida() {
        return this == SAIU_PARA_ENTREGA || this == RETIRADO;
    }

    public boolean isAntesDaSaida() {
        return this == RECEBIDO || this == EM_PREPARACAO || this == PRONTO;
    }

    public static List<StatusPedido> fluxoPara(TipoEntrega tipoEntrega) {
        StatusPedido saida = tipoEntrega == TipoEntrega.RETIRADA ? RETIRADO : SAIU_PARA_ENTREGA;
        return List.of(RECEBIDO, EM_PREPARACAO, PRONTO, saida, FINALIZADO);
    }
}