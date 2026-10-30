package com.juniverse.jdbccrud.web;

import com.juniverse.jdbccrud.model.CategoryStock;
import com.juniverse.jdbccrud.model.PageResult;
import com.juniverse.jdbccrud.model.Product;
import com.juniverse.jdbccrud.repository.ProductBatchRepository.NewProduct;
import com.juniverse.jdbccrud.service.ProductService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
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

    /**
     * Datos para reemplazar un producto: los mismos campos más la versión que el cliente leyó.
     *
     * @param version versión leída (control optimista)
     * @param product nuevos datos
     */
    public record UpdateRequest(@Min(value = 0, message = "La versión es obligatoria") int version, @Valid @NotNull ProductRequest product) {
    }

    /**
     * Ajuste de existencias.
     *
     * @param delta cantidad a sumar (negativa para una salida)
     */
    public record StockRequest(@NotNull(message = "delta es obligatorio") Integer delta) {
    }

    /**
     * Movimiento de existencias entre dos productos.
     *
     * @param fromId origen
     * @param toId destino
     * @param quantity unidades
     */
    public record TransferRequest(@NotNull Long fromId, @NotNull Long toId, @Min(value = 1, message = "La cantidad mínima es 1") int quantity) {
    }

    /**
     * Lote de productos a importar.
     *
     * @param products productos
     */
    public record ImportRequest(@NotEmpty(message = "Envía al menos un producto") List<@Valid ProductRequest> products) {
    }

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
