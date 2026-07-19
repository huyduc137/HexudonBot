package org.example.client;

/**
 * TODO — CHƯA DÙNG. Thay thế các RuntimeException thô trong {@link HexudonClient} bằng
 * exception có phân loại rõ ràng, để Engine/GUI xử lý khác nhau tuỳ loại lỗi:
 *
 *   - AUTH        (401/403)         -> token hết hạn / sai -> dừng, yêu cầu nhập lại token.
 *   - VALIDATION  (422, theo schema HTTPValidationError) -> lỗi format action/agent-type
 *                                       -> log chi tiết field "detail" rồi rollback về
 *                                          LazyGreedyPlanner cho ngày đó thay vì crash cả engine.
 *   - NETWORK     (timeout, connection refused) -> nên retry với backoff thay vì fail ngay,
 *                                       vì mất 1 ngày thi đấu do lỗi mạng tạm thời là rất đắt.
 *   - SERVER      (5xx)              -> retry giới hạn số lần rồi báo lỗi.
 */
public class ApiException extends RuntimeException {

    public enum Kind { AUTH, VALIDATION, NETWORK, SERVER, UNKNOWN }

    public final Kind kind;
    public final int statusCode;

    public ApiException(Kind kind, int statusCode, String message, Throwable cause) {
        super(message, cause);
        this.kind = kind;
        this.statusCode = statusCode;
    }

    // TODO: viết factory static fromHttpResponse(int statusCode, String body) để HexudonClient dùng.
}
