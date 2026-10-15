package com.ChicaemeSAS.BackendSistema.service;

import com.ChicaemeSAS.BackendSistema.model.ArticuloAlquiler;
import com.ChicaemeSAS.BackendSistema.repository.ArticuloAlquilerRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ArticuloAlquilerService {

    @Autowired
    private ArticuloAlquilerRepository repository;

    public ArticuloAlquiler guardarArticulo(ArticuloAlquiler articulo) {
        return repository.save(articulo);
    }

    public List<ArticuloAlquiler> obtenerTodos() {
        return repository.findAll();
    }

    public ArticuloAlquiler obtenerPorId(long id) {
        return repository.findById(id).orElse(null);
    }

    public void eliminarPorId(long id) {
        repository.deleteById(id);
    }

    /**
     * Actualiza datos del artículo.
     * REGLA: el stockTotal solo puede aumentar (reposición/compra).
     * No se permite bajar el stock a mano; las bajas vienen de alquileres y daños.
     */
    public ArticuloAlquiler actualizarArticulo(Long id, ArticuloAlquiler datosNuevos) {
        return repository.findById(id).map(articuloExistente -> {
            articuloExistente.setNombre(datosNuevos.getNombre());
            articuloExistente.setDescripcion(datosNuevos.getDescripcion());
            articuloExistente.setPrecioAlquiler(datosNuevos.getPrecioAlquiler());
            articuloExistente.setFotoUrl(datosNuevos.getFotoUrl());

            Integer stockNuevo = datosNuevos.getStockTotal();
            Integer stockActual = articuloExistente.getStockTotal();

            if (stockNuevo != null) {
                if (stockNuevo < stockActual) {
                    throw new RuntimeException(
                            "No se puede reducir el stock manualmente. "
                                    + "Las bajas solo ocurren por alquileres aprobados o por daños/pérdidas en devoluciones. "
                                    + "Para aumentar inventario (compra/reposición), indica un valor mayor al actual ("
                                    + stockActual + ")."
                    );
                }
                articuloExistente.setStockTotal(stockNuevo);
            }

            return repository.save(articuloExistente);
        }).orElse(null);
    }

    /** Endpoint explícito solo para sumar stock (reposición) */
    public ArticuloAlquiler aumentarStock(Long id, int cantidad) {
        if (cantidad <= 0) {
            throw new RuntimeException("La cantidad a agregar debe ser mayor que 0");
        }
        return repository.findById(id).map(art -> {
            art.setStockTotal(art.getStockTotal() + cantidad);
            return repository.save(art);
        }).orElseThrow(() -> new RuntimeException("Artículo no encontrado"));
    }
}
