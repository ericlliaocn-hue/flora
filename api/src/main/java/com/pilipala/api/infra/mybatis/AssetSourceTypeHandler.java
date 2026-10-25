package com.pilipala.api.infra.mybatis;

import org.apache.ibatis.type.MappedTypes;

import com.pilipala.api.project.domain.AssetSource;

@MappedTypes(AssetSource.class)
public class AssetSourceTypeHandler extends PilipalaEnumTypeHandler<AssetSource> {

    public AssetSourceTypeHandler() {
        super(AssetSource::of, AssetSource::value);
    }
}
