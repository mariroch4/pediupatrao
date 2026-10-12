package com.umc.pediupatrao.entity;

public enum TipoOperacao {

    // PEDIDOS
    INCLUSAO_PEDIDO("INCLUSÃO DE PEDIDO", "bg-success"),
    ALTERACAO_PEDIDO("ALTERAÇÃO DE PEDIDO", "bg-primary"),
    ALTERACAO_STATUS_PEDIDO("ALTERAÇÃO DE STATUS DO PEDIDO", "bg-info text-dark"),
    DESCONTO_PEDIDO("DESCONTO EM PEDIDO", "bg-warning text-dark"),
    CANCELAMENTO_PEDIDO("CANCELAMENTO DE PEDIDO", "bg-danger"),

    // CLIENTES
    INCLUSAO_CLIENTE("INCLUSÃO DE CLIENTE", "bg-success"),
    ALTERACAO_CLIENTE("ALTERAÇÃO DE CLIENTE", "bg-primary"),
    EXCLUSAO_CLIENTE("EXCLUSÃO DE CLIENTE", "bg-dark"),

    // USUÁRIOS
    INCLUSAO_USUARIO("INCLUSÃO DE USUÁRIO", "bg-success"),
    ALTERACAO_USUARIO("ALTERAÇÃO DE USUÁRIO", "bg-primary"),
    EXCLUSAO_USUARIO("EXCLUSÃO DE USUÁRIO", "bg-dark");

    private final String descricao;
    private final String corBadge;

    TipoOperacao(String descricao, String corBadge) {
        this.descricao = descricao;
        this.corBadge = corBadge;
    }

    public String getDescricao() {
        return descricao;
    }

    public String getCorBadge() {
        return corBadge;
    }
}