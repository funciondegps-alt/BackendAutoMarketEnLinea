package com.ChicaemeSAS.BackendSistema.controller;

import com.ChicaemeSAS.BackendSistema.model.ArticuloAlquiler;
import com.ChicaemeSAS.BackendSistema.service.ArticuloAlquilerService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/articulos")
public class ArticuloAlquilerController {

    @Autowired
    private ArticuloAlquilerService service;

    @GetMapping
    public List<ArticuloAlquiler> listarArticulos() {
        return service.obtenerTodos();
    }

    @PostMapping
    public ArticuloAlquiler crearArticulo(@RequestBody ArticuloAlquiler articulo) {
        return service.guardarArticulo(articulo);
    }

    @GetMapping("/{id}")
    public ArticuloAlquiler obtenerArticulo(@PathVariable Long id) {
        return service.obtenerPorId(id);
    }

    /**
     * Actualiza artículo. El stock solo puede aumentar.
     * Si envías un stockTotal menor al actual → error 500 con mensaje claro.
     */
    @PutMapping("/{id}")
    public ArticuloAlquiler actualizarArticulo(
            @PathVariable Long id,
            @RequestBody ArticuloAlquiler actualizandoArticulo
    ) {
        return service.actualizarArticulo(id, actualizandoArticulo);
    }

    /** Suma stock (compra/reposición). Body: { "cantidad": 50 } */
    @PutMapping("/{id}/aumentar-stock")
    public ArticuloAlquiler aumentarStock(
            @PathVariable Long id,
            @RequestBody Map<String, Integer> body
    ) {
        return service.aumentarStock(id, body.getOrDefault("cantidad", 0));
    }

    @DeleteMapping("/{id}")
    public void eliminar(@PathVariable Long id) {
        service.eliminarPorId(id);
    }
}
