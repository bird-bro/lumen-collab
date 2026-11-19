package org.birdbro.lumen.core.mapper;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Types;

import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import org.apache.ibatis.type.JdbcType;

/**
 * jsonb write handler for the kernel (mirrors the patient domain's PatientJsonbTypeHandler
 * - lumen-core-registration-kernel task 2.2): the jsonb columns (exam_order
 * .patient_snapshot / exam_order.exam_items) must hold REAL jsonb objects, not
 * double-encoded quoted strings. MyBatis-Plus' JacksonTypeHandler writes through
 * {@code setString}, which pgjdbc serializes as a quoted scalar inside the jsonb
 * column; this subclass re-binds the serialized JSON with {@code setObject(i, json,
 * Types.OTHER)} - pgjdbc sends Types.OTHER as an UNSPECIFIED-typed parameter, so the
 * server casts the text to the jsonb target directly. The URL {@code stringtype=
 * unspecified} flag is deliberately NOT set anywhere: a flag there would mask a
 * handler-route regression. Reads stay on the parent (rs.getString + Jackson parse).
 *
 * <p>A kernel-owned copy, NOT an import of the patient class: the kernel never depends
 * on a department module (the dependency iron rule), and a shared copy in
 * framework/common would put exam-kernel concerns on every service's classpath.</p>
 *
 * @author birdbro
 */
public class KernelJsonbTypeHandler extends JacksonTypeHandler {

    /** MyBatis constructs field handlers with the mapped field's type. */
    public KernelJsonbTypeHandler(Class<?> type) {
        super(type);
    }

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, Object parameter,
            JdbcType jdbcType) throws SQLException {
        ps.setObject(i, toJson(parameter), Types.OTHER);
    }
}
