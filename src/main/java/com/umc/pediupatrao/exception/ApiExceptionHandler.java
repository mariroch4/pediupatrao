package com.umc.pediupatrao.exception;

import com.umc.pediupatrao.entity.StatusPedido;
import com.umc.pediupatrao.entity.TipoEntrega;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice(annotations = RestController.class)
public class ApiExceptionHandler {

    @ExceptionHandler(RegraNegocioException.class)
    public ResponseEntity<Map<String, Object>> regraNegocio(RegraNegocioException ex) {
        return resposta(HttpStatus.UNPROCESSABLE_ENTITY, "Regra de negócio violada", ex.getMessage());
    }

    @ExceptionHandler(RecursoNaoEncontradoException.class)
    public ResponseEntity<Map<String, Object>> naoEncontrado(RecursoNaoEncontradoException ex) {
        return resposta(HttpStatus.NOT_FOUND, "Não encontrado", ex.getMessage());
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, Object>> corpoInvalido(HttpMessageNotReadableException ex) {
        return resposta(HttpStatus.BAD_REQUEST, "Requisição inválida",
                "Corpo da requisição inválido. Verifique os campos e valores enviados.");
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Map<String, Object>> parametroInvalido(MethodArgumentTypeMismatchException ex) {
        String mensagem = "Parâmetro '" + ex.getName() + "' com valor inválido: '" + ex.getValue() + "'.";
        Class<?> tipo = ex.getRequiredType();
        if (tipo != null && tipo.isEnum()) {
            mensagem += " Valores aceitos: " + valoresAceitos(tipo.getEnumConstants()) + ".";
        } else if (tipo == Integer.class || tipo == int.class) {
            mensagem += " Informe um número inteiro.";
        }
        return resposta(HttpStatus.BAD_REQUEST, "Parâmetro inválido", mensagem);
    }

    @ExceptionHandler(BindException.class)
    public ResponseEntity<Map<String, Object>> filtroInvalido(BindException ex) {
        FieldError erro = ex.getFieldError();
        if (erro == null) {
            return resposta(HttpStatus.BAD_REQUEST, "Parâmetro inválido", "Parâmetros da requisição inválidos.");
        }
        Object valor = erro.getRejectedValue();
        if (valor instanceof Object[] array && array.length > 0) {
            valor = array[0];
        }
        String campo = erro.getField();
        String mensagem = "Parâmetro '" + campo + "' com valor inválido: '" + valor + "'." + dica(campo);
        return resposta(HttpStatus.BAD_REQUEST, "Parâmetro inválido", mensagem);
    }

    private String dica(String campo) {
        return switch (campo) {
            case "dataInicio", "dataFim" -> " Use o formato AAAA-MM-DD (ex.: 2026-11-02).";
            case "tipoEntrega" -> " Valores aceitos: " + valoresAceitos(TipoEntrega.values()) + ".";
            case "status" -> " Valores aceitos: " + valoresAceitos(StatusPedido.values()) + ".";
            default -> "";
        };
    }

    private String valoresAceitos(Object[] constantes) {
        return Arrays.stream(constantes).map(String::valueOf).collect(Collectors.joining(", "));
    }

    private ResponseEntity<Map<String, Object>> resposta(HttpStatus status, String erro, String mensagem) {
        Map<String, Object> corpo = new LinkedHashMap<>();
        corpo.put("timestamp", LocalDateTime.now().toString());
        corpo.put("status", status.value());
        corpo.put("erro", erro);
        corpo.put("mensagem", mensagem);
        return ResponseEntity.status(status).body(corpo);
    }
}