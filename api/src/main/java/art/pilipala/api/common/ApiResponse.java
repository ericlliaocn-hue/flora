package art.pilipala.api.common;

/**
 * C 端公共信封，形状与 docs/engineering/api/gateway-contract.md 一致。
 *
 * <p>字段名是 snake_case：靠 application.yml 里的
 * {@code spring.jackson.property-naming-strategy=SNAKE_CASE} 统一转换，
 * 代码里保持 camelCase。
 *
 * @param ok       业务是否成功；失败时由 errorCode / message 说明原因
 * @param data     业务载荷，失败时为 null
 * @param errorCode 稳定大写蛇形错误码，成功时为 null
 * @param message  当前语言的友好短句，成功时为 null
 */
public record ApiResponse<T>(boolean ok, T data, String errorCode, String message) {

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(true, data, null, null);
    }

    public static <T> ApiResponse<T> error(String errorCode, String message) {
        return new ApiResponse<>(false, null, errorCode, message);
    }
}
