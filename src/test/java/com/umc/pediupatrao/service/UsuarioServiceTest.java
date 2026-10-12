package com.umc.pediupatrao.service;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.umc.pediupatrao.entity.Perfil;
import com.umc.pediupatrao.entity.Usuario;
import com.umc.pediupatrao.exception.RegraNegocioException;
import com.umc.pediupatrao.repository.UsuarioRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class UsuarioServiceTest {

    @Mock
    private UsuarioRepository usuarioRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private AuditoriaService auditoriaService;

    @InjectMocks
    private UsuarioService usuarioService;

    @BeforeEach
    void autenticarComoAdmin() {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "admin", null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
    }

    @AfterEach
    void limpar() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("RF01: ninguém pode alterar o próprio perfil")
    void naoAlteraOProprioPerfil() {
        Usuario admin = usuario("1", "admin", Perfil.ADMIN);
        when(usuarioRepository.findById("1")).thenReturn(Optional.of(admin));
        when(usuarioRepository.findByUsername("admin")).thenReturn(Optional.of(admin));

        RegraNegocioException erro = assertThrows(RegraNegocioException.class,
                () -> usuarioService.atualizarUsuario("1", "admin", null, Perfil.ATENDENTE));

        assertTrue(erro.getMessage().contains("próprio perfil"));
        verify(usuarioRepository, never()).save(any());
    }

    @Test
    @DisplayName("RF01: ninguém pode excluir o próprio usuário")
    void naoExcluiOProprioUsuario() {
        Usuario admin = usuario("1", "admin", Perfil.ADMIN);
        when(usuarioRepository.findById("1")).thenReturn(Optional.of(admin));

        assertThrows(RegraNegocioException.class, () -> usuarioService.excluirUsuario("1"));
        verify(usuarioRepository, never()).delete(any());
    }

    @Test
    @DisplayName("RF01: senha com menos de 6 caracteres é recusada")
    void senhaCurtaEhRecusada() {
        when(usuarioRepository.findByUsername("novo.usuario")).thenReturn(Optional.empty());

        RegraNegocioException erro = assertThrows(RegraNegocioException.class,
                () -> usuarioService.criarUsuario("novo.usuario", "123", Perfil.ATENDENTE));

        assertTrue(erro.getMessage().contains("6 caracteres"));
        verify(usuarioRepository, never()).save(any());
    }

    @Test
    @DisplayName("RF01: username duplicado é recusado")
    void usernameDuplicadoEhRecusado() {
        when(usuarioRepository.findByUsername("gerente01"))
                .thenReturn(Optional.of(usuario("2", "gerente01", Perfil.GERENTE)));

        assertThrows(RegraNegocioException.class,
                () -> usuarioService.criarUsuario("gerente01", "senha123", Perfil.ATENDENTE));
        verify(usuarioRepository, never()).save(any());
    }

    private Usuario usuario(String id, String username, Perfil perfil) {
        Usuario usuario = new Usuario();
        usuario.setId(id);
        usuario.setUsername(username);
        usuario.setPassword("hash");
        usuario.setRole(perfil);
        return usuario;
    }
}
