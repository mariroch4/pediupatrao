package com.umc.pediupatrao.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.umc.pediupatrao.dto.NovoPedidoRequest;
import com.umc.pediupatrao.entity.Cliente;
import com.umc.pediupatrao.entity.ItemPedido;
import com.umc.pediupatrao.entity.Pedido;
import com.umc.pediupatrao.entity.Produto;
import com.umc.pediupatrao.entity.StatusPedido;
import com.umc.pediupatrao.entity.TipoEntrega;
import com.umc.pediupatrao.entity.TipoOperacao;
import com.umc.pediupatrao.exception.RegraNegocioException;
import com.umc.pediupatrao.repository.ClienteRepository;
import com.umc.pediupatrao.repository.PedidoRepository;
import com.umc.pediupatrao.repository.ProdutoRepository;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Testes das regras de negócio de pedidos (RF02, RF03, RF04 e auditoria do RF05).
 * Os repositórios são "mocks": não é necessário o MongoDB rodando.
 */
@ExtendWith(MockitoExtension.class)
class PedidoServiceTest {

    @Mock
    private PedidoRepository pedidoRepository;
    @Mock
    private ClienteRepository clienteRepository;
    @Mock
    private ProdutoRepository produtoRepository;
    @Mock
    private AuditoriaService auditoriaService;

    @InjectMocks
    private PedidoService pedidoService;

    @BeforeEach
    void configurar() {
        // save devolve o próprio pedido recebido (simula o banco)
        lenient().when(pedidoRepository.save(any(Pedido.class))).thenAnswer(invocacao -> invocacao.getArgument(0));
    }

    @AfterEach
    void limpar() {
        SecurityContextHolder.clearContext();
    }

    // ===================== RF02: valores calculados no servidor =====================

    @Test
    @DisplayName("RF02: total calculado com o preço do BANCO; status, responsável e entrada definidos pelo servidor")
    void criarPedidoCalculaTotalNoServidor() {
        autenticarComo("atendente01", "ATENDENTE");
        when(clienteRepository.findById("cliente1")).thenReturn(Optional.of(cliente()));
        when(produtoRepository.findById("produto1")).thenReturn(Optional.of(produto(45.90, true)));

        Pedido pedido = pedidoService.criarPedido(new NovoPedidoRequest("cliente1", TipoEntrega.ENTREGA, null,
                List.of(new NovoPedidoRequest.ItemRequest("produto1", 2))));

        assertValor("91.80", pedido.getValorTotal()); // 2 x 45,90
        assertEquals(StatusPedido.RECEBIDO, pedido.getStatus());
        assertEquals("atendente01", pedido.getResponsavelEntrada());
        assertNotNull(pedido.getDataHoraEntrada());
        verify(auditoriaService).registrar(eq(TipoOperacao.INCLUSAO_PEDIDO), eq("Pedido"), any(), anyList(), isNull());
    }

    @Test
    @DisplayName("RF02: produto inativo não pode entrar no pedido")
    void produtoInativoNaoEntraNoPedido() {
        autenticarComo("atendente01", "ATENDENTE");
        when(clienteRepository.findById("cliente1")).thenReturn(Optional.of(cliente()));
        when(produtoRepository.findById("produto1")).thenReturn(Optional.of(produto(45.90, false)));

        assertThrows(RegraNegocioException.class, () -> pedidoService.criarPedido(
                new NovoPedidoRequest("cliente1", TipoEntrega.ENTREGA, null,
                        List.of(new NovoPedidoRequest.ItemRequest("produto1", 1)))));
        verify(pedidoRepository, never()).save(any());
    }

    // ===================== RF04: desconto =====================

    @Test
    @DisplayName("RF04: desconto acima de 20% é recusado")
    void descontoAcimaDoLimiteEhRecusado() {
        autenticarComo("gerente01", "GERENTE");

        assertThrows(RegraNegocioException.class,
                () -> pedidoService.aplicarDesconto("pedido1", new BigDecimal("25"), null));
        verify(pedidoRepository, never()).save(any());
    }

    @Test
    @DisplayName("RF04: desconto de 20% recalcula o total no servidor e registra auditoria")
    void descontoDeVintePorCentoRecalculaTotal() {
        autenticarComo("gerente01", "GERENTE");
        pedidoCom(StatusPedido.RECEBIDO); // subtotal R$ 100,00

        Pedido pedido = pedidoService.aplicarDesconto("pedido1", new BigDecimal("20"), "Cliente fidelidade");

        assertValor("20.00", pedido.getValorDesconto());
        assertValor("80.00", pedido.getValorTotal());
        assertEquals("gerente01", pedido.getResponsavelDesconto());
        verify(auditoriaService).registrar(eq(TipoOperacao.DESCONTO_PEDIDO), eq("Pedido"), eq("pedido1"),
                anyList(), eq("Cliente fidelidade"));
    }

    @Test
    @DisplayName("RF04: desconto após a saída é recusado")
    void descontoAposSaidaEhRecusado() {
        autenticarComo("gerente01", "GERENTE");
        pedidoCom(StatusPedido.SAIU_PARA_ENTREGA);

        assertThrows(RegraNegocioException.class,
                () -> pedidoService.aplicarDesconto("pedido1", new BigDecimal("10"), null));
        verify(pedidoRepository, never()).save(any());
    }

    // ===================== RF04: cancelamento =====================

    @Test
    @DisplayName("RF04: cancelamento sem justificativa é recusado")
    void cancelamentoSemJustificativaEhRecusado() {
        autenticarComo("gerente01", "GERENTE");

        assertThrows(RegraNegocioException.class, () -> pedidoService.cancelarPedido("pedido1", "   "));
        verify(pedidoRepository, never()).save(any());
    }

    @Test
    @DisplayName("RF04/RF05: cancelamento preserva itens e valores e registra auditoria COM justificativa")
    void cancelamentoPreservaDadosERegistraAuditoria() {
        autenticarComo("gerente01", "GERENTE");
        pedidoCom(StatusPedido.EM_PREPARACAO);

        Pedido pedido = pedidoService.cancelarPedido("pedido1", "Cliente desistiu do pedido");

        assertEquals(StatusPedido.CANCELADO, pedido.getStatus());
        assertEquals("Cliente desistiu do pedido", pedido.getJustificativaCancelamento());
        assertEquals("gerente01", pedido.getResponsavelCancelamento());
        assertNotNull(pedido.getDataHoraCancelamento());
        assertEquals(1, pedido.getItens().size());       // itens preservados
        assertValor("100.00", pedido.getValorTotal());   // valores preservados
        verify(auditoriaService).registrar(eq(TipoOperacao.CANCELAMENTO_PEDIDO), eq("Pedido"), eq("pedido1"),
                anyList(), eq("Cliente desistiu do pedido"));
    }

    @Test
    @DisplayName("RF04: cancelamento após a saída é recusado")
    void cancelamentoAposSaidaEhRecusado() {
        autenticarComo("gerente01", "GERENTE");
        pedidoCom(StatusPedido.FINALIZADO);

        assertThrows(RegraNegocioException.class,
                () -> pedidoService.cancelarPedido("pedido1", "Cliente desistiu do pedido"));
        verify(pedidoRepository, never()).save(any());
    }

    // ===================== RF03: transições de status =====================

    @Test
    @DisplayName("RF03: finalizar um pedido EM PREPARAÇÃO é recusado (exemplo do enunciado)")
    void finalizarPedidoEmPreparacaoEhRecusado() {
        autenticarComo("atendente01", "ATENDENTE");
        pedidoCom(StatusPedido.EM_PREPARACAO);

        RegraNegocioException erro = assertThrows(RegraNegocioException.class,
                () -> pedidoService.alterarStatus("pedido1", StatusPedido.FINALIZADO));
        assertTrue(erro.getMessage().contains("Transição inválida"));
        verify(pedidoRepository, never()).save(any());
    }

    @Test
    @DisplayName("RF03: a saída registra obrigatoriamente data/hora e responsável")
    void saidaRegistraResponsavelEDataHora() {
        autenticarComo("atendente01", "ATENDENTE");
        pedidoCom(StatusPedido.PRONTO);

        Pedido pedido = pedidoService.alterarStatus("pedido1", StatusPedido.SAIU_PARA_ENTREGA);

        assertEquals(StatusPedido.SAIU_PARA_ENTREGA, pedido.getStatus());
        assertEquals("atendente01", pedido.getResponsavelSaida());
        assertNotNull(pedido.getDataHoraSaida());
        verify(auditoriaService).registrar(eq(TipoOperacao.ALTERACAO_STATUS_PEDIDO), eq("Pedido"), eq("pedido1"),
                anyList(), isNull());
    }

    @Test
    @DisplayName("RF03: pedido cancelado não pode ter o status alterado")
    void pedidoCanceladoNaoMudaStatus() {
        autenticarComo("atendente01", "ATENDENTE");
        pedidoCom(StatusPedido.CANCELADO);

        assertThrows(RegraNegocioException.class,
                () -> pedidoService.alterarStatus("pedido1", StatusPedido.EM_PREPARACAO));
        verify(pedidoRepository, never()).save(any());
    }

    // ===================== auxiliares =====================

    private void autenticarComo(String usuario, String perfil) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                usuario, null, List.of(new SimpleGrantedAuthority("ROLE_" + perfil))));
    }

    /** Pedido de entrega com 2 x R$ 50,00 (subtotal R$ 100,00) no status informado. */
    private Pedido pedidoCom(StatusPedido status) {
        Pedido pedido = new Pedido();
        pedido.setId("pedido1");
        pedido.setClienteId("cliente1");
        pedido.setClienteNome("Maria Teste");
        pedido.setTipoEntrega(TipoEntrega.ENTREGA);
        pedido.setItens(new ArrayList<>(List.of(
                new ItemPedido("produto1", "Pizza Calabresa", 2, new BigDecimal("50.00")))));
        pedido.setPercentualDesconto(new BigDecimal("0.00"));
        pedido.setSubtotal(new BigDecimal("100.00"));
        pedido.setValorDesconto(new BigDecimal("0.00"));
        pedido.setValorTotal(new BigDecimal("100.00"));
        pedido.setStatus(status);
        lenient().when(pedidoRepository.findById("pedido1")).thenReturn(Optional.of(pedido));
        return pedido;
    }

    private Cliente cliente() {
        Cliente cliente = new Cliente();
        cliente.setId("cliente1");
        cliente.setNome("Maria Teste");
        return cliente;
    }

    private Produto produto(double preco, boolean ativo) {
        Produto produto = new Produto();
        produto.setId("produto1");
        produto.setNome("Pizza Calabresa");
        produto.setPreco(preco);
        produto.setAtivo(ativo);
        return produto;
    }

    /** Compara valores monetários ignorando a escala (91.8 == 91.80). */
    private static void assertValor(String esperado, BigDecimal atual) {
        assertEquals(0, new BigDecimal(esperado).compareTo(atual),
                "Esperado R$ " + esperado + ", obtido R$ " + atual);
    }
}