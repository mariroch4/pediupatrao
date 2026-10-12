package com.umc.pediupatrao.service;

import com.umc.pediupatrao.config.SegurancaUtil;
import com.umc.pediupatrao.dto.RegistroAuditoria;
import com.umc.pediupatrao.entity.AlteracaoCampo;
import com.umc.pediupatrao.entity.Auditoria;
import com.umc.pediupatrao.entity.TipoOperacao;
import com.umc.pediupatrao.exception.RegraNegocioException;
import com.umc.pediupatrao.repository.AuditoriaRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

@Service
public class AuditoriaService {

    private static final ZoneId FUSO_HORARIO = ZoneId.of("America/Sao_Paulo");
    private static final int LIMITE_CONSULTA = 500;

    private static final Set<String> CAMPOS_PESSOAIS_CLIENTE =
            Set.of("nome", "telefone", "cep", "logradouro", "numero", "complemento");

    private final AuditoriaRepository auditoriaRepository;

    public AuditoriaService(AuditoriaRepository auditoriaRepository) {
        this.auditoriaRepository = auditoriaRepository;
    }

    // REGISTRO
    public Auditoria registrar(TipoOperacao operacao, String entidade, String entidadeId,
                               List<AlteracaoCampo> alteracoes, String justificativa) {
        String usuario = SegurancaUtil.usernameAtual();
        String perfil = SegurancaUtil.perfilAtual();

        Auditoria auditoria = new Auditoria(
                usuario != null ? usuario : "sistema",
                perfil != null ? perfil : "SISTEMA",
                LocalDateTime.now(FUSO_HORARIO),
                operacao,
                entidade,
                entidadeId,
                alteracoes != null ? List.copyOf(alteracoes) : List.of(),
                justificativa);

        return auditoriaRepository.inserir(auditoria);
    }

    public static List<AlteracaoCampo> compararCampos(Map<String, ?> antes, Map<String, ?> depois) {
        Set<String> campos = new LinkedHashSet<>();
        campos.addAll(antes.keySet());
        campos.addAll(depois.keySet());

        List<AlteracaoCampo> alteracoes = new ArrayList<>();
        for (String campo : campos) {
            String valorAnterior = comoTexto(antes.get(campo));
            String valorNovo = comoTexto(depois.get(campo));
            if (!Objects.equals(valorAnterior, valorNovo)) {
                alteracoes.add(new AlteracaoCampo(campo, valorAnterior, valorNovo));
            }
        }
        return alteracoes;
    }

    // CONSULTA

    @PreAuthorize("hasAnyRole('ADMIN', 'GERENTE')")
    public List<RegistroAuditoria> consultar(String entidade, String entidadeId, String usuario,
                                             TipoOperacao operacao, LocalDate dataInicio, LocalDate dataFim) {
        if (dataInicio != null && dataFim != null && dataFim.isBefore(dataInicio)) {
            throw new RegraNegocioException("A data final não pode ser anterior à data inicial.");
        }
        return auditoriaRepository
                .pesquisar(entidade, entidadeId, usuario, operacao, dataInicio, dataFim, LIMITE_CONSULTA)
                .stream()
                .map(this::paraExibicao)
                .toList();
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'GERENTE')")
    public Optional<RegistroAuditoria> buscarPorId(String id) {
        return auditoriaRepository.buscarPorId(id).map(this::paraExibicao);
    }

    public boolean dadosPessoaisRestritos() {
        return !"GERENTE".equals(SegurancaUtil.perfilAtual());
    }

    // PROTEÇÃO DOS DADOS
    private RegistroAuditoria paraExibicao(Auditoria auditoria) {
        boolean mascarar = "Cliente".equals(auditoria.getEntidade()) && dadosPessoaisRestritos();

        List<AlteracaoCampo> alteracoes = new ArrayList<>();
        boolean algumMascarado = false;
        if (auditoria.getAlteracoes() != null) {
            for (AlteracaoCampo campo : auditoria.getAlteracoes()) {
                if (mascarar && CAMPOS_PESSOAIS_CLIENTE.contains(campo.getCampo())) {
                    alteracoes.add(new AlteracaoCampo(campo.getCampo(),
                            mascararValor(campo.getCampo(), campo.getValorAnterior()),
                            mascararValor(campo.getCampo(), campo.getValorNovo())));
                    algumMascarado = true;
                } else {
                    alteracoes.add(campo);
                }
            }
        }

        return new RegistroAuditoria(
                auditoria.getId(),
                auditoria.getUsuario(),
                auditoria.getPerfil(),
                auditoria.getDataHora(),
                auditoria.getOperacao(),
                auditoria.getOperacaoDescricao(),
                auditoria.getEntidade(),
                auditoria.getEntidadeId(),
                alteracoes,
                auditoria.getJustificativa(),
                algumMascarado);
    }

    private static String mascararValor(String campo, String valor) {
        if (valor == null || valor.isEmpty()) {
            return valor;
        }
        if ("telefone".equals(campo)) {
            String digitos = valor.replaceAll("\\D", "");
            return digitos.length() >= 4
                    ? "(**) *****-" + digitos.substring(digitos.length() - 4)
                    : "****";
        }
        int asteriscos = Math.max(3, Math.min(valor.length() - 1, 8));
        return valor.charAt(0) + "*".repeat(asteriscos);
    }

    private static String comoTexto(Object valor) {
        if (valor == null) {
            return null;
        }
        String texto = valor.toString().trim();
        return texto.isEmpty() ? null : texto;
    }
}