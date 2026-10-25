package com.flora.api.infra.mybatis;

import org.apache.ibatis.type.MappedTypes;

import com.flora.api.project.domain.AssetSource;

@MappedTypes(AssetSource.class)
public class AssetSourceTypeHandler extends FloraEnumTypeHandler<AssetSource> {

    public AssetSourceTypeHandler() {
        super(AssetSource::of, AssetSource::value);
    }
}
