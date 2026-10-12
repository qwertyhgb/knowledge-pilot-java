package com.knowledgepilot.controller;

import com.knowledgepilot.common.Result;
import com.knowledgepilot.dto.UserCreateRequest;
import com.knowledgepilot.service.UserAccountService;
import com.knowledgepilot.vo.UserVO;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户账号接口。
 *
 * <p>当前提供用户注册；后续的账号查询、资料修改等接口统一收口在本控制器。</p>
 */
@Tag(name = "用户账号", description = "注册、账号信息相关接口")
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserAccountController {

    private final UserAccountService userAccountService;

    /**
     * 用户注册。
     *
     * <p>处理链路：</p>
     * <ol>
     *   <li>{@code @Valid} 触发 {@link UserCreateRequest} 上的 JSR-380 校验
     *       （非空/长度/邮箱格式），失败由 {@code GlobalExceptionHandler} 统一转成 400</li>
     *   <li>Service 层做入参规范化、用户名/邮箱查重，重复抛业务异常（409）</li>
     *   <li>口令 BCrypt 散列后落库，成功返回脱敏后的用户信息（不含口令散列）</li>
     * </ol>
     *
     * @param request 注册请求体
     * @return 新建用户的信息
     */
    @Operation(summary = "用户注册", description = "提交用户名、密码、邮箱创建账号，成功返回用户信息")
    @PostMapping
    public Result<UserVO> createUserAccount(@Valid @RequestBody UserCreateRequest request) {
        return Result.success(userAccountService.createUser(request));
    }
}
