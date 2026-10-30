package com.juniverse.jdbccrud.model;

import java.util.List;

/**
 * Página de resultados.
 *
 * @param content elementos de la página
 * @param page número de página (desde 0)
 * @param size tamaño de página
 * @param totalElements total de elementos que cumplen el filtro
 * @param totalPages total de páginas
 * @param <T> tipo de elemento
 */
public record PageResult<T>(List<T> content, int page, int size, long totalElements, int totalPages) {
}
