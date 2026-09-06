package com.beautyboy.routine;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * v3 개인화 모델 파일 두 개(아이템 임베딩, 사용자 탑)를 배포한다(설계 §11). 배포 파이프라인이
 * {@code classpath:model/<version>/} 아래에 떨어뜨린 파일을 기동 시 한 번 읽어 캐싱한다. 이
 * 엔드포인트는 루틴 페이지 진입마다 전 사용자가 부르므로 요청마다 파일을 다시 읽지 않는다
 * ({@code FlowRuleService}와 같은 이유다).
 *
 * <p>모델 디렉터리가 아예 없어도, 또는 두 파일의 {@code version}이 서로 달라도 기동은 그대로
 * 성공한다. 이때 {@link #available()}은 false이고 컨트롤러가 404를 준다. 브라우저는 그 상태를
 * v2 규칙 기반 추천으로의 폴백 신호로 쓴다(설계 §11).
 */
@Service
public class ModelFileService {

    private static final Logger log = LoggerFactory.getLogger(ModelFileService.class);
    private static final String ITEM_EMBEDDINGS_PATTERN = "classpath*:model/*/item_embeddings.json";

    private final ObjectMapper objectMapper;

    /** 기동 시 한 번 쓰고 이후 읽기만 한다. 모델이 없으면 null로 남는다. */
    private volatile ModelFiles cached;

    public ModelFileService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    void loadModel() {
        String version = latestVersion();
        if (version == null) {
            log.info("모델 디렉터리가 없다. 모델 배포 API는 404를 준다.");
            return;
        }
        try {
            JsonNode itemEmbeddings = readJson(version, "item_embeddings.json");
            JsonNode userTower = readJson(version, "user_tower.json");
            String itemVersion = itemEmbeddings.path("version").asText(null);
            String towerVersion = userTower.path("version").asText(null);
            if (itemVersion == null || !itemVersion.equals(towerVersion)) {
                log.warn("모델 파일 두 개의 version이 다르다 (item={}, userTower={}). 모델 없음 상태로 둔다.",
                        itemVersion, towerVersion);
                return;
            }
            this.cached = new ModelFiles(itemVersion, itemEmbeddings, userTower);
        } catch (IOException e) {
            log.warn("모델 파일을 읽는 데 실패했다 (version={}). 모델 없음 상태로 둔다.", version, e);
        }
    }

    public boolean available() {
        return cached != null;
    }

    public String version() {
        return cached.version();
    }

    public JsonNode itemEmbeddings() {
        return cached.itemEmbeddings();
    }

    public JsonNode userTower() {
        return cached.userTower();
    }

    private JsonNode readJson(String version, String fileName) throws IOException {
        ClassPathResource resource = new ClassPathResource("model/" + version + "/" + fileName);
        return objectMapper.readTree(resource.getInputStream());
    }

    /** 디렉터리가 여럿이면 점 뒤를 정수로 비교해 가장 큰 버전을 고른다(v3.2가 v3.10보다 크게 되지 않게). */
    private String latestVersion() {
        List<String> versions = discoverVersions();
        String latest = null;
        for (String version : versions) {
            if (!hasUserTower(version)) {
                continue;
            }
            if (latest == null || compareVersions(version, latest) > 0) {
                latest = version;
            }
        }
        return latest;
    }

    private List<String> discoverVersions() {
        List<String> versions = new ArrayList<>();
        try {
            PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
            Resource[] resources = resolver.getResources(ITEM_EMBEDDINGS_PATTERN);
            for (Resource resource : resources) {
                String path = resource.getURL().getPath();
                String[] segments = path.split("/");
                if (segments.length >= 2) {
                    versions.add(segments[segments.length - 2]);
                }
            }
        } catch (IOException e) {
            log.info("모델 디렉터리를 찾지 못했다.", e);
        }
        return versions;
    }

    private boolean hasUserTower(String version) {
        return new ClassPathResource("model/" + version + "/user_tower.json").exists();
    }

    /** {@code v3.2}가 {@code v3.10}보다 크게 나오지 않도록, 점으로 나눈 조각을 정수로 비교한다. */
    static int compareVersions(String a, String b) {
        int[] partsA = versionParts(a);
        int[] partsB = versionParts(b);
        int length = Math.max(partsA.length, partsB.length);
        for (int i = 0; i < length; i++) {
            int va = i < partsA.length ? partsA[i] : 0;
            int vb = i < partsB.length ? partsB[i] : 0;
            if (va != vb) {
                return Integer.compare(va, vb);
            }
        }
        return 0;
    }

    private static int[] versionParts(String version) {
        String stripped = version.startsWith("v") ? version.substring(1) : version;
        String[] tokens = stripped.split("\\.");
        int[] result = new int[tokens.length];
        for (int i = 0; i < tokens.length; i++) {
            result[i] = parseIntSafe(tokens[i]);
        }
        return result;
    }

    private static int parseIntSafe(String token) {
        try {
            return Integer.parseInt(token);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private record ModelFiles(String version, JsonNode itemEmbeddings, JsonNode userTower) {
    }
}
