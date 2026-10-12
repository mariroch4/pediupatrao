package com.umc.pediupatrao.dto;

import com.umc.pediupatrao.entity.TipoEntrega;
import java.util.List;

public record NovoPedidoRequest(
        String clienteId,
        TipoEntrega tipoEntrega,
        String observacao,
        List<ItemRequest> itens) {

    public record ItemRequest(String produtoId, Integer quantidade) {
    }
}