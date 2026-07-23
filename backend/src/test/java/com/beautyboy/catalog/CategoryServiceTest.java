package com.beautyboy.catalog;

import com.beautyboy.common.BusinessException;
import com.beautyboy.common.ErrorCode;
import com.beautyboy.catalog.dto.CategoryTreeNode;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManagerFactory;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
@Transactional
class CategoryServiceTest {

    @Autowired
    CategoryService categoryService;
    @Autowired
    CategoryRepository categoryRepository;
    @Autowired
    EntityManagerFactory entityManagerFactory;

    @Test
    void 트리가_3계층으로_중첩된다() {
        저장_3계층_픽스처();

        List<CategoryTreeNode> roots = categoryService.tree();

        assertThat(roots).hasSize(1);
        CategoryTreeNode root = roots.get(0);
        assertThat(root.code()).isEqualTo("C001");
        assertThat(root.depth()).isEqualTo(1);
        assertThat(root.children()).hasSize(1);

        CategoryTreeNode mid = root.children().get(0);
        assertThat(mid.code()).isEqualTo("C001001");
        assertThat(mid.depth()).isEqualTo(2);
        assertThat(mid.children()).hasSize(1);

        CategoryTreeNode leaf = mid.children().get(0);
        assertThat(leaf.code()).isEqualTo("C001001001");
        assertThat(leaf.depth()).isEqualTo(3);
        assertThat(leaf.children()).isEmpty();
    }

    @Test
    void 형제_노드는_sort_order_오름차순이다() {
        categoryRepository.save(new Category("C001", null, "스킨케어", 1, 0));
        categoryRepository.save(new Category("C001002", "C001", "토너", 2, 2));
        categoryRepository.save(new Category("C001001", "C001", "클렌저", 2, 1));

        List<CategoryTreeNode> roots = categoryService.tree();

        List<String> midCodes = roots.get(0).children().stream().map(CategoryTreeNode::code).toList();
        assertThat(midCodes).containsExactly("C001001", "C001002");
    }

    @Test
    void 없는_코드로_findLeafPrefix_하면_GOODS_CATEGORY_NOT_FOUND() {
        assertThatThrownBy(() -> categoryService.findLeafPrefix("C999"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.GOODS_CATEGORY_NOT_FOUND);
    }

    @Test
    void 존재하는_leaf_코드는_그대로_접두사로_반환된다() {
        저장_3계층_픽스처();

        String prefix = categoryService.findLeafPrefix("C001001001");

        assertThat(prefix).isEqualTo("C001001001");
    }

    @Test
    void 트리_조립은_전체조회_1회로_끝난다() {
        저장_3계층_픽스처();

        categoryRepository.flush();

        SessionFactory sessionFactory = entityManagerFactory.unwrap(SessionFactory.class);
        Statistics statistics = sessionFactory.getStatistics();
        statistics.clear();

        categoryService.tree();

        assertThat(statistics.getPrepareStatementCount()).isEqualTo(1);
    }

    private void 저장_3계층_픽스처() {
        categoryRepository.save(new Category("C001", null, "스킨케어", 1, 0));
        categoryRepository.save(new Category("C001001", "C001", "토너/스킨", 2, 0));
        categoryRepository.save(new Category("C001001001", "C001001", "토너", 3, 0));
    }
}
