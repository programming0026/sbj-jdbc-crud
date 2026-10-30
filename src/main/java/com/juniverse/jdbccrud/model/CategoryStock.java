package com.juniverse.jdbccrud.model;

import java.math.BigDecimal;

/**
 * Resumen de inventario por categoría. JdbcClient lo rellena solo: cada columna del SELECT ({@code total_stock}) se
 * enlaza con el parámetro del constructor equivalente ({@code totalStock}).
 *
 * @param category nombre de la categoría
 * @param products cantidad de productos
 * @param totalStock unidades en existencia
 * @param inventoryValue valor del inventario (precio por existencias)
 */
public record CategoryStock(String category, long products, long totalStock, BigDecimal inventoryValue) {
}
