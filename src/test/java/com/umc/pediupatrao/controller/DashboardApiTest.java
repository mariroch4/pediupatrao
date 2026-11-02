package com.umc.pediupatrao.controller;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.umc.pediupatrao.config.SecurityConfig;
import com.umc.pediupatrao.dto.dashboard.CriterioRanking;
import com.umc.pediupatrao.dto.dashboard.FiltroDashboard;
import com.umc.pediupatrao.dto.dashboard.IndicadoresDashboard;
import com.umc.pediupatrao.dto.dashboard.ProdutoMaisVendido;
import com.umc.pediupatrao.entity.StatusPedido;
import com.umc.pediupatrao.entity.TipoEntrega;
import com.umc.pediupatrao.exception.RegraNegocioException;
import com.umc.pediupatrao.service.DashboardService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = DashboardController.class)
@Import(SecurityConfig.class)
class DashboardApiTest {

    private static final String INDICADORES = "/api/dashboard/indicadores";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DashboardService dashboardService;

    @BeforeEach
    void configurar() {
        when(dashboardService.normalizar(any())).thenAnswer(invocacao -> invocacao.getArgument(0));
        when(dashboardService.indicadores(any())).thenReturn(indicadoresDeExemplo());
    }

    // Autorização

    @Test
    @DisplayName("Dashboard sem login responde 401")
    void semLoginResponde401() throws Exception {
        mockMvc.perform(get(INDICADORES))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "atendente01", roles = "ATENDENTE")
    @DisplayName("ATENDENTE não acessa o dashboard (403)")
    void atendenteRecebe403() throws Exception {
        mockMvc.perform(get(INDICADORES))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
    }

    @Test
    @WithMockUser(username = "gerente01", roles = "GERENTE")
    @DisplayName("GERENTE acessa os indicadores (200) no formato padrão filtros/atualizadoEm/dados")
    void gerenteAcessaIndicadores() throws Exception {
        mockMvc.perform(get(INDICADORES))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.filtros").exists())
                .andExpect(jsonPath("$.atualizadoEm").exists())
                .andExpect(jsonPath("$.dados.totalPedidos").value(3))
                .andExpect(jsonPath("$.dados.faturamento").value(145.0))
                .andExpect(jsonPath("$.dados.periodoAnterior.variacaoFaturamento").value(45.0));
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    @DisplayName("ADMIN acessa o dashboard (200)")
    void adminAcessaIndicadores() throws Exception {
        mockMvc.perform(get(INDICADORES))
                .andExpect(status().isOk());
    }

    // Filtros

    @Test
    @WithMockUser(username = "gerente01", roles = "GERENTE")
    @DisplayName("RF05: os filtros da URL chegam ao service convertidos")
    void filtrosDaUrlChegamAoService() throws Exception {
        mockMvc.perform(get(INDICADORES)
                        .param("dataInicio", "2026-10-01")
                        .param("dataFim", "2026-10-31")
                        .param("tipoEntrega", "RETIRADA")
                        .param("status", "FINALIZADO")
                        .param("responsavel", "atendente01"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.filtros.dataInicio").value("2026-10-01"))
                .andExpect(jsonPath("$.filtros.tipoEntrega").value("RETIRADA"));

        verify(dashboardService).indicadores(new FiltroDashboard(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31),
                TipoEntrega.RETIRADA, StatusPedido.FINALIZADO, "atendente01"));
    }

    @Test
    @WithMockUser(username = "gerente01", roles = "GERENTE")
    @DisplayName("RF04: limite e critério do ranking chegam ao service")
    void parametrosDoRanking() throws Exception {
        when(dashboardService.produtosMaisVendidos(any(), any(), any())).thenReturn(List.of(
                new ProdutoMaisVendido(1, "p1", "Pizza Calabresa", 10, new BigDecimal("459.00"), 8, new BigDecimal("60.0"))));

        mockMvc.perform(get("/api/dashboard/produtos-mais-vendidos")
                        .param("limite", "3")
                        .param("ordenarPor", "FATURAMENTO"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dados[0].nome").value("Pizza Calabresa"))
                .andExpect(jsonPath("$.dados[0].posicao").value(1));

        verify(dashboardService).produtosMaisVendidos(any(), eq(3), eq(CriterioRanking.FATURAMENTO));
    }

    // Tratamento de erros

    @Test
    @WithMockUser(username = "gerente01", roles = "GERENTE")
    @DisplayName("RF07: agrupamento inexistente responde 400 com mensagem clara")
    void agrupamentoInvalidoResponde400() throws Exception {
        mockMvc.perform(get("/api/dashboard/vendas").param("agrupamento", "ANO"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensagem", containsString("agrupamento")))
                .andExpect(jsonPath("$.mensagem", containsString("SEMANA")));
    }

    @Test
    @WithMockUser(username = "gerente01", roles = "GERENTE")
    @DisplayName("RF07: data em formato errado responde 400")
    void dataEmFormatoErradoResponde400() throws Exception {
        mockMvc.perform(get(INDICADORES).param("dataInicio", "31/10/2026"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensagem", containsString("dataInicio")));
    }

    @Test
    @WithMockUser(username = "gerente01", roles = "GERENTE")
    @DisplayName("RF07: status inexistente responde 400 listando os valores aceitos")
    void statusInvalidoResponde400() throws Exception {
        mockMvc.perform(get(INDICADORES).param("status", "XYZ"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensagem", containsString("FINALIZADO")));
    }

    @Test
    @WithMockUser(username = "gerente01", roles = "GERENTE")
    @DisplayName("RF07: limite não numérico responde 400")
    void limiteNaoNumericoResponde400() throws Exception {
        mockMvc.perform(get("/api/dashboard/pedidos-recentes").param("limite", "abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensagem", containsString("limite")));
    }

    @Test
    @WithMockUser(username = "gerente01", roles = "GERENTE")
    @DisplayName("RF07: regra de negócio violada (data final antes da inicial) responde 422")
    void regraVioladaResponde422() throws Exception {
        when(dashboardService.normalizar(any()))
                .thenThrow(new RegraNegocioException("A data final não pode ser anterior à data inicial."));

        mockMvc.perform(get(INDICADORES)
                        .param("dataInicio", "2026-11-10")
                        .param("dataFim", "2026-11-01"))
                .andExpect(status().is(422))
                .andExpect(jsonPath("$.mensagem", containsString("data final")));
    }

    // auxiliares

    private IndicadoresDashboard indicadoresDeExemplo() {
        return new IndicadoresDashboard(
                LocalDate.of(2026, 10, 4), LocalDate.of(2026, 11, 2),
                new BigDecimal("145.00"), 3, 2, new BigDecimal("72.50"), 3,
                new BigDecimal("5.00"), 1, 1, new BigDecimal("33.3"),
                new IndicadoresDashboard.Comparativo(
                        LocalDate.of(2026, 9, 4), LocalDate.of(2026, 10, 3),
                        new BigDecimal("100.00"), 1, new BigDecimal("100.00"),
                        new BigDecimal("45.0"), new BigDecimal("200.0"), new BigDecimal("-27.5")));
    }
}