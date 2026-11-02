package com.umc.pediupatrao.controller;

import com.umc.pediupatrao.dto.dashboard.AgrupamentoVendas;
import com.umc.pediupatrao.dto.dashboard.CriterioRanking;
import com.umc.pediupatrao.dto.dashboard.FiltroDashboard;
import com.umc.pediupatrao.dto.dashboard.IndicadoresDashboard;
import com.umc.pediupatrao.dto.dashboard.PedidoRecente;
import com.umc.pediupatrao.dto.dashboard.PontoSerieVendas;
import com.umc.pediupatrao.dto.dashboard.ProdutoMaisVendido;
import com.umc.pediupatrao.dto.dashboard.RespostaDashboard;
import com.umc.pediupatrao.dto.dashboard.SituacaoPedidos;
import com.umc.pediupatrao.service.DashboardService;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping("/indicadores")
    public RespostaDashboard<IndicadoresDashboard> indicadores(@ModelAttribute FiltroDashboard filtro) {
        FiltroDashboard f = dashboardService.normalizar(filtro);
        return responder(f, dashboardService.indicadores(f));
    }

    @GetMapping("/vendas")
    public RespostaDashboard<List<PontoSerieVendas>> vendas(
            @ModelAttribute FiltroDashboard filtro,
            @RequestParam(defaultValue = "DIA") AgrupamentoVendas agrupamento) {
        FiltroDashboard f = dashboardService.normalizar(filtro);
        return responder(f, dashboardService.vendasPorPeriodo(f, agrupamento));
    }

    @GetMapping("/vendas-por-hora")
    public RespostaDashboard<List<PontoSerieVendas>> vendasPorHora(@ModelAttribute FiltroDashboard filtro) {
        FiltroDashboard f = dashboardService.normalizar(filtro);
        return responder(f, dashboardService.vendasPorHora(f));
    }

    @GetMapping("/vendas-por-tipo")
    public RespostaDashboard<List<PontoSerieVendas>> vendasPorTipo(@ModelAttribute FiltroDashboard filtro) {
        FiltroDashboard f = dashboardService.normalizar(filtro);
        return responder(f, dashboardService.vendasPorTipoEntrega(f));
    }

    @GetMapping("/status")
    public RespostaDashboard<SituacaoPedidos> status(@ModelAttribute FiltroDashboard filtro) {
        FiltroDashboard f = dashboardService.normalizar(filtro);
        return responder(f, dashboardService.situacaoPedidos(f));
    }

    @GetMapping("/produtos-mais-vendidos")
    public RespostaDashboard<List<ProdutoMaisVendido>> produtosMaisVendidos(
            @ModelAttribute FiltroDashboard filtro,
            @RequestParam(required = false) Integer limite,
            @RequestParam(defaultValue = "QUANTIDADE") CriterioRanking ordenarPor) {
        FiltroDashboard f = dashboardService.normalizar(filtro);
        return responder(f, dashboardService.produtosMaisVendidos(f, limite, ordenarPor));
    }

    @GetMapping("/pedidos-recentes")
    public RespostaDashboard<List<PedidoRecente>> pedidosRecentes(
            @ModelAttribute FiltroDashboard filtro,
            @RequestParam(required = false) Integer limite) {
        FiltroDashboard f = dashboardService.normalizar(filtro);
        return responder(f, dashboardService.pedidosRecentes(f, limite));
    }

    private <T> RespostaDashboard<T> responder(FiltroDashboard filtro, T dados) {
        return new RespostaDashboard<>(filtro, LocalDateTime.now(DashboardService.FUSO_HORARIO).withNano(0), dados);
    }
}
