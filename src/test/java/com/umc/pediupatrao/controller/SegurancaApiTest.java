package com.umc.pediupatrao.controller;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.umc.pediupatrao.config.SecurityConfig;
import com.umc.pediupatrao.service.AuditoriaService;
import com.umc.pediupatrao.service.PedidoService;
import com.umc.pediupatrao.service.UsuarioService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Testes de AUTORIZAÇÃO NO BACKEND (RF01, RF04, RF05).
 * Sobe a camada web com o SecurityConfig real e simula requisições HTTP às APIs.
 * Prova que as restrições não dependem de esconder botões na tela.
 */
@WebMvcTest(controllers = {PedidoController.class, UsuarioController.class, AuditoriaController.class})
@Import(SecurityConfig.class)
class SegurancaApiTest {

    private static final String DESCONTO_JSON = "{\"percentual\": 10}";
    private static final String CANCELAMENTO_JSON = "{\"justificativa\": \"Tentativa sem permissao\"}";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PedidoService pedidoService;
    @MockitoBean
    private UsuarioService usuarioService;
    @MockitoBean
    private AuditoriaService auditoriaService;

    @Test
    @DisplayName("API sem login responde 401")
    void apiSemLoginResponde401() throws Exception {
        mockMvc.perform(get("/api/pedidos"))
                .andExpect(status().isUnauthorized());
    }

    // ===== RF04: desconto e cancelamento exclusivos do GERENTE =====

    @Test
    @WithMockUser(username = "atendente01", roles = "ATENDENTE")
    @DisplayName("ATENDENTE não aplica desconto (403)")
    void atendenteNaoAplicaDesconto() throws Exception {
        mockMvc.perform(post("/api/pedidos/p1/desconto").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(DESCONTO_JSON))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    @DisplayName("Nem o ADMIN aplica desconto (403)")
    void adminNaoAplicaDesconto() throws Exception {
        mockMvc.perform(post("/api/pedidos/p1/desconto").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(DESCONTO_JSON))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    @DisplayName("Nem o ADMIN cancela pedido (403)")
    void adminNaoCancelaPedido() throws Exception {
        mockMvc.perform(post("/api/pedidos/p1/cancelar").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(CANCELAMENTO_JSON))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "gerente01", roles = "GERENTE")
    @DisplayName("GERENTE aplica desconto (200)")
    void gerenteAplicaDesconto() throws Exception {
        mockMvc.perform(post("/api/pedidos/p1/desconto").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(DESCONTO_JSON))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "gerente01", roles = "GERENTE")
    @DisplayName("Requisição sem token CSRF é recusada (403), mesmo para o GERENTE")
    void requisicaoSemCsrfEhRecusada() throws Exception {
        mockMvc.perform(post("/api/pedidos/p1/desconto")
                        .contentType(MediaType.APPLICATION_JSON).content(DESCONTO_JSON))
                .andExpect(status().isForbidden());
    }

    // ===== RF01: usuários e autoelevação =====

    @Test
    @WithMockUser(username = "atendente01", roles = "ATENDENTE")
    @DisplayName("ATENDENTE não lista usuários (403)")
    void atendenteNaoListaUsuarios() throws Exception {
        mockMvc.perform(get("/api/usuarios"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "atendente01", roles = "ATENDENTE")
    @DisplayName("Autoelevação: ATENDENTE tentando virar ADMIN pela API (403)")
    void atendenteNaoSeAutoEleva() throws Exception {
        mockMvc.perform(put("/api/usuarios/u1").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\": \"atendente01\", \"role\": \"ADMIN\"}"))
                .andExpect(status().isForbidden());
    }

    // ===== RF05: consulta restrita e integridade da auditoria =====

    @Test
    @WithMockUser(username = "atendente01", roles = "ATENDENTE")
    @DisplayName("ATENDENTE não consulta a auditoria (403)")
    void atendenteNaoConsultaAuditoria() throws Exception {
        mockMvc.perform(get("/api/auditoria"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "gerente01", roles = "GERENTE")
    @DisplayName("GERENTE consulta a auditoria (200)")
    void gerenteConsultaAuditoria() throws Exception {
        mockMvc.perform(get("/api/auditoria"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    @DisplayName("Registro de auditoria não pode ser excluído pela API (405)")
    void auditoriaNaoPodeSerExcluida() throws Exception {
        mockMvc.perform(delete("/api/auditoria/a1").with(csrf()))
                .andExpect(status().isMethodNotAllowed());
    }
}
