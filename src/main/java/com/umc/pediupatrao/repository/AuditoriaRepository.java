package com.umc.pediupatrao.repository;

import com.umc.pediupatrao.entity.Auditoria;
import com.umc.pediupatrao.entity.TipoOperacao;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Repository;

@Repository
public class AuditoriaRepository {

    private final MongoTemplate mongoTemplate;

    public AuditoriaRepository(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    public Auditoria inserir(Auditoria auditoria) {
        return mongoTemplate.insert(auditoria);
    }

    public Optional<Auditoria> buscarPorId(String id) {
        return Optional.ofNullable(mongoTemplate.findById(id, Auditoria.class));
    }

    public List<Auditoria> pesquisar(String entidade, String entidadeId, String usuario,
                                     TipoOperacao operacao, LocalDate dataInicio, LocalDate dataFim,
                                     int limite) {
        Query query = new Query();

        if (temTexto(entidade)) {
            query.addCriteria(Criteria.where("entidade").is(entidade.trim()));
        }
        if (temTexto(entidadeId)) {
            String hexadecimal = entidadeId.replaceAll("[^0-9a-fA-F]", "");
            if (hexadecimal.isEmpty()) {
                query.addCriteria(Criteria.where("entidadeId").is("__nenhum__"));
            } else {
                query.addCriteria(Criteria.where("entidadeId").regex(hexadecimal + "$", "i"));
            }
        }
        if (temTexto(usuario)) {
            query.addCriteria(Criteria.where("usuario").is(usuario.trim()));
        }
        if (operacao != null) {
            query.addCriteria(Criteria.where("operacao").is(operacao));
        }
        if (dataInicio != null || dataFim != null) {
            Criteria periodo = Criteria.where("dataHora");
            if (dataInicio != null) {
                periodo = periodo.gte(dataInicio.atStartOfDay());
            }
            if (dataFim != null) {
                periodo = periodo.lt(dataFim.plusDays(1).atStartOfDay());
            }
            query.addCriteria(periodo);
        }

        query.with(Sort.by(Sort.Direction.DESC, "dataHora")).limit(limite);
        return mongoTemplate.find(query, Auditoria.class);
    }

    private boolean temTexto(String valor) {
        return valor != null && !valor.isBlank();
    }
}