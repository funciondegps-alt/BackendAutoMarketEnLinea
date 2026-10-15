package com.ChicaemeSAS.BackendSistema.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "cotizacion_detalle")
public class CotizacionDetalle {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "cotizacion_id", nullable = false)
    private Cotizacion cotizacion;

    @ManyToOne
    @JoinColumn(name = "articulo_id")
    private ArticuloAlquiler articuloAlquiler;

    @ManyToOne
    @JoinColumn(name = "servicio_id")
    private servicios servicio;

    /** Cantidad alquilada / comprometida */
    @Column(nullable = false)
    private Integer cantidad;

    @Column(nullable = false)
    private Double precioUnitario;

    @Column(nullable = false)
    private Double subtotal;

    // ── Devolución (se llenan al registrar regreso del material) ──────────
    /** Unidades que volvieron en buen estado */
    private Integer cantidadDevueltaBuena;

    /** Unidades dañadas (se restan del stock total, no vuelven a disponible) */
    private Integer cantidadDanada;

    /** Unidades perdidas (se restan del stock total) */
    private Integer cantidadPerdida;
}
