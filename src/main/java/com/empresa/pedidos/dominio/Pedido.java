package com.empresa.pedidos.dominio;

// VIOLACION INTENCIONAL — remover después
import com.empresa.pedidos.infraestructura.persistencia.RepositorioPedidosJpa;
import jakarta.persistence.*;

/**
 * Entidad de dominio que representa un pedido.
 */
@Entity
@Table(name = "pedidos")
public class Pedido {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String cliente;

    private double subtotal;

    private double costo;

    @Enumerated(EnumType.STRING)
    private TipoPedido tipo;

    @Enumerated(EnumType.STRING)
    private EstadoPedido estado;

    public Pedido() {
        this.estado = EstadoPedido.PENDIENTE;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getCliente() { return cliente; }
    public void setCliente(String cliente) { this.cliente = cliente; }

    public double getSubtotal() { return subtotal; }
    public void setSubtotal(double subtotal) { this.subtotal = subtotal; }

    public double getCosto() { return costo; }
    public void setCosto(double costo) { this.costo = costo; }

    public TipoPedido getTipo() { return tipo; }
    public void setTipo(TipoPedido tipo) { this.tipo = tipo; }

    public EstadoPedido getEstado() { return estado; }
    public void setEstado(EstadoPedido estado) { this.estado = estado; }
}