package com.umc.pediupatrao.config;

import com.umc.pediupatrao.entity.Perfil;
import com.umc.pediupatrao.entity.Produto;
import com.umc.pediupatrao.entity.Usuario;
import com.umc.pediupatrao.repository.ProdutoRepository;
import com.umc.pediupatrao.repository.UsuarioRepository;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

// Cria os dados iniciais para testes
 
@Configuration
public class DataInitializer {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    @Bean
    CommandLineRunner initDatabase(UsuarioRepository usuarioRepository,
                                   ProdutoRepository produtoRepository,
                                   PasswordEncoder encoder) {
        return args -> {
            criarOuCorrigir(usuarioRepository, encoder, "admin", "teste123", Perfil.ADMIN);
            criarOuCorrigir(usuarioRepository, encoder, "gerente01", "gerente123", Perfil.GERENTE);
            criarOuCorrigir(usuarioRepository, encoder, "atendente01", "atendente123", Perfil.ATENDENTE);
            cadastrarProdutosIniciais(produtoRepository);
        };
    }

    private void criarOuCorrigir(UsuarioRepository repo, PasswordEncoder encoder,
                                 String username, String senha, Perfil perfil) {

        Optional<Usuario> existente = repo.findByUsername(username);

        if (existente.isEmpty()) {
            Usuario usuario = new Usuario();
            usuario.setUsername(username);
            usuario.setPassword(encoder.encode(senha));
            usuario.setRole(perfil);
            repo.save(usuario);
            log.info("Usuario inicial '{}' criado com perfil {}", username, perfil);

        } else if (existente.get().getRole() == null) {
            Usuario usuario = existente.get();
            usuario.setRole(perfil);
            repo.save(usuario);
            log.info("Perfil do usuario '{}' corrigido para {}", username, perfil);
        }
    }

    private void cadastrarProdutosIniciais(ProdutoRepository repo) {
        if (repo.count() > 0) {
            return;
        }
        repo.save(produto("Pizza Calabresa", "Pizza", 45.90));
        repo.save(produto("Pizza Margherita", "Pizza", 42.50));
        repo.save(produto("Pizza Quatro Queijos", "Pizza", 49.90));
        repo.save(produto("Esfiha de Carne", "Esfiha", 6.50));
        repo.save(produto("Refrigerante 2L", "Bebida", 12.00));
        log.info("Produtos iniciais cadastrados para demonstracao");
    }

    private Produto produto(String nome, String tipo, double preco) {
        Produto produto = new Produto();
        produto.setNome(nome);
        produto.setTipo(tipo);
        produto.setPreco(preco);
        produto.setAtivo(true);
        return produto;
    }
}