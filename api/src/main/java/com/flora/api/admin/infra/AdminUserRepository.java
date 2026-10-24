package com.flora.api.admin.infra;

import java.util.List;
import java.util.UUID;

import com.flora.api.admin.domain.AdminUser;

/**
 * 运维后台的用户查询。SQL 只在 JDBC 实现里。
 */
public interface AdminUserRepository {
    /**
     * 按用户名模糊匹配列出用户（带钱包余额快照），按注册时间倒序。
     *
     * @param query 用户名关键字；为空时列出全部
     * @param limit 单页上限
     */
    List<AdminUser> findUsers(String query, int limit);
}
