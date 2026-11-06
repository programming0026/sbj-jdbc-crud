package com.juniverse.jdbccrud.controller;

import com.juniverse.jdbccrud.dto.ImportRequest;
import com.juniverse.jdbccrud.dto.ProductRequest;
import com.juniverse.jdbccrud.dto.StockRequest;
import com.juniverse.jdbccrud.dto.TransferRequest;
import com.juniverse.jdbccrud.dto.UpdateRequest;
import com.juniverse.jdbccrud.model.CategoryStock;
import com.juniverse.jdbccrud.model.PageResult;
import com.juniverse.jdbccrud.model.Product;
import com.juniverse.jdbccrud.repository.ProductBatchRepository.NewProduct;
import com.juniverse.jdbccrud.service.ProductService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** API REST del CRUD de productos ({@code /api/products}). */
@RestController
@RequestMapping("/api/products")
public class ProductController {

    private final ProductService service;

    /**
     * Crea el controlador.
     *
     * @param service servicio de productos
     */
    public ProductController(ProductService service) {
        this.service = service;
    }

    /**
     * Lista productos con búsqueda, filtro por categoría, orden y paginación.
     *
     * @param q texto en nombre o SKU
     * @param categoryId categoría
     * @param sort campo de orden: name, price, stock, created, sku
     * @param desc orden descendente
     * @param page página (desde 0)
     * @param size tamaño (1 a 100)
     * @return página de productos
     */
    @GetMapping
    public PageResult<Product> list(@RequestParam(required = false) String q, @RequestParam(required = false) Long categoryId,
            @RequestParam(defaultValue = "name") String sort, @RequestParam(defaultValue = "false") boolean desc,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "10") int size) {
        return service.search(q, categoryId, sort, desc, Math.max(page, 0), Math.min(Math.max(size, 1), 100));
    }

    /**
     * Obtiene un producto.
     *
     * @param id identificador
     * @return el producto
     */
    @GetMapping("/{id}")
    public Product get(@PathVariable long id) {
        return service.get(id);
    }

    /**
     * Crea un producto.
     *
     * @param request datos validados
     * @return 201 con el producto y la cabecera Location
     */
    @PostMapping
    public ResponseEntity<Product> create(@Valid @RequestBody ProductRequest request) {
        Product created = service.create(request.sku(), request.name(), request.description(), request.price(), request.stock(), request.categoryId());
        return ResponseEntity.created(URI.create("/api/products/" + created.id())).body(created);
    }

    /**
     * Reemplaza un producto (requiere la versión leída).
     *
     * @param id identificador
     * @param request versión y nuevos datos
     * @return el producto actualizado
     */
    @PutMapping("/{id}")
    public Product update(@PathVariable long id, @Valid @RequestBody UpdateRequest request) {
        ProductRequest p = request.product();
        return service.update(id, request.version(), p.sku(), p.name(), p.description(), p.price(), p.stock(), p.categoryId());
    }

    /**
     * Elimina un producto.
     *
     * @param id identificador
     */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable long id) {
        service.delete(id);
    }

    /**
     * Suma o resta existencias de forma atómica.
     *
     * @param id identificador
     * @param request cantidad
     * @return el producto con el stock nuevo
     */
    @PatchMapping("/{id}/stock")
    public Product adjustStock(@PathVariable long id, @Valid @RequestBody StockRequest request) {
        return service.adjustStock(id, request.delta());
    }

    /**
     * Mueve existencias entre dos productos (transacción).
     *
     * @param request origen, destino y cantidad
     * @return 204
     */
    @PostMapping("/transfer")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void transfer(@Valid @RequestBody TransferRequest request) {
        service.transferStock(request.fromId(), request.toId(), request.quantity());
    }

    /**
     * Importa un lote de productos: todo o nada.
     *
     * @param request productos a importar
     * @return cuántos se insertaron
     */
    @PostMapping("/import")
    public Map<String, Integer> importProducts(@Valid @RequestBody ImportRequest request) {
        List<NewProduct> rows = request.products().stream()
                .map(p -> new NewProduct(p.sku(), p.name(), p.description(), p.price(), p.stock(), p.categoryId())).toList();
        return Map.of("inserted", service.importProducts(rows));
    }

    /**
     * Inventario por categoría (agregación).
     *
     * @return resumen por categoría
     */
    @GetMapping("/reports/stock-by-category")
    public List<CategoryStock> stockByCategory() {
        return service.stockByCategory();
    }
}
