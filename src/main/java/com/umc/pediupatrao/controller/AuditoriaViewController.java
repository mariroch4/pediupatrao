package com.umc.pediupatrao.controller;

import com.umc.pediupatrao.dto.RegistroAuditoria;
import com.umc.pediupatrao.entity.TipoOperacao;
import com.umc.pediupatrao.exception.RegraNegocioException;
import com.umc.pediupatrao.service.AuditoriaService;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@RequestMapping("/auditoria")
public class AuditoriaViewController {

    private final AuditoriaService auditoriaService;

    public AuditoriaViewController(AuditoriaService auditoriaService) {
        this.auditoriaService = auditoriaService;
    }

    @GetMapping
    public String consultar(
            @RequestParam(required = false) String entidade,
            @RequestParam(required = false) String entidadeId,
            @RequestParam(required = false) String usuario,
            @RequestParam(required = false) TipoOperacao operacao,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataInicio,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataFim,
            Model model) {

        List<RegistroAuditoria> registros = List.of();
        try {
            registros = auditoriaService.consultar(entidade, entidadeId, usuario, operacao, dataInicio, dataFim);
        } catch (RegraNegocioException e) {
            model.addAttribute("erro", e.getMessage());
        }

        model.addAttribute("registros", registros);
        model.addAttribute("entidades", List.of("Pedido", "Cliente", "Usuario"));
        model.addAttribute("operacoes", TipoOperacao.values());
        model.addAttribute("dadosPessoaisRestritos", auditoriaService.dadosPessoaisRestritos());

        // Devolve os filtros para manter o formulário preenchido
        model.addAttribute("filtroEntidade", entidade);
        model.addAttribute("filtroEntidadeId", entidadeId);
        model.addAttribute("filtroUsuario", usuario);
        model.addAttribute("filtroOperacao", operacao);
        model.addAttribute("filtroDataInicio", dataInicio);
        model.addAttribute("filtroDataFim", dataFim);

        model.addAttribute("content", "auditoria/lista :: content");
        return "layout";
    }
}