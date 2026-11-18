package com.quell.repository;

import java.util.List;
import java.util.Optional;

import com.quell.domain.Category;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CategoryRepository extends JpaRepository<Category, Long> {

    Optional<Category> findByNameIgnoreCase(String name);

    boolean existsByNameIgnoreCaseAndIdNot(String name, Long id);

    boolean existsByNameIgnoreCase(String name);

    /** Loads the categories together with their keywords in one query. */
    @EntityGraph(attributePaths = "keywords")
    List<Category> findAllByOrderByNameAsc();
}
