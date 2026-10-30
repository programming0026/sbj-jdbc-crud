package com.juniverse.jdbccrud.service;

import com.juniverse.jdbccrud.exception.CategoryNotFoundException;
import com.juniverse.jdbccrud.exception.InsufficientStockException;
import com.juniverse.jdbccrud.exception.ProductNotFoundException;
import com.juniverse.jdbccrud.exception.VersionConflictException;
import com.juniverse.jdbccrud.model.CategoryStock;
import com.juniverse.jdbccrud.model.PageResult;
import com.juniverse.jdbccrud.model.Product;
import com.juniverse.jdbccrud.repository.CategoryRepository;
import com.juniverse.jdbccrud.repository.ProductBatchRepository;
import com.juniverse.jdbccrud.repository.ProductBatchRepository.NewProduct;
import com.juniverse.jdbccrud.repository.ProductRepository;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Lógica de negocio y <b>transacciones</b>. {@code @Transactional} abre una transacción al entrar al método y la confirma al
 * salir; si el método lanza una excepción no comprobada, TODO lo que hizo se deshace (rollback).
 */
@Service
public class ProductService {

    private static final int BATCH_SIZE = 500;

    private final ProductRepository products;
    private final ProductBatchRepository batch;
    private final CategoryRepository categories;

    /**
     * Crea el servicio.
     *
     * @param products CRUD de productos
     * @param batch operaciones por lote
     * @param categories categorías
     */
    public ProductService(ProductRepository products, ProductBatchRepository batch, CategoryRepository categories) {
        this.products = products;
        this.batch = batch;
        this.categories = categories;
    }

    /**
     * Busca un producto.
     *
     * @param id identificador
     * @return el producto
     * @throws ProductNotFoundException si no existe
     */
    @Transactional(readOnly = true)
    public Product get(long id) {
        return products.findById(id).orElseThrow(() -> new ProductNotFoundException(id));
    }

    /**
     * Lista con filtros y paginación.
     *
     * @param text texto de búsqueda
     * @param categoryId categoría
     * @param sort campo de orden
     * @param descending orden descendente
     * @param page página (desde 0)
     * @param size tamaño
     * @return página de productos
     */
    @Transactional(readOnly = true)
    public PageResult<Product> search(String text, Long categoryId, String sort, boolean descending, int page, int size) {
        return products.search(text, categoryId, sort, descending, page, size);
    }

    /**
     * Crea un producto. Si el SKU ya existe, PostgreSQL lo rechaza por su índice único y Spring lo traduce a
     * {@code DuplicateKeyException}: la unicidad la garantiza la base, no una consulta previa (que sería vulnerable a
     * condiciones de carrera).
     *
     * @param sku código
     * @param name nombre
     * @param description descripción
     * @param price precio
     * @param stock existencias
     * @param categoryId categoría
     * @return el producto creado
     */
    @Transactional
    public Product create(String sku, String name, String description, BigDecimal price, int stock, long categoryId) {
        requireCategory(categoryId);
        long id = products.insert(sku, name, description, price, stock, categoryId);
        return get(id);
    }

    /**
     * Reemplaza los datos de un producto con control de concurrencia optimista.
     *
     * @param id identificador
     * @param version versión que el cliente leyó
     * @param sku código
     * @param name nombre
     * @param description descripción
     * @param price precio
     * @param stock existencias
     * @param categoryId categoría
     * @return el producto actualizado
     * @throws ProductNotFoundException si no existe
     * @throws VersionConflictException si cambió desde que se leyó
     */
    @Transactional
    public Product update(long id, int version, String sku, String name, String description, BigDecimal price, int stock, long categoryId) {
        requireCategory(categoryId);
        if (products.update(id, version, sku, name, description, price, stock, categoryId) == 0) {
            throw products.exists(id) ? new VersionConflictException(id) : new ProductNotFoundException(id);
        }
        return get(id);
    }

    /**
     * Elimina un producto.
     *
     * @param id identificador
     * @throws ProductNotFoundException si no existe
     */
    @Transactional
    public void delete(long id) {
        if (products.delete(id) == 0) {
            throw new ProductNotFoundException(id);
        }
    }

    /**
     * Ajusta las existencias de forma atómica.
     *
     * @param id identificador
     * @param delta cantidad a sumar (negativa para una salida)
     * @return el producto con el stock nuevo
     * @throws ProductNotFoundException si no existe
     * @throws InsufficientStockException si la salida supera las existencias
     */
    @Transactional
    public Product adjustStock(long id, int delta) {
        if (products.adjustStock(id, delta) == 0) {
            throw products.exists(id) ? new InsufficientStockException(id, -delta) : new ProductNotFoundException(id);
        }
        return get(id);
    }

    /**
     * Mueve existencias de un producto a otro en UNA transacción: o se hacen las dos operaciones o ninguna. Si el destino
     * no existe, la salida del origen (ya ejecutada) se deshace.
     *
     * @param fromId producto de origen
     * @param toId producto de destino
     * @param quantity unidades a mover
     */
    @Transactional
    public void transferStock(long fromId, long toId, int quantity) {
        adjustStock(fromId, -quantity);
        adjustStock(toId, quantity);
    }

    /**
     * Importa muchos productos por lote en una sola transacción: si UNA fila falla (por ejemplo, SKU duplicado) no se inserta
     * ninguna.
     *
     * @param rows productos a importar
     * @return cantidad insertada
     */
    @Transactional
    public int importProducts(List<NewProduct> rows) {
        return batch.insertAll(rows, BATCH_SIZE);
    }

    /**
     * Inventario agrupado por categoría.
     *
     * @return resumen por categoría
     */
    @Transactional(readOnly = true)
    public List<CategoryStock> stockByCategory() {
        return products.stockByCategory();
    }

    private void requireCategory(long categoryId) {
        categories.findById(categoryId).orElseThrow(() -> new CategoryNotFoundException(categoryId));
    }
}
