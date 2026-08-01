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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@SpringBootTest(classes = BillingsoftwareApplication.class)
@ActiveProfiles("test")
class CategoryServiceIntegrationTest {

    @Autowired
    private CategoryService categoryService;

    @Autowired
    private CategoryRepository categoryRepository;

    @MockBean
    private FileUploadService fileUploadService;

    private CategoryRequest categoryRequest;
    private MultipartFile multipartFile;

    @BeforeEach
    void setUp() {
        categoryRepository.deleteAll();

        categoryRequest = CategoryRequest.builder()
                .name("Test Electronics")
                .description("Test electronic devices")
                .bgColor("#FF5733")
                .build();

        multipartFile = new MockMultipartFile(
                "file",
                "test-image.jpg",
                "image/jpeg",
                "test image content".getBytes()
        );

        when(fileUploadService.uploadFile(any(MultipartFile.class)))
                .thenReturn("https://example.com/test-image.jpg");
        when(fileUploadService.deleteFile(any())).thenReturn(true);
    }

    @AfterEach
    void tearDown() {
        categoryRepository.deleteAll();
    }

    @Test
    @DisplayName("Should save and retrieve category from database")
    void shouldSaveAndRetrieveCategoryFromDatabase() throws Exception {
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
        assertEquals(0, savedCategory.getItems());

        assertEquals(1, allCategories.size());
        assertEquals(savedCategory.getCategoryId(), allCategories.get(0).getCategoryId());
    }

    @Test
    @DisplayName("Should save multiple categories and retrieve all")
    void shouldSaveMultipleCategoriesAndRetrieveAll() throws Exception {
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
    void shouldDeleteCategoryFromDatabase() throws Exception {
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
        RuntimeException exception = assertThrows(RuntimeException.class, () ->
                categoryService.delete("non-existent-id"));

        assertEquals("Category not found: non-existent-id", exception.getMessage());
    }

    @Test
    @DisplayName("Should handle unique constraint on category name")
    void shouldHandleUniqueConstraintOnCategoryName() throws Exception {
        categoryService.add(categoryRequest, multipartFile);

        CategoryRequest duplicateRequest = CategoryRequest.builder()
                .name(categoryRequest.getName())
                .description("Different description")
                .bgColor("#000000")
                .build();

        assertThrows(Exception.class, () ->
                categoryService.add(duplicateRequest, multipartFile));
    }

    @Test
    @DisplayName("Should verify category entity persistence")
    void shouldVerifyCategoryEntityPersistence() throws Exception {
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
