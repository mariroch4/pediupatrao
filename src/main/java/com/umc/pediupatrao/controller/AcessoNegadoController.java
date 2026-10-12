package com.umc.pediupatrao.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
public class AcessoNegadoController {

    @RequestMapping("/acesso-negado")
    public String acessoNegado(HttpServletRequest request, Model model) {
        Object mensagem = request.getAttribute("mensagemAcessoNegado");
        model.addAttribute("mensagem", mensagem != null
                ? mensagem
                : "Seu perfil não tem permissão para acessar esta página.");
        model.addAttribute("content", "acesso-negado :: content");
        return "layout";
    }
}