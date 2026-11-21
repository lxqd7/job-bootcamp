package com.jobbootcamp.course.application;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.jobbootcamp.common.error.BusinessException;
import com.jobbootcamp.common.error.ErrorCode;
import com.jobbootcamp.common.web.ErrorData;
import com.jobbootcamp.course.domain.Category;
import com.jobbootcamp.course.domain.dto.CategoryCreateRequest;
import com.jobbootcamp.course.domain.dto.CategoryItem;
import com.jobbootcamp.course.domain.dto.CategoryTreeNode;
import com.jobbootcamp.course.domain.dto.CategoryUpdateRequest;
import com.jobbootcamp.course.infrastructure.CategoryMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 分类树以闭包表支持子树检索。任何父子关系变更后重建闭包，避免只更新直接父子导致后代丢失。
 */
@Service
public class CategoryService {

    private final CategoryMapper categoryMapper;
    private final CategoryClosureService closureService;

    public CategoryService(CategoryMapper categoryMapper, CategoryClosureService closureService) {
        this.categoryMapper = categoryMapper;
        this.closureService = closureService;
    }

    public List<CategoryNode> tree() {
        List<Category> categories = categoryMapper.selectList(Wrappers.<Category>lambdaQuery()
                .eq(Category::getEnabled, true)
                .orderByAsc(Category::getSort)
                .orderByAsc(Category::getId));
        return CategoryTreeBuilder.build(categories);
    }

    public List<CategoryTreeNode> adminTree() {
        List<Category> categories = categoryMapper.selectList(Wrappers.<Category>lambdaQuery()
                .orderByAsc(Category::getSort)
                .orderByAsc(Category::getId));
        Map<Long, List<Category>> byParent = new LinkedHashMap<>();
        for (Category category : categories) {
            byParent.computeIfAbsent(category.getParentId(), key -> new ArrayList<>()).add(category);
        }
        return buildAdminChildren(0L, byParent);
    }

    @Transactional
    public CategoryItem create(CategoryCreateRequest request) {
        long parentId = request.parentId() == null ? 0L : request.parentId();
        requireParent(parentId);
        LocalDateTime now = utcNow();
        Category category = new Category();
        category.setParentId(parentId);
        category.setName(request.name().trim());
        category.setSort(request.sort() == null ? 0 : request.sort());
        category.setEnabled(request.enabled() == null || request.enabled());
        category.setCreatedAt(now);
        category.setUpdatedAt(now);
        category.setRevision(0L);
        categoryMapper.insert(category);
        closureService.rebuildAll();
        return toItem(category);
    }

    @Transactional
    public CategoryItem update(Long id, CategoryUpdateRequest request) {
        Category current = categoryMapper.selectById(id);
        if (current == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "分类不存在");
        }
        long parentId = request.parentId() == null ? current.getParentId() : request.parentId();
        requireParent(parentId);
        if (parentId == id || (parentId != 0 && closureService.isDescendant(id, parentId))) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "不能把分类移动到自身或子分类下");
        }
        LocalDateTime now = utcNow();
        int updated = categoryMapper.update(null, Wrappers.<Category>lambdaUpdate()
                .set(Category::getName, request.name().trim())
                .set(Category::getParentId, parentId)
                .set(Category::getSort, request.sort() == null ? current.getSort() : request.sort())
                .set(Category::getEnabled, request.enabled() == null ? current.getEnabled() : request.enabled())
                .set(Category::getUpdatedAt, now)
                .setSql("revision = revision + 1")
                .eq(Category::getId, id)
                .eq(Category::getRevision, request.revision()));
        if (updated == 0) {
            Long currentRevision = categoryMapper.selectById(id).getRevision();
            throw new BusinessException(ErrorCode.CONFLICT, "分类已在其他位置更新，请刷新后重试",
                    new ErrorData(null, currentRevision, null));
        }
        closureService.rebuildAll();
        return toItem(categoryMapper.selectById(id));
    }

    public boolean exists(Long id) {
        return id != null && categoryMapper.selectById(id) != null;
    }

    public List<Long> descendantIds(Long categoryId) {
        if (categoryId == null) {
            return List.of();
        }
        return closureService.descendantIds(categoryId);
    }

    public CategoryItem item(Long id) {
        Category category = categoryMapper.selectById(id);
        if (category == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "分类不存在");
        }
        return toItem(category);
    }

    private void requireParent(long parentId) {
        if (parentId != 0 && categoryMapper.selectById(parentId) == null) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "父分类不存在");
        }
    }

    private List<CategoryTreeNode> buildAdminChildren(Long parentId, Map<Long, List<Category>> byParent) {
        List<CategoryTreeNode> nodes = new ArrayList<>();
        for (Category category : byParent.getOrDefault(parentId, List.of())) {
            nodes.add(new CategoryTreeNode(
                    String.valueOf(category.getId()),
                    category.getName(),
                    String.valueOf(category.getParentId()),
                    category.getSort() == null ? 0 : category.getSort(),
                    Boolean.TRUE.equals(category.getEnabled()),
                    category.getRevision() == null ? 0 : category.getRevision(),
                    buildAdminChildren(category.getId(), byParent)));
        }
        return nodes;
    }

    private CategoryItem toItem(Category category) {
        return new CategoryItem(
                String.valueOf(category.getId()),
                category.getName(),
                String.valueOf(category.getParentId()),
                category.getSort() == null ? 0 : category.getSort(),
                Boolean.TRUE.equals(category.getEnabled()),
                category.getRevision() == null ? 0 : category.getRevision());
    }

    private LocalDateTime utcNow() {
        return LocalDateTime.now(ZoneOffset.UTC);
    }
}