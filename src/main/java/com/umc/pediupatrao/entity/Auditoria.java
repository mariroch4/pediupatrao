package com.umc.pediupatrao.entity;

import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "auditoria")
public class Auditoria {

    @Id
    private String id;

    private String usuario;          
    private String perfil;           
    private LocalDateTime dataHora;  
    private TipoOperacao operacao;   
    private String entidade;         
    private String entidadeId;       // id do registro afetado
    private List<AlteracaoCampo> alteracoes; 
    private String justificativa;    

    // Usado pelo Spring Data ao ler do MongoDB
    protected Auditoria() {
    }

    public Auditoria(String usuario, String perfil, LocalDateTime dataHora, TipoOperacao operacao,
                     String entidade, String entidadeId, List<AlteracaoCampo> alteracoes,
                     String justificativa) {
        this.usuario = usuario;
        this.perfil = perfil;
        this.dataHora = dataHora;
        this.operacao = operacao;
        this.entidade = entidade;
        this.entidadeId = entidadeId;
        this.alteracoes = alteracoes;
        this.justificativa = justificativa;
    }

    public String getId() {
        return id;
    }

    public String getUsuario() {
        return usuario;
    }

    public String getPerfil() {
        return perfil;
    }

    public LocalDateTime getDataHora() {
        return dataHora;
    }

    public TipoOperacao getOperacao() {
        return operacao;
    }
    
    public String getOperacaoDescricao() {
        return operacao != null ? operacao.getDescricao() : null;
    }

    public String getEntidade() {
        return entidade;
    }

    public String getEntidadeId() {
        return entidadeId;
    }

    public List<AlteracaoCampo> getAlteracoes() {
        return alteracoes;
    }

    public String getJustificativa() {
        return justificativa;
    }
}