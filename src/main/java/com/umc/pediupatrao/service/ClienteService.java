package com.umc.pediupatrao.service;

import com.umc.pediupatrao.entity.AlteracaoCampo;
import com.umc.pediupatrao.entity.Cliente;
import com.umc.pediupatrao.entity.TipoOperacao;
import com.umc.pediupatrao.exception.RecursoNaoEncontradoException;
import com.umc.pediupatrao.exception.RegraNegocioException;
import com.umc.pediupatrao.repository.ClienteRepository;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

@Service
public class ClienteService {

    public static final String ENTIDADE = "Cliente";
    private static final int TAMANHO_MAXIMO_CAMPO = 100;

    private final ClienteRepository clienteRepository;
    private final AuditoriaService auditoriaService;

    public ClienteService(ClienteRepository clienteRepository, AuditoriaService auditoriaService) {
        this.clienteRepository = clienteRepository;
        this.auditoriaService = auditoriaService;
    }

    // CONSULTAS

    @PreAuthorize("hasAnyRole('GERENTE', 'ATENDENTE')")
    public List<Cliente> listarClientes() {
        return clienteRepository.findAll(Sort.by(Sort.Direction.ASC, "nome"));
    }

    @PreAuthorize("hasAnyRole('GERENTE', 'ATENDENTE')")
    public Optional<Cliente> buscarPorId(String id) {
        return clienteRepository.findById(id);
    }

    // INCLUSÃO

    @PreAuthorize("hasRole('ATENDENTE')")
    public Cliente criar(Cliente dados) {
        Cliente novo = new Cliente();
        copiarCamposPermitidos(dados, novo);
        validar(novo);
        validarTelefoneUnico(novo);

        Cliente salvo = clienteRepository.save(novo);

        auditoriaService.registrar(TipoOperacao.INCLUSAO_CLIENTE, ENTIDADE, salvo.getId(),
                AuditoriaService.compararCampos(Map.of(), retrato(salvo)), null);

        return salvo;
    }

    // ALTERAÇÃO

    @PreAuthorize("hasRole('ATENDENTE')")
    public Cliente atualizar(String id, Cliente dados) {
        Cliente existente = clienteRepository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Cliente não encontrado."));

        Map<String, Object> antes = retrato(existente); // AUDITORIA: retrato antes

        copiarCamposPermitidos(dados, existente);
        validar(existente);
        validarTelefoneUnico(existente);

        List<AlteracaoCampo> alteracoes = AuditoriaService.compararCampos(antes, retrato(existente));
        if (alteracoes.isEmpty()) {
            return existente; 
        }

        Cliente salvo = clienteRepository.save(existente);

        auditoriaService.registrar(TipoOperacao.ALTERACAO_CLIENTE, ENTIDADE, salvo.getId(), alteracoes, null);

        return salvo;
    }

    // EXCLUSÃO

    @PreAuthorize("hasRole('GERENTE')")
    public void excluir(String id) {
        Cliente existente = clienteRepository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Cliente não encontrado."));

        clienteRepository.delete(existente);

        auditoriaService.registrar(TipoOperacao.EXCLUSAO_CLIENTE, ENTIDADE, id,
                AuditoriaService.compararCampos(retrato(existente), Map.of()), null);
    }

    // REGRAS
    
    private void copiarCamposPermitidos(Cliente origem, Cliente destino) {
        if (origem == null) {
            throw new RegraNegocioException("Dados do cliente não informados.");
        }
        destino.setNome(limpar(origem.getNome()));
        destino.setTelefone(limpar(origem.getTelefone()));
        destino.setCep(limpar(origem.getCep()));
        destino.setLogradouro(limpar(origem.getLogradouro()));
        destino.setNumero(limpar(origem.getNumero()));
        destino.setComplemento(limpar(origem.getComplemento()));
        destino.setBairro(limpar(origem.getBairro()));
        destino.setCidade(limpar(origem.getCidade()));
        String estado = limpar(origem.getEstado());
        destino.setEstado(estado != null ? estado.toUpperCase() : null);
    }

    private void validar(Cliente cliente) {
        if (cliente.getNome() == null || cliente.getNome().length() < 2) {
            throw new RegraNegocioException("O nome do cliente é obrigatório (mínimo de 2 caracteres).");
        }
        if (cliente.getTelefone() == null) {
            throw new RegraNegocioException("O telefone do cliente é obrigatório.");
        }
        int digitosTelefone = somenteDigitos(cliente.getTelefone()).length();
        if (digitosTelefone < 10 || digitosTelefone > 11) {
            throw new RegraNegocioException("Telefone inválido: informe DDD + número (10 ou 11 dígitos).");
        }
        if (cliente.getCep() != null && somenteDigitos(cliente.getCep()).length() != 8) {
            throw new RegraNegocioException("CEP inválido: deve ter 8 dígitos.");
        }
        if (cliente.getEstado() != null && !cliente.getEstado().matches("[A-Z]{2}")) {
            throw new RegraNegocioException("Estado inválido: use a sigla com 2 letras (ex.: SP).");
        }
        for (String valor : List.of(
                cliente.getNome(), valorOuVazio(cliente.getLogradouro()), valorOuVazio(cliente.getNumero()),
                valorOuVazio(cliente.getComplemento()), valorOuVazio(cliente.getBairro()),
                valorOuVazio(cliente.getCidade()))) {
            if (valor.length() > TAMANHO_MAXIMO_CAMPO) {
                throw new RegraNegocioException("Os campos do cliente podem ter no máximo "
                        + TAMANHO_MAXIMO_CAMPO + " caracteres.");
            }
        }
    }

    private void validarTelefoneUnico(Cliente cliente) {
        String digitos = somenteDigitos(cliente.getTelefone());

        clienteRepository.findAll().stream()
                .filter(outro -> !outro.getId().equals(cliente.getId()))
                .filter(outro -> digitos.equals(somenteDigitos(outro.getTelefone())))
                .findFirst()
                .ifPresent(outro -> {
                    throw new RegraNegocioException("Já existe um cliente cadastrado com este telefone ("
                            + outro.getNome() + ").");
                });
    }

    private Map<String, Object> retrato(Cliente c) {
        Map<String, Object> campos = new LinkedHashMap<>();
        campos.put("nome", c.getNome());
        campos.put("telefone", c.getTelefone());
        campos.put("cep", c.getCep());
        campos.put("logradouro", c.getLogradouro());
        campos.put("numero", c.getNumero());
        campos.put("complemento", c.getComplemento());
        campos.put("bairro", c.getBairro());
        campos.put("cidade", c.getCidade());
        campos.put("estado", c.getEstado());
        return campos;
    }

    private String somenteDigitos(String valor) {
        return valor != null ? valor.replaceAll("\\D", "") : "";
    }

    private String limpar(String valor) {
        if (valor == null) {
            return null;
        }
        String texto = valor.trim();
        return texto.isEmpty() ? null : texto;
    }

    private String valorOuVazio(String valor) {
        return valor != null ? valor : "";
    }
}