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
import org.springframework.http.HttpStatus;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CategoryServiceTest {

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private ItemRepository itemRepository;

    @Mock
    private MultipartFile multipartFile;

    @InjectMocks
    private CategoryServiceImpl categoryService;

    private CategoryRequest categoryRequest;
    private CategoryEntity categoryEntity;

    @BeforeEach
    void setUp() {
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
                .imgUrl(null)
                .createdAt(now)
                .updatedAt(now)
                .build();
    }

    @Test
    @DisplayName("Should successfully add a new category")
    void add_ShouldAddNewCategory_Successfully() throws IOException {
        when(categoryRepository.save(any(CategoryEntity.class)))
                .thenReturn(categoryEntity);
        when(itemRepository.countByCategoryId(anyLong()))
                .thenReturn(0);

        CategoryResponse result = categoryService.add(categoryRequest, multipartFile);

        assertNotNull(result);
        assertEquals("test-category-id", result.getCategoryId());
        assertEquals("Electronics", result.getName());
        assertNull(result.getImgUrl());

        verify(categoryRepository).save(any(CategoryEntity.class));
        verify(itemRepository).countByCategoryId(anyLong());
    }

    @Test
    @DisplayName("Should return all categories when reading")
    void read_ShouldReturnAllCategories() {
        CategoryEntity categoryEntity2 = CategoryEntity.builder()
                .id(2L)
                .categoryId("test-category-id-2")
                .name("Books")
                .description("Books and literature")
                .bgColor("#33FF57")
                .imgUrl(null)
                .createdAt(Timestamp.valueOf(LocalDateTime.now()))
                .updatedAt(Timestamp.valueOf(LocalDateTime.now()))
                .build();

        List<CategoryEntity> categoryEntities = Arrays.asList(categoryEntity, categoryEntity2);
        when(categoryRepository.findAll()).thenReturn(categoryEntities);
        when(itemRepository.countByCategoryId(1L)).thenReturn(5);
        when(itemRepository.countByCategoryId(2L)).thenReturn(3);

        List<CategoryResponse> result = categoryService.read();

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
        when(categoryRepository.findAll()).thenReturn(Arrays.asList());

        List<CategoryResponse> result = categoryService.read();
        
        assertNotNull(result);
        assertTrue(result.isEmpty());

        verify(categoryRepository).findAll();
        verify(itemRepository, never()).countByCategoryId(anyLong());
    }

    @Test
    @DisplayName("Should successfully delete existing empty category")
    void delete_ShouldDeleteCategory_Successfully() {
        String categoryId = "test-category-id";
        when(categoryRepository.findByCategoryId(categoryId))
                .thenReturn(Optional.of(categoryEntity));
        when(itemRepository.countByCategoryId(1L)).thenReturn(0);

        categoryService.delete(categoryId);

        verify(categoryRepository).findByCategoryId(categoryId);
        verify(categoryRepository).delete(categoryEntity);
    }

    @Test
    @DisplayName("Should reject delete when category has items")
    void delete_ShouldReject_WhenCategoryHasItems() {
        String categoryId = "test-category-id";
        when(categoryRepository.findByCategoryId(categoryId))
                .thenReturn(Optional.of(categoryEntity));
        when(itemRepository.countByCategoryId(1L)).thenReturn(3);

        assertThrows(ResponseStatusException.class, () -> categoryService.delete(categoryId));
        verify(categoryRepository, never()).delete(any(CategoryEntity.class));
    }

    @Test
    @DisplayName("Should throw ResponseStatusException when category not found for deletion")
    void delete_ShouldThrowResponseStatusException_WhenCategoryNotFound() {
        String categoryId = "non-existent-category-id";
        when(categoryRepository.findByCategoryId(categoryId))
                .thenReturn(Optional.empty());

        ResponseStatusException exception = assertThrows(ResponseStatusException.class, () -> {
            categoryService.delete(categoryId);
        });

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
        assertEquals("Category not found: " + categoryId, exception.getReason());
        verify(categoryRepository, never()).delete(any(CategoryEntity.class));
    }
}
