package com.ChicaemeSAS.BackendSistema.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.io.File;

// El CORS de la API se configura solo en SecurityConfig.
// Aqui unicamente se publican las imagenes subidas.
@Configuration
public class CorsConfig implements WebMvcConfigurer {

    // Un archivo guardado en uploads/foo.jpg queda accesible en /uploads/foo.jpg
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String rutaAbsoluta = new File("uploads").getAbsolutePath();
        registry.addResourceHandler("/uploads/**")
                .addResourceLocations("file:" + rutaAbsoluta + File.separator);
    }
}
