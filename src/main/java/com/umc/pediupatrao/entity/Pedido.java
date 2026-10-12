package com.umc.pediupatrao.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "pedidos")
public class Pedido {

    @Id
    private String id;

    private String clienteId;
    private String clienteNome;

    private TipoEntrega tipoEntrega;
    private String observacao;

    private List<ItemPedido> itens = new ArrayList<>();
    private BigDecimal subtotal;
    private BigDecimal percentualDesconto;
    private BigDecimal valorDesconto;
    private BigDecimal valorTotal;

    private StatusPedido status;

    // Entrada
    private LocalDateTime dataHoraEntrada;
    private String responsavelEntrada;

    // Saída 
    private LocalDateTime dataHoraSaida;
    private String responsavelSaida;

    // Finalização
    private LocalDateTime dataHoraFinalizacao;
    private String responsavelFinalizacao;

    // Desconto 
    private LocalDateTime dataHoraDesconto;
    private String responsavelDesconto;

    // Cancelamento 
    private LocalDateTime dataHoraCancelamento;
    private String responsavelCancelamento;
    private String justificativaCancelamento;

    // Histórico de andamento
    private List<RegistroStatus> historicoStatus = new ArrayList<>();

    public void registrarHistorico(StatusPedido novoStatus, LocalDateTime dataHora, String usuario) {
        if (historicoStatus == null) {
            historicoStatus = new ArrayList<>();
        }
        historicoStatus.add(new RegistroStatus(novoStatus, dataHora, usuario));
    }

    // GET/SET

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getClienteId() { return clienteId; }
    public void setClienteId(String clienteId) { this.clienteId = clienteId; }

    public String getClienteNome() { return clienteNome; }
    public void setClienteNome(String clienteNome) { this.clienteNome = clienteNome; }

    public TipoEntrega getTipoEntrega() { return tipoEntrega; }
    public void setTipoEntrega(TipoEntrega tipoEntrega) { this.tipoEntrega = tipoEntrega; }

    public String getObservacao() { return observacao; }
    public void setObservacao(String observacao) { this.observacao = observacao; }

    public List<ItemPedido> getItens() { return itens; }
    public void setItens(List<ItemPedido> itens) { this.itens = itens; }

    public BigDecimal getSubtotal() { return subtotal; }
    public void setSubtotal(BigDecimal subtotal) { this.subtotal = subtotal; }

    public BigDecimal getPercentualDesconto() { return percentualDesconto; }
    public void setPercentualDesconto(BigDecimal percentualDesconto) { this.percentualDesconto = percentualDesconto; }

    public BigDecimal getValorDesconto() { return valorDesconto; }
    public void setValorDesconto(BigDecimal valorDesconto) { this.valorDesconto = valorDesconto; }

    public BigDecimal getValorTotal() { return valorTotal; }
    public void setValorTotal(BigDecimal valorTotal) { this.valorTotal = valorTotal; }

    public StatusPedido getStatus() { return status; }
    public void setStatus(StatusPedido status) { this.status = status; }

    public LocalDateTime getDataHoraEntrada() { return dataHoraEntrada; }
    public void setDataHoraEntrada(LocalDateTime dataHoraEntrada) { this.dataHoraEntrada = dataHoraEntrada; }

    public String getResponsavelEntrada() { return responsavelEntrada; }
    public void setResponsavelEntrada(String responsavelEntrada) { this.responsavelEntrada = responsavelEntrada; }

    public LocalDateTime getDataHoraSaida() { return dataHoraSaida; }
    public void setDataHoraSaida(LocalDateTime dataHoraSaida) { this.dataHoraSaida = dataHoraSaida; }

    public String getResponsavelSaida() { return responsavelSaida; }
    public void setResponsavelSaida(String responsavelSaida) { this.responsavelSaida = responsavelSaida; }

    public LocalDateTime getDataHoraFinalizacao() { return dataHoraFinalizacao; }
    public void setDataHoraFinalizacao(LocalDateTime dataHoraFinalizacao) { this.dataHoraFinalizacao = dataHoraFinalizacao; }

    public String getResponsavelFinalizacao() { return responsavelFinalizacao; }
    public void setResponsavelFinalizacao(String responsavelFinalizacao) { this.responsavelFinalizacao = responsavelFinalizacao; }

    public LocalDateTime getDataHoraDesconto() { return dataHoraDesconto; }
    public void setDataHoraDesconto(LocalDateTime dataHoraDesconto) { this.dataHoraDesconto = dataHoraDesconto; }

    public String getResponsavelDesconto() { return responsavelDesconto; }
    public void setResponsavelDesconto(String responsavelDesconto) { this.responsavelDesconto = responsavelDesconto; }

    public LocalDateTime getDataHoraCancelamento() { return dataHoraCancelamento; }
    public void setDataHoraCancelamento(LocalDateTime dataHoraCancelamento) { this.dataHoraCancelamento = dataHoraCancelamento; }

    public String getResponsavelCancelamento() { return responsavelCancelamento; }
    public void setResponsavelCancelamento(String responsavelCancelamento) { this.responsavelCancelamento = responsavelCancelamento; }

    public String getJustificativaCancelamento() { return justificativaCancelamento; }
    public void setJustificativaCancelamento(String justificativaCancelamento) { this.justificativaCancelamento = justificativaCancelamento; }

    public List<RegistroStatus> getHistoricoStatus() { return historicoStatus; }
}