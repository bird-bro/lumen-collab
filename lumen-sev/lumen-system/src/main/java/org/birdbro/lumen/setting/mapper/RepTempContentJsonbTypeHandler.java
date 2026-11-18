package org.birdbro.lumen.setting.mapper;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Types;

import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import org.apache.ibatis.type.JdbcType;

/**
 * jsonb write handler for the exam-setting kernel's {@code rep_temp.content}
 * (rep-template-structured-content design D4): the column must hold a REAL jsonb
 * OBJECT ({@code jsonb_typeof = 'object'}), not a double-encoded quoted string.
 * MyBatis-Plus' copied {@link JacksonTypeHandler} writes through {@code setString},
 * which the pgjdbc VARCHAR default serializes as a quoted scalar inside the jsonb
 * column; this subclass re-binds the serialized JSON with
 * {@code setObject(i, json, Types.OTHER)} - pgjdbc sends {@code Types.OTHER} as an
 * UNSPECIFIED-typed parameter, so the server casts the text to the jsonb target
 * directly. Same shape as the patient-domain {@code PatientJsonbTypeHandler}
 * precedent (no URL {@code stringtype} flag anywhere - a flag would mask a
 * handler-route regression).
 *
 * <p>Reads stay on the parent ({@code rs.getString} + Jackson parse back into the
 * field type, {@code RepTempContent}); {@code RepTemp} carries
 * {@code autoResultMap = true} so row mapping picks this handler up.</p>
 *
 * @author birdbro
 */
public class RepTempContentJsonbTypeHandler extends JacksonTypeHandler {

    /** MyBatis constructs field handlers with the mapped field's type. */
    public RepTempContentJsonbTypeHandler(Class<?> type) {
        super(type);
    }

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, Object parameter,
            JdbcType jdbcType) throws SQLException {
        ps.setObject(i, toJson(parameter), Types.OTHER);
    }
}
