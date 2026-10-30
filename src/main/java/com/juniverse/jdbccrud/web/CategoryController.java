package com.juniverse.jdbccrud.web;

import com.juniverse.jdbccrud.model.Category;
import com.juniverse.jdbccrud.repository.CategoryRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.net.URI;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** API REST de categorías ({@code /api/categories}). */
@RestController
@RequestMapping("/api/categories")
public class CategoryController {

    /**
     * Datos para crear una categoría.
     *
     * @param name nombre
     */
    public record CategoryRequest(@NotBlank(message = "El nombre es obligatorio") @Size(max = 80, message = "El nombre no puede superar los 80 caracteres") String name) {
    }

    private final CategoryRepository repository;

    /**
     * Crea el controlador.
     *
     * @param repository categorías
     */
    public CategoryController(CategoryRepository repository) {
        this.repository = repository;
    }

    /**
     * Lista las categorías.
     *
     * @return categorías ordenadas por nombre
     */
    @GetMapping
    public List<Category> list() {
        return repository.findAll();
    }

    /**
     * Crea una categoría (409 si el nombre ya existe).
     *
     * @param request datos validados
     * @return 201 con la categoría
     */
    @PostMapping
    public ResponseEntity<Category> create(@Valid @RequestBody CategoryRequest request) {
        Category created = repository.insert(request.name().trim());
        return ResponseEntity.created(URI.create("/api/categories/" + created.id())).body(created);
    }
}
