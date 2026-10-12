package com.umc.pediupatrao.entity;

import java.time.LocalDateTime;

public class RegistroStatus {

    private StatusPedido status;
    private LocalDateTime dataHora;
    private String usuario;

    protected RegistroStatus() {
    }

    public RegistroStatus(StatusPedido status, LocalDateTime dataHora, String usuario) {
        this.status = status;
        this.dataHora = dataHora;
        this.usuario = usuario;
    }

    public StatusPedido getStatus() {
        return status;
    }

    public LocalDateTime getDataHora() {
        return dataHora;
    }

    public String getUsuario() {
        return usuario;
    }
}