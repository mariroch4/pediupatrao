package com.umc.pediupatrao.dto;

import com.umc.pediupatrao.entity.AlteracaoCampo;
import com.umc.pediupatrao.entity.TipoOperacao;
import java.time.LocalDateTime;
import java.util.List;

public record RegistroAuditoria(
        String id,
        String usuario,
        String perfil,
        LocalDateTime dataHora,
        TipoOperacao operacao,
        String operacaoDescricao,
        String entidade,
        String entidadeId,
        List<AlteracaoCampo> alteracoes,
        String justificativa,
        boolean dadosMascarados) {
}