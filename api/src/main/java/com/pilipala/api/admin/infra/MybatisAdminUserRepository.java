package com.pilipala.api.admin.infra;

import java.util.List;

import org.springframework.stereotype.Repository;

import com.pilipala.api.admin.domain.AdminUser;

@Repository
public class MybatisAdminUserRepository implements AdminUserRepository {

    private final AdminUserMapper mapper;

    public MybatisAdminUserRepository(AdminUserMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public List<AdminUser> findUsers(String query, int limit) {
        String keyword = query == null ? "" : query.trim();
        if (keyword.isEmpty()) {
            return mapper.findUsersAll(limit);
        }
        return mapper.findUsersByQuery("%" + keyword + "%", limit);
    }
}
