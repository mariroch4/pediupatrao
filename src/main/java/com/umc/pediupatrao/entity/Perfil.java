package com.umc.pediupatrao.entity;

//Usar enum impede que sejam gravados perfis inexistentes no banco
public enum Perfil {

    ADMIN("Administrador"),
    GERENTE("Gerente"),
    ATENDENTE("Atendente");

    private final String descricao;

    Perfil(String descricao) {
        this.descricao = descricao;
    }

    public String getDescricao() {
        return descricao;
    }
}