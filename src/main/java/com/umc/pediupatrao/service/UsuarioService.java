package com.umc.pediupatrao.service;

import com.umc.pediupatrao.config.SegurancaUtil;
import com.umc.pediupatrao.entity.AlteracaoCampo;
import com.umc.pediupatrao.entity.Perfil;
import com.umc.pediupatrao.entity.TipoOperacao;
import com.umc.pediupatrao.entity.Usuario;
import com.umc.pediupatrao.exception.RecursoNaoEncontradoException;
import com.umc.pediupatrao.exception.RegraNegocioException;
import com.umc.pediupatrao.repository.UsuarioRepository;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@PreAuthorize("hasRole('ADMIN')")
public class UsuarioService {

    private static final Logger log = LoggerFactory.getLogger(UsuarioService.class);
    private static final int TAMANHO_MINIMO_SENHA = 6;
    private static final String PADRAO_USERNAME = "^[a-zA-Z0-9._-]{3,30}$";
    private static final String ENTIDADE = "Usuario";

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditoriaService auditoriaService;

    public UsuarioService(UsuarioRepository usuarioRepository, PasswordEncoder passwordEncoder,
                          AuditoriaService auditoriaService) {
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
        this.auditoriaService = auditoriaService;
    }

    public List<Usuario> listarTodos() {
        return usuarioRepository.findAll();
    }

    public Optional<Usuario> buscarPorId(String id) {
        return usuarioRepository.findById(id);
    }

    public Usuario criarUsuario(String username, String senha, Perfil perfil) {
        String nome = validarUsername(username, null);
        validarPerfil(perfil);
        validarSenha(senha);

        Usuario usuario = new Usuario();
        usuario.setUsername(nome);
        usuario.setPassword(passwordEncoder.encode(senha));
        usuario.setRole(perfil);

        Usuario salvo = usuarioRepository.save(usuario);

        // AUDITORIA: inclusão
        auditoriaService.registrar(TipoOperacao.INCLUSAO_USUARIO, ENTIDADE, salvo.getId(),
                AuditoriaService.compararCampos(Map.of(), retrato(salvo)), null);

        log.info("Usuario '{}' criado com perfil {} por '{}'", nome, perfil, SegurancaUtil.usernameAtual());
        return salvo;
    }

    public Usuario atualizarUsuario(String id, String username, String novaSenha, Perfil novoPerfil) {
        Usuario existente = buscarObrigatorio(id);
        String nome = validarUsername(username, id);
        validarPerfil(novoPerfil);

        boolean ehOProprioUsuario = existente.getUsername().equals(SegurancaUtil.usernameAtual());

        // Proteção para ninguém alterar o próprio username
        if (ehOProprioUsuario && !nome.equals(existente.getUsername())) {
            throw new RegraNegocioException("Você não pode alterar o seu próprio username enquanto está logado.");
        }

        Perfil perfilAnterior = existente.getRole();
        if (perfilAnterior != novoPerfil) {
            // Proteção contra alteração do próprio privilégio
            if (ehOProprioUsuario) {
                throw new RegraNegocioException("Você não pode alterar o seu próprio perfil.");
            }
            garantirQueNaoEhUltimoAdmin(existente);
        }

        Map<String, Object> antes = retrato(existente); // AUDITORIA: retrato antes da alteração

        existente.setUsername(nome);
        existente.setRole(novoPerfil);

        boolean senhaAlterada = false;
        if (novaSenha != null && !novaSenha.isBlank()) {
            validarSenha(novaSenha);
            existente.setPassword(passwordEncoder.encode(novaSenha));
            senhaAlterada = true;
        }

        Usuario salvo = usuarioRepository.save(existente);

        // AUDITORIA: registra só os campos que mudaram
        List<AlteracaoCampo> alteracoes = new java.util.ArrayList<>(
                AuditoriaService.compararCampos(antes, retrato(salvo)));
        if (senhaAlterada) {
            // Nunca registrar a senha nem o hash: apenas que ela foi alterada
            alteracoes.add(new AlteracaoCampo("senha", "[protegida]", "[alterada]"));
        }
        if (!alteracoes.isEmpty()) {
            auditoriaService.registrar(TipoOperacao.ALTERACAO_USUARIO, ENTIDADE, salvo.getId(), alteracoes, null);
        }

        if (perfilAnterior != novoPerfil) {
            log.info("Perfil do usuario '{}' alterado de {} para {} por '{}'",
                    nome, perfilAnterior, novoPerfil, SegurancaUtil.usernameAtual());
        }
        return salvo;
    }

    public void excluirUsuario(String id) {
        Usuario existente = buscarObrigatorio(id);

        if (existente.getUsername().equals(SegurancaUtil.usernameAtual())) {
            throw new RegraNegocioException("Você não pode excluir o seu próprio usuário.");
        }
        garantirQueNaoEhUltimoAdmin(existente);

        usuarioRepository.delete(existente);

        // AUDITORIA: exclusão 
        auditoriaService.registrar(TipoOperacao.EXCLUSAO_USUARIO, ENTIDADE, id,
                AuditoriaService.compararCampos(retrato(existente), Map.of()), null);

        log.info("Usuario '{}' excluido por '{}'", existente.getUsername(), SegurancaUtil.usernameAtual());
    }

    private Map<String, Object> retrato(Usuario usuario) {
        Map<String, Object> campos = new LinkedHashMap<>();
        campos.put("username", usuario.getUsername());
        campos.put("perfil", usuario.getRole());
        return campos;
    }

    // VALIDAÇÕES

    private Usuario buscarObrigatorio(String id) {
        return usuarioRepository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Usuário não encontrado."));
    }

    private void garantirQueNaoEhUltimoAdmin(Usuario usuario) {
        if (usuario.getRole() == Perfil.ADMIN && usuarioRepository.countByRole(Perfil.ADMIN) <= 1) {
            throw new RegraNegocioException("Operação não permitida: o sistema precisa manter pelo menos um ADMIN.");
        }
    }

    private String validarUsername(String username, String idAtual) {
        if (username == null || username.isBlank()) {
            throw new RegraNegocioException("O username é obrigatório.");
        }
        String nome = username.trim();
        if (!nome.matches(PADRAO_USERNAME)) {
            throw new RegraNegocioException(
                    "O username deve ter de 3 a 30 caracteres (letras, números, ponto, hífen ou underline).");
        }
        usuarioRepository.findByUsername(nome)
                .filter(outro -> !outro.getId().equals(idAtual))
                .ifPresent(outro -> {
                    throw new RegraNegocioException("Já existe um usuário com este username.");
                });
        return nome;
    }

    private void validarPerfil(Perfil perfil) {
        if (perfil == null) {
            throw new RegraNegocioException("Selecione um perfil válido: ADMIN, GERENTE ou ATENDENTE.");
        }
    }

    private void validarSenha(String senha) {
        if (senha == null || senha.length() < TAMANHO_MINIMO_SENHA) {
            throw new RegraNegocioException(
                    "A senha deve ter pelo menos " + TAMANHO_MINIMO_SENHA + " caracteres.");
        }
    }
}