package com.beautyboy.catalog;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class GoodsSnapshotBatchTest {

    @Autowired
    GoodsQueryService goodsQueryService;
    @Autowired
    BrandRepository brandRepository;
    @Autowired
    GoodsRepository goodsRepository;

    @Test
    @DisplayName("배치 조회는 요청한 키 그대로 돌려준다 — 응답의 optionId로는 요청을 되찾을 수 없다")
    void 요청한_키로_돌려준다() {
        Goods goods = 상품_저장("토너", 20000, 16000);
        옵션_저장(goods, "300ml", 3000, 40, 2);
        Long 대표옵션_id = 옵션_저장(goods, "200ml", 0, 150, 1);

        GoodsQueryService.OrderSnapshotKey key = new GoodsQueryService.OrderSnapshotKey(goods.getId(), null);

        Map<GoodsQueryService.OrderSnapshotKey, GoodsQueryService.OrderGoodsSnapshot> result =
                goodsQueryService.findOrderSnapshots(List.of(key));

        assertThat(result).containsOnlyKeys(new GoodsQueryService.OrderSnapshotKey(goods.getId(), null));
        assertThat(result.get(key).optionId()).isEqualTo(대표옵션_id);
    }

    @Test
    @DisplayName("optionNo가 null이면 배치도 대표 옵션(sortOrder 최소, 동률이면 id 최소)으로 채운다")
    void null_옵션은_대표_옵션으로() {
        Goods goods = 상품_저장("토너", 20000, 16000);
        옵션_저장(goods, "300ml", 3000, 40, 2);
        옵션_저장(goods, "200ml", 0, 150, 1);

        GoodsQueryService.OrderSnapshotKey key = new GoodsQueryService.OrderSnapshotKey(goods.getId(), null);

        Map<GoodsQueryService.OrderSnapshotKey, GoodsQueryService.OrderGoodsSnapshot> result =
                goodsQueryService.findOrderSnapshots(List.of(key));

        GoodsQueryService.OrderGoodsSnapshot snapshot = result.get(key);
        assertThat(snapshot.optionName()).isEqualTo("200ml");
        assertThat(snapshot.unitPrice()).isEqualTo(16000);
        assertThat(snapshot.stock()).isEqualTo(150);
    }

    @Test
    @DisplayName("숨김 상품은 맵에 키가 없다 — 예외를 던지지 않는다(단건의 Optional.empty와 같은 계약)")
    void 숨김_상품은_키가_없다() {
        Goods 숨김상품 = 상품_저장("단종 상품", 10000, 10000);
        숨김상품.hide();
        goodsRepository.save(숨김상품);
        Goods 정상상품 = 상품_저장("정상 상품", 10000, 10000);

        GoodsQueryService.OrderSnapshotKey 숨김키 = new GoodsQueryService.OrderSnapshotKey(숨김상품.getId(), null);
        GoodsQueryService.OrderSnapshotKey 정상키 = new GoodsQueryService.OrderSnapshotKey(정상상품.getId(), null);

        Map<GoodsQueryService.OrderSnapshotKey, GoodsQueryService.OrderGoodsSnapshot> result =
                goodsQueryService.findOrderSnapshots(List.of(숨김키, 정상키));

        assertThat(result).doesNotContainKey(숨김키);
        assertThat(result).containsKey(정상키);
    }

    @Test
    @DisplayName("그 상품의 것이 아닌 옵션을 붙인 키는 맵에 없다")
    void 남의_옵션은_키가_없다() {
        Goods 상품A = 상품_저장("상품A", 10000, 10000);
        Goods 상품B = 상품_저장("상품B", 90000, 90000);
        Long 상품B의_옵션 = 옵션_저장(상품B, "옵션", 0, 5, 0);

        GoodsQueryService.OrderSnapshotKey key = new GoodsQueryService.OrderSnapshotKey(상품A.getId(), 상품B의_옵션);

        Map<GoodsQueryService.OrderSnapshotKey, GoodsQueryService.OrderGoodsSnapshot> result =
                goodsQueryService.findOrderSnapshots(List.of(key));

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("같은 상품의 서로 다른 옵션 두 키가 각각 자기 옵션으로 해석된다")
    void 같은_상품_다른_옵션() {
        Goods goods = 상품_저장("토너", 20000, 16000);
        Long 옵션_200ml_id = 옵션_저장(goods, "200ml", 0, 150, 1);
        Long 옵션_300ml_id = 옵션_저장(goods, "300ml", 3000, 40, 2);

        GoodsQueryService.OrderSnapshotKey 키_200ml = new GoodsQueryService.OrderSnapshotKey(goods.getId(), 옵션_200ml_id);
        GoodsQueryService.OrderSnapshotKey 키_300ml = new GoodsQueryService.OrderSnapshotKey(goods.getId(), 옵션_300ml_id);

        Map<GoodsQueryService.OrderSnapshotKey, GoodsQueryService.OrderGoodsSnapshot> result =
                goodsQueryService.findOrderSnapshots(List.of(키_200ml, 키_300ml));

        assertThat(result).hasSize(2);
        assertThat(result.get(키_200ml).unitPrice()).isNotEqualTo(result.get(키_300ml).unitPrice());
    }

    @Test
    @DisplayName("빈 입력은 빈 맵 — 쿼리를 내지 않는다")
    void 빈_입력() {
        assertThat(goodsQueryService.findOrderSnapshots(List.of())).isEmpty();
    }

    @Test
    @DisplayName("단건 조회와 배치 조회의 결과가 같다 — 해석 로직이 두 벌로 갈라지지 않았다는 증거")
    void 단건과_배치가_같다() {
        Goods 옵션있는_상품 = 상품_저장("선크림", 30000, 24000);
        Long 옵션id = 옵션_저장(옵션있는_상품, "50ml 대용량", 3000, 7, 0);
        Goods 옵션없는_상품 = 상품_저장("옵션 없는 상품", 20000, 16000);

        GoodsQueryService.OrderSnapshotKey 키_옵션지정 = new GoodsQueryService.OrderSnapshotKey(옵션있는_상품.getId(), 옵션id);
        GoodsQueryService.OrderSnapshotKey 키_옵션null = new GoodsQueryService.OrderSnapshotKey(옵션있는_상품.getId(), null);
        GoodsQueryService.OrderSnapshotKey 키_옵션없음 = new GoodsQueryService.OrderSnapshotKey(옵션없는_상품.getId(), null);

        List<GoodsQueryService.OrderSnapshotKey> keys = List.of(키_옵션지정, 키_옵션null, 키_옵션없음);
        Map<GoodsQueryService.OrderSnapshotKey, GoodsQueryService.OrderGoodsSnapshot> 배치결과 =
                goodsQueryService.findOrderSnapshots(keys);

        for (GoodsQueryService.OrderSnapshotKey key : keys) {
            assertThat(배치결과.get(key))
                    .isEqualTo(goodsQueryService.findOrderSnapshot(key.goodsNo(), key.optionNo()).orElseThrow());
        }
    }

    private Goods 상품_저장(String name, int listPrice, int salePrice) {
        Brand brand = brandRepository.save(new Brand("브랜드" + System.nanoTime(), null));
        return goodsRepository.save(
                new Goods(brand, "C001001001", name, null, "https://img/x.jpg", listPrice, salePrice));
    }

    private Long 옵션_저장(Goods goods, String name, int addPrice, int stock, int sortOrder) {
        GoodsOption option = new GoodsOption(goods, name, addPrice, stock, sortOrder);
        goods.getOptions().add(option);
        Goods saved = goodsRepository.save(goods);
        goodsRepository.flush();
        return saved.getOptions().stream()
                .filter(o -> o.getName().equals(name))
                .map(GoodsOption::getId)
                .findFirst()
                .orElseThrow();
    }
}
