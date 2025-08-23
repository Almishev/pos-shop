package in.bushansirgur.billingsoftware.repository;

import in.bushansirgur.billingsoftware.entity.CategoryEntity;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@ActiveProfiles("test")
class CategoryRepositoryTest {

    @Autowired
    private CategoryRepository categoryRepository;

    private CategoryEntity testCategory;

    @BeforeEach
    void setUp() {
        
        categoryRepository.deleteAll();

        testCategory = CategoryEntity.builder()
                .categoryId("test-category-123")
                .name("Test Electronics")
                .description("Test electronic devices")
                .bgColor("#FF5733")
                .imgUrl("https://example.com/test-image.jpg")
                .createdAt(Timestamp.valueOf(LocalDateTime.now()))
                .updatedAt(Timestamp.valueOf(LocalDateTime.now()))
                .build();
    }

    @AfterEach
    void tearDown() {
        try {
            categoryRepository.deleteAll();
        } catch (Exception e) {
            // Ignore cleanup errors
        }
    }

    @Test
    @DisplayName("Should save category successfully")
    void shouldSaveCategorySuccessfully() {
        
        CategoryEntity savedCategory = categoryRepository.save(testCategory);

        assertNotNull(savedCategory);
        assertNotNull(savedCategory.getId());
        assertEquals(testCategory.getCategoryId(), savedCategory.getCategoryId());
        assertEquals(testCategory.getName(), savedCategory.getName());
        assertEquals(testCategory.getDescription(), savedCategory.getDescription());
        assertEquals(testCategory.getBgColor(), savedCategory.getBgColor());
        assertEquals(testCategory.getImgUrl(), savedCategory.getImgUrl());
    }

    @Test
    @DisplayName("Should find category by categoryId")
    void shouldFindCategoryByCategoryId() {
        
        CategoryEntity savedCategory = categoryRepository.save(testCategory);

        Optional<CategoryEntity> foundCategory = categoryRepository.findByCategoryId(testCategory.getCategoryId());

        assertTrue(foundCategory.isPresent());
        assertEquals(savedCategory.getId(), foundCategory.get().getId());
        assertEquals(testCategory.getCategoryId(), foundCategory.get().getCategoryId());
        assertEquals(testCategory.getName(), foundCategory.get().getName());
    }

    @Test
    @DisplayName("Should return empty when category not found by categoryId")
    void shouldReturnEmptyWhenCategoryNotFoundByCategoryId() {
       
        Optional<CategoryEntity> foundCategory = categoryRepository.findByCategoryId("non-existent-id");

        assertFalse(foundCategory.isPresent());
    }

    @Test
    @DisplayName("Should find all categories")
    void shouldFindAllCategories() {
       
        categoryRepository.save(testCategory);

        CategoryEntity category2 = CategoryEntity.builder()
                .categoryId("test-category-456")
                .name("Test Books")
                .description("Test books and literature")
                .bgColor("#33FF57")
                .imgUrl("https://example.com/test-image2.jpg")
                .createdAt(Timestamp.valueOf(LocalDateTime.now()))
                .updatedAt(Timestamp.valueOf(LocalDateTime.now()))
                .build();
        categoryRepository.save(category2);

        List<CategoryEntity> allCategories = categoryRepository.findAll();

        assertEquals(2, allCategories.size());
        assertTrue(allCategories.stream()
                .anyMatch(cat -> cat.getCategoryId().equals("test-category-123")));
        assertTrue(allCategories.stream()
                .anyMatch(cat -> cat.getCategoryId().equals("test-category-456")));
    }

    @Test
    @DisplayName("Should return empty list when no categories exist")
    void shouldReturnEmptyListWhenNoCategoriesExist() {
       
        List<CategoryEntity> allCategories = categoryRepository.findAll();

        assertTrue(allCategories.isEmpty());
    }

    @Test
    @DisplayName("Should delete category successfully")
    void shouldDeleteCategorySuccessfully() {
       
        CategoryEntity savedCategory = categoryRepository.save(testCategory);
        Long categoryId = savedCategory.getId();

        categoryRepository.delete(savedCategory);

        Optional<CategoryEntity> deletedCategory = categoryRepository.findById(categoryId);
        assertFalse(deletedCategory.isPresent());
    }

    @Test
    @DisplayName("Should enforce unique constraint on categoryId")
    void shouldEnforceUniqueConstraintOnCategoryId() {

        categoryRepository.save(testCategory);

        CategoryEntity duplicateCategory = CategoryEntity.builder()
                .categoryId(testCategory.getCategoryId()) 
                .name("Different Name")
                .description("Different description")
                .bgColor("#000000")
                .imgUrl("https://example.com/different-image.jpg")
                .createdAt(Timestamp.valueOf(LocalDateTime.now()))
                .updatedAt(Timestamp.valueOf(LocalDateTime.now()))
                .build();

        assertThrows(Exception.class, () -> {
            categoryRepository.save(duplicateCategory);
        });
    }

    @Test
    @DisplayName("Should enforce unique constraint on name")
    void shouldEnforceUniqueConstraintOnName() {
    
        categoryRepository.save(testCategory);

        CategoryEntity duplicateNameCategory = CategoryEntity.builder()
                .categoryId("different-category-id")
                .name(testCategory.getName()) 
                .description("Different description")
                .bgColor("#000000")
                .imgUrl("https://example.com/different-image.jpg")
                .createdAt(Timestamp.valueOf(LocalDateTime.now()))
                .updatedAt(Timestamp.valueOf(LocalDateTime.now()))
                .build();

        assertThrows(Exception.class, () -> {
            categoryRepository.save(duplicateNameCategory);
        });
    }




}
