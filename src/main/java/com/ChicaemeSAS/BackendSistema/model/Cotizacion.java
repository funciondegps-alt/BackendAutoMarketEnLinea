package com.ChicaemeSAS.BackendSistema.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Estados del evento (campo estado):
 *   Pendiente | Aprobado | En evento | En lavado | Finalizado | Rechazado
 *
 * Estados de pago (campo estadoPago):
 *   Sin pago | Parcial | Pagado | Credito | Vencido
 */
@Data
@Entity
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "cotizaciones")
public class Cotizacion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonFormat(pattern = "yyyy-MM-dd")
    @Column(nullable = false)
    private LocalDate fechaEvento;

    /** Día en que el material sale de bodega */
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate fechaSalida;

    /** Día en que el material debe regresar */
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate fechaRetorno;

    @Column(nullable = false)
    private Integer cantidadPersonas;

    @Column(nullable = false, length = 50)
    private String estado;

    @Column(nullable = false)
    private Double total;

    @Column(columnDefinition = "TEXT")
    private String observaciones;

    // ── Pago ──────────────────────────────────────────────────────────────
    /** Suma de todos los abonos registrados */
    @Column(nullable = false)
    private Double montoPagado = 0.0;

    /**
     * Sin pago | Parcial | Pagado | Credito | Vencido
     */
    @Column(nullable = false, length = 30)
    private String estadoPago = "Sin pago";

    /** Solo aplica si estadoPago = Credito */
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate fechaVencimientoPago;

    @ManyToOne
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    @OneToMany(mappedBy = "cotizacion", cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonIgnoreProperties("cotizacion")
    private List<CotizacionDetalle> detalles = new ArrayList<>();

    @OneToMany(mappedBy = "cotizacion", cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonIgnoreProperties("cotizacion")
    private List<Pago> pagos = new ArrayList<>();

    /** Saldo pendiente (no se persiste; se calcula al serializar si hace falta en el service) */
    @Transient
    public Double getSaldo() {
        double t = total != null ? total : 0.0;
        double p = montoPagado != null ? montoPagado : 0.0;
        return Math.max(0.0, t - p);
    }
}
