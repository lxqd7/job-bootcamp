package com.jobbootcamp.course.application;

import com.jobbootcamp.common.error.BusinessException;
import com.jobbootcamp.common.error.ErrorCode;
import com.jobbootcamp.course.domain.Category;
import com.jobbootcamp.course.infrastructure.CategoryClosureMapper;
import com.jobbootcamp.course.infrastructure.CategoryMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class CategoryClosureService {

    private final CategoryClosureMapper closureMapper;
    private final CategoryMapper categoryMapper;

    public CategoryClosureService(CategoryClosureMapper closureMapper, CategoryMapper categoryMapper) {
        this.closureMapper = closureMapper;
        this.categoryMapper = categoryMapper;
    }

    @Transactional
    public void rebuildAll() {
        closureMapper.deleteAll();
        List<Category> categories = categoryMapper.selectList(null);
        Map<Long, Category> byId = new HashMap<>();
        for (Category category : categories) {
            byId.put(category.getId(), category);
        }
        for (Category category : categories) {
            closureMapper.insertRow(category.getId(), category.getId(), 0);
            Set<Long> visited = new HashSet<>();
            visited.add(category.getId());
            Long parentId = category.getParentId();
            int depth = 1;
            while (parentId != null && parentId != 0) {
                Category parent = byId.get(parentId);
                if (parent == null) {
                    break;
                }
                if (!visited.add(parent.getId()) || depth > 100) {
                    throw new BusinessException(ErrorCode.DATA_INTEGRITY, "分类层级存在环");
                }
                closureMapper.insertRow(parent.getId(), category.getId(), depth);
                parentId = parent.getParentId();
                depth++;
            }
        }
    }

    public List<Long> descendantIds(Long ancestorId) {
        return closureMapper.selectDescendantIds(ancestorId);
    }

    public boolean isDescendant(Long ancestorId, Long descendantId) {
        return closureMapper.countRelation(ancestorId, descendantId) > 0;
    }
}