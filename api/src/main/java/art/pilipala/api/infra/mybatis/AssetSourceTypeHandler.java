package art.pilipala.api.infra.mybatis;

import org.apache.ibatis.type.MappedTypes;

import art.pilipala.api.project.domain.AssetSource;

@MappedTypes(AssetSource.class)
public class AssetSourceTypeHandler extends PilipalaEnumTypeHandler<AssetSource> {

    public AssetSourceTypeHandler() {
        super(AssetSource::of, AssetSource::value);
    }
}
