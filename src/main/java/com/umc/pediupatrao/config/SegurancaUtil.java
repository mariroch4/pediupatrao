package com.umc.pediupatrao.config;

import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

// Utilitário para obter o usuário autenticado e seu perfil

public final class SegurancaUtil {

    private SegurancaUtil() {
    }

    public static String usernameAtual() {
        Authentication auth = autenticacao();
        return auth != null ? auth.getName() : null;
    }

    public static String perfilAtual() {
        Authentication auth = autenticacao();
        if (auth == null) {
            return null;
        }
        return auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(a -> a.startsWith("ROLE_"))
                .map(a -> a.substring("ROLE_".length()))
                .findFirst()
                .orElse(null);
    }

    private static Authentication autenticacao() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth instanceof AnonymousAuthenticationToken) {
            return null;
        }
        return auth;
    }
}