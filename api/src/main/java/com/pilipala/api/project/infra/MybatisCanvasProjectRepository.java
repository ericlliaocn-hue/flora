package com.pilipala.api.project.infra;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Repository;

import com.pilipala.api.project.domain.CanvasProject;

@Repository
public class MybatisCanvasProjectRepository implements CanvasProjectRepository {

    private final CanvasProjectMapper mapper;

    public MybatisCanvasProjectRepository(CanvasProjectMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public CanvasProject insert(UUID userId, String name, String canvasDataJson) {
        return mapper.insert(userId, name, canvasDataJson);
    }

    @Override
    public Optional<CanvasProject> findByIdAndUser(UUID projectId, UUID userId) {
        return Optional.ofNullable(mapper.findByIdAndUser(projectId, userId));
    }

    @Override
    public List<CanvasProject> findByUser(UUID userId, int limit) {
        return mapper.findByUser(userId, limit);
    }

    @Override
    public Optional<CanvasProject> update(UUID projectId, UUID userId, String name, String canvasDataJson) {
        return Optional.ofNullable(mapper.update(projectId, userId, name, canvasDataJson));
    }

    @Override
    public boolean delete(UUID projectId, UUID userId) {
        return mapper.delete(projectId, userId) > 0;
    }
}
