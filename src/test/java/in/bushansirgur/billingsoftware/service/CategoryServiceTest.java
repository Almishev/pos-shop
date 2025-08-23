package in.bushansirgur.billingsoftware.service;

import in.bushansirgur.billingsoftware.entity.CategoryEntity;
import in.bushansirgur.billingsoftware.io.CategoryRequest;
import in.bushansirgur.billingsoftware.io.CategoryResponse;
import in.bushansirgur.billingsoftware.repository.CategoryRepository;
import in.bushansirgur.billingsoftware.repository.ItemRepository;
import in.bushansirgur.billingsoftware.service.impl.CategoryServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CategoryServiceTest {

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private ItemRepository itemRepository;

    @Mock
    private FileUploadService fileUploadService;

    @Mock
    private MultipartFile multipartFile;

    @InjectMocks
    private CategoryServiceImpl categoryService;

    private CategoryRequest categoryRequest;
    private CategoryEntity categoryEntity;
    private CategoryResponse expectedResponse;

    @BeforeEach
    void setUp() {
        // Подготовка на тестови данни
        categoryRequest = CategoryRequest.builder()
                .name("Electronics")
                .description("Electronic devices and gadgets")
                .bgColor("#FF5733")
                .build();

        Timestamp now = Timestamp.valueOf(LocalDateTime.now());
        categoryEntity = CategoryEntity.builder()
                .id(1L)
                .categoryId("test-category-id")
                .name("Electronics")
                .description("Electronic devices and gadgets")
                .bgColor("#FF5733")
                .imgUrl("https://example.com/image.jpg")
                .createdAt(now)
                .updatedAt(now)
                .build();

        expectedResponse = CategoryResponse.builder()
                .categoryId("test-category-id")
                .name("Electronics")
                .description("Electronic devices and gadgets")
                .bgColor("#FF5733")
                .imgUrl("https://example.com/image.jpg")
                .createdAt(now)
                .updatedAt(now)
                .items(5)
                .build();
    }

    @Test
    @DisplayName("Should successfully add a new category")
    void add_ShouldAddNewCategory_Successfully() throws IOException {
        // Arrange
        when(fileUploadService.uploadFile(any(MultipartFile.class)))
                .thenReturn("https://example.com/image.jpg");
        when(categoryRepository.save(any(CategoryEntity.class)))
                .thenReturn(categoryEntity);
        when(itemRepository.countByCategoryId(anyLong()))
                .thenReturn(5);

        // Act
        CategoryResponse result = categoryService.add(categoryRequest, multipartFile);

        // Assert
        assertNotNull(result);
        assertEquals(expectedResponse.getCategoryId(), result.getCategoryId());
        assertEquals(expectedResponse.getName(), result.getName());
        assertEquals(expectedResponse.getDescription(), result.getDescription());
        assertEquals(expectedResponse.getBgColor(), result.getBgColor());
        assertEquals(expectedResponse.getImgUrl(), result.getImgUrl());
        assertEquals(expectedResponse.getItems(), result.getItems());

        verify(fileUploadService).uploadFile(multipartFile);
        verify(categoryRepository).save(any(CategoryEntity.class));
        verify(itemRepository).countByCategoryId(anyLong());
    }

    @Test
    @DisplayName("Should throw IOException when file upload fails")
    void add_ShouldThrowIOException_WhenFileUploadFails() throws IOException {
        // Arrange
        when(fileUploadService.uploadFile(any(MultipartFile.class)))
                .thenThrow(new RuntimeException("File upload failed"));

        // Act & Assert
        assertThrows(RuntimeException.class, () -> {
            categoryService.add(categoryRequest, multipartFile);
        });

        verify(fileUploadService).uploadFile(multipartFile);
        verify(categoryRepository, never()).save(any(CategoryEntity.class));
    }

    @Test
    @DisplayName("Should return all categories when reading")
    void read_ShouldReturnAllCategories() {
        // Arrange
        CategoryEntity categoryEntity2 = CategoryEntity.builder()
                .id(2L)
                .categoryId("test-category-id-2")
                .name("Books")
                .description("Books and literature")
                .bgColor("#33FF57")
                .imgUrl("https://example.com/image2.jpg")
                .createdAt(Timestamp.valueOf(LocalDateTime.now()))
                .updatedAt(Timestamp.valueOf(LocalDateTime.now()))
                .build();

        List<CategoryEntity> categoryEntities = Arrays.asList(categoryEntity, categoryEntity2);
        when(categoryRepository.findAll()).thenReturn(categoryEntities);
        when(itemRepository.countByCategoryId(1L)).thenReturn(5);
        when(itemRepository.countByCategoryId(2L)).thenReturn(3);

        // Act
        List<CategoryResponse> result = categoryService.read();

        // Assert
        assertNotNull(result);
        assertEquals(2, result.size());
        assertEquals("Electronics", result.get(0).getName());
        assertEquals("Books", result.get(1).getName());
        assertEquals(5, result.get(0).getItems());
        assertEquals(3, result.get(1).getItems());

        verify(categoryRepository).findAll();
        verify(itemRepository, times(2)).countByCategoryId(anyLong());
    }

    @Test
    @DisplayName("Should return empty list when no categories exist")
    void read_ShouldReturnEmptyList_WhenNoCategoriesExist() {
        // Arrange
        when(categoryRepository.findAll()).thenReturn(Arrays.asList());

        // Act
        List<CategoryResponse> result = categoryService.read();

        // Assert
        assertNotNull(result);
        assertTrue(result.isEmpty());

        verify(categoryRepository).findAll();
        verify(itemRepository, never()).countByCategoryId(anyLong());
    }

    @Test
    @DisplayName("Should successfully delete existing category")
    void delete_ShouldDeleteCategory_Successfully() {
        // Arrange
        String categoryId = "test-category-id";
        when(categoryRepository.findByCategoryId(categoryId))
                .thenReturn(Optional.of(categoryEntity));

        // Act
        categoryService.delete(categoryId);

        // Assert
        verify(categoryRepository).findByCategoryId(categoryId);
        verify(fileUploadService).deleteFile(categoryEntity.getImgUrl());
        verify(categoryRepository).delete(categoryEntity);
    }

    @Test
    @DisplayName("Should throw RuntimeException when category not found for deletion")
    void delete_ShouldThrowRuntimeException_WhenCategoryNotFound() {
        // Arrange
        String categoryId = "non-existent-category-id";
        when(categoryRepository.findByCategoryId(categoryId))
                .thenReturn(Optional.empty());

        // Act & Assert
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            categoryService.delete(categoryId);
        });

        assertEquals("Category not found: " + categoryId, exception.getMessage());

        verify(categoryRepository).findByCategoryId(categoryId);
        verify(fileUploadService, never()).deleteFile(anyString());
        verify(categoryRepository, never()).delete(any(CategoryEntity.class));
    }

    @Test
    @DisplayName("Should handle null categoryId in delete method")
    void delete_ShouldHandleNullCategoryId() {
        // Arrange
        when(categoryRepository.findByCategoryId(null))
                .thenReturn(Optional.empty());

        // Act & Assert
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            categoryService.delete(null);
        });

        assertEquals("Category not found: null", exception.getMessage());

        verify(categoryRepository).findByCategoryId(null);
        verify(fileUploadService, never()).deleteFile(anyString());
        verify(categoryRepository, never()).delete(any(CategoryEntity.class));
    }

    @Test
    @DisplayName("Should handle empty categoryId in delete method")
    void delete_ShouldHandleEmptyCategoryId() {
        // Arrange
        when(categoryRepository.findByCategoryId(""))
                .thenReturn(Optional.empty());

        // Act & Assert
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            categoryService.delete("");
        });

        assertEquals("Category not found: ", exception.getMessage());

        verify(categoryRepository).findByCategoryId("");
        verify(fileUploadService, never()).deleteFile(anyString());
        verify(categoryRepository, never()).delete(any(CategoryEntity.class));
    }
}
