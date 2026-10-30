package com.juniverse.jdbccrud.exception;

/** No existe el producto (404). */
public class ProductNotFoundException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * Crea la excepción.
     *
     * @param id producto buscado
     */
    public ProductNotFoundException(long id) {
        super("No existe el producto " + id);
    }
}
