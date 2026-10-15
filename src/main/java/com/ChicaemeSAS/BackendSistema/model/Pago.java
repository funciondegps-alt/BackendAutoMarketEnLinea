package com.ChicaemeSAS.BackendSistema.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "pago")
public class Pago {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Fecha del abono (antes se llamaba fehcaPago por typo) */
    @JsonFormat(pattern = "yyyy-MM-dd")
    @Column(name = "fecha_pago", nullable = false)
    private LocalDate fechaPago;

    @Column(nullable = false)
    private Double monto;

    /** Transferencia | Efectivo | Consignacion | Cheque | Otro */
    @Column(nullable = false, length = 100)
    private String metodoPago;

    /** Número de comprobante, referencia bancaria, etc. */
    @Column(length = 200)
    private String comprobantePago;

    /** Nota libre del admin */
    @Column(columnDefinition = "TEXT")
    private String notas;

    @ManyToOne
    @JoinColumn(name = "cotizacion_id", nullable = false)
    @JsonIgnoreProperties({"detalles", "pagos"})
    private Cotizacion cotizacion;
}
