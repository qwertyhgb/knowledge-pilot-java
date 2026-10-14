package com.knowledgepilot.common;

import jakarta.validation.ConstraintViolationException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.validation.method.ParameterValidationResult;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * 全局异常处理器：把各类异常统一转换为 {@link Result} 响应体 + 对应的 HTTP 状态码。
 *
 * <p>处理器按异常类型精确匹配，无需显式排序：</p>
 * <ul>
 *   <li>{@link BizException} —— 业务异常，按 code 映射 HTTP 状态码</li>
 *   <li>参数相关异常 —— 请求体不合法/校验失败/类型不匹配，统一返回 400</li>
 *   <li>{@link NoResourceFoundException} —— 路径不存在，返回 404</li>
 *   <li>{@link Exception} —— 兜底，返回 500（日志留全栈，响应已脱敏）</li>
 * </ul>
 *
 * <p><b>参数相关异常的细分</b>（都返回 400，缺一个就会掉进 500 兜底）：</p>
 * <ul>
 *   <li>{@link MethodArgumentNotValidException} —— {@code @Valid @RequestBody} 或
 *       {@code @Valid @ModelAttribute} 上的 JSR-380 校验失败
 *       （如 {@code UserCreateRequest}、{@code WorkspacePageQuery}）</li>
 *   <li>{@link HandlerMethodValidationException} —— Spring 6.1+ 起，{@code @Valid}/
 *       {@code @Validated} 作用在控制器方法参数上（{@code @RequestParam}、
 *       {@code @PathVariable}、{@code @RequestHeader}）时的校验失败</li>
 *   <li>{@link ConstraintViolationException} —— 直接在普通 Bean 方法参数上使用
 *       约束注解并启用方法级校验（{@code @Validated} 类）时的失败</li>
 *   <li>{@link BindException} —— 数据绑定阶段的失败（如查询参数类型不匹配）；
 *       {@link MethodArgumentNotValidException} 是其子类，会被更精确的分支优先匹配</li>
 * </ul>
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 业务异常：code 即 HTTP 状态码（400~599）。
     * code 不合法时降级为 400，保证响应体 code 与 HTTP 状态码始终一致。
     */
    @ExceptionHandler(BizException.class)
    public ResponseEntity<Result<Void>> handleBiz(BizException ex) {
        HttpStatus status = HttpStatus.resolve(ex.getCode());
        if (status == null) {
            status = HttpStatus.BAD_REQUEST;
        }
        // 5xx 级别的业务异常通常意味着代码缺陷或环境问题，记 ERROR 日志便于排查
        if (status.is5xxServerError()) {
            log.error("业务异常 code={}, message={}", status.value(), ex.getMessage(), ex);
        }
        return ResponseEntity.status(status)
                .body(Result.error(status.value(), ex.getMessage()));
    }

    /**
     * 请求体字段校验失败（@Valid + @RequestBody），汇总所有字段错误后返回 400。
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Result<Void>> handleInvalidBody(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> e.getField() + ": " + e.getDefaultMessage())
                .collect(Collectors.joining("; "));
        return ResponseEntity.badRequest()
                .body(Result.error(HttpStatus.BAD_REQUEST.value(), "参数校验失败 -> " + message));
    }

    /**
     * 数据绑定失败（如 {@code @ModelAttribute} 查询对象字段类型不匹配），返回 400。
     *
     * <p>注意：{@link MethodArgumentNotValidException} 是本异常的子类，绑定 + 校验混合失败
     * 会优先匹配上面更精确的分支；这里兜住其余纯绑定失败场景，避免掉进 500 兜底。</p>
     */
    @ExceptionHandler(BindException.class)
    public ResponseEntity<Result<Void>> handleBind(BindException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> e.getField() + ": " + e.getDefaultMessage())
                .collect(Collectors.joining("; "));
        return ResponseEntity.badRequest()
                .body(Result.error(HttpStatus.BAD_REQUEST.value(), "参数绑定失败 -> " + message));
    }

    /**
     * 控制器方法参数级校验失败（如 {@code @RequestParam @Min(1) int page}），返回 400。
     *
     * <p>Spring 6.1 起这类失败抛的是 {@link HandlerMethodValidationException}，
     * 既不是 {@link MethodArgumentNotValidException}（那是请求体）也不是
     * {@link ConstraintViolationException}，不单独处理就会落到兜底变成 500。</p>
     *
     * <p>方法参数没有字段名概念，只能靠参数名定位；若编译时未开启
     * {@code -parameters}，参数名会是 {@code arg0} 之类的占位符（Spring Boot 的
     * 父 POM 默认已开启 {@code -parameters}，正常情况下能看到真实参数名）。</p>
     */
    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<Result<Void>> handleMethodValidation(HandlerMethodValidationException ex) {
        List<String> details = new ArrayList<>();
        for (ParameterValidationResult result : ex.getParameterValidationResults()) {
            String parameterName = result.getMethodParameter().getParameterName();
            String label = parameterName == null ? "参数" : parameterName;
            result.getResolvableErrors().forEach(error -> details.add(label + ": " + messageOf(error)));
        }
        // 跨参数校验（如"两个参数至少给一个"）不挂在单个参数上，单独取
        ex.getCrossParameterValidationResults().forEach(error -> details.add(messageOf(error)));

        String message = details.isEmpty() ? "参数不合法" : String.join("; ", details);
        return ResponseEntity.badRequest()
                .body(Result.error(HttpStatus.BAD_REQUEST.value(), "参数校验失败 -> " + message));
    }

    /**
     * 取约束违规的默认文案；为 null 时降级为通用说明。
     * 必须兜住 null：{@code String.join} 遇到 null 元素会直接抛 NPE。
     */
    private static String messageOf(MessageSourceResolvable error) {
        String defaultMessage = error.getDefaultMessage();
        return defaultMessage == null ? "参数不合法" : defaultMessage;
    }

    /**
     * 方法/参数级约束校验失败（如 @Size、@NotNull 用在非请求体参数上），返回 400。
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Result<Void>> handleConstraintViolation(ConstraintViolationException ex) {
        String message = ex.getConstraintViolations().stream()
                .map(v -> v.getPropertyPath() + ": " + v.getMessage())
                .collect(Collectors.joining("; "));
        return ResponseEntity.badRequest()
                .body(Result.error(HttpStatus.BAD_REQUEST.value(), "参数校验失败 -> " + message));
    }

    /**
     * 请求体不是合法 JSON（反序列化失败），返回 400；
     * 不透传底层解析器的具体消息，避免泄露内部细节。
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Result<Void>> handleNotReadable(HttpMessageNotReadableException ex) {
        log.debug("请求体解析失败", ex);
        return ResponseEntity.badRequest()
                .body(Result.error(HttpStatus.BAD_REQUEST.value(), "请求体格式错误，请检查 JSON 是否合法"));
    }

    /**
     * 路径/查询参数类型不匹配（如 /user/abc 但 id 期望 Long），返回 400。
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Result<Void>> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        String requiredType = ex.getRequiredType() == null
                ? "未知"
                : ex.getRequiredType().getSimpleName();
        String message = String.format("参数类型错误 -> %s: '%s'，期望类型 %s",
                ex.getName(), ex.getValue(), requiredType);
        return ResponseEntity.badRequest()
                .body(Result.error(HttpStatus.BAD_REQUEST.value(), message));
    }

    /**
     * 路径未匹配到任何处理器（接口不存在或静态资源缺失），返回 404；
     * 若交给兜底处理会被错误地返回成 500。
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<Result<Void>> handleNoResource(NoResourceFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Result.error(HttpStatus.NOT_FOUND.value(), "接口不存在: " + ex.getResourcePath()));
    }

    /**
     * 兜底：未预期的异常。
     * 日志保留完整堆栈用于排查；响应只返回通用文案，不透传 ex.getMessage()，
     * 避免 SQL、类名、文件路径等内部信息泄露给前端。
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Result<Void>> handleOther(Exception ex) {
        log.error("未处理异常", ex);
        return ResponseEntity.internalServerError()
                .body(Result.error(HttpStatus.INTERNAL_SERVER_ERROR.value(), "服务器内部错误"));
    }
}
