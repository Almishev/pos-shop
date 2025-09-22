package in.bushansirgur.billingsoftware.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import in.bushansirgur.billingsoftware.io.CategoryRequest;
import in.bushansirgur.billingsoftware.io.CategoryResponse;
import in.bushansirgur.billingsoftware.service.CategoryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;

import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(CategoryController.class)
@ActiveProfiles("test")
class CategoryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private CategoryService categoryService;

    @Autowired
    private ObjectMapper objectMapper;

    private CategoryRequest categoryRequest;
    private CategoryResponse categoryResponse;
    private MockMultipartFile multipartFile;

    @BeforeEach
    void setUp() {
        categoryRequest = CategoryRequest.builder()
                .name("Test Electronics")
                .description("Test electronic devices")
                .bgColor("#FF5733")
                .build();

        Timestamp now = Timestamp.valueOf(LocalDateTime.now());
        categoryResponse = CategoryResponse.builder()
                .categoryId("test-category-123")
                .name("Test Electronics")
                .description("Test electronic devices")
                .bgColor("#FF5733")
                .imgUrl("https://example.com/test-image.jpg")
                .createdAt(now)
                .updatedAt(now)
                .items(0)
                .build();

        multipartFile = new MockMultipartFile(
                "file",
                "test-image.jpg",
                "image/jpeg",
                "test image content".getBytes()
        );
    }

    @Test
    @DisplayName("Should create category successfully")
    void shouldCreateCategorySuccessfully() throws Exception {
      
        when(categoryService.add(any(CategoryRequest.class), any(MultipartFile.class)))
                .thenReturn(categoryResponse);

        MockMultipartFile categoryJson = new MockMultipartFile(
                "category",
                "",
                "application/json",
                objectMapper.writeValueAsString(categoryRequest).getBytes()
        );

        // Act & Assert
        mockMvc.perform(multipart("/admin/categories")
                        .file(multipartFile)
                        .file(categoryJson))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.categoryId").value("test-category-123"))
                .andExpect(jsonPath("$.name").value("Test Electronics"))
                .andExpect(jsonPath("$.description").value("Test electronic devices"))
                .andExpect(jsonPath("$.bgColor").value("#FF5733"))
                .andExpect(jsonPath("$.imgUrl").value("https://example.com/test-image.jpg"))
                .andExpect(jsonPath("$.items").value(0));

        verify(categoryService).add(any(CategoryRequest.class), any(MultipartFile.class));
    }

    @Test
    @DisplayName("Should return bad request when JSON parsing fails")
    void shouldReturnBadRequestWhenJsonParsingFails() throws Exception {
        // Arrange
        MockMultipartFile invalidJson = new MockMultipartFile(
                "category",
                "",
                "application/json",
                "invalid json".getBytes()
        );

        // Act & Assert
        mockMvc.perform(multipart("/admin/categories")
                        .file(multipartFile)
                        .file(invalidJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Exception occurred while parsing the json")));

        verify(categoryService, never()).add(any(CategoryRequest.class), any(MultipartFile.class));
    }

    @Test
    @DisplayName("Should return bad request when file processing fails")
    void shouldReturnBadRequestWhenFileProcessingFails() throws Exception {
        // Arrange
        when(categoryService.add(any(CategoryRequest.class), any(MultipartFile.class)))
                .thenThrow(new IOException("File processing failed"));

        MockMultipartFile categoryJson = new MockMultipartFile(
                "category",
                "",
                "application/json",
                objectMapper.writeValueAsString(categoryRequest).getBytes()
        );

        // Act & Assert
        mockMvc.perform(multipart("/admin/categories")
                        .file(multipartFile)
                        .file(categoryJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Error occurred while processing the file")));

        verify(categoryService).add(any(CategoryRequest.class), any(MultipartFile.class));
    }

    @Test
    @DisplayName("Should fetch all categories successfully")
    void shouldFetchAllCategoriesSuccessfully() throws Exception {
        // Arrange
        CategoryResponse categoryResponse2 = CategoryResponse.builder()
                .categoryId("test-category-456")
                .name("Test Books")
                .description("Test books and literature")
                .bgColor("#33FF57")
                .imgUrl("https://example.com/test-image2.jpg")
                .createdAt(Timestamp.valueOf(LocalDateTime.now()))
                .updatedAt(Timestamp.valueOf(LocalDateTime.now()))
                .items(5)
                .build();

        List<CategoryResponse> categories = Arrays.asList(categoryResponse, categoryResponse2);
        when(categoryService.read()).thenReturn(categories);

        // Act & Assert
        mockMvc.perform(get("/categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].categoryId").value("test-category-123"))
                .andExpect(jsonPath("$[0].name").value("Test Electronics"))
                .andExpect(jsonPath("$[1].categoryId").value("test-category-456"))
                .andExpect(jsonPath("$[1].name").value("Test Books"))
                .andExpect(jsonPath("$[1].items").value(5));

        verify(categoryService).read();
    }

    @Test
    @DisplayName("Should return empty list when no categories exist")
    void shouldReturnEmptyListWhenNoCategoriesExist() throws Exception {
        // Arrange
        when(categoryService.read()).thenReturn(Arrays.asList());

        // Act & Assert
        mockMvc.perform(get("/categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());

        verify(categoryService).read();
    }

    @Test
    @DisplayName("Should delete category successfully")
    void shouldDeleteCategorySuccessfully() throws Exception {
        // Arrange
        String categoryId = "test-category-123";
        doNothing().when(categoryService).delete(categoryId);

        // Act & Assert
        mockMvc.perform(delete("/admin/categories/{categoryId}", categoryId))
                .andExpect(status().isNoContent());

        verify(categoryService).delete(categoryId);
    }

    @Test
    @DisplayName("Should return not found when deleting non-existent category")
    void shouldReturnNotFoundWhenDeletingNonExistentCategory() throws Exception {
        // Arrange
        String categoryId = "non-existent-category";
        doThrow(new RuntimeException("Category not found: " + categoryId))
                .when(categoryService).delete(categoryId);

        // Act & Assert
        mockMvc.perform(delete("/admin/categories/{categoryId}", categoryId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Category not found: " + categoryId));

        verify(categoryService).delete(categoryId);
    }

    @Test
    @DisplayName("Should handle null categoryId in delete")
    void shouldHandleNullCategoryIdInDelete() throws Exception {
        // Arrange
        doThrow(new RuntimeException("Category not found: null"))
                .when(categoryService).delete(null);

        // Act & Assert
        mockMvc.perform(delete("/admin/categories/{categoryId}", "null"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Category not found: null"));

        verify(categoryService).delete("null");
    }

    @Test
    @DisplayName("Should handle empty categoryId in delete")
    void shouldHandleEmptyCategoryIdInDelete() throws Exception {
        // Arrange
        doThrow(new RuntimeException("Category not found: "))
                .when(categoryService).delete("");

        // Act & Assert
        mockMvc.perform(delete("/admin/categories/{categoryId}", ""))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Category not found: "));

        verify(categoryService).delete("");
    }

    @Test
    @DisplayName("Should handle missing file in create category")
    void shouldHandleMissingFileInCreateCategory() throws Exception {
        // Arrange
        MockMultipartFile categoryJson = new MockMultipartFile(
                "category",
                "",
                "application/json",
                objectMapper.writeValueAsString(categoryRequest).getBytes()
        );

        // Act & Assert
        mockMvc.perform(multipart("/admin/categories")
                        .file(categoryJson))
                .andExpect(status().isBadRequest());

        verify(categoryService, never()).add(any(CategoryRequest.class), any(MultipartFile.class));
    }

    @Test
    @DisplayName("Should handle missing category data in create category")
    void shouldHandleMissingCategoryDataInCreateCategory() throws Exception {
        // Act & Assert
        mockMvc.perform(multipart("/admin/categories")
                        .file(multipartFile))
                .andExpect(status().isBadRequest());

        verify(categoryService, never()).add(any(CategoryRequest.class), any(MultipartFile.class));
    }
}
