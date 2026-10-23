package com.example.mymangaapp.mymangaapp.service;

import com.example.mymangaapp.mymangaapp.dto.category.request.CategoryRequest;
import com.example.mymangaapp.mymangaapp.dto.category.response.CategoryResponse;
import com.example.mymangaapp.mymangaapp.entity.Category;
import com.example.mymangaapp.mymangaapp.exception.AppException;
import com.example.mymangaapp.mymangaapp.exception.ResponseCode;
import com.example.mymangaapp.mymangaapp.mapper.CategoryMapper;
import com.example.mymangaapp.mymangaapp.repository.CategoryRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j @Transactional(readOnly = true)
public class CategoryService {

    CategoryRepository categoryRepository;
    CategoryMapper categoryMapper;


    // ---------------------------------chức năng public (cho khách) --------------------------------- //

    // public endpoint
    public List<CategoryResponse> getAllCategories() {
        return categoryRepository.findAll().stream()
                .map(categoryMapper::toCategoryResponse)
                .toList();
    }

    // public
    public CategoryResponse getCategoryById(@NonNull Long id) {
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new AppException(ResponseCode.CATEGORY_NOT_FOUND));

        return categoryMapper.toCategoryResponse(category);
    }


    // ---------------------------------chức năng của admin ---------------------------------

    // admin
    @Transactional
    public CategoryResponse createCategory(@NonNull CategoryRequest request) {
        if (categoryRepository.existsByName(request.getName().trim())) {
            throw new AppException(ResponseCode.CATEGORY_NAME_ALREADY_EXISTED);
        }

        Category category = categoryMapper.toCategory(request);
        category.setName(request.getName().trim());

        return categoryMapper.toCategoryResponse(categoryRepository.save(category));
    }

    // admin
    @Transactional
    public CategoryResponse updateCategoryById(@NonNull Long id, @NonNull CategoryRequest request) {
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new AppException(ResponseCode.CATEGORY_NOT_FOUND));

        if (request.getName() != null && !request.getName().trim().equalsIgnoreCase(category.getName())
                && categoryRepository.existsByName(request.getName().trim())) {
            throw new AppException(ResponseCode.CATEGORY_NAME_ALREADY_EXISTED);
        }

        categoryMapper.updateCategoryFromRequest(category, request);
        if (request.getName() != null) {
            category.setName(request.getName().trim());
        }

        return categoryMapper.toCategoryResponse(categoryRepository.save(category));
    }

    // admin
    @Transactional
    public void deleteCategoryById(@NonNull Long id) {
        if (!categoryRepository.existsById(id)) {
            throw new AppException(ResponseCode.CATEGORY_NOT_FOUND);
        }

        categoryRepository.deleteById(id);
    }

}
