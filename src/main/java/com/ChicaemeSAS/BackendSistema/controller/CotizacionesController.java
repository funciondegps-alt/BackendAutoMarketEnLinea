package com.ChicaemeSAS.BackendSistema.controller;

import com.ChicaemeSAS.BackendSistema.model.Cotizacion;
import com.ChicaemeSAS.BackendSistema.model.CotizacionDetalle;
import com.ChicaemeSAS.BackendSistema.service.CotizacionesService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/cotizaciones")
public class CotizacionesController {

    @Autowired
    private CotizacionesService cotizacionesService;

    @GetMapping
    public List<Cotizacion> obtenerCotizaciones() {
        return cotizacionesService.findAll();
    }

    @GetMapping("/usuario/{usuarioId}")
    public List<Cotizacion> getCotizacionesByUsuario(@PathVariable Long usuarioId) {
        return cotizacionesService.obtenerCotizacionesPorUsuario(usuarioId);
    }

    @GetMapping("/{id}")
    public Cotizacion getCotizacionById(@PathVariable Long id) {
        return cotizacionesService.buscarCotizacionPorId(id);
    }

    @PostMapping
    public Cotizacion enviarCotizacion(@RequestBody Cotizacion creandoCotizacion) {
        return cotizacionesService.guardarCotizacion(creandoCotizacion);
    }

    /** Cambia estado: Pendiente | Aprobado | En evento | En lavado | Finalizado | Rechazado */
    @PutMapping("/{id}/estado")
    public Cotizacion cambiarEstado(@PathVariable Long id, @RequestParam String estado) {
        return cotizacionesService.cambiarEstado(id, estado);
    }

    /**
     * Confirma que el material se entregó → pasa a "En evento".
     * Body opcional: cotización con detalles ajustados (cantidades de última hora).
     */
    @PutMapping("/{id}/confirmar-entrega")
    public Cotizacion confirmarEntrega(
            @PathVariable Long id,
            @RequestBody(required = false) Cotizacion ajustes
    ) {
        return cotizacionesService.confirmarEntrega(id, ajustes);
    }

    /**
     * Registra devolución con buenos / dañados / perdidos por cada detalle.
     * Body: { "detalles": [ { "id": 1, "cantidadDevueltaBuena": 490, "cantidadDanada": 5, "cantidadPerdida": 5 } ],
     *         "finalizar": false }
     * Si finalizar=true → estado Finalizado; si no → En lavado.
     */
    @PutMapping("/{id}/devolucion")
    public Cotizacion registrarDevolucion(
            @PathVariable Long id,
            @RequestBody Map<String, Object> body
    ) {
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> raw = (List<Map<String, Object>>) body.get("detalles");
        boolean finalizar = Boolean.TRUE.equals(body.get("finalizar"));

        List<CotizacionDetalle> detalles = raw.stream().map(m -> {
            CotizacionDetalle d = new CotizacionDetalle();
            d.setId(Long.valueOf(m.get("id").toString()));
            if (m.get("cantidadDevueltaBuena") != null)
                d.setCantidadDevueltaBuena(Integer.valueOf(m.get("cantidadDevueltaBuena").toString()));
            if (m.get("cantidadDanada") != null)
                d.setCantidadDanada(Integer.valueOf(m.get("cantidadDanada").toString()));
            if (m.get("cantidadPerdida") != null)
                d.setCantidadPerdida(Integer.valueOf(m.get("cantidadPerdida").toString()));
            return d;
        }).toList();

        return cotizacionesService.registrarDevolucion(id, detalles, finalizar);
    }

    /** En lavado → Finalizado (lavado terminado, material listo) */
    @PutMapping("/{id}/lavado-terminado")
    public Cotizacion lavadoTerminado(@PathVariable Long id) {
        return cotizacionesService.marcarLavadoTerminado(id);
    }

    @DeleteMapping("/{id}")
    public void eliminarCotizacion(@PathVariable Long id) {
        cotizacionesService.eliminarCotizacionPorId(id);
    }

    /** Solo permitido si estado es Pendiente o Rechazado */
    @PutMapping("/{id}")
    public Cotizacion actualizarCotizacion(@PathVariable Long id, @RequestBody Cotizacion cotizacion) {
        return cotizacionesService.actualizarCotizacion(id, cotizacion);
    }
}
