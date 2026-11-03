package com.umc.pediupatrao.service;

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
import com.umc.pediupatrao.entity.RegistroStatus;
import com.umc.pediupatrao.entity.StatusPedido;
import com.umc.pediupatrao.entity.TipoEntrega;
import com.umc.pediupatrao.exception.RegraNegocioException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

@Service
@PreAuthorize("hasAnyRole('ADMIN','GERENTE')")
public class DashboardService {

    public static final ZoneId FUSO_HORARIO = ZoneId.of("America/Sao_Paulo");
    public static final int DIAS_PADRAO = 30;
    public static final int PERIODO_MAXIMO_DIAS = 366;
    public static final int LIMITE_MAXIMO = 50;

    private static final DateTimeFormatter FORMATO_DIA = DateTimeFormatter.ofPattern("dd/MM");
    private static final DateTimeFormatter FORMATO_MES = DateTimeFormatter.ofPattern("MM/yyyy");
    private static final BigDecimal CEM = BigDecimal.valueOf(100);

    private final MongoTemplate mongoTemplate;
    private final Clock clock;

    @Autowired
    public DashboardService(MongoTemplate mongoTemplate) {
        this(mongoTemplate, Clock.system(FUSO_HORARIO));
    }

    DashboardService(MongoTemplate mongoTemplate, Clock clock) {
        this.mongoTemplate = mongoTemplate;
        this.clock = clock;
    }

    // Filtros: valida e completa o filtro com os valores padrão

    public FiltroDashboard normalizar(FiltroDashboard filtro) {
        FiltroDashboard f = filtro != null ? filtro : FiltroDashboard.vazio();
        LocalDate hoje = LocalDate.now(clock);

        LocalDate fim = f.dataFim() != null ? f.dataFim() : hoje;
        LocalDate inicio = f.dataInicio() != null ? f.dataInicio() : fim.minusDays(DIAS_PADRAO - 1);

        if (fim.isBefore(inicio)) {
            throw new RegraNegocioException("A data final não pode ser anterior à data inicial.");
        }
        long dias = ChronoUnit.DAYS.between(inicio, fim) + 1;
        if (dias > PERIODO_MAXIMO_DIAS) {
            throw new RegraNegocioException("O período máximo de consulta é de " + PERIODO_MAXIMO_DIAS + " dias.");
        }

        String responsavel = f.responsavel() == null || f.responsavel().isBlank() ? null : f.responsavel().trim();
        return new FiltroDashboard(inicio, fim, f.tipoEntrega(), f.status(), responsavel);
    }

    // Indicadores gerais de vendas

    public IndicadoresDashboard indicadores(FiltroDashboard filtro) {
        FiltroDashboard f = normalizar(filtro);
        Totais atual = totalizar(buscarPedidos(f));

        long dias = ChronoUnit.DAYS.between(f.dataInicio(), f.dataFim()) + 1;
        FiltroDashboard filtroAnterior = new FiltroDashboard(
                f.dataInicio().minusDays(dias), f.dataInicio().minusDays(1),
                f.tipoEntrega(), f.status(), f.responsavel());
        Totais anterior = totalizar(buscarPedidos(filtroAnterior));

        IndicadoresDashboard.Comparativo comparativo = new IndicadoresDashboard.Comparativo(
                filtroAnterior.dataInicio(),
                filtroAnterior.dataFim(),
                dinheiro(anterior.faturamento),
                anterior.pedidos,
                anterior.ticketMedio(),
                variacao(atual.faturamento, anterior.faturamento),
                variacao(BigDecimal.valueOf(atual.pedidos), BigDecimal.valueOf(anterior.pedidos)),
                variacao(atual.ticketMedio(), anterior.ticketMedio()));

        return new IndicadoresDashboard(
                f.dataInicio(),
                f.dataFim(),
                dinheiro(atual.faturamento),
                atual.pedidos,
                atual.validos,
                atual.ticketMedio(),
                atual.itens,
                dinheiro(atual.descontos),
                atual.comDesconto,
                atual.cancelados,
                percentual(atual.cancelados, atual.pedidos),
                comparativo);
    }

    // Análise de vendas (séries para os gráficos)

    public List<PontoSerieVendas> vendasPorPeriodo(FiltroDashboard filtro, AgrupamentoVendas agrupamento) {
        FiltroDashboard f = normalizar(filtro);
        AgrupamentoVendas ag = agrupamento != null ? agrupamento : AgrupamentoVendas.DIA;

        Map<LocalDate, Acumulador> grupos = new LinkedHashMap<>();
        for (LocalDate d = inicioDoGrupo(f.dataInicio(), ag); !d.isAfter(f.dataFim()); d = proximoGrupo(d, ag)) {
            grupos.put(d, new Acumulador());
        }

        for (Pedido pedido : naoCancelados(buscarPedidos(f))) {
            LocalDate chave = inicioDoGrupo(pedido.getDataHoraEntrada().toLocalDate(), ag);
            grupos.computeIfAbsent(chave, k -> new Acumulador()).somar(pedido);
        }

        List<PontoSerieVendas> serie = new ArrayList<>();
        grupos.forEach((inicio, acc) -> serie.add(
                new PontoSerieVendas(rotulo(inicio, ag), acc.pedidos, dinheiro(acc.faturamento))));
        return serie;
    }

    public List<PontoSerieVendas> vendasPorHora(FiltroDashboard filtro) {
        FiltroDashboard f = normalizar(filtro);

        Acumulador[] horas = new Acumulador[24];
        for (int h = 0; h < 24; h++) {
            horas[h] = new Acumulador();
        }
        for (Pedido pedido : naoCancelados(buscarPedidos(f))) {
            horas[pedido.getDataHoraEntrada().getHour()].somar(pedido);
        }

        int primeira = -1;
        int ultima = -1;
        for (int h = 0; h < 24; h++) {
            if (horas[h].pedidos > 0) {
                if (primeira < 0) {
                    primeira = h;
                }
                ultima = h;
            }
        }

        List<PontoSerieVendas> serie = new ArrayList<>();
        if (primeira < 0) {
            return serie; 
        }
        for (int h = primeira; h <= ultima; h++) {
            serie.add(new PontoSerieVendas(String.format("%02dh", h), horas[h].pedidos, dinheiro(horas[h].faturamento)));
        }
        return serie;
    }

    public List<PontoSerieVendas> vendasPorTipoEntrega(FiltroDashboard filtro) {
        FiltroDashboard f = normalizar(filtro);

        Map<TipoEntrega, Acumulador> porTipo = new EnumMap<>(TipoEntrega.class);
        for (TipoEntrega tipo : TipoEntrega.values()) {
            porTipo.put(tipo, new Acumulador());
        }
        for (Pedido pedido : naoCancelados(buscarPedidos(f))) {
            if (pedido.getTipoEntrega() != null) {
                porTipo.get(pedido.getTipoEntrega()).somar(pedido);
            }
        }

        List<PontoSerieVendas> serie = new ArrayList<>();
        porTipo.forEach((tipo, acc) -> serie.add(
                new PontoSerieVendas(tipo.getDescricao(), acc.pedidos, dinheiro(acc.faturamento))));
        return serie;
    }

    // Situação dos pedidos

    public SituacaoPedidos situacaoPedidos(FiltroDashboard filtro) {
        FiltroDashboard f = normalizar(filtro);
        List<Pedido> pedidos = buscarPedidos(f);
        long total = pedidos.size();

        Map<StatusPedido, Long> contagem = new EnumMap<>(StatusPedido.class);
        for (Pedido pedido : pedidos) {
            if (pedido.getStatus() != null) {
                contagem.merge(pedido.getStatus(), 1L, Long::sum);
            }
        }

        List<StatusQuantidade> porStatus = Arrays.stream(StatusPedido.values())
                .map(s -> {
                    long quantidade = contagem.getOrDefault(s, 0L);
                    return new StatusQuantidade(s.name(), s.getDescricao(), s.getCorBadge(), s.getIcone(),
                            quantidade, percentual(quantidade, total));
                })
                .toList();

        long finalizados = contagem.getOrDefault(StatusPedido.FINALIZADO, 0L);
        long cancelados = contagem.getOrDefault(StatusPedido.CANCELADO, 0L);
        long emAndamento = pedidos.stream()
                .filter(p -> p.getStatus() != null
                        && p.getStatus() != StatusPedido.FINALIZADO
                        && p.getStatus() != StatusPedido.CANCELADO)
                .count();

        return new SituacaoPedidos(
                total,
                emAndamento,
                finalizados,
                cancelados,
                porStatus,
                mediaMinutos(pedidos, Pedido::getDataHoraEntrada, p -> momentoDoStatus(p, StatusPedido.PRONTO)),
                mediaMinutos(pedidos, Pedido::getDataHoraEntrada, Pedido::getDataHoraSaida),
                mediaMinutos(pedidos, Pedido::getDataHoraEntrada, Pedido::getDataHoraFinalizacao));
    }

    // Produtos mais vendidos

    public List<ProdutoMaisVendido> produtosMaisVendidos(FiltroDashboard filtro, Integer limite, CriterioRanking criterio) {
        FiltroDashboard f = normalizar(filtro);
        int maximo = validarLimite(limite, 5);
        CriterioRanking ordem = criterio != null ? criterio : CriterioRanking.QUANTIDADE;

        Map<String, AcumuladorProduto> porProduto = new LinkedHashMap<>();
        BigDecimal faturamentoItens = BigDecimal.ZERO;

        for (Pedido pedido : naoCancelados(buscarPedidos(f))) {
            Set<String> contadosNestePedido = new HashSet<>();
            for (ItemPedido item : itensDe(pedido)) {
                String chave = item.getProdutoId() != null ? item.getProdutoId() : item.getNomeProduto();
                if (chave == null) {
                    continue;
                }
                AcumuladorProduto acc = porProduto.computeIfAbsent(chave,
                        k -> new AcumuladorProduto(item.getProdutoId(), item.getNomeProduto()));
                BigDecimal subtotal = valor(item.getSubtotal());
                acc.quantidade += item.getQuantidade() != null ? item.getQuantidade() : 0;
                acc.faturamento = acc.faturamento.add(subtotal);
                if (contadosNestePedido.add(chave)) {
                    acc.pedidos++;
                }
                faturamentoItens = faturamentoItens.add(subtotal);
            }
        }

        Comparator<AcumuladorProduto> porQuantidade = Comparator.comparingLong(a -> a.quantidade);
        Comparator<AcumuladorProduto> porFaturamento = Comparator.comparing(a -> a.faturamento);
        Comparator<AcumuladorProduto> comparador = ordem == CriterioRanking.FATURAMENTO
                ? porFaturamento.thenComparing(porQuantidade)
                : porQuantidade.thenComparing(porFaturamento);

        List<AcumuladorProduto> ordenados = porProduto.values().stream()
                .sorted(comparador.reversed())
                .limit(maximo)
                .toList();

        List<ProdutoMaisVendido> ranking = new ArrayList<>();
        for (int i = 0; i < ordenados.size(); i++) {
            AcumuladorProduto a = ordenados.get(i);
            ranking.add(new ProdutoMaisVendido(
                    i + 1,
                    a.produtoId,
                    a.nome,
                    a.quantidade,
                    dinheiro(a.faturamento),
                    a.pedidos,
                    percentual(a.faturamento, faturamentoItens)));
        }
        return ranking;
    }

    // Pedidos recentes (tabela do dashboard)

    public List<PedidoRecente> pedidosRecentes(FiltroDashboard filtro, Integer limite) {
        FiltroDashboard f = normalizar(filtro);
        int maximo = validarLimite(limite, 10);

        Query consulta = montarConsulta(f)
                .with(Sort.by(Sort.Direction.DESC, "dataHoraEntrada"))
                .limit(maximo);

        return mongoTemplate.find(consulta, Pedido.class).stream()
                .map(p -> new PedidoRecente(
                        p.getId(),
                        p.getClienteNome(),
                        p.getTipoEntrega() != null ? p.getTipoEntrega().name() : null,
                        p.getTipoEntrega() != null ? p.getTipoEntrega().getDescricao() : null,
                        p.getStatus() != null ? p.getStatus().name() : null,
                        p.getStatus() != null ? p.getStatus().getDescricao() : null,
                        p.getStatus() != null ? p.getStatus().getCorBadge() : null,
                        dinheiro(valor(p.getValorTotal())),
                        p.getDataHoraEntrada(),
                        p.getResponsavelEntrada()))
                .toList();
    }

    // Consulta ao MongoDB

    private List<Pedido> buscarPedidos(FiltroDashboard f) {
        return mongoTemplate.find(montarConsulta(f), Pedido.class);
    }

    private Query montarConsulta(FiltroDashboard f) {
        Criteria criteria = Criteria.where("dataHoraEntrada")
                .gte(f.dataInicio().atStartOfDay())
                .lt(f.dataFim().plusDays(1).atStartOfDay());

        if (f.tipoEntrega() != null) {
            criteria = criteria.and("tipoEntrega").is(f.tipoEntrega().name());
        }
        if (f.status() != null) {
            criteria = criteria.and("status").is(f.status().name());
        }
        if (f.responsavel() != null) {
            criteria = criteria.and("responsavelEntrada").is(f.responsavel());
        }
        return new Query(criteria);
    }

    // Auxiliares de cálculo

    private Totais totalizar(List<Pedido> pedidos) {
        Totais t = new Totais();
        for (Pedido pedido : pedidos) {
            t.pedidos++;
            if (isCancelado(pedido)) {
                t.cancelados++;
                continue;
            }
            t.validos++;
            t.faturamento = t.faturamento.add(valor(pedido.getValorTotal()));
            BigDecimal desconto = valor(pedido.getValorDesconto());
            if (desconto.signum() > 0) {
                t.descontos = t.descontos.add(desconto);
                t.comDesconto++;
            }
            for (ItemPedido item : itensDe(pedido)) {
                t.itens += item.getQuantidade() != null ? item.getQuantidade() : 0;
            }
        }
        return t;
    }

    private List<Pedido> naoCancelados(List<Pedido> pedidos) {
        return pedidos.stream()
                .filter(p -> !isCancelado(p) && p.getDataHoraEntrada() != null)
                .toList();
    }

    private boolean isCancelado(Pedido pedido) {
        return pedido.getStatus() == StatusPedido.CANCELADO;
    }

    private List<ItemPedido> itensDe(Pedido pedido) {
        return pedido.getItens() != null ? pedido.getItens() : List.of();
    }

    private LocalDateTime momentoDoStatus(Pedido pedido, StatusPedido status) {
        if (pedido.getHistoricoStatus() == null) {
            return null;
        }
        return pedido.getHistoricoStatus().stream()
                .filter(r -> r.getStatus() == status)
                .map(RegistroStatus::getDataHora)
                .findFirst()
                .orElse(null);
    }

    private Double mediaMinutos(List<Pedido> pedidos,
                                Function<Pedido, LocalDateTime> inicio,
                                Function<Pedido, LocalDateTime> fim) {
        double soma = 0;
        int quantidade = 0;
        for (Pedido pedido : pedidos) {
            if (isCancelado(pedido)) {
                continue;
            }
            LocalDateTime a = inicio.apply(pedido);
            LocalDateTime b = fim.apply(pedido);
            if (a == null || b == null || b.isBefore(a)) {
                continue;
            }
            soma += Duration.between(a, b).getSeconds() / 60.0;
            quantidade++;
        }
        if (quantidade == 0) {
            return null;
        }
        return BigDecimal.valueOf(soma / quantidade).setScale(1, RoundingMode.HALF_UP).doubleValue();
    }

    private LocalDate inicioDoGrupo(LocalDate data, AgrupamentoVendas agrupamento) {
        return switch (agrupamento) {
            case DIA -> data;
            case SEMANA -> data.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            case MES -> data.withDayOfMonth(1);
        };
    }

    private LocalDate proximoGrupo(LocalDate data, AgrupamentoVendas agrupamento) {
        return switch (agrupamento) {
            case DIA -> data.plusDays(1);
            case SEMANA -> data.plusWeeks(1);
            case MES -> data.plusMonths(1);
        };
    }

    private String rotulo(LocalDate inicio, AgrupamentoVendas agrupamento) {
        return switch (agrupamento) {
            case DIA -> inicio.format(FORMATO_DIA);
            case SEMANA -> "Sem. " + inicio.format(FORMATO_DIA);
            case MES -> inicio.format(FORMATO_MES);
        };
    }

    private int validarLimite(Integer limite, int padrao) {
        if (limite == null) {
            return padrao;
        }
        if (limite < 1 || limite > LIMITE_MAXIMO) {
            throw new RegraNegocioException("O limite deve estar entre 1 e " + LIMITE_MAXIMO + ".");
        }
        return limite;
    }

    private static BigDecimal valor(BigDecimal v) {
        return v != null ? v : BigDecimal.ZERO;
    }

    private static BigDecimal dinheiro(BigDecimal v) {
        return valor(v).setScale(2, RoundingMode.HALF_UP);
    }

    private static BigDecimal percentual(long parte, long total) {
        return percentual(BigDecimal.valueOf(parte), BigDecimal.valueOf(total));
    }

    private static BigDecimal percentual(BigDecimal parte, BigDecimal total) {
        if (total == null || total.signum() == 0) {
            return BigDecimal.ZERO.setScale(1);
        }
        return parte.multiply(CEM).divide(total, 1, RoundingMode.HALF_UP);
    }

    /** Variação percentual em relação ao período anterior (null se o anterior for zero). */
    private static BigDecimal variacao(BigDecimal atual, BigDecimal anterior) {
        if (anterior == null || anterior.signum() == 0) {
            return null;
        }
        return valor(atual).subtract(anterior).multiply(CEM).divide(anterior, 1, RoundingMode.HALF_UP);
    }

    // Classes internas de acumulação

    private static final class Totais {
        long pedidos;
        long validos;
        long cancelados;
        long comDesconto;
        long itens;
        BigDecimal faturamento = BigDecimal.ZERO;
        BigDecimal descontos = BigDecimal.ZERO;

        BigDecimal ticketMedio() {
            if (validos == 0) {
                return BigDecimal.ZERO.setScale(2);
            }
            return faturamento.divide(BigDecimal.valueOf(validos), 2, RoundingMode.HALF_UP);
        }
    }

    private static final class Acumulador {
        long pedidos;
        BigDecimal faturamento = BigDecimal.ZERO;

        void somar(Pedido pedido) {
            pedidos++;
            faturamento = faturamento.add(valor(pedido.getValorTotal()));
        }
    }

    private static final class AcumuladorProduto {
        final String produtoId;
        final String nome;
        long quantidade;
        long pedidos;
        BigDecimal faturamento = BigDecimal.ZERO;

        AcumuladorProduto(String produtoId, String nome) {
            this.produtoId = produtoId;
            this.nome = nome;
        }
    }
}