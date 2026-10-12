package com.umc.pediupatrao.controller;

import com.umc.pediupatrao.config.SegurancaUtil;
import com.umc.pediupatrao.entity.Perfil;
import com.umc.pediupatrao.entity.Usuario;
import com.umc.pediupatrao.exception.RecursoNaoEncontradoException;
import com.umc.pediupatrao.exception.RegraNegocioException;
import com.umc.pediupatrao.service.UsuarioService;
import java.util.Optional;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/usuarios")
public class UsuarioViewController {

    private final UsuarioService usuarioService;

    public UsuarioViewController(UsuarioService usuarioService) {
        this.usuarioService = usuarioService;
    }

    @GetMapping
    public String listar(Model model) {
        model.addAttribute("usuarios", usuarioService.listarTodos());
        model.addAttribute("content", "usuarios/lista :: content");
        return "layout";
    }

    @GetMapping("/novo")
    public String novo(Model model) {
        return exibirFormulario(new Usuario(), model);
    }

    @GetMapping("/editar/{id}")
    public String editar(@PathVariable String id, Model model, RedirectAttributes redirect) {
        Optional<Usuario> encontrado = usuarioService.buscarPorId(id);
        if (encontrado.isEmpty()) {
            redirect.addFlashAttribute("erro", "Usuário não encontrado.");
            return "redirect:/usuarios";
        }
        Usuario usuario = encontrado.get();
        usuario.setPassword(null); 
        return exibirFormulario(usuario, model);
    }

    @PostMapping("/salvar")
    public String salvar(@ModelAttribute Usuario usuario, Model model, RedirectAttributes redirect) {
        try {
            usuarioService.criarUsuario(usuario.getUsername(), usuario.getPassword(), usuario.getRole());
            redirect.addFlashAttribute("sucesso", "Usuário criado com sucesso!");
            return "redirect:/usuarios";
        } catch (RegraNegocioException e) {
            model.addAttribute("erro", e.getMessage());
            usuario.setId(null);
            usuario.setPassword(null);
            return exibirFormulario(usuario, model);
        }
    }

    @PostMapping("/editar/{id}")
    public String atualizar(@PathVariable String id, @ModelAttribute Usuario usuario,
                            Model model, RedirectAttributes redirect) {
        try {
            usuarioService.atualizarUsuario(id, usuario.getUsername(), usuario.getPassword(), usuario.getRole());
            redirect.addFlashAttribute("sucesso", "Usuário atualizado com sucesso!");
            return "redirect:/usuarios";
        } catch (RegraNegocioException e) {
            model.addAttribute("erro", e.getMessage());
            usuario.setId(id);
            usuario.setPassword(null);
            return exibirFormulario(usuario, model);
        } catch (RecursoNaoEncontradoException e) {
            redirect.addFlashAttribute("erro", e.getMessage());
            return "redirect:/usuarios";
        }
    }

    @PostMapping("/deletar/{id}")
    public String deletar(@PathVariable String id, RedirectAttributes redirect) {
        try {
            usuarioService.excluirUsuario(id);
            redirect.addFlashAttribute("sucesso", "Usuário removido com sucesso!");
        } catch (RegraNegocioException | RecursoNaoEncontradoException e) {
            redirect.addFlashAttribute("erro", e.getMessage());
        }
        return "redirect:/usuarios";
    }

    private String exibirFormulario(Usuario usuario, Model model) {
        // Verifica no banco se o usuário em edição é o próprio usuário logado
        boolean ehOProprio = usuario.getId() != null && usuarioService.buscarPorId(usuario.getId())
                .map(u -> u.getUsername().equals(SegurancaUtil.usernameAtual()))
                .orElse(false);

        model.addAttribute("usuario", usuario);
        model.addAttribute("perfis", Perfil.values());
        model.addAttribute("ehOProprio", ehOProprio);
        model.addAttribute("content", "usuarios/form :: content");
        return "layout";
    }
}