package com.umc.pediupatrao.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.umc.pediupatrao.dto.dashboard.AgrupamentoVendas;
import com.umc.pediupatrao.dto.dashboard.CriterioRanking;
import com.umc.pediupatrao.dto.dashboard.FiltroDashboard;
import com.umc.pediupatrao.dto.dashboard.IndicadoresDashboard;
import com.umc.pediupatrao.dto.dashboard.PedidoRecente;
import com.umc.pediupatrao.dto.dashboard.PontoSerieVendas;
import com.umc.pediupatrao.dto.dashboard.ProdutoMaisVendido;
import com.umc.pediupatrao.dto.dashboard.SituacaoPedidos;
import com.umc.pediupatrao.dto.dashboard.StatusQuantidade;
import com.umc.pediupatrao.entity.ItemPedido;
import com.umc.pediupatrao.entity.Pedido;
import com.umc.pediupatrao.entity.StatusPedido;
import com.umc.pediupatrao.entity.TipoEntrega;
import com.umc.pediupatrao.exception.RegraNegocioException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;

@ExtendWith(MockitoExtension.class)
class DashboardServiceTest {

    private static final LocalDate HOJE = LocalDate.of(2026, 11, 2);

    @Mock
    private MongoTemplate mongoTemplate;

    private DashboardService dashboardService;

    @BeforeEach
    void configurar() {
        Clock relogio = Clock.fixed(Instant.parse("2026-11-02T15:00:00Z"), DashboardService.FUSO_HORARIO);
        dashboardService = new DashboardService(mongoTemplate, relogio);
    }

    // filtros e validações 

    @Test
    @DisplayName("RF05: sem datas informadas, usa os últimos 30 dias até hoje")
    void periodoPadraoUltimos30Dias() {
        FiltroDashboard filtro = dashboardService.normalizar(FiltroDashboard.vazio());

        assertEquals(LocalDate.of(2026, 10, 4), filtro.dataInicio());
        assertEquals(HOJE, filtro.dataFim());
    }

    @Test
    @DisplayName("RF05: data final anterior à inicial é recusada antes de consultar o banco")
    void dataFinalAntesDaInicialGeraErro() {
        FiltroDashboard filtro = periodo(LocalDate.of(2026, 11, 10), LocalDate.of(2026, 11, 1));

        RegraNegocioException erro = assertThrows(RegraNegocioException.class,
                () -> dashboardService.indicadores(filtro));

        assertTrue(erro.getMessage().contains("data final"));
        verifyNoInteractions(mongoTemplate);
    }

    @Test
    @DisplayName("RF05: período maior que 366 dias é recusado")
    void periodoMaiorQueOLimiteGeraErro() {
        FiltroDashboard filtro = periodo(LocalDate.of(2025, 1, 1), HOJE);

        assertThrows(RegraNegocioException.class, () -> dashboardService.normalizar(filtro));
    }

    @Test
    @DisplayName("RF05: período, tipo de entrega, status e responsável são aplicados na consulta ao MongoDB")
    void filtrosSaoAplicadosNaConsulta() {
        when(mongoTemplate.find(any(Query.class), eq(Pedido.class))).thenReturn(List.of());

        dashboardService.situacaoPedidos(new FiltroDashboard(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31),
                TipoEntrega.RETIRADA, StatusPedido.FINALIZADO, "  gerente01  "));

        ArgumentCaptor<Query> consulta = ArgumentCaptor.forClass(Query.class);
        verify(mongoTemplate).find(consulta.capture(), eq(Pedido.class));
        Document criterios = consulta.getValue().getQueryObject();

        assertEquals("RETIRADA", criterios.get("tipoEntrega"));
        assertEquals("FINALIZADO", criterios.get("status"));
        assertEquals("gerente01", criterios.get("responsavelEntrada")); // espaços removidos
        Document periodo = (Document) criterios.get("dataHoraEntrada");
        assertEquals(LocalDate.of(2026, 10, 1).atStartOfDay(), periodo.get("$gte"));
        assertEquals(LocalDate.of(2026, 11, 1).atStartOfDay(), periodo.get("$lt")); // inclui o dia 31 inteiro
    }

    @Test
    @DisplayName("Limite fora do intervalo de 1 a 50 é recusado")
    void limiteInvalidoGeraErro() {
        assertThrows(RegraNegocioException.class,
                () -> dashboardService.produtosMaisVendidos(FiltroDashboard.vazio(), 100, CriterioRanking.QUANTIDADE));
        assertThrows(RegraNegocioException.class,
                () -> dashboardService.pedidosRecentes(FiltroDashboard.vazio(), 0));
    }

    // indicadores

    @Test
    @DisplayName("RF01: faturamento ignora cancelados; calcula ticket médio, itens, descontos, cancelamentos e variação")
    void indicadoresDoPeriodo() {
        Pedido semDesconto = pedido(StatusPedido.FINALIZADO, TipoEntrega.ENTREGA, HOJE.atTime(12, 0), 0,
                item("p1", "Pizza Calabresa", 2, "50.00"));                // total 100,00
        Pedido comDesconto = pedido(StatusPedido.FINALIZADO, TipoEntrega.RETIRADA, HOJE.atTime(19, 0), 10,
                item("p2", "Pizza Margherita", 1, "50.00"));               // 50,00 - 10% = 45,00
        Pedido cancelado = pedido(StatusPedido.CANCELADO, TipoEntrega.ENTREGA, HOJE.atTime(20, 0), 0,
                item("p3", "Pizza Quatro Queijos", 1, "80.00"));           // não entra no faturamento
        Pedido periodoAnterior = pedido(StatusPedido.FINALIZADO, TipoEntrega.ENTREGA,
                LocalDate.of(2026, 9, 10).atTime(20, 0), 0, item("p1", "Pizza Calabresa", 2, "50.00"));

        when(mongoTemplate.find(any(Query.class), eq(Pedido.class)))
                .thenReturn(List.of(semDesconto, comDesconto, cancelado), List.of(periodoAnterior));

        IndicadoresDashboard r = dashboardService.indicadores(FiltroDashboard.vazio());

        assertValor("145.00", r.faturamento());
        assertEquals(3, r.totalPedidos());
        assertEquals(2, r.pedidosValidos());
        assertValor("72.50", r.ticketMedio());       
        assertEquals(3, r.itensVendidos());           
        assertValor("5.00", r.totalDescontos());
        assertEquals(1, r.pedidosComDesconto());
        assertEquals(1, r.pedidosCancelados());
        assertValor("33.3", r.taxaCancelamento());  

        assertEquals(LocalDate.of(2026, 9, 4), r.periodoAnterior().dataInicio());
        assertEquals(LocalDate.of(2026, 10, 3), r.periodoAnterior().dataFim());
        assertValor("45.0", r.periodoAnterior().variacaoFaturamento());   
        assertValor("200.0", r.periodoAnterior().variacaoPedidos());      
        assertValor("-27.5", r.periodoAnterior().variacaoTicketMedio());  
    }

    @Test
    @DisplayName("RF01: período sem pedidos não divide por zero e a variação fica vazia")
    void periodoSemPedidos() {
        when(mongoTemplate.find(any(Query.class), eq(Pedido.class))).thenReturn(List.of());

        IndicadoresDashboard r = dashboardService.indicadores(FiltroDashboard.vazio());

        assertValor("0.00", r.faturamento());
        assertValor("0.00", r.ticketMedio());
        assertValor("0.0", r.taxaCancelamento());
        assertNull(r.periodoAnterior().variacaoFaturamento());
        assertNull(r.periodoAnterior().variacaoPedidos());
    }

    // séries dos gráficos

    @Test
    @DisplayName("RF02: vendas por dia incluem os dias sem venda com zero")
    void vendasPorDiaCompletaDiasSemVenda() {
        when(mongoTemplate.find(any(Query.class), eq(Pedido.class))).thenReturn(List.of(
                pedido(StatusPedido.FINALIZADO, TipoEntrega.ENTREGA, LocalDate.of(2026, 11, 2).atTime(20, 0), 0,
                        item("p1", "Pizza Calabresa", 1, "45.90"))));

        List<PontoSerieVendas> serie = dashboardService.vendasPorPeriodo(
                periodo(LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 3)), AgrupamentoVendas.DIA);

        assertEquals(3, serie.size());
        assertEquals("01/11", serie.get(0).rotulo());
        assertEquals(0, serie.get(0).pedidos());
        assertEquals("02/11", serie.get(1).rotulo());
        assertEquals(1, serie.get(1).pedidos());
        assertValor("45.90", serie.get(1).faturamento());
        assertEquals(0, serie.get(2).pedidos());
    }

    @Test
    @DisplayName("RF02: vendas por semana agrupam de segunda a domingo e ignoram cancelados")
    void vendasPorSemana() {
        when(mongoTemplate.find(any(Query.class), eq(Pedido.class))).thenReturn(List.of(
                pedido(StatusPedido.FINALIZADO, TipoEntrega.ENTREGA, LocalDate.of(2026, 10, 30).atTime(20, 0), 0,
                        item("p1", "Pizza Calabresa", 1, "45.90")),
                pedido(StatusPedido.FINALIZADO, TipoEntrega.ENTREGA, LocalDate.of(2026, 11, 3).atTime(20, 0), 0,
                        item("p1", "Pizza Calabresa", 1, "45.90")),
                pedido(StatusPedido.CANCELADO, TipoEntrega.ENTREGA, LocalDate.of(2026, 11, 4).atTime(20, 0), 0,
                        item("p1", "Pizza Calabresa", 1, "45.90"))));

        List<PontoSerieVendas> serie = dashboardService.vendasPorPeriodo(
                periodo(LocalDate.of(2026, 10, 28), LocalDate.of(2026, 11, 8)), AgrupamentoVendas.SEMANA);

        assertEquals(2, serie.size());
        assertEquals("Sem. 26/10", serie.get(0).rotulo());
        assertEquals(1, serie.get(0).pedidos());
        assertEquals("Sem. 02/11", serie.get(1).rotulo());
        assertEquals(1, serie.get(1).pedidos()); 
    }

    @Test
    @DisplayName("RF02: vendas por hora vão da primeira à última hora com pedidos")
    void vendasPorHora() {
        when(mongoTemplate.find(any(Query.class), eq(Pedido.class))).thenReturn(List.of(
                pedido(StatusPedido.FINALIZADO, TipoEntrega.ENTREGA, HOJE.atTime(12, 10), 0, item("p1", "Pizza", 1, "40.00")),
                pedido(StatusPedido.FINALIZADO, TipoEntrega.ENTREGA, HOJE.atTime(12, 40), 0, item("p1", "Pizza", 1, "40.00")),
                pedido(StatusPedido.FINALIZADO, TipoEntrega.ENTREGA, HOJE.atTime(19, 30), 0, item("p1", "Pizza", 1, "40.00"))));

        List<PontoSerieVendas> serie = dashboardService.vendasPorHora(FiltroDashboard.vazio());

        assertEquals(8, serie.size());
        assertEquals("12h", serie.get(0).rotulo());
        assertEquals(2, serie.get(0).pedidos());
        assertEquals(0, serie.get(3).pedidos()); 
        assertEquals("19h", serie.get(7).rotulo());
        assertEquals(1, serie.get(7).pedidos());
    }

    // situação dos pedidos 

    @Test
    @DisplayName("RF03: conta pedidos por status, em andamento e calcula os tempos médios")
    void situacaoDosPedidos() {
        LocalDateTime entrada1 = HOJE.atTime(19, 0);
        Pedido finalizado1 = pedido(StatusPedido.FINALIZADO, TipoEntrega.ENTREGA, entrada1, 0, item("p1", "Pizza", 1, "40.00"));
        finalizado1.registrarHistorico(StatusPedido.PRONTO, entrada1.plusMinutes(30), "atendente01");
        finalizado1.setDataHoraSaida(entrada1.plusMinutes(40));
        finalizado1.setDataHoraFinalizacao(entrada1.plusMinutes(60));

        LocalDateTime entrada2 = HOJE.atTime(20, 0);
        Pedido finalizado2 = pedido(StatusPedido.FINALIZADO, TipoEntrega.RETIRADA, entrada2, 0, item("p1", "Pizza", 1, "40.00"));
        finalizado2.registrarHistorico(StatusPedido.PRONTO, entrada2.plusMinutes(20), "atendente01");
        finalizado2.setDataHoraSaida(entrada2.plusMinutes(30));
        finalizado2.setDataHoraFinalizacao(entrada2.plusMinutes(40));

        Pedido emPreparo = pedido(StatusPedido.EM_PREPARACAO, TipoEntrega.ENTREGA, HOJE.atTime(11, 50), 0, item("p1", "Pizza", 1, "40.00"));
        Pedido cancelado = pedido(StatusPedido.CANCELADO, TipoEntrega.ENTREGA, HOJE.atTime(11, 30), 0, item("p1", "Pizza", 1, "40.00"));

        when(mongoTemplate.find(any(Query.class), eq(Pedido.class)))
                .thenReturn(List.of(finalizado1, finalizado2, emPreparo, cancelado));

        SituacaoPedidos s = dashboardService.situacaoPedidos(FiltroDashboard.vazio());

        assertEquals(4, s.totalPedidos());
        assertEquals(1, s.emAndamento());
        assertEquals(2, s.finalizados());
        assertEquals(1, s.cancelados());
        assertEquals(StatusPedido.values().length, s.porStatus().size()); 

        StatusQuantidade finalizados = s.porStatus().stream()
                .filter(q -> q.status().equals("FINALIZADO")).findFirst().orElseThrow();
        assertEquals(2, finalizados.quantidade());
        assertValor("50.0", finalizados.percentual());

        assertEquals(25.0, s.tempoMedioPreparoMinutos());  
        assertEquals(35.0, s.tempoMedioAteSaidaMinutos()); 
        assertEquals(50.0, s.tempoMedioTotalMinutos());    
    }

    // produtos mais vendidos

    @Test
    @DisplayName("RF04: ranking por quantidade e por faturamento, sem contar pedidos cancelados")
    void rankingDeProdutos() {
        Pedido pedido1 = pedido(StatusPedido.FINALIZADO, TipoEntrega.ENTREGA, HOJE.atTime(19, 0), 0,
                item("pizza", "Pizza Calabresa", 2, "45.90"),   // 91,80
                item("esfiha", "Esfiha de Carne", 8, "6.50"));   // 52,00
        Pedido pedido2 = pedido(StatusPedido.FINALIZADO, TipoEntrega.RETIRADA, HOJE.atTime(20, 0), 0,
                item("esfiha", "Esfiha de Carne", 4, "6.50"));   // 26,00
        Pedido cancelado = pedido(StatusPedido.CANCELADO, TipoEntrega.ENTREGA, HOJE.atTime(21, 0), 0,
                item("pizza", "Pizza Calabresa", 10, "45.90"));  // ignorado

        when(mongoTemplate.find(any(Query.class), eq(Pedido.class)))
                .thenReturn(List.of(pedido1, pedido2, cancelado));

        List<ProdutoMaisVendido> porQuantidade =
                dashboardService.produtosMaisVendidos(FiltroDashboard.vazio(), 5, CriterioRanking.QUANTIDADE);
        assertEquals(2, porQuantidade.size());
        assertEquals(1, porQuantidade.get(0).posicao());
        assertEquals("Esfiha de Carne", porQuantidade.get(0).nome());
        assertEquals(12, porQuantidade.get(0).quantidade());
        assertEquals(2, porQuantidade.get(0).pedidos());
        assertValor("78.00", porQuantidade.get(0).faturamento());
        assertEquals(2, porQuantidade.get(1).quantidade()); 

        List<ProdutoMaisVendido> porFaturamento =
                dashboardService.produtosMaisVendidos(FiltroDashboard.vazio(), 5, CriterioRanking.FATURAMENTO);
        assertEquals("Pizza Calabresa", porFaturamento.get(0).nome());
        assertValor("91.80", porFaturamento.get(0).faturamento());
        assertValor("54.1", porFaturamento.get(0).participacaoFaturamento()); 
    }

    // Pedidos recentes 

    @Test
    @DisplayName("Pedidos recentes: consulta ordenada do mais novo para o mais antigo, com limite")
    void pedidosRecentesOrdenadosELimitados() {
        when(mongoTemplate.find(any(Query.class), eq(Pedido.class))).thenReturn(List.of(
                pedido(StatusPedido.PRONTO, TipoEntrega.RETIRADA, HOJE.atTime(11, 40), 0, item("p1", "Pizza", 1, "40.00"))));

        List<PedidoRecente> recentes = dashboardService.pedidosRecentes(FiltroDashboard.vazio(), 5);

        ArgumentCaptor<Query> consulta = ArgumentCaptor.forClass(Query.class);
        verify(mongoTemplate).find(consulta.capture(), eq(Pedido.class));
        assertEquals(5, consulta.getValue().getLimit());
        assertEquals(-1, consulta.getValue().getSortObject().get("dataHoraEntrada")); 

        assertEquals(1, recentes.size());
        assertEquals("PRONTO", recentes.get(0).status());
        assertEquals("RETIRADA", recentes.get(0).tipoEntrega());
        assertValor("40.00", recentes.get(0).valorTotal());
    }

    // auxiliares

    private FiltroDashboard periodo(LocalDate inicio, LocalDate fim) {
        return new FiltroDashboard(inicio, fim, null, null, null);
    }

    private ItemPedido item(String produtoId, String nome, int quantidade, String preco) {
        return new ItemPedido(produtoId, nome, quantidade, new BigDecimal(preco));
    }

    private Pedido pedido(StatusPedido status, TipoEntrega tipo, LocalDateTime entrada,
                          int percentualDesconto, ItemPedido... itens) {
        Pedido pedido = new Pedido();
        pedido.setStatus(status);
        pedido.setTipoEntrega(tipo);
        pedido.setDataHoraEntrada(entrada);
        pedido.setResponsavelEntrada("atendente01");
        pedido.setClienteNome("Cliente Teste");
        pedido.setItens(new ArrayList<>(Arrays.asList(itens)));

        BigDecimal subtotal = Arrays.stream(itens)
                .map(ItemPedido::getSubtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal desconto = subtotal.multiply(BigDecimal.valueOf(percentualDesconto))
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        pedido.setSubtotal(subtotal);
        pedido.setPercentualDesconto(BigDecimal.valueOf(percentualDesconto));
        pedido.setValorDesconto(desconto);
        pedido.setValorTotal(subtotal.subtract(desconto));
        pedido.registrarHistorico(StatusPedido.RECEBIDO, entrada, "atendente01");
        return pedido;
    }

    private void assertValor(String esperado, BigDecimal atual) {
        assertTrue(atual != null && new BigDecimal(esperado).compareTo(atual) == 0,
                "Esperado " + esperado + ", mas foi " + atual);
    }
}