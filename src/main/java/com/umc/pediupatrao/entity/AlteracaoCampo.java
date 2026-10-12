package com.umc.pediupatrao.entity;

public class AlteracaoCampo {

    private String campo;
    private String valorAnterior;
    private String valorNovo;

    protected AlteracaoCampo() {
    }

    public AlteracaoCampo(String campo, String valorAnterior, String valorNovo) {
        this.campo = campo;
        this.valorAnterior = valorAnterior;
        this.valorNovo = valorNovo;
    }

    public String getCampo() {
        return campo;
    }

    public String getValorAnterior() {
        return valorAnterior;
    }

    public String getValorNovo() {
        return valorNovo;
    }
}
