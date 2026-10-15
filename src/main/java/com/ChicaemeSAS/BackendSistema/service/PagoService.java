package com.ChicaemeSAS.BackendSistema.service;

import com.ChicaemeSAS.BackendSistema.model.Cotizacion;
import com.ChicaemeSAS.BackendSistema.model.Pago;
import com.ChicaemeSAS.BackendSistema.repository.CotizacionesRepository;
import com.ChicaemeSAS.BackendSistema.repository.PagoRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
public class PagoService {

    @Autowired
    private PagoRepository pagoRepository;

    @Autowired
    private CotizacionesRepository cotizacionesRepository;

    public List<Pago> obtenerTodos() {
        return pagoRepository.findAll();
    }

    public Pago obtenerPorId(Long id) {
        return pagoRepository.findById(id).orElse(null);
    }

    public List<Pago> obtenerPorCotizacion(Long cotizacionId) {
        return pagoRepository.findByCotizacion_Id(cotizacionId);
    }

    /**
     * Registra un abono y recalcula montoPagado + estadoPago de la cotización.
     */
    @Transactional
    public Pago registrarPago(Pago pago) {
        if (pago.getCotizacion() == null || pago.getCotizacion().getId() == null) {
            throw new RuntimeException("El pago debe estar asociado a una cotización");
        }
        Cotizacion cot = cotizacionesRepository.findById(pago.getCotizacion().getId())
                .orElseThrow(() -> new RuntimeException("Cotización no encontrada"));

        if (pago.getFechaPago() == null) {
            pago.setFechaPago(LocalDate.now());
        }
        pago.setCotizacion(cot);
        Pago guardado = pagoRepository.save(pago);

        recalcularPagoCotizacion(cot);
        return guardado;
    }

    @Transactional
    public Pago actualizarPago(Long id, Pago datos) {
        return pagoRepository.findById(id).map(existente -> {
            existente.setFechaPago(datos.getFechaPago());
            existente.setMonto(datos.getMonto());
            existente.setMetodoPago(datos.getMetodoPago());
            existente.setComprobantePago(datos.getComprobantePago());
            existente.setNotas(datos.getNotas());
            Pago guardado = pagoRepository.save(existente);
            recalcularPagoCotizacion(existente.getCotizacion());
            return guardado;
        }).orElse(null);
    }

    @Transactional
    public void eliminarPorId(Long id) {
        Pago pago = pagoRepository.findById(id).orElse(null);
        if (pago != null) {
            Cotizacion cot = pago.getCotizacion();
            pagoRepository.deleteById(id);
            if (cot != null) {
                recalcularPagoCotizacion(cot);
            }
        }
    }

    /**
     * Marca la cotización como crédito con fecha de vencimiento.
     * No registra un pago; solo cambia el estado de cobro.
     */
    @Transactional
    public Cotizacion marcarCredito(Long cotizacionId, LocalDate fechaVencimiento) {
        Cotizacion cot = cotizacionesRepository.findById(cotizacionId)
                .orElseThrow(() -> new RuntimeException("Cotización no encontrada"));
        cot.setEstadoPago("Credito");
        cot.setFechaVencimientoPago(fechaVencimiento);
        // Si ya hay abonos, el estado puede seguir siendo Parcial; el admin decide.
        if (cot.getMontoPagado() != null && cot.getMontoPagado() > 0
                && cot.getMontoPagado() < cot.getTotal()) {
            cot.setEstadoPago("Parcial");
        }
        return cotizacionesRepository.save(cot);
    }

    private void recalcularPagoCotizacion(Cotizacion cot) {
        List<Pago> pagos = pagoRepository.findByCotizacion_Id(cot.getId());
        double sum = pagos.stream().mapToDouble(p -> p.getMonto() != null ? p.getMonto() : 0).sum();
        cot.setMontoPagado(sum);

        double total = cot.getTotal() != null ? cot.getTotal() : 0;
        if (sum <= 0) {
            // Mantener Credito si ya estaba marcado y no hay pagos
            if (!"Credito".equals(cot.getEstadoPago())) {
                cot.setEstadoPago("Sin pago");
            }
        } else if (sum >= total) {
            cot.setEstadoPago("Pagado");
        } else {
            cot.setEstadoPago("Parcial");
        }

        // Marcar vencido si es crédito/parcial y pasó la fecha
        if (cot.getFechaVencimientoPago() != null
                && cot.getFechaVencimientoPago().isBefore(LocalDate.now())
                && sum < total) {
            cot.setEstadoPago("Vencido");
        }

        cotizacionesRepository.save(cot);
    }
}
