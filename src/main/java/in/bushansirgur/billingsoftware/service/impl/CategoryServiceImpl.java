package in.bushansirgur.billingsoftware.service.impl;

import in.bushansirgur.billingsoftware.entity.CategoryEntity;
import in.bushansirgur.billingsoftware.io.CategoryRequest;
import in.bushansirgur.billingsoftware.io.CategoryResponse;
import in.bushansirgur.billingsoftware.repository.CategoryRepository;
import in.bushansirgur.billingsoftware.repository.ItemRepository;
import in.bushansirgur.billingsoftware.service.CategoryService;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CategoryServiceImpl implements CategoryService {

    private final CategoryRepository categoryRepository;
    private final ItemRepository itemRepository;

    public CategoryResponse add(CategoryRequest request, MultipartFile file) throws IOException {
        CategoryEntity newCategory = convertToEntity(request);
        newCategory.setImgUrl(null);
        newCategory = categoryRepository.save(newCategory);
        return convertToResponse(newCategory);
    }

    @Override
    public CategoryResponse update(String categoryId, CategoryRequest request, MultipartFile file) throws IOException {
        CategoryEntity existing = categoryRepository.findByCategoryId(categoryId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Category not found: " + categoryId));
        existing.setName(request.getName());
        existing.setDescription(request.getDescription());
        existing.setBgColor(request.getBgColor());
        existing.setImgUrl(null);
        return convertToResponse(categoryRepository.save(existing));
    }

    @Override
    public List<CategoryResponse> read() {
        return categoryRepository.findAll()
                .stream()
                .map(categoryEntity -> convertToResponse(categoryEntity))
                .collect(Collectors.toList());
    }

    @Override
    public void delete(String categoryId) {
        CategoryEntity existingCategory = categoryRepository.findByCategoryId(categoryId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Category not found: " + categoryId));
        Integer itemsCount = itemRepository.countByCategoryId(existingCategory.getId());
        if (itemsCount != null && itemsCount > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Cannot delete category with existing items (" + itemsCount + ")");
        }
        categoryRepository.delete(existingCategory);
    }

    private CategoryResponse convertToResponse(CategoryEntity newCategory) {
        Integer itemsCount = itemRepository.countByCategoryId(newCategory.getId());
        return CategoryResponse.builder()
                .categoryId(newCategory.getCategoryId())
                .name(newCategory.getName())
                .description(newCategory.getDescription())
                .bgColor(newCategory.getBgColor())
                .imgUrl(newCategory.getImgUrl())
                .createdAt(newCategory.getCreatedAt())
                .updatedAt(newCategory.getUpdatedAt())
                .items(itemsCount)
                .build();
    }

    private CategoryEntity convertToEntity(CategoryRequest request) {
        return CategoryEntity.builder()
                .categoryId(UUID.randomUUID().toString())
                .name(request.getName())
                .description(request.getDescription())
                .bgColor(request.getBgColor())
                .build();
    }
}
