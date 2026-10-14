package com.ChicaemeSAS.BackendSistema.config;

import com.ChicaemeSAS.BackendSistema.security.JwtAuthFilter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Autowired
    private JwtAuthFilter jwtAuthFilter;

    // URL del frontend en linea (variable FRONTEND_URL). Si no existe, vale http://localhost:5173
    @Value("${app.frontend.url}")
    private String frontendUrl;

    // Encriptador de contrasenas. Se usa en UsuariosService para hashear y comparar.
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    // Necesario si en algun momento quieren autenticar manualmente.
    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    // CORS registrado DENTRO de Spring Security, asi hasta las respuestas de rechazo
    // (401/403) llevan el header Access-Control-Allow-Origin.
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        // Quitamos la barra final por si FRONTEND_URL se escribio con ella:
        // el origen del navegador nunca la lleva y CORS compara el texto exacto.
        String frontend = frontendUrl.endsWith("/")
                ? frontendUrl.substring(0, frontendUrl.length() - 1)
                : frontendUrl;

        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(List.of("http://localhost:5173", frontend));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", configuration);
        return source;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // Enganchamos el bean de arriba directamente en Spring Security
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))

                // CSRF desactivado: usamos JWT sin estado, no aplica.
                .csrf(csrf -> csrf.disable())

                // Sin sesiones en el servidor: cada peticion se valida con su propio token.
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                .authorizeHttpRequests(auth -> auth
                        // ---------- PREFLIGHT DE CORS: siempre libre ----------
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()

                        // ---------- /error libre: sin esto, cualquier error (400, 404, 500)
                        // se convierte en un 403 vacio y se pierde el mensaje real ----------
                        .requestMatchers("/error").permitAll()

                        // ---------- RUTAS PUBLICAS (sin login) ----------
                        .requestMatchers(HttpMethod.POST, "/api/usuarios/login").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/usuarios/google-login").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/usuarios/forgot-password").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/usuarios/reset-password").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/usuarios").permitAll()      // registro
                        .requestMatchers(HttpMethod.POST, "/api/contactos").permitAll()     // formulario de contacto
                        .requestMatchers(HttpMethod.GET, "/api/articulos/**").permitAll()   // catalogo publico
                        .requestMatchers(HttpMethod.GET, "/api/servicios/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/categorias/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/uploads/**").permitAll()          // ver las fotos ya subidas
                        .requestMatchers(HttpMethod.POST, "/api/archivos/imagen").permitAll() // subir una foto (el registro pasa antes de tener token)

                        // ---------- RUTAS SOLO PARA ADMIN ----------
                        .requestMatchers("/api/empleados/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/usuarios").hasRole("ADMIN")       // listar todos
                        .requestMatchers(HttpMethod.DELETE, "/api/usuarios/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/contactos/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/articulos/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/api/articulos/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/api/articulos/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/servicios/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/api/servicios/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/api/servicios/**").hasRole("ADMIN")
                        .requestMatchers("/api/categorias/**").hasRole("ADMIN") // POST/PUT/DELETE (el GET ya quedo publico arriba)

                        // ---------- CUALQUIER OTRA RUTA: requiere estar logueado ----------
                        // cotizaciones, pagos, ver/editar perfil propio (/api/usuarios/{id}), etc.
                        .anyRequest().authenticated()
                )

                // Nuestro filtro revisa el token ANTES del filtro estandar de Spring Security
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
