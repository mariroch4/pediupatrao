package com.umc.pediupatrao.controller;

import com.umc.pediupatrao.dto.NovoPedidoRequest;
import com.umc.pediupatrao.entity.Pedido;
import com.umc.pediupatrao.entity.StatusPedido;
import com.umc.pediupatrao.entity.TipoEntrega;
import com.umc.pediupatrao.exception.RecursoNaoEncontradoException;
import com.umc.pediupatrao.exception.RegraNegocioException;
import com.umc.pediupatrao.service.ClienteService;
import com.umc.pediupatrao.service.PedidoService;
import com.umc.pediupatrao.service.ProdutoService;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/pedidos")
public class PedidoViewController {

    private final PedidoService pedidoService;
    private final ClienteService clienteService;
    private final ProdutoService produtoService;

    public PedidoViewController(PedidoService pedidoService, ClienteService clienteService,
                                ProdutoService produtoService) {
        this.pedidoService = pedidoService;
        this.clienteService = clienteService;
        this.produtoService = produtoService;
    }

    @GetMapping
    public String listar(Model model) {
        model.addAttribute("pedidos", pedidoService.listarPedidos());
        model.addAttribute("content", "pedidos/lista :: content");
        return "layout";
    }

    @GetMapping("/novo")
    public String novo(Model model) {
        model.addAttribute("clientes", clienteService.listarClientes());
        model.addAttribute("produtos", produtoService.listarProdutos().stream()
                .filter(p -> Boolean.TRUE.equals(p.getAtivo()))
                .toList());
        model.addAttribute("tiposEntrega", TipoEntrega.values());
        model.addAttribute("content", "pedidos/form :: content");
        return "layout";
    }

    @PostMapping("/salvar")
    public String salvar(@RequestParam(required = false) String clienteId,
                         @RequestParam(required = false) TipoEntrega tipoEntrega,
                         @RequestParam(required = false) String observacao,
                         @RequestParam(name = "produtoIds", required = false) List<String> produtoIds,
                         @RequestParam(name = "quantidades", required = false) List<Integer> quantidades,
                         RedirectAttributes redirect) {
        try {
            List<NovoPedidoRequest.ItemRequest> itens = new ArrayList<>();
            if (produtoIds != null && quantidades != null) {
                if (produtoIds.size() != quantidades.size()) {
                    throw new RegraNegocioException("Itens do pedido inválidos.");
                }
                for (int i = 0; i < produtoIds.size(); i++) {
                    Integer quantidade = quantidades.get(i);
                    if (quantidade != null && quantidade > 0) {
                        itens.add(new NovoPedidoRequest.ItemRequest(produtoIds.get(i), quantidade));
                    }
                }
            }

            Pedido pedido = pedidoService.criarPedido(
                    new NovoPedidoRequest(clienteId, tipoEntrega, observacao, itens));

            redirect.addFlashAttribute("sucesso", "Pedido registrado com sucesso!");
            return "redirect:/pedidos/" + pedido.getId();

        } catch (RegraNegocioException e) {
            redirect.addFlashAttribute("erro", e.getMessage());
            return "redirect:/pedidos/novo";
        }
    }

    @GetMapping("/{id}")
    public String detalhe(@PathVariable String id, Model model, RedirectAttributes redirect) {
        try {
            Pedido pedido = pedidoService.buscarPorId(id);
            model.addAttribute("pedido", pedido);
            model.addAttribute("fluxo", StatusPedido.fluxoPara(pedido.getTipoEntrega()));
            model.addAttribute("content", "pedidos/detalhe :: content");
            return "layout";
        } catch (RecursoNaoEncontradoException e) {
            redirect.addFlashAttribute("erro", e.getMessage());
            return "redirect:/pedidos";
        }
    }

    @PostMapping("/{id}/status")
    public String alterarStatus(@PathVariable String id,
                                @RequestParam(required = false) StatusPedido novoStatus,
                                RedirectAttributes redirect) {
        try {
            Pedido pedido = pedidoService.alterarStatus(id, novoStatus);
            redirect.addFlashAttribute("sucesso", "Status alterado para: " + pedido.getStatus().getDescricao());
        } catch (RegraNegocioException | RecursoNaoEncontradoException e) {
            redirect.addFlashAttribute("erro", e.getMessage());
        }
        return "redirect:/pedidos/" + id;
    }

    @PostMapping("/{id}/cancelar")
    public String cancelar(@PathVariable String id,
                           @RequestParam(required = false) String justificativa,
                           RedirectAttributes redirect) {
        try {
            pedidoService.cancelarPedido(id, justificativa);
            redirect.addFlashAttribute("sucesso", "Pedido cancelado. A justificativa foi registrada na auditoria.");
        } catch (RegraNegocioException | RecursoNaoEncontradoException e) {
            redirect.addFlashAttribute("erro", e.getMessage());
        }
        return "redirect:/pedidos/" + id;
    }

    @PostMapping("/{id}/desconto")
    public String aplicarDesconto(@PathVariable String id,
                                  @RequestParam(required = false) BigDecimal percentual,
                                  @RequestParam(required = false) String motivo,
                                  RedirectAttributes redirect) {
        try {
            Pedido pedido = pedidoService.aplicarDesconto(id, percentual, motivo);
            redirect.addFlashAttribute("sucesso", "Desconto de " + pedido.getPercentualDesconto()
                    + "% aplicado. Novo total: R$ " + pedido.getValorTotal());
        } catch (RegraNegocioException | RecursoNaoEncontradoException e) {
            redirect.addFlashAttribute("erro", e.getMessage());
        }
        return "redirect:/pedidos/" + id;
    }
}