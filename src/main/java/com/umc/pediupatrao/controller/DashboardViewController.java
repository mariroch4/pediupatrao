package com.umc.pediupatrao.controller;

import com.umc.pediupatrao.entity.StatusPedido;
import com.umc.pediupatrao.entity.TipoEntrega;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/dashboard")
public class DashboardViewController {

    @GetMapping
    public String dashboard(Model model) {
        model.addAttribute("tiposEntrega", TipoEntrega.values());
        model.addAttribute("statusPedido", StatusPedido.values());
        model.addAttribute("content", "dashboard/index :: content");
        return "layout";
    }
}