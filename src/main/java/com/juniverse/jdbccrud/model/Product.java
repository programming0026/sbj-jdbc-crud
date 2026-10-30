package com.juniverse.jdbccrud.model;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Producto tal como se lee de la base (incluye el nombre de su categoría gracias a un JOIN).
 *
 * @param id identificador
 * @param sku código único
 * @param name nombre
 * @param description descripción
 * @param price precio
 * @param stock existencias
 * @param categoryId categoría
 * @param categoryName nombre de la categoría
 * @param createdAt alta
 * @param updatedAt última modificación
 * @param version versión para el control de concurrencia optimista
 */
public record Product(Long id, String sku, String name, String description, BigDecimal price, int stock, Long categoryId,
        String categoryName, Instant createdAt, Instant updatedAt, int version) {
}
