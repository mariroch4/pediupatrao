package com.umc.pediupatrao.service;

import com.umc.pediupatrao.config.SegurancaUtil;
import com.umc.pediupatrao.dto.NovoPedidoRequest;
import com.umc.pediupatrao.entity.Cliente;
import com.umc.pediupatrao.entity.ItemPedido;
import com.umc.pediupatrao.entity.Pedido;
import com.umc.pediupatrao.entity.Produto;
import com.umc.pediupatrao.entity.StatusPedido;
import com.umc.pediupatrao.entity.TipoOperacao;
import com.umc.pediupatrao.exception.RecursoNaoEncontradoException;
import com.umc.pediupatrao.exception.RegraNegocioException;
import com.umc.pediupatrao.repository.ClienteRepository;
import com.umc.pediupatrao.repository.PedidoRepository;
import com.umc.pediupatrao.repository.ProdutoRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

@Service
public class PedidoService {

    public static final String ENTIDADE = "Pedido";

    private static final ZoneId FUSO_HORARIO = ZoneId.of("America/Sao_Paulo");
    private static final DateTimeFormatter FORMATO_DATA_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");
    private static final int QUANTIDADE_MAXIMA_POR_PRODUTO = 50;
    private static final int MAXIMO_PRODUTOS_DIFERENTES = 30;
    private static final int TAMANHO_MAXIMO_OBSERVACAO = 200;

    // Regras do RF04
    private static final BigDecimal DESCONTO_MAXIMO_PERCENTUAL = new BigDecimal("20");
    private static final int TAMANHO_MINIMO_JUSTIFICATIVA = 10;
    private static final int TAMANHO_MAXIMO_JUSTIFICATIVA = 300;
    private static final int TAMANHO_MAXIMO_MOTIVO_DESCONTO = 200;

    private final PedidoRepository pedidoRepository;
    private final ClienteRepository clienteRepository;
    private final ProdutoRepository produtoRepository;
    private final AuditoriaService auditoriaService;

    public PedidoService(PedidoRepository pedidoRepository, ClienteRepository clienteRepository,
                         ProdutoRepository produtoRepository, AuditoriaService auditoriaService) {
        this.pedidoRepository = pedidoRepository;
        this.clienteRepository = clienteRepository;
        this.produtoRepository = produtoRepository;
        this.auditoriaService = auditoriaService;
    }

    // CONSULTAS

    @PreAuthorize("hasAnyRole('ADMIN', 'GERENTE', 'ATENDENTE')")
    public List<Pedido> listarPedidos() {
        return pedidoRepository.findAll(Sort.by(Sort.Direction.DESC, "dataHoraEntrada"));
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'GERENTE', 'ATENDENTE')")
    public Pedido buscarPorId(String id) {
        return pedidoRepository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Pedido não encontrado."));
    }

    // CRIAÇÃO

    @PreAuthorize("hasAnyRole('GERENTE', 'ATENDENTE')")
    public Pedido criarPedido(NovoPedidoRequest dados) {
        if (dados == null) {
            throw new RegraNegocioException("Dados do pedido não informados.");
        }

        Cliente cliente = buscarCliente(dados.clienteId());

        if (dados.tipoEntrega() == null) {
            throw new RegraNegocioException("Informe se o pedido é para entrega ou retirada.");
        }

        Pedido pedido = new Pedido();
        pedido.setClienteId(cliente.getId());
        pedido.setClienteNome(cliente.getNome());
        pedido.setTipoEntrega(dados.tipoEntrega());
        pedido.setObservacao(validarObservacao(dados.observacao()));
        pedido.setItens(montarItens(dados.itens()));
        pedido.setPercentualDesconto(BigDecimal.ZERO.setScale(2));
        recalcularTotais(pedido);

        LocalDateTime agora = agora();
        String usuario = SegurancaUtil.usernameAtual();
        pedido.setStatus(StatusPedido.RECEBIDO);
        pedido.setDataHoraEntrada(agora);
        pedido.setResponsavelEntrada(usuario);
        pedido.registrarHistorico(StatusPedido.RECEBIDO, agora, usuario);

        Pedido salvo = pedidoRepository.save(pedido);

        // AUDITORIA: inclusão do pedido
        auditoriaService.registrar(TipoOperacao.INCLUSAO_PEDIDO, ENTIDADE, salvo.getId(),
                AuditoriaService.compararCampos(Map.of(), retrato(salvo)), null);

        return salvo;
    }

    // TRANSIÇÃO DE STATUS

    @PreAuthorize("hasAnyRole('GERENTE', 'ATENDENTE')")
    public Pedido alterarStatus(String id, StatusPedido novoStatus) {
        if (novoStatus == null) {
            throw new RegraNegocioException("Informe o novo status do pedido.");
        }
        if (novoStatus == StatusPedido.CANCELADO) {
            throw new RegraNegocioException(
                    "Para cancelar, use a operação de cancelamento (exclusiva do GERENTE, com justificativa).");
        }

        Pedido pedido = buscarPorId(id);
        StatusPedido statusAtual = pedido.getStatus();

        if (statusAtual == StatusPedido.CANCELADO) {
            throw new RegraNegocioException("Pedido cancelado não pode ter o status alterado.");
        }
        if (statusAtual == StatusPedido.FINALIZADO) {
            throw new RegraNegocioException("Pedido finalizado não pode ter o status alterado.");
        }
        if (!statusAtual.podeIrPara(novoStatus, pedido.getTipoEntrega())) {
            String permitidos = statusAtual.proximosPermitidos(pedido.getTipoEntrega()).stream()
                    .map(StatusPedido::getDescricao)
                    .collect(Collectors.joining(", "));
            throw new RegraNegocioException("Transição inválida: " + statusAtual.getDescricao()
                    + " → " + novoStatus.getDescricao() + ". Próximo status permitido: " + permitidos + ".");
        }

        Map<String, Object> antes = retrato(pedido); // AUDITORIA: retrato antes

        LocalDateTime agora = agora();
        String usuario = SegurancaUtil.usernameAtual();

        pedido.setStatus(novoStatus);

        if (novoStatus.isSaida()) {
            pedido.setDataHoraSaida(agora);
            pedido.setResponsavelSaida(usuario);
        }
        if (novoStatus == StatusPedido.FINALIZADO) {
            pedido.setDataHoraFinalizacao(agora);
            pedido.setResponsavelFinalizacao(usuario);
        }
        pedido.registrarHistorico(novoStatus, agora, usuario);

        Pedido salvo = pedidoRepository.save(pedido);

        // AUDITORIA: mudança de status
        auditoriaService.registrar(TipoOperacao.ALTERACAO_STATUS_PEDIDO, ENTIDADE, salvo.getId(),
                AuditoriaService.compararCampos(antes, retrato(salvo)), null);

        return salvo;
    }

    @PreAuthorize("hasRole('GERENTE')")
    public Pedido cancelarPedido(String id, String justificativa) {
        String motivo = justificativa != null ? justificativa.trim() : "";
        if (motivo.length() < TAMANHO_MINIMO_JUSTIFICATIVA) {
            throw new RegraNegocioException("A justificativa do cancelamento é obrigatória e deve ter pelo menos "
                    + TAMANHO_MINIMO_JUSTIFICATIVA + " caracteres.");
        }
        if (motivo.length() > TAMANHO_MAXIMO_JUSTIFICATIVA) {
            throw new RegraNegocioException("A justificativa pode ter no máximo "
                    + TAMANHO_MAXIMO_JUSTIFICATIVA + " caracteres.");
        }

        Pedido pedido = buscarPorId(id);
        validarAntesDaSaida(pedido, "ser cancelado");

        Map<String, Object> antes = retrato(pedido); // AUDITORIA: retrato antes

        LocalDateTime agora = agora();
        String usuario = SegurancaUtil.usernameAtual();

        // Apenas o status e os dados do cancelamento mudam: itens e valores são preservados
        pedido.setStatus(StatusPedido.CANCELADO);
        pedido.setDataHoraCancelamento(agora);
        pedido.setResponsavelCancelamento(usuario);
        pedido.setJustificativaCancelamento(motivo);
        pedido.registrarHistorico(StatusPedido.CANCELADO, agora, usuario);

        Pedido salvo = pedidoRepository.save(pedido);

        // AUDITORIA: cancelamento COM justificativa
        auditoriaService.registrar(TipoOperacao.CANCELAMENTO_PEDIDO, ENTIDADE, salvo.getId(),
                AuditoriaService.compararCampos(antes, retrato(salvo)), motivo);

        return salvo;
    }

    // DESCONTO

    @PreAuthorize("hasRole('GERENTE')")
    public Pedido aplicarDesconto(String id, BigDecimal percentual, String motivo) {
        if (percentual == null) {
            throw new RegraNegocioException("Informe o percentual de desconto.");
        }
        if (percentual.compareTo(BigDecimal.ZERO) <= 0) {
            throw new RegraNegocioException("O percentual de desconto deve ser maior que zero.");
        }
        if (percentual.compareTo(DESCONTO_MAXIMO_PERCENTUAL) > 0) {
            throw new RegraNegocioException("O desconto máximo permitido é de "
                    + DESCONTO_MAXIMO_PERCENTUAL + "% do valor do pedido.");
        }
        if (percentual.stripTrailingZeros().scale() > 2) {
            throw new RegraNegocioException("Use no máximo duas casas decimais no percentual de desconto.");
        }

        String motivoTexto = null;
        if (motivo != null && !motivo.isBlank()) {
            motivoTexto = motivo.trim();
            if (motivoTexto.length() > TAMANHO_MAXIMO_MOTIVO_DESCONTO) {
                throw new RegraNegocioException("O motivo do desconto pode ter no máximo "
                        + TAMANHO_MAXIMO_MOTIVO_DESCONTO + " caracteres.");
            }
        }

        Pedido pedido = buscarPorId(id);
        validarAntesDaSaida(pedido, "receber desconto");

        BigDecimal novoPercentual = percentual.setScale(2, RoundingMode.HALF_UP);
        if (pedido.getPercentualDesconto() != null
                && novoPercentual.compareTo(pedido.getPercentualDesconto()) == 0) {
            throw new RegraNegocioException("Este percentual de desconto já está aplicado ao pedido.");
        }

        Map<String, Object> antes = retrato(pedido); // AUDITORIA: retrato antes

        pedido.setPercentualDesconto(novoPercentual);
        recalcularTotais(pedido); 
        pedido.setDataHoraDesconto(agora());
        pedido.setResponsavelDesconto(SegurancaUtil.usernameAtual());

        Pedido salvo = pedidoRepository.save(pedido);

        // AUDITORIA: desconto 
        auditoriaService.registrar(TipoOperacao.DESCONTO_PEDIDO, ENTIDADE, salvo.getId(),
                AuditoriaService.compararCampos(antes, retrato(salvo)), motivoTexto);

        return salvo;
    }

    private void validarAntesDaSaida(Pedido pedido, String acao) {
        StatusPedido status = pedido.getStatus();
        if (status == StatusPedido.CANCELADO) {
            throw new RegraNegocioException("O pedido já está cancelado e não pode mais ser alterado.");
        }
        if (!status.isAntesDaSaida()) {
            throw new RegraNegocioException("O pedido não pode " + acao
                    + " após a saída para entrega ou retirada (status atual: " + status.getDescricao() + ").");
        }
    }

    // CÁLCULO DOS VALORES
    private void recalcularTotais(Pedido pedido) {
        BigDecimal subtotal = pedido.getItens().stream()
                .map(ItemPedido::getSubtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);

        BigDecimal percentual = pedido.getPercentualDesconto() != null
                ? pedido.getPercentualDesconto()
                : BigDecimal.ZERO;

        BigDecimal valorDesconto = subtotal.multiply(percentual)
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);

        pedido.setSubtotal(subtotal);
        pedido.setValorDesconto(valorDesconto);
        pedido.setValorTotal(subtotal.subtract(valorDesconto));
    }

    private List<ItemPedido> montarItens(List<NovoPedidoRequest.ItemRequest> itensRequisicao) {
        if (itensRequisicao == null || itensRequisicao.isEmpty()) {
            throw new RegraNegocioException("O pedido deve ter pelo menos um item.");
        }

        Map<String, Integer> quantidadePorProduto = new LinkedHashMap<>();
        for (NovoPedidoRequest.ItemRequest item : itensRequisicao) {
            if (item == null || item.produtoId() == null || item.produtoId().isBlank()) {
                throw new RegraNegocioException("Existe um item sem produto informado.");
            }
            if (item.quantidade() == null || item.quantidade() < 1) {
                throw new RegraNegocioException("A quantidade de cada item deve ser de pelo menos 1.");
            }
            quantidadePorProduto.merge(item.produtoId().trim(), item.quantidade(), Integer::sum);
        }

        if (quantidadePorProduto.size() > MAXIMO_PRODUTOS_DIFERENTES) {
            throw new RegraNegocioException("O pedido pode ter no máximo " + MAXIMO_PRODUTOS_DIFERENTES
                    + " produtos diferentes.");
        }

        List<ItemPedido> itens = new ArrayList<>();
        for (Map.Entry<String, Integer> entrada : quantidadePorProduto.entrySet()) {
            String produtoId = entrada.getKey();
            int quantidade = entrada.getValue();

            if (quantidade > QUANTIDADE_MAXIMA_POR_PRODUTO) {
                throw new RegraNegocioException("A quantidade máxima por produto é " + QUANTIDADE_MAXIMA_POR_PRODUTO + ".");
            }

            Produto produto = produtoRepository.findById(produtoId)
                    .orElseThrow(() -> new RegraNegocioException("Produto não encontrado: " + produtoId));

            if (!Boolean.TRUE.equals(produto.getAtivo())) {
                throw new RegraNegocioException("O produto '" + produto.getNome() + "' está inativo.");
            }
            if (produto.getPreco() == null || produto.getPreco() <= 0) {
                throw new RegraNegocioException("O produto '" + produto.getNome() + "' está sem preço válido.");
            }

            BigDecimal precoUnitario = BigDecimal.valueOf(produto.getPreco()).setScale(2, RoundingMode.HALF_UP);
            itens.add(new ItemPedido(produto.getId(), produto.getNome(), quantidade, precoUnitario));
        }
        return itens;
    }

    // VALIDAÇÕES

    private Cliente buscarCliente(String clienteId) {
        if (clienteId == null || clienteId.isBlank()) {
            throw new RegraNegocioException("Selecione o cliente do pedido.");
        }
        return clienteRepository.findById(clienteId.trim())
                .orElseThrow(() -> new RegraNegocioException("Cliente não encontrado."));
    }

    private String validarObservacao(String observacao) {
        if (observacao == null || observacao.isBlank()) {
            return null;
        }
        String texto = observacao.trim();
        if (texto.length() > TAMANHO_MAXIMO_OBSERVACAO) {
            throw new RegraNegocioException("A observação pode ter no máximo " + TAMANHO_MAXIMO_OBSERVACAO + " caracteres.");
        }
        return texto;
    }

    // AUDITORIA
    
    private Map<String, Object> retrato(Pedido p) {
        Map<String, Object> campos = new LinkedHashMap<>();
        campos.put("cliente", p.getClienteNome());
        campos.put("clienteId", p.getClienteId());
        campos.put("tipoEntrega", p.getTipoEntrega());
        campos.put("itens", descreverItens(p.getItens()));
        campos.put("subtotal", p.getSubtotal());
        campos.put("percentualDesconto", p.getPercentualDesconto());
        campos.put("valorDesconto", p.getValorDesconto());
        campos.put("valorTotal", p.getValorTotal());
        campos.put("status", p.getStatus());
        campos.put("observacao", p.getObservacao());
        campos.put("dataHoraEntrada", formatar(p.getDataHoraEntrada()));
        campos.put("responsavelEntrada", p.getResponsavelEntrada());
        campos.put("dataHoraSaida", formatar(p.getDataHoraSaida()));
        campos.put("responsavelSaida", p.getResponsavelSaida());
        campos.put("dataHoraFinalizacao", formatar(p.getDataHoraFinalizacao()));
        campos.put("responsavelFinalizacao", p.getResponsavelFinalizacao());
        campos.put("dataHoraDesconto", formatar(p.getDataHoraDesconto()));
        campos.put("responsavelDesconto", p.getResponsavelDesconto());
        campos.put("dataHoraCancelamento", formatar(p.getDataHoraCancelamento()));
        campos.put("responsavelCancelamento", p.getResponsavelCancelamento());
        campos.put("justificativaCancelamento", p.getJustificativaCancelamento());
        return campos;
    }

    private String descreverItens(List<ItemPedido> itens) {
        if (itens == null || itens.isEmpty()) {
            return null;
        }
        return itens.stream()
                .map(i -> i.getQuantidade() + "x " + i.getNomeProduto() + " (R$ " + i.getPrecoUnitario() + ")")
                .collect(Collectors.joining("; "));
    }

    private String formatar(LocalDateTime dataHora) {
        return dataHora != null ? dataHora.format(FORMATO_DATA_HORA) : null;
    }

    private LocalDateTime agora() {
        return LocalDateTime.now(FUSO_HORARIO);
    }
}