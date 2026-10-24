package com.flora.api.admin.web;

import java.util.List;

import com.flora.api.admin.application.AdminGuard;
import com.flora.api.admin.application.AdminUserView;
import com.flora.api.admin.application.ListAdminUsers;
import com.flora.api.common.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 运维后台：用户查询。
 *
 * <p>登录闸门由 {@code AuthPathPolicy} 覆盖整个 {@code /api/v1/**}；
 * 这里再过一道 {@link AdminGuard}：普通用户一律 FORBIDDEN。
 *
 * <p>失败一律走统一信封（HTTP 200 + ok=false + error_code + message）。
 */
@RestController
@RequestMapping("/api/v1/admin/users")
public class AdminUserController {

    private final AdminGuard guard;
    private final ListAdminUsers users;

    public AdminUserController(AdminGuard guard, ListAdminUsers users) {
        this.guard = guard;
        this.users = users;
    }

    /**
     * @param query 用户名关键字；为空时列出全部
     * @param limit 单页上限，内部收敛到 1..200
     */
    @GetMapping
    public ApiResponse<List<AdminUserView>> list(@RequestParam(value = "q", required = false) String query,
                                                 @RequestParam(value = "limit", defaultValue = "50") int limit) {
        guard.requireAdmin();
        return ApiResponse.ok(users.list(query, limit));
    }
}
