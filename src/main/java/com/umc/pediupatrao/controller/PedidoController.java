package com.umc.pediupatrao.controller;

import com.umc.pediupatrao.dto.AlterarStatusRequest;
import com.umc.pediupatrao.dto.CancelarPedidoRequest;
import com.umc.pediupatrao.dto.DescontoRequest;
import com.umc.pediupatrao.dto.NovoPedidoRequest;
import com.umc.pediupatrao.entity.Pedido;
import com.umc.pediupatrao.service.PedidoService;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/pedidos")
public class PedidoController {

    private final PedidoService pedidoService;

    public PedidoController(PedidoService pedidoService) {
        this.pedidoService = pedidoService;
    }

    @PostMapping
    public ResponseEntity<Pedido> criarPedido(@RequestBody NovoPedidoRequest dados) {
        Pedido criado = pedidoService.criarPedido(dados);
        return ResponseEntity.status(HttpStatus.CREATED).body(criado);
    }

    @GetMapping
    public List<Pedido> listarPedidos() {
        return pedidoService.listarPedidos();
    }

    @GetMapping("/{id}")
    public Pedido buscarPorId(@PathVariable String id) {
        return pedidoService.buscarPorId(id);
    }

    @PutMapping("/{id}/status")
    public Pedido alterarStatus(@PathVariable String id, @RequestBody AlterarStatusRequest dados) {
        return pedidoService.alterarStatus(id, dados != null ? dados.status() : null);
    }

    @PostMapping("/{id}/cancelar")
    public Pedido cancelar(@PathVariable String id,
                           @RequestBody(required = false) CancelarPedidoRequest dados) {
        return pedidoService.cancelarPedido(id, dados != null ? dados.justificativa() : null);
    }

    @PostMapping("/{id}/desconto")
    public Pedido aplicarDesconto(@PathVariable String id,
                                  @RequestBody(required = false) DescontoRequest dados) {
        return pedidoService.aplicarDesconto(id,
                dados != null ? dados.percentual() : null,
                dados != null ? dados.motivo() : null);
    }
}