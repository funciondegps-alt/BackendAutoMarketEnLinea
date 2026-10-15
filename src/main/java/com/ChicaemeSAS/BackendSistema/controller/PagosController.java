package com.ChicaemeSAS.BackendSistema.controller;

import com.ChicaemeSAS.BackendSistema.model.Cotizacion;
import com.ChicaemeSAS.BackendSistema.model.Pago;
import com.ChicaemeSAS.BackendSistema.service.PagoService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/pagos")
public class PagosController {

    @Autowired
    private PagoService pagoService;

    @GetMapping
    public List<Pago> findAll() {
        return pagoService.obtenerTodos();
    }

    @GetMapping("/{id}")
    public Pago buscarPorId(@PathVariable Long id) {
        return pagoService.obtenerPorId(id);
    }

    @GetMapping("/cotizacion/{cotizacionId}")
    public List<Pago> porCotizacion(@PathVariable Long cotizacionId) {
        return pagoService.obtenerPorCotizacion(cotizacionId);
    }

    /** Registrar un abono. Body: { monto, metodoPago, comprobantePago?, notas?, fechaPago?, cotizacion: { id } } */
    @PostMapping
    public Pago agregarPago(@RequestBody Pago pago) {
        return pagoService.registrarPago(pago);
    }

    @PutMapping("/{id}")
    public Pago editarPago(@PathVariable Long id, @RequestBody Pago pago) {
        return pagoService.actualizarPago(id, pago);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        pagoService.eliminarPorId(id);
    }

    /**
     * Marca la cotización como crédito.
     * Body: { "fechaVencimiento": "2026-11-15" }
     */
    @PutMapping("/credito/{cotizacionId}")
    public Cotizacion marcarCredito(
            @PathVariable Long cotizacionId,
            @RequestBody Map<String, String> body
    ) {
        LocalDate fecha = LocalDate.parse(body.get("fechaVencimiento"));
        return pagoService.marcarCredito(cotizacionId, fecha);
    }
}
