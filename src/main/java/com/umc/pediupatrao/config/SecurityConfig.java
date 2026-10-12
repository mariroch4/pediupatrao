package com.umc.pediupatrao.config;

import jakarta.servlet.DispatcherType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.access.AccessDeniedHandlerImpl;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.csrf.CsrfException;
import org.springframework.security.web.util.matcher.RequestMatcher;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity 
public class SecurityConfig {

    private static final Logger log = LoggerFactory.getLogger(SecurityConfig.class);

    private static final RequestMatcher API = request -> request.getRequestURI().startsWith("/api/");

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(Customizer.withDefaults())

                .authorizeHttpRequests(auth -> auth
                        .dispatcherTypeMatchers(DispatcherType.FORWARD, DispatcherType.ERROR).permitAll()

                        .requestMatchers("/login", "/css/**", "/dist/**", "/plugins/**",
                                "/js/**", "/images/**").permitAll()

                        .requestMatchers("/usuarios/**", "/api/usuarios/**", "/admin").hasRole("ADMIN")

                        .requestMatchers("/auditoria/**", "/api/auditoria/**").hasAnyRole("ADMIN", "GERENTE")

                        // PEDIDOS
                        .requestMatchers("/pedidos/*/cancelar", "/pedidos/*/desconto",
                                "/api/pedidos/*/cancelar", "/api/pedidos/*/desconto").hasRole("GERENTE")
                        .requestMatchers(HttpMethod.GET, "/pedidos/novo").hasAnyRole("GERENTE", "ATENDENTE")
                        .requestMatchers(HttpMethod.GET, "/pedidos/**", "/api/pedidos/**")
                                .hasAnyRole("ADMIN", "GERENTE", "ATENDENTE")
                        .requestMatchers("/pedidos/**", "/api/pedidos/**").hasAnyRole("GERENTE", "ATENDENTE")

                        // CLIENTES
                        .requestMatchers(HttpMethod.DELETE, "/api/clientes/**").hasRole("GERENTE")
                        .requestMatchers(HttpMethod.GET, "/clientes", "/api/clientes", "/api/clientes/*")
                                .hasAnyRole("GERENTE", "ATENDENTE")
                        .requestMatchers("/clientes/**", "/api/clientes/**").hasRole("ATENDENTE")

                        // PRODUTOS
                        .requestMatchers(HttpMethod.GET, "/produtos", "/api/produtos").authenticated()
                        .requestMatchers("/produtos/**", "/api/produtos/**").hasAnyRole("ADMIN", "GERENTE")

                        .anyRequest().authenticated()
                )

                .formLogin(form -> form
                        .loginPage("/login")
                        .defaultSuccessUrl("/", true)
                        .permitAll()
                )

                .logout(logout -> logout
                        .logoutUrl("/logout")
                        .logoutSuccessUrl("/login?logout")
                        .permitAll()
                )

                .exceptionHandling(ex -> ex
                        .defaultAuthenticationEntryPointFor(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED), API)
                        .accessDeniedHandler(accessDeniedHandler())
                );

        return http.build();
    }

    // Trata tentativas de acesso indevido

    private AccessDeniedHandler accessDeniedHandler() {
        AccessDeniedHandlerImpl paginaErro = new AccessDeniedHandlerImpl();
        paginaErro.setErrorPage("/acesso-negado");

        return (request, response, exception) -> {
            boolean falhaCsrf = exception instanceof CsrfException;
            String mensagem = falhaCsrf
                    ? "Requisição recusada: token CSRF ausente ou inválido. Recarregue a página e tente novamente."
                    : "Seu perfil não tem permissão para realizar esta operação.";

            log.warn("Acesso negado: usuario={}, metodo={}, uri={}, motivo={}",
                    usuarioAtual(), request.getMethod(), request.getRequestURI(),
                    falhaCsrf ? "CSRF" : "PERFIL_SEM_PERMISSAO");

            if (API.matches(request)) {
                response.setStatus(HttpStatus.FORBIDDEN.value());
                response.setContentType("application/json;charset=UTF-8");
                response.getWriter().write(
                        "{\"status\":403,\"erro\":\"Acesso negado\",\"mensagem\":\"" + mensagem + "\"}");
            } else {
                request.setAttribute("mensagemAcessoNegado", mensagem);
                paginaErro.handle(request, response, exception);
            }
        };
    }

    private String usuarioAtual() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth instanceof AnonymousAuthenticationToken) {
            return "anonimo";
        }
        return auth.getName();
    }
}