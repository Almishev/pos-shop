package in.bushansirgur.billingsoftware.service;

import in.bushansirgur.billingsoftware.BillingsoftwareApplication;
import in.bushansirgur.billingsoftware.entity.CategoryEntity;
import in.bushansirgur.billingsoftware.io.CategoryRequest;
import in.bushansirgur.billingsoftware.io.CategoryResponse;
import in.bushansirgur.billingsoftware.repository.CategoryRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@SpringBootTest(classes = BillingsoftwareApplication.class)
@ActiveProfiles("test")
class CategoryServiceIntegrationTest {

    @Autowired
    private CategoryService categoryService;

    @Autowired
    private CategoryRepository categoryRepository;

    @MockBean
    private MultipartFile multipartFile;

    private CategoryRequest categoryRequest;

    @BeforeEach
    void setUp() {
        
        categoryRepository.deleteAll();

        categoryRequest = CategoryRequest.builder()
                .name("Test Electronics")
                .description("Test electronic devices")
                .bgColor("#FF5733")
                .build();

        when(multipartFile.getOriginalFilename()).thenReturn("test-image.jpg");
        when(multipartFile.getContentType()).thenReturn("image/jpeg");
    }

    @AfterEach
    void tearDown() {
       
        categoryRepository.deleteAll();
    }

    @Test
    @DisplayName("Should save and retrieve category from database")
    void shouldSaveAndRetrieveCategoryFromDatabase() throws IOException {
      
        when(multipartFile.getBytes()).thenReturn("test image content".getBytes());

      
        CategoryResponse savedCategory = categoryService.add(categoryRequest, multipartFile);
        List<CategoryResponse> allCategories = categoryService.read();

    
        assertNotNull(savedCategory);
        assertNotNull(savedCategory.getCategoryId());
        assertEquals(categoryRequest.getName(), savedCategory.getName());
        assertEquals(categoryRequest.getDescription(), savedCategory.getDescription());
        assertEquals(categoryRequest.getBgColor(), savedCategory.getBgColor());
        assertNotNull(savedCategory.getImgUrl());
        assertNotNull(savedCategory.getCreatedAt());
        assertNotNull(savedCategory.getUpdatedAt());
        assertEquals(0, savedCategory.getItems()); // Няма продукти в категорията

        assertEquals(1, allCategories.size());
        assertEquals(savedCategory.getCategoryId(), allCategories.get(0).getCategoryId());
    }

    @Test
    @DisplayName("Should save multiple categories and retrieve all")
    void shouldSaveMultipleCategoriesAndRetrieveAll() throws IOException {
        
        when(multipartFile.getBytes()).thenReturn("test image content".getBytes());

        CategoryRequest categoryRequest2 = CategoryRequest.builder()
                .name("Test Books")
                .description("Test books and literature")
                .bgColor("#33FF57")
                .build();

        categoryService.add(categoryRequest, multipartFile);
        categoryService.add(categoryRequest2, multipartFile);
        List<CategoryResponse> allCategories = categoryService.read();

        assertEquals(2, allCategories.size());
        assertTrue(allCategories.stream()
                .anyMatch(cat -> cat.getName().equals("Test Electronics")));
        assertTrue(allCategories.stream()
                .anyMatch(cat -> cat.getName().equals("Test Books")));
    }

    @Test
    @DisplayName("Should delete category from database")
    void shouldDeleteCategoryFromDatabase() throws IOException {
       
        when(multipartFile.getBytes()).thenReturn("test image content".getBytes());
        CategoryResponse savedCategory = categoryService.add(categoryRequest, multipartFile);
        String categoryId = savedCategory.getCategoryId();

       
        categoryService.delete(categoryId);
        List<CategoryResponse> allCategories = categoryService.read();

        
        assertEquals(0, allCategories.size());
        assertTrue(categoryRepository.findByCategoryId(categoryId).isEmpty());
    }

    @Test
    @DisplayName("Should throw exception when deleting non-existent category")
    void shouldThrowExceptionWhenDeletingNonExistentCategory() {
       
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            categoryService.delete("non-existent-id");
        });

        assertEquals("Category not found: non-existent-id", exception.getMessage());
    }

    @Test
    @DisplayName("Should handle unique constraint on category name")
    void shouldHandleUniqueConstraintOnCategoryName() throws IOException {
       
        when(multipartFile.getBytes()).thenReturn("test image content".getBytes());

    
        categoryService.add(categoryRequest, multipartFile);

    
        CategoryRequest duplicateRequest = CategoryRequest.builder()
                .name(categoryRequest.getName()) 
                .description("Different description")
                .bgColor("#000000")
                .build();

        
        assertThrows(Exception.class, () -> {
            categoryService.add(duplicateRequest, multipartFile);
        });
    }



    @Test
    @DisplayName("Should verify category entity persistence")
    void shouldVerifyCategoryEntityPersistence() throws IOException {
       
        when(multipartFile.getBytes()).thenReturn("test image content".getBytes());

        CategoryResponse savedCategory = categoryService.add(categoryRequest, multipartFile);
        
       
        CategoryEntity persistedEntity = categoryRepository.findByCategoryId(savedCategory.getCategoryId()).orElse(null);

        assertNotNull(persistedEntity);
        assertEquals(categoryRequest.getName(), persistedEntity.getName());
        assertEquals(categoryRequest.getDescription(), persistedEntity.getDescription());
        assertEquals(categoryRequest.getBgColor(), persistedEntity.getBgColor());
        assertNotNull(persistedEntity.getImgUrl());
        assertNotNull(persistedEntity.getCreatedAt());
        assertNotNull(persistedEntity.getUpdatedAt());
        assertNotNull(persistedEntity.getCategoryId());
    }
}
