package org.birdbro.lumen.search.mapper;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Types;

import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import org.apache.ibatis.type.JdbcType;

/**
 * jsonb write handler for the system schema's jsonb columns (the kernel
 * KernelJsonbTypeHandler shape, self-contained: lumen-system does NOT depend on
 * lumen-core - the dependency iron rule). The search_filter.query column must hold
 * REAL jsonb objects, not double-encoded quoted strings: MyBatis-Plus'
 * JacksonTypeHandler writes through {@code setString}, which pgjdbc serializes as a
 * quoted scalar inside the jsonb column; this subclass re-binds the serialized JSON
 * with {@code setObject(i, json, Types.OTHER)} so the server casts the text to jsonb
 * directly. Reads stay on the parent (rs.getString + Jackson parse - a Map field
 * needs no element typing).
 *
 * @author birdbro
 */
public class SystemJsonbTypeHandler extends JacksonTypeHandler {

    /** MyBatis constructs field handlers with the mapped field's type. */
    public SystemJsonbTypeHandler(Class<?> type) {
        super(type);
    }

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, Object parameter,
            JdbcType jdbcType) throws SQLException {
        ps.setObject(i, toJson(parameter), Types.OTHER);
    }
}
