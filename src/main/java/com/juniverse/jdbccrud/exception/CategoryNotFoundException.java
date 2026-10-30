package com.juniverse.jdbccrud.exception;

/** No existe la categoría (404). */
public class CategoryNotFoundException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * Crea la excepción.
     *
     * @param id categoría buscada
     */
    public CategoryNotFoundException(long id) {
        super("No existe la categoría " + id);
    }
}
