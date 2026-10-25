package com.pilipala.worker.infra.mybatis;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.function.Function;

import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;

public abstract class PilipalaEnumTypeHandler<T> extends BaseTypeHandler<T> {

    private final Function<String, T> fromDb;
    private final Function<T, String> toDb;

    protected PilipalaEnumTypeHandler(Function<String, T> fromDb, Function<T, String> toDb) {
        this.fromDb = fromDb;
        this.toDb = toDb;
    }

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, T parameter, JdbcType jdbcType) throws SQLException {
        ps.setString(i, toDb.apply(parameter));
    }

    @Override
    public T getNullableResult(ResultSet rs, String columnName) throws SQLException {
        return parse(rs.getString(columnName));
    }

    @Override
    public T getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        return parse(rs.getString(columnIndex));
    }

    @Override
    public T getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        return parse(cs.getString(columnIndex));
    }

    private T parse(String raw) {
        if (raw == null) {
            return null;
        }
        return fromDb.apply(raw);
    }
}
