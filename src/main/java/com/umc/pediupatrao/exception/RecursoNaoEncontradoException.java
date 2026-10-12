package com.umc.pediupatrao.exception;

// Lançada quando um registro não é encontrado

public class RecursoNaoEncontradoException extends RuntimeException {

    public RecursoNaoEncontradoException(String mensagem) {
        super(mensagem);
    }
}