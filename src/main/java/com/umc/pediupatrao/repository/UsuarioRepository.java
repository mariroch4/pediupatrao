package com.umc.pediupatrao.repository;

import com.umc.pediupatrao.entity.Perfil;
import com.umc.pediupatrao.entity.Usuario;
import java.util.Optional;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface UsuarioRepository extends MongoRepository<Usuario, String> {

    Optional<Usuario> findByUsername(String username);

    // Usado para garantir que o sistema nunca fique sem ADMIN
    long countByRole(Perfil role);
}