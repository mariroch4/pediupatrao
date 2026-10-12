package com.umc.pediupatrao.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;

import com.umc.pediupatrao.dto.RegistroAuditoria;
import com.umc.pediupatrao.entity.AlteracaoCampo;
import com.umc.pediupatrao.entity.Auditoria;
import com.umc.pediupatrao.entity.TipoOperacao;
import com.umc.pediupatrao.repository.AuditoriaRepository;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Testes da auditoria (RF05): campos alterados, identificação do usuário e proteção de dados pessoais.
 */
@ExtendWith(MockitoExtension.class)
class AuditoriaServiceTest {

    @Mock
    private AuditoriaRepository auditoriaRepository;

    @InjectMocks
    private AuditoriaService auditoriaService;

    @AfterEach
    void limpar() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("RF05: somente os campos que mudaram são registrados, com valor anterior e novo")
    void registraSomenteCamposAlterados() {
        Map<String, Object> antes = new LinkedHashMap<>();
        antes.put("logradouro", "Rua das Flores");
        antes.put("numero", "100");
        antes.put("complemento", null);

        Map<String, Object> depois = new LinkedHashMap<>();
        depois.put("logradouro", "Av. Brasil");
        depois.put("numero", "100");
        depois.put("complemento", ""); // vazio e nulo são equivalentes

        List<AlteracaoCampo> alteracoes = AuditoriaService.compararCampos(antes, depois);

        assertEquals(1, alteracoes.size());
        assertEquals("logradouro", alteracoes.get(0).getCampo());
        assertEquals("Rua das Flores", alteracoes.get(0).getValorAnterior());
        assertEquals("Av. Brasil", alteracoes.get(0).getValorNovo());
    }

    @Test
    @DisplayName("RF05: usuário, perfil e data/hora vêm do login, não da requisição")
    void registroIdentificaUsuarioLogado() {
        autenticarComo("atendente01", "ATENDENTE");
        when(auditoriaRepository.inserir(any(Auditoria.class))).thenAnswer(invocacao -> invocacao.getArgument(0));

        Auditoria registro = auditoriaService.registrar(TipoOperacao.ALTERACAO_CLIENTE, "Cliente", "c1",
                List.of(new AlteracaoCampo("telefone", "(11) 98888-7777", "(11) 97777-6666")), null);

        assertEquals("atendente01", registro.getUsuario());
        assertEquals("ATENDENTE", registro.getPerfil());
        assertNotNull(registro.getDataHora());
        assertEquals(TipoOperacao.ALTERACAO_CLIENTE, registro.getOperacao());
    }

    @Test
    @DisplayName("RF05: ADMIN vê os dados pessoais de clientes MASCARADOS")
    void adminVeDadosPessoaisMascarados() {
        autenticarComo("admin", "ADMIN");
        when(auditoriaRepository.pesquisar(any(), any(), any(), any(), any(), any(), anyInt()))
                .thenReturn(List.of(registroDeTelefone()));

        RegistroAuditoria registro = auditoriaService.consultar(null, null, null, null, null, null).get(0);

        assertEquals("(**) *****-6666", registro.alteracoes().get(0).getValorNovo());
        assertTrue(registro.dadosMascarados());
    }

    @Test
    @DisplayName("RF05: GERENTE vê os dados de clientes completos")
    void gerenteVeDadosCompletos() {
        autenticarComo("gerente01", "GERENTE");
        when(auditoriaRepository.pesquisar(any(), any(), any(), any(), any(), any(), anyInt()))
                .thenReturn(List.of(registroDeTelefone()));

        RegistroAuditoria registro = auditoriaService.consultar(null, null, null, null, null, null).get(0);

        assertEquals("(11) 97777-6666", registro.alteracoes().get(0).getValorNovo());
        assertFalse(registro.dadosMascarados());
    }

    private Auditoria registroDeTelefone() {
        return new Auditoria("atendente01", "ATENDENTE", LocalDateTime.now(), TipoOperacao.ALTERACAO_CLIENTE,
                "Cliente", "c1",
                List.of(new AlteracaoCampo("telefone", "(11) 98888-7777", "(11) 97777-6666")), null);
    }

    private void autenticarComo(String usuario, String perfil) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                usuario, null, List.of(new SimpleGrantedAuthority("ROLE_" + perfil))));
    }
}