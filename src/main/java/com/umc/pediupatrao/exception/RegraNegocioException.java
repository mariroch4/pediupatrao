package com.umc.pediupatrao.exception;

// Lançada quando uma regra de negócio é violada

public class RegraNegocioException extends RuntimeException {

    public RegraNegocioException(String mensagem) {
        super(mensagem);
    }
}