package com.juniverse.jdbccrud.controller;

import com.juniverse.jdbccrud.dto.CategoryRequest;
import com.juniverse.jdbccrud.model.Category;
import com.juniverse.jdbccrud.repository.CategoryRepository;
import jakarta.validation.Valid;
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
