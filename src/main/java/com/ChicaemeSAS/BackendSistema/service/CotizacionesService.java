package com.ChicaemeSAS.BackendSistema.service;

import com.ChicaemeSAS.BackendSistema.model.ArticuloAlquiler;
import com.ChicaemeSAS.BackendSistema.model.Cotizacion;
import com.ChicaemeSAS.BackendSistema.model.CotizacionDetalle;
import com.ChicaemeSAS.BackendSistema.repository.ArticuloAlquilerRepository;
import com.ChicaemeSAS.BackendSistema.repository.CotizacionesRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

@Service
public class CotizacionesService {

    private static final Set<String> ESTADOS_EDITABLES = Set.of("Pendiente", "Rechazado");
    private static final Set<String> ESTADOS_VALIDOS = Set.of(
            "Pendiente", "Aprobado", "En evento", "En lavado", "Finalizado", "Rechazado"
    );

    @Autowired
    private CotizacionesRepository cotizacionesRepository;

    @Autowired
    private ArticuloAlquilerRepository articuloRepository;

    public List<Cotizacion> findAll() {
        return cotizacionesRepository.findAll();
    }

    public Cotizacion buscarCotizacionPorId(Long id) {
        return cotizacionesRepository.findById(id).orElse(null);
    }

    public List<Cotizacion> obtenerCotizacionesPorUsuario(Long usuarioId) {
        return cotizacionesRepository.findByUsuario_Id(usuarioId);
    }

    @Transactional
    public Cotizacion guardarCotizacion(Cotizacion cotizacion) {
        double totalCalculado = 0.0;
        cotizacion.setEstado("Pendiente");
        if (cotizacion.getEstadoPago() == null) cotizacion.setEstadoPago("Sin pago");
        if (cotizacion.getMontoPagado() == null) cotizacion.setMontoPagado(0.0);

        if (cotizacion.getDetalles() != null) {
            for (CotizacionDetalle detalle : cotizacion.getDetalles()) {
                detalle.setCotizacion(cotizacion);
                if (detalle.getCantidad() != null && detalle.getPrecioUnitario() != null) {
                    detalle.setSubtotal(detalle.getCantidad() * detalle.getPrecioUnitario());
                    totalCalculado += detalle.getSubtotal();
                }
            }
        }
        cotizacion.setTotal(totalCalculado);
        return cotizacionesRepository.save(cotizacion);
    }

    /**
     * Cambia el estado del pedido aplicando las reglas de stock.
     *
     * Aprobado  → descuenta stock (reserva)
     * Rechazado desde Aprobado / En evento → devuelve lo reservado (sin daños aún)
     * En evento → no mueve stock (ya estaba reservado al aprobar)
     * En lavado → no mueve stock (material físico de vuelta, pendiente lavado)
     * Finalizado → NO devuelve stock automáticamente; usar registrarDevolucion
     */
    @Transactional
    public Cotizacion cambiarEstado(Long id, String nuevoEstado) {
        if (!ESTADOS_VALIDOS.contains(nuevoEstado)) {
            throw new RuntimeException("Estado no válido: " + nuevoEstado);
        }

        Cotizacion cotizacion = cotizacionesRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Cotización no encontrada"));

        String actual = cotizacion.getEstado();
        if (actual.equals(nuevoEstado)) return cotizacion;

        // Aprobar → reservar stock
        if ("Aprobado".equals(nuevoEstado) && !"Aprobado".equals(actual) && !"En evento".equals(actual)) {
            descontarStock(cotizacion);
        }

        // Salir de Aprobado o En evento hacia Rechazado → liberar reserva completa
        if ("Rechazado".equals(nuevoEstado)
                && ("Aprobado".equals(actual) || "En evento".equals(actual))) {
            devolverStockCompleto(cotizacion);
        }

        // De Aprobado a Pendiente (solo vía este endpoint si se permite) → liberar
        if ("Pendiente".equals(nuevoEstado) && "Aprobado".equals(actual)) {
            devolverStockCompleto(cotizacion);
        }

        // Finalizado ya no devuelve stock aquí. Debe usarse registrarDevolucion antes o junto.
        cotizacion.setEstado(nuevoEstado);
        return cotizacionesRepository.save(cotizacion);
    }

    /**
     * Confirma que el material salió. Pasa a "En evento".
     * Opcionalmente puede traer detalles ajustados (cantidades reales de última hora).
     */
    @Transactional
    public Cotizacion confirmarEntrega(Long id, Cotizacion ajustesOpcionales) {
        Cotizacion cotizacion = cotizacionesRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Cotización no encontrada"));

        if (!"Aprobado".equals(cotizacion.getEstado())) {
            throw new RuntimeException("Solo se puede confirmar entrega de pedidos en estado Aprobado");
        }

        // Si el admin ajustó cantidades de último momento
        if (ajustesOpcionales != null && ajustesOpcionales.getDetalles() != null
                && !ajustesOpcionales.getDetalles().isEmpty()) {
            // Liberar reserva anterior
            devolverStockCompleto(cotizacion);
            // Reemplazar detalles
            cotizacion.getDetalles().clear();
            double nuevoTotal = 0.0;
            for (CotizacionDetalle d : ajustesOpcionales.getDetalles()) {
                d.setCotizacion(cotizacion);
                d.setSubtotal(d.getCantidad() * d.getPrecioUnitario());
                nuevoTotal += d.getSubtotal();
                cotizacion.getDetalles().add(d);
            }
            cotizacion.setTotal(nuevoTotal);
            // Reservar de nuevo con cantidades reales
            descontarStock(cotizacion);
        }

        cotizacion.setEstado("En evento");
        return cotizacionesRepository.save(cotizacion);
    }

    /**
     * Registra la devolución del material:
     * - cantidadDevueltaBuena → vuelve al stock
     * - cantidadDanada + cantidadPerdida → NO vuelven (ya estaban descontadas al aprobar;
     *   el stock físico permanente se reduce porque no se reponen)
     *
     * Al aprobar se descontó "cantidad". Al devolver:
     *   se reponen solo las buenas.
     *   dañadas/perdidas permanecen fuera del stock (pérdida real).
     *
     * Luego el pedido pasa a "En lavado" (o "Finalizado" si se indica).
     */
    @Transactional
    public Cotizacion registrarDevolucion(Long id, List<CotizacionDetalle> devolucion, boolean pasarAFinalizado) {
        Cotizacion cotizacion = cotizacionesRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Cotización no encontrada"));

        if (!"En evento".equals(cotizacion.getEstado()) && !"Aprobado".equals(cotizacion.getEstado())) {
            throw new RuntimeException("Solo se registra devolución en Aprobado o En evento");
        }

        for (CotizacionDetalle dev : devolucion) {
            CotizacionDetalle original = cotizacion.getDetalles().stream()
                    .filter(d -> d.getId() != null && d.getId().equals(dev.getId()))
                    .findFirst()
                    .orElseThrow(() -> new RuntimeException("Detalle no encontrado: " + dev.getId()));

            int buena = dev.getCantidadDevueltaBuena() != null ? dev.getCantidadDevueltaBuena() : 0;
            int danada = dev.getCantidadDanada() != null ? dev.getCantidadDanada() : 0;
            int perdida = dev.getCantidadPerdida() != null ? dev.getCantidadPerdida() : 0;

            if (buena + danada + perdida > original.getCantidad()) {
                throw new RuntimeException("Las cantidades de devolución superan lo alquilado en un ítem");
            }

            original.setCantidadDevueltaBuena(buena);
            original.setCantidadDanada(danada);
            original.setCantidadPerdida(perdida);

            // Solo las buenas vuelven al stock disponible
            if (original.getArticuloAlquiler() != null && buena > 0) {
                ArticuloAlquiler art = articuloRepository.findById(original.getArticuloAlquiler().getId())
                        .orElseThrow();
                art.setStockTotal(art.getStockTotal() + buena);
                articuloRepository.save(art);
            }
            // Dañadas y perdidas: ya estaban fuera del stock desde el descuento al aprobar.
            // No se reponen → el inventario físico queda reducido de forma permanente.
        }

        cotizacion.setEstado(pasarAFinalizado ? "Finalizado" : "En lavado");
        return cotizacionesRepository.save(cotizacion);
    }

    /**
     * Marca que el lavado terminó → material listo. Pasa a Finalizado.
     * (El stock bueno ya se reintegró en registrarDevolucion.)
     */
    @Transactional
    public Cotizacion marcarLavadoTerminado(Long id) {
        Cotizacion cotizacion = cotizacionesRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Cotización no encontrada"));
        if (!"En lavado".equals(cotizacion.getEstado())) {
            throw new RuntimeException("El pedido no está en estado En lavado");
        }
        cotizacion.setEstado("Finalizado");
        return cotizacionesRepository.save(cotizacion);
    }

    @Transactional
    public void eliminarCotizacionPorId(Long id) {
        Cotizacion cotizacion = cotizacionesRepository.findById(id).orElse(null);
        if (cotizacion != null) {
            if ("Aprobado".equals(cotizacion.getEstado()) || "En evento".equals(cotizacion.getEstado())) {
                devolverStockCompleto(cotizacion);
            }
            cotizacionesRepository.deleteById(id);
        }
    }

    /**
     * Solo se puede editar en Pendiente o Rechazado.
     * Si estaba Aprobado, hay que rechazar primero (o usar confirmarEntrega con ajustes).
     */
    @Transactional
    public Cotizacion actualizarCotizacion(Long id, Cotizacion cotizacionNueva) {
        return cotizacionesRepository.findById(id).map(existente -> {
            if (!ESTADOS_EDITABLES.contains(existente.getEstado())) {
                throw new RuntimeException(
                        "No se puede editar una cotización en estado '" + existente.getEstado()
                                + "'. Solo Pendiente o Rechazado. Si está Aprobada, recházala primero o usa confirmar entrega."
                );
            }

            existente.setCantidadPersonas(cotizacionNueva.getCantidadPersonas());
            existente.setFechaEvento(cotizacionNueva.getFechaEvento());
            existente.setFechaSalida(cotizacionNueva.getFechaSalida());
            existente.setFechaRetorno(cotizacionNueva.getFechaRetorno());
            existente.setObservaciones(cotizacionNueva.getObservaciones());
            existente.setEstado("Pendiente");

            if (cotizacionNueva.getDetalles() != null) {
                existente.getDetalles().clear();
                double nuevoTotal = 0.0;
                for (CotizacionDetalle nuevoDetalle : cotizacionNueva.getDetalles()) {
                    nuevoDetalle.setCotizacion(existente);
                    nuevoDetalle.setSubtotal(nuevoDetalle.getCantidad() * nuevoDetalle.getPrecioUnitario());
                    nuevoTotal += nuevoDetalle.getSubtotal();
                    existente.getDetalles().add(nuevoDetalle);
                }
                existente.setTotal(nuevoTotal);
            }

            return cotizacionesRepository.save(existente);
        }).orElse(null);
    }

    // ── Stock helpers ─────────────────────────────────────────────────────

    private void descontarStock(Cotizacion cotizacion) {
        if (cotizacion.getDetalles() == null) return;
        for (CotizacionDetalle detalle : cotizacion.getDetalles()) {
            if (detalle.getArticuloAlquiler() == null) continue;
            ArticuloAlquiler articulo = articuloRepository.findById(detalle.getArticuloAlquiler().getId())
                    .orElseThrow(() -> new RuntimeException("Artículo no encontrado"));
            if (articulo.getStockTotal() < detalle.getCantidad()) {
                throw new RuntimeException("Stock insuficiente para: " + articulo.getNombre()
                        + " (disponible: " + articulo.getStockTotal() + ", pedido: " + detalle.getCantidad() + ")");
            }
            articulo.setStockTotal(articulo.getStockTotal() - detalle.getCantidad());
            articuloRepository.save(articulo);
        }
    }

    private void devolverStockCompleto(Cotizacion cotizacion) {
        if (cotizacion.getDetalles() == null) return;
        for (CotizacionDetalle detalle : cotizacion.getDetalles()) {
            if (detalle.getArticuloAlquiler() == null) continue;
            ArticuloAlquiler articulo = articuloRepository.findById(detalle.getArticuloAlquiler().getId())
                    .orElseThrow();
            articulo.setStockTotal(articulo.getStockTotal() + detalle.getCantidad());
            articuloRepository.save(articulo);
        }
    }
}
