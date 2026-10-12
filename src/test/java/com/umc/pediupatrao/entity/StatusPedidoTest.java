package com.umc.pediupatrao.entity;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class StatusPedidoTest {

    @Test
    @DisplayName("Fluxo de ENTREGA: cada status avança apenas para o próximo")
    void fluxoDeEntrega() {
        assertTrue(StatusPedido.RECEBIDO.podeIrPara(StatusPedido.EM_PREPARACAO, TipoEntrega.ENTREGA));
        assertTrue(StatusPedido.EM_PREPARACAO.podeIrPara(StatusPedido.PRONTO, TipoEntrega.ENTREGA));
        assertTrue(StatusPedido.PRONTO.podeIrPara(StatusPedido.SAIU_PARA_ENTREGA, TipoEntrega.ENTREGA));
        assertTrue(StatusPedido.SAIU_PARA_ENTREGA.podeIrPara(StatusPedido.FINALIZADO, TipoEntrega.ENTREGA));
    }

    @Test
    @DisplayName("Fluxo de RETIRADA: a saída é RETIRADO, e não SAIU PARA ENTREGA")
    void fluxoDeRetirada() {
        assertTrue(StatusPedido.PRONTO.podeIrPara(StatusPedido.RETIRADO, TipoEntrega.RETIRADA));
        assertFalse(StatusPedido.PRONTO.podeIrPara(StatusPedido.SAIU_PARA_ENTREGA, TipoEntrega.RETIRADA));
        assertFalse(StatusPedido.PRONTO.podeIrPara(StatusPedido.RETIRADO, TipoEntrega.ENTREGA));
        assertTrue(StatusPedido.RETIRADO.podeIrPara(StatusPedido.FINALIZADO, TipoEntrega.RETIRADA));
    }

    @Test
    @DisplayName("Não é permitido pular etapas (ex.: finalizar um pedido em preparação)")
    void naoPulaEtapas() {
        assertFalse(StatusPedido.RECEBIDO.podeIrPara(StatusPedido.PRONTO, TipoEntrega.ENTREGA));
        assertFalse(StatusPedido.RECEBIDO.podeIrPara(StatusPedido.FINALIZADO, TipoEntrega.ENTREGA));
        assertFalse(StatusPedido.EM_PREPARACAO.podeIrPara(StatusPedido.FINALIZADO, TipoEntrega.ENTREGA));
    }

    @Test
    @DisplayName("Não é permitido voltar etapas")
    void naoVoltaEtapas() {
        assertFalse(StatusPedido.PRONTO.podeIrPara(StatusPedido.EM_PREPARACAO, TipoEntrega.ENTREGA));
        assertFalse(StatusPedido.EM_PREPARACAO.podeIrPara(StatusPedido.RECEBIDO, TipoEntrega.ENTREGA));
    }

    @Test
    @DisplayName("FINALIZADO e CANCELADO são estados finais: nenhuma transição é permitida")
    void estadosFinais() {
        assertTrue(StatusPedido.FINALIZADO.proximosPermitidos(TipoEntrega.ENTREGA).isEmpty());
        assertTrue(StatusPedido.CANCELADO.proximosPermitidos(TipoEntrega.ENTREGA).isEmpty());
    }

    @Test
    @DisplayName("CANCELADO nunca é uma transição comum (só pela operação exclusiva do GERENTE)")
    void canceladoNaoEhTransicaoComum() {
        for (StatusPedido status : StatusPedido.values()) {
            assertFalse(status.podeIrPara(StatusPedido.CANCELADO, TipoEntrega.ENTREGA));
            assertFalse(status.podeIrPara(StatusPedido.CANCELADO, TipoEntrega.RETIRADA));
        }
    }

    @Test
    @DisplayName("Desconto e cancelamento só antes da saída (Recebido, Em preparação, Pronto)")
    void antesDaSaida() {
        assertTrue(StatusPedido.RECEBIDO.isAntesDaSaida());
        assertTrue(StatusPedido.EM_PREPARACAO.isAntesDaSaida());
        assertTrue(StatusPedido.PRONTO.isAntesDaSaida());
        assertFalse(StatusPedido.SAIU_PARA_ENTREGA.isAntesDaSaida());
        assertFalse(StatusPedido.RETIRADO.isAntesDaSaida());
        assertFalse(StatusPedido.FINALIZADO.isAntesDaSaida());
        assertFalse(StatusPedido.CANCELADO.isAntesDaSaida());
    }
}