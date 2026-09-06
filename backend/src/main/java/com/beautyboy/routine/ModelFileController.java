package com.beautyboy.routine;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.util.function.Supplier;

/**
 * v3 개인화 모델 파일 두 개를 그대로 서빙한다(설계 §11). {@code ModelFileService}가 기동 시
 * 캐싱해 둔 파일 내용을 진입마다 그대로 돌려주고, {@code If-None-Match}가 캐시된 version과
 * 같으면 본문 없이 304로 끊는다. {@code unquote} 헬퍼까지 {@code FlowRuleController}의 ETag
 * 패턴을 그대로 옮겼다(약한 검증자 {@code W/"..."}와 따옴표 없는 값을 같게 접는다).
 *
 * <p><b>응답 본문을 {@code ApiResponse}로 감싸지 않는다.</b> 이 두 파일은 스키마가 이미
 * Wave A에서 고정된 계약이고, 브라우저가 받은 JSON을 그대로 JSON Schema로 검증한다. 봉투를
 * 씌우면 클라이언트가 벗기는 코드를 따로 들고 있어야 하고 스키마도 두 겹이 된다.
 *
 * <p>모델 디렉터리가 없거나 두 파일의 version이 서로 어긋나면 {@code ModelFileService}가
 * "모델 없음" 상태로 남고, 이 컨트롤러는 두 엔드포인트 모두 404를 준다. 서버 기동 자체는
 * 그 상태에서도 정상이다.
 *
 * <p>ETag 헤더는 RFC 9110대로 따옴표에 싸여 나간다({@code FlowRuleController}와 같은 이유로
 * Spring이 감싼다). 클라이언트가 되돌려 보내는 값은 따옴표가 있든 없든 {@code unquote}로 같게
 * 본다.
 */
@RestController
public class ModelFileController {

    private final ModelFileService modelFileService;

    public ModelFileController(ModelFileService modelFileService) {
        this.modelFileService = modelFileService;
    }

    @GetMapping("/api/v1/model/items")
    public ResponseEntity<JsonNode> items(
            @RequestHeader(value = HttpHeaders.IF_NONE_MATCH, required = false) String ifNoneMatch) {
        return respond(ifNoneMatch, modelFileService::itemEmbeddings);
    }

    @GetMapping("/api/v1/model/user-tower")
    public ResponseEntity<JsonNode> userTower(
            @RequestHeader(value = HttpHeaders.IF_NONE_MATCH, required = false) String ifNoneMatch) {
        return respond(ifNoneMatch, modelFileService::userTower);
    }

    private ResponseEntity<JsonNode> respond(String ifNoneMatch, Supplier<JsonNode> body) {
        if (!modelFileService.available()) {
            return ResponseEntity.notFound().build();
        }
        String version = modelFileService.version();
        if (version.equals(unquote(ifNoneMatch))) {
            return ResponseEntity.status(HttpStatus.NOT_MODIFIED).build();
        }
        return ResponseEntity.ok()
                .header(HttpHeaders.ETAG, version)
                .body(body.get());
    }

    /** {@code "abc"}·{@code W/"abc"}·{@code abc}를 모두 {@code abc}로 접는다. */
    private static String unquote(String etag) {
        if (etag == null) {
            return null;
        }
        String trimmed = etag.trim();
        if (trimmed.startsWith("W/")) {
            trimmed = trimmed.substring(2);
        }
        if (trimmed.length() >= 2 && trimmed.startsWith("\"") && trimmed.endsWith("\"")) {
            trimmed = trimmed.substring(1, trimmed.length() - 1);
        }
        return trimmed;
    }
}
