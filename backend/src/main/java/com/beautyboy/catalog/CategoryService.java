package com.beautyboy.catalog;

import com.beautyboy.catalog.dto.CategoryTreeNode;
import com.beautyboy.common.BusinessException;
import com.beautyboy.common.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class CategoryService {

    private final CategoryRepository categoryRepository;

    public CategoryService(CategoryRepository categoryRepository) {
        this.categoryRepository = categoryRepository;
    }

    /**
     * 전체 카테고리를 1회 조회한 뒤 parentCode 기준으로 묶어 메모리에서 트리를 조립한다.
     * 카테고리는 수십 개뿐이라 재귀 쿼리보다 이 방식이 단순하고 N+1도 발생하지 않는다.
     */
    @Transactional(readOnly = true)
    public List<CategoryTreeNode> tree() {
        List<Category> all = categoryRepository.findAll();
        Map<String, List<Category>> childrenByParent = all.stream()
                .filter(c -> c.getParentCode() != null)
                .collect(Collectors.groupingBy(Category::getParentCode));

        return all.stream()
                .filter(c -> c.getParentCode() == null)
                .sorted(Comparator.comparingInt(Category::getSortOrder))
                .map(root -> toNode(root, childrenByParent))
                .toList();
    }

    private CategoryTreeNode toNode(Category category, Map<String, List<Category>> childrenByParent) {
        List<CategoryTreeNode> children = childrenByParent
                .getOrDefault(category.getCode(), List.of())
                .stream()
                .sorted(Comparator.comparingInt(Category::getSortOrder))
                .map(child -> toNode(child, childrenByParent))
                .toList();
        return new CategoryTreeNode(category.getCode(), category.getName(), category.getDepth(), children);
    }

    /**
     * 상품 목록 조회가 category_code LIKE '{prefix}%'에 쓸 접두사를 돌려준다.
     * 코드 자체가 곧 접두사이므로(계층 인코딩), 존재 여부만 확인하고 그대로 반환한다.
     */
    @Transactional(readOnly = true)
    public String findLeafPrefix(String code) {
        Category category = categoryRepository.findById(code)
                .orElseThrow(() -> new BusinessException(ErrorCode.GOODS_CATEGORY_NOT_FOUND));
        return category.getCode();
    }
}
