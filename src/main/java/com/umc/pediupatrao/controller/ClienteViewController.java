package com.umc.pediupatrao.controller;

import com.umc.pediupatrao.config.SegurancaUtil;
import com.umc.pediupatrao.entity.Cliente;
import com.umc.pediupatrao.exception.RecursoNaoEncontradoException;
import com.umc.pediupatrao.exception.RegraNegocioException;
import com.umc.pediupatrao.service.ClienteService;
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
@RequestMapping("/clientes")
public class ClienteViewController {

    private final ClienteService clienteService;

    public ClienteViewController(ClienteService clienteService) {
        this.clienteService = clienteService;
    }

    @GetMapping
    public String listar(Model model) {
        String perfil = SegurancaUtil.perfilAtual();
        model.addAttribute("podeEditar", "ATENDENTE".equals(perfil));
        model.addAttribute("podeExcluir", "GERENTE".equals(perfil));
        model.addAttribute("content", "clientes/lista :: content");
        return "layout";
    }

    @GetMapping("/novo")
    public String novo(Model model) {
        return exibirFormulario(new Cliente(), model);
    }

    @PostMapping("/salvar")
    public String salvar(@ModelAttribute Cliente cliente, Model model, RedirectAttributes redirect) {
        try {
            clienteService.criar(cliente);
            redirect.addFlashAttribute("sucesso", "Cliente cadastrado com sucesso!");
            return "redirect:/clientes";
        } catch (RegraNegocioException e) {
            model.addAttribute("erro", e.getMessage());
            cliente.setId(null);
            return exibirFormulario(cliente, model);
        }
    }

    @GetMapping("/editar/{id}")
    public String editar(@PathVariable String id, Model model, RedirectAttributes redirect) {
        Optional<Cliente> encontrado = clienteService.buscarPorId(id);
        if (encontrado.isEmpty()) {
            redirect.addFlashAttribute("erro", "Cliente não encontrado.");
            return "redirect:/clientes";
        }
        return exibirFormulario(encontrado.get(), model);
    }

    @PostMapping("/editar/{id}")
    public String atualizar(@PathVariable String id, @ModelAttribute Cliente cliente,
                            Model model, RedirectAttributes redirect) {
        try {
            clienteService.atualizar(id, cliente);
            redirect.addFlashAttribute("sucesso", "Cliente atualizado com sucesso!");
            return "redirect:/clientes";
        } catch (RegraNegocioException e) {
            model.addAttribute("erro", e.getMessage());
            cliente.setId(id);
            return exibirFormulario(cliente, model);
        } catch (RecursoNaoEncontradoException e) {
            redirect.addFlashAttribute("erro", e.getMessage());
            return "redirect:/clientes";
        }
    }

    private String exibirFormulario(Cliente cliente, Model model) {
        model.addAttribute("cliente", cliente);
        model.addAttribute("content", "clientes/form :: content");
        return "layout";
    }
}