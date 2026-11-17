package org.birdbro.lumen.patient.mapper;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Types;

import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import org.apache.ibatis.type.JdbcType;

/**
 * jsonb write handler for the patient module (patient-domain b2-review amendment):
 * the {@code patient_event_outbox.payload} column must hold a REAL jsonb object, not
 * a double-encoded quoted string. MyBatis-Plus' copied {@link JacksonTypeHandler}
 * writes through {@code setString}, which the pgjdbc VARCHAR default serializes as a
 * quoted scalar inside the jsonb column; this subclass re-binds the serialized JSON
 * with {@code setObject(i, json, Types.OTHER)} - pgjdbc sends {@code Types.OTHER} as
 * an UNSPECIFIED-typed parameter, so the server casts the text to the jsonb target
 * directly. The URL {@code stringtype=unspecified} flag the String route needed is
 * thereby obsolete (and deliberately NOT set anywhere: a flag here would mask a
 * handler-route regression).
 *
 * <p>Reads stay on the parent ({@code rs.getString} + Jackson parse back into the
 * field type, e.g. {@code Map<String, Object>}).</p>
 *
 * @author birdbro
 */
public class PatientJsonbTypeHandler extends JacksonTypeHandler {

    /** MyBatis constructs field handlers with the mapped field's type. */
    public PatientJsonbTypeHandler(Class<?> type) {
        super(type);
    }

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, Object parameter,
            JdbcType jdbcType) throws SQLException {
        ps.setObject(i, toJson(parameter), Types.OTHER);
    }
}
