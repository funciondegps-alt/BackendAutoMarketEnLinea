package com.ChicaemeSAS.BackendSistema.controller;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;

// Un solo endpoint reutilizable: recibe cualquier foto (de usuario, articulo o
// servicio), la sube a Cloudinary y devuelve la URL publica (https). Los modelos
// siguen guardando un String URL; lo unico que cambia es donde queda el archivo:
// ya no en el disco de Railway (que se borra en cada despliegue) sino en Cloudinary.
@RestController
@RequestMapping("/api/archivos")
public class ArchivoController {

    private static final List<String> TIPOS_PERMITIDOS = List.of("image/jpeg", "image/png", "image/webp");
    private static final long TAMANO_MAXIMO_BYTES = 5L * 1024 * 1024; // 5 MB

    private final Cloudinary cloudinary;

    // Formato esperado de la variable: cloudinary://API_KEY:API_SECRET@CLOUD_NAME
    // Si esta vacia o mal escrita, el backend arranca igual y solo falla la subida.
    public ArchivoController(@Value("${cloudinary.url:}") String cloudinaryUrl) {
        Cloudinary instancia = null;
        if (cloudinaryUrl != null && !cloudinaryUrl.isBlank()) {
            try {
                instancia = new Cloudinary(cloudinaryUrl.trim());
            } catch (IllegalArgumentException e) {
                System.err.println("CLOUDINARY_URL tiene un formato invalido: " + e.getMessage());
            }
        }
        this.cloudinary = instancia;
    }

    @PostMapping("/imagen")
    public ResponseEntity<?> subirImagen(@RequestParam("archivo") MultipartFile archivo) {

        if (cloudinary == null) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("El almacenamiento de imagenes no esta configurado.");
        }
        if (archivo.isEmpty()) {
            return ResponseEntity.badRequest().body("No se recibio ningun archivo.");
        }
        if (!TIPOS_PERMITIDOS.contains(archivo.getContentType())) {
            return ResponseEntity.badRequest().body("Solo se permiten imagenes JPG, PNG o WEBP.");
        }
        if (archivo.getSize() > TAMANO_MAXIMO_BYTES) {
            return ResponseEntity.badRequest().body("La imagen no puede superar los 5 MB.");
        }

        try {
            Map<?, ?> resultado = cloudinary.uploader().upload(
                    archivo.getBytes(),
                    ObjectUtils.asMap(
                            "folder", "chicaeme",
                            "resource_type", "image"
                    )
            );
            String url = (String) resultado.get("secure_url");
            return ResponseEntity.ok(Map.of("url", url));

        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("No se pudo guardar la imagen.");
        }
    }
}
