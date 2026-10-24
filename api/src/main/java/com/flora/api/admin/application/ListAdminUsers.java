package com.flora.api.admin.application;

import java.util.List;

import com.flora.api.admin.domain.AdminUser;
import com.flora.api.admin.infra.AdminUserRepository;
import org.springframework.stereotype.Service;

/**
 * 用例：查用户列表（带钱包余额快照）。
 *
 * <p>排序固定为注册时间倒序；{@code query} 为空时列出全部。
 */
@Service
public class ListAdminUsers {

    private final AdminUserRepository users;

    public ListAdminUsers(AdminUserRepository users) {
        this.users = users;
    }

    /**
     * @param query 用户名关键字；空时查全部
     * @param limit 单页上限，内部收敛到 1..200
     */
    public List<AdminUserView> list(String query, int limit) {
        return users.findUsers(query, AdminLimits.limit(limit)).stream()
                .map(AdminUserView::of)
                .toList();
    }
}
