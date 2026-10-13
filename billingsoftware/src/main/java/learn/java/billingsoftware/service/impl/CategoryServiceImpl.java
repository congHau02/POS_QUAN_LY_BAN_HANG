package learn.java.billingsoftware.service.impl;

import learn.java.billingsoftware.entity.CategoryEntity;
import learn.java.billingsoftware.io.CategoryRequest;
import learn.java.billingsoftware.io.CategoryResponse;
import learn.java.billingsoftware.repository.CategoryRepository;
import learn.java.billingsoftware.repository.ItemRepository;
import learn.java.billingsoftware.service.ActivityLogService;
import learn.java.billingsoftware.service.CategoryService;
import learn.java.billingsoftware.service.FileUploadService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CategoryServiceImpl implements CategoryService {
    private final CategoryRepository categoryRepository;
    private final FileUploadService fileUploadService;
    private final ItemRepository itemRepository;
    private final ActivityLogService activityLogService;

    @Override
    public CategoryResponse add(CategoryRequest request, MultipartFile file) throws IOException {
        String imgUrl = fileUploadService.uploadFile(file);
        CategoryEntity newCategory = convertToEntity(request);
        newCategory.setImgUrl(imgUrl);
        newCategory = categoryRepository.save(newCategory);
        activityLogService.logActivity("CREATE", "CATEGORY", newCategory.getCategoryId(), "Created category: " + newCategory.getName());
        return convertToResponse(newCategory);
    }

    @Override
    public CategoryResponse create(CategoryRequest request) {
        CategoryEntity newCategory = convertToEntity(request);
        newCategory = categoryRepository.save(newCategory);
        activityLogService.logActivity("CREATE", "CATEGORY", newCategory.getCategoryId(), "Created category: " + newCategory.getName());
        return convertToResponse(newCategory);
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
        boolean isFileDelete = true;
        if (existingCategory.getImgUrl() != null && !existingCategory.getImgUrl().trim().isEmpty()) {
            isFileDelete = fileUploadService.deleteFile(existingCategory.getImgUrl());
        }
        if(isFileDelete){
            categoryRepository.delete(existingCategory);
            activityLogService.logActivity("DELETE", "CATEGORY", existingCategory.getCategoryId(), "Deleted category: " + existingCategory.getName());
        } else {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Unable to delete image item");
        }
    }

    @Override
    public CategoryResponse update(String categoryId, CategoryRequest request, MultipartFile file) throws IOException {
        CategoryEntity existingCategory = categoryRepository.findByCategoryId(categoryId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Category not found: " + categoryId));

        if (request.getName() != null && !request.getName().trim().isEmpty()) {
            existingCategory.setName(request.getName().trim());
        }
        if (request.getDescription() != null) {
            existingCategory.setDescription(request.getDescription());
        }
        if (request.getBgColor() != null) {
            existingCategory.setBgColor(request.getBgColor());
        }

        if (file != null && !file.isEmpty()) {
            if (existingCategory.getImgUrl() != null && !existingCategory.getImgUrl().trim().isEmpty()) {
                try {
                    fileUploadService.deleteFile(existingCategory.getImgUrl());
                } catch (Exception ignored) {
                }
            }
            String newImgUrl = fileUploadService.uploadFile(file);
            existingCategory.setImgUrl(newImgUrl);
        }

        existingCategory = categoryRepository.save(existingCategory);
        activityLogService.logActivity("UPDATE", "CATEGORY", existingCategory.getCategoryId(), "Updated category: " + existingCategory.getName());
        return convertToResponse(existingCategory);
    }

    private CategoryResponse convertToResponse(CategoryEntity newCategory) {
        Integer itemCount = itemRepository.countByCategoryId(newCategory.getId());
        return CategoryResponse.builder()
                .categoryId(newCategory.getCategoryId())
                .name(newCategory.getName())
                .description(newCategory.getDescription())
                .bgColor(newCategory.getBgColor())
                .imgUrl(newCategory.getImgUrl())
                .createdAt(newCategory.getCreatedAt())
                .updatedAt(newCategory.getUpdatedAt())
                .items(itemCount)
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
