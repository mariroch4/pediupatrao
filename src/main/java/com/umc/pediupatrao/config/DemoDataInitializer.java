package com.umc.pediupatrao.config;

import com.umc.pediupatrao.entity.Cliente;
import com.umc.pediupatrao.entity.ItemPedido;
import com.umc.pediupatrao.entity.Pedido;
import com.umc.pediupatrao.entity.Produto;
import com.umc.pediupatrao.entity.StatusPedido;
import com.umc.pediupatrao.entity.TipoEntrega;
import com.umc.pediupatrao.repository.ClienteRepository;
import com.umc.pediupatrao.repository.PedidoRepository;
import com.umc.pediupatrao.repository.ProdutoRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class DemoDataInitializer {

    private static final Logger log = LoggerFactory.getLogger(DemoDataInitializer.class);

    private static final ZoneId FUSO_HORARIO = ZoneId.of("America/Sao_Paulo");
    private static final int DIAS_DE_HISTORICO = 60;
    private static final long SEMENTE = 42L;

    private static final int[] HORAS = {11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23};
    private static final int[] PESOS_HORA = {1, 4, 4, 2, 1, 1, 1, 3, 6, 7, 6, 3, 1};

    private static final String[] JUSTIFICATIVAS = {
        "Cliente desistiu do pedido",
        "Endereço fora da área de entrega",
        "Cliente não atendeu a ligação de confirmação",
        "Produto em falta no estoque"
    };

    private final PedidoRepository pedidoRepository;
    private final ClienteRepository clienteRepository;
    private final ProdutoRepository produtoRepository;
    private final boolean habilitado;
    private final int quantidadePedidos;

    public DemoDataInitializer(PedidoRepository pedidoRepository,
                               ClienteRepository clienteRepository,
                               ProdutoRepository produtoRepository,
                               @Value("${pediupatrao.demo.gerar-pedidos:false}") boolean habilitado,
                               @Value("${pediupatrao.demo.quantidade-pedidos:180}") int quantidadePedidos) {
        this.pedidoRepository = pedidoRepository;
        this.clienteRepository = clienteRepository;
        this.produtoRepository = produtoRepository;
        this.habilitado = habilitado;
        this.quantidadePedidos = quantidadePedidos;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void gerarDadosDeDemonstracao() {
        if (!habilitado) {
            return;
        }
        if (pedidoRepository.count() > 0) {
            log.info("Dados de demonstracao nao gerados: ja existem pedidos no banco.");
            return;
        }

        List<Produto> produtos = produtoRepository.findAll().stream()
                .filter(p -> Boolean.TRUE.equals(p.getAtivo()) && p.getPreco() != null && p.getPreco() > 0)
                .toList();
        if (produtos.isEmpty()) {
            log.warn("Dados de demonstracao nao gerados: nenhum produto ativo cadastrado.");
            return;
        }

        List<Cliente> clientes = garantirClientes();
        Random random = new Random(SEMENTE);
        LocalDateTime agora = LocalDateTime.now(FUSO_HORARIO);
        LocalDate hoje = agora.toLocalDate();

        List<Pedido> pedidos = new ArrayList<>();
        for (int i = 0; i < quantidadePedidos; i++) {
            LocalDate dia = sortearDia(random, hoje);
            LocalDateTime entrada = dia.atTime(sortearHora(random), random.nextInt(60));
            if (entrada.isAfter(agora)) {
                entrada = agora.minusMinutes(5 + random.nextInt(90));
            }
            pedidos.add(montarPedido(random, entrada, agora, clientes, produtos));
        }

        pedidoRepository.saveAll(pedidos);
        log.info("{} pedidos de demonstracao gerados nos ultimos {} dias.", pedidos.size(), DIAS_DE_HISTORICO);
    }

    // MONTAGEM DO PEDIDO

    private Pedido montarPedido(Random random, LocalDateTime entrada, LocalDateTime agora,
                                List<Cliente> clientes, List<Produto> produtos) {
        Pedido pedido = new Pedido();

        Cliente cliente = clientes.get(random.nextInt(clientes.size()));
        pedido.setClienteId(cliente.getId());
        pedido.setClienteNome(cliente.getNome());
        pedido.setTipoEntrega(random.nextDouble() < 0.65 ? TipoEntrega.ENTREGA : TipoEntrega.RETIRADA);
        pedido.setItens(sortearItens(random, produtos));

        boolean comDesconto = random.nextDouble() < 0.12;
        pedido.setPercentualDesconto(comDesconto
                ? BigDecimal.valueOf(5L * (1 + random.nextInt(3))).setScale(2)
                : BigDecimal.ZERO.setScale(2));
        calcularTotais(pedido);

        String responsavel = random.nextDouble() < 0.75 ? "atendente01" : "gerente01";
        pedido.setStatus(StatusPedido.RECEBIDO);
        pedido.setDataHoraEntrada(entrada);
        pedido.setResponsavelEntrada(responsavel);
        pedido.registrarHistorico(StatusPedido.RECEBIDO, entrada, responsavel);

        if (comDesconto) {
            pedido.setDataHoraDesconto(entrada.plusMinutes(2));
            pedido.setResponsavelDesconto("gerente01");
        }

        LocalDateTime momentoCancelamento = entrada.plusMinutes(5 + random.nextInt(11));
        if (random.nextDouble() < 0.08 && !momentoCancelamento.isAfter(agora)) {
            cancelar(pedido, momentoCancelamento, random);
        } else {
            avancarFluxo(pedido, entrada, agora, random);
        }
        return pedido;
    }

    private List<ItemPedido> sortearItens(Random random, List<Produto> produtos) {
        List<Produto> embaralhados = new ArrayList<>(produtos);
        Collections.shuffle(embaralhados, random);
        int quantidadeDeProdutos = 1 + random.nextInt(Math.min(3, embaralhados.size()));

        List<ItemPedido> itens = new ArrayList<>();
        for (int i = 0; i < quantidadeDeProdutos; i++) {
            Produto produto = embaralhados.get(i);
            int quantidade = "Esfiha".equalsIgnoreCase(produto.getTipo())
                    ? 2 + random.nextInt(7)  
                    : 1 + random.nextInt(2);  
            BigDecimal preco = BigDecimal.valueOf(produto.getPreco()).setScale(2, RoundingMode.HALF_UP);
            itens.add(new ItemPedido(produto.getId(), produto.getNome(), quantidade, preco));
        }
        return itens;
    }

    private void calcularTotais(Pedido pedido) {
        BigDecimal subtotal = pedido.getItens().stream()
                .map(ItemPedido::getSubtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal valorDesconto = subtotal.multiply(pedido.getPercentualDesconto())
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        pedido.setSubtotal(subtotal);
        pedido.setValorDesconto(valorDesconto);
        pedido.setValorTotal(subtotal.subtract(valorDesconto));
    }

    private void avancarFluxo(Pedido pedido, LocalDateTime entrada, LocalDateTime agora, Random random) {
        String responsavel = pedido.getResponsavelEntrada();
        boolean entrega = pedido.getTipoEntrega() == TipoEntrega.ENTREGA;
        StatusPedido statusSaida = entrega ? StatusPedido.SAIU_PARA_ENTREGA : StatusPedido.RETIRADO;

        LocalDateTime emPreparo = entrada.plusMinutes(3 + random.nextInt(5));
        LocalDateTime pronto = emPreparo.plusMinutes(15 + random.nextInt(20));
        LocalDateTime saida = pronto.plusMinutes(3 + random.nextInt(10));
        LocalDateTime finalizado = saida.plusMinutes(entrega ? 15 + random.nextInt(25) : 1 + random.nextInt(5));

        if (emPreparo.isAfter(agora)) {
            return; 
        }
        pedido.setStatus(StatusPedido.EM_PREPARACAO);
        pedido.registrarHistorico(StatusPedido.EM_PREPARACAO, emPreparo, responsavel);

        if (pronto.isAfter(agora)) {
            return;
        }
        pedido.setStatus(StatusPedido.PRONTO);
        pedido.registrarHistorico(StatusPedido.PRONTO, pronto, responsavel);

        if (saida.isAfter(agora)) {
            return;
        }
        pedido.setStatus(statusSaida);
        pedido.setDataHoraSaida(saida);
        pedido.setResponsavelSaida(responsavel);
        pedido.registrarHistorico(statusSaida, saida, responsavel);

        if (finalizado.isAfter(agora)) {
            return;
        }
        pedido.setStatus(StatusPedido.FINALIZADO);
        pedido.setDataHoraFinalizacao(finalizado);
        pedido.setResponsavelFinalizacao(responsavel);
        pedido.registrarHistorico(StatusPedido.FINALIZADO, finalizado, responsavel);
    }

    private void cancelar(Pedido pedido, LocalDateTime momento, Random random) {
        pedido.setStatus(StatusPedido.CANCELADO);
        pedido.setDataHoraCancelamento(momento);
        pedido.setResponsavelCancelamento("gerente01");
        pedido.setJustificativaCancelamento(JUSTIFICATIVAS[random.nextInt(JUSTIFICATIVAS.length)]);
        pedido.registrarHistorico(StatusPedido.CANCELADO, momento, "gerente01");
    }

    // SORTEIOS

    private LocalDate sortearDia(Random random, LocalDate hoje) {
        while (true) {
            int diasAtras = random.nextInt(DIAS_DE_HISTORICO);
            LocalDate dia = hoje.minusDays(diasAtras);
            DayOfWeek diaDaSemana = dia.getDayOfWeek();
            boolean fimDeSemana = diaDaSemana == DayOfWeek.FRIDAY
                    || diaDaSemana == DayOfWeek.SATURDAY
                    || diaDaSemana == DayOfWeek.SUNDAY;
            double pesoSemana = fimDeSemana ? 1.0 : 0.55;
            double pesoTendencia = 0.6 + 0.4 * (1.0 - (double) diasAtras / DIAS_DE_HISTORICO);
            if (random.nextDouble() < pesoSemana * pesoTendencia) {
                return dia;
            }
        }
    }

    private int sortearHora(Random random) {
        int total = 0;
        for (int peso : PESOS_HORA) {
            total += peso;
        }
        int sorteio = random.nextInt(total);
        for (int i = 0; i < HORAS.length; i++) {
            sorteio -= PESOS_HORA[i];
            if (sorteio < 0) {
                return HORAS[i];
            }
        }
        return HORAS[HORAS.length - 1];
    }

    // CLIENTES

    private List<Cliente> garantirClientes() {
        List<Cliente> existentes = clienteRepository.findAll();
        if (!existentes.isEmpty()) {
            return existentes;
        }

        String[] nomes = {"Ana Souza", "Bruno Lima", "Carla Mendes", "Diego Rocha", "Elaine Castro",
            "Fábio Nunes", "Gabriela Alves", "Henrique Dias", "Isabela Freitas", "João Pereira"};
        String[] bairros = {"Centro", "Jardim Imperador", "Vila Amorim", "Parque Maria Helena", "Jardim Revista"};

        List<Cliente> criados = new ArrayList<>();
        for (int i = 0; i < nomes.length; i++) {
            Cliente cliente = new Cliente();
            cliente.setNome(nomes[i]);
            cliente.setTelefone(String.format("(11) 9%04d-%04d", 8100 + i, 1000 + i * 37));
            cliente.setLogradouro("Rua Demonstração");
            cliente.setNumero(String.valueOf(100 + i * 10));
            cliente.setBairro(bairros[i % bairros.length]);
            cliente.setCidade("Suzano");
            cliente.setEstado("SP");
            criados.add(clienteRepository.save(cliente));
        }
        log.info("{} clientes de demonstracao criados.", criados.size());
        return criados;
    }
}
