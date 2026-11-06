package com.juniverse.jdbccrud.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/**
 * Datos para crear un producto.
 *
 * @param sku código (mayúsculas, dígitos y guiones)
 * @param name nombre
 * @param description descripción opcional
 * @param price precio
 * @param stock existencias
 * @param categoryId categoría
 */
public record ProductRequest(
        @NotBlank(message = "El SKU es obligatorio") @Pattern(regexp = "[A-Z0-9-]{3,30}", message = "El SKU admite mayúsculas, dígitos y guiones (3 a 30)") String sku,
        @NotBlank(message = "El nombre es obligatorio") @Size(max = 120, message = "El nombre no puede superar los 120 caracteres") String name,
        @Size(max = 500, message = "La descripción no puede superar los 500 caracteres") String description,
        @NotNull(message = "El precio es obligatorio") @DecimalMin(value = "0.00", message = "El precio no puede ser negativo") BigDecimal price,
        @Min(value = 0, message = "El stock no puede ser negativo") int stock,
        @NotNull(message = "La categoría es obligatoria") Long categoryId) {
}
