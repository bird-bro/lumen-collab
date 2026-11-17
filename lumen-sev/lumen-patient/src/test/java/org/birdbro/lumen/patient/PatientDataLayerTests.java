package org.birdbro.lumen.patient;

import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;

import org.birdbro.lumen.patient.entity.PatientEventOutbox;
import org.birdbro.lumen.patient.entity.PatientIdentifier;
import org.birdbro.lumen.patient.entity.PatientMaster;
import org.birdbro.lumen.patient.entity.PatientMergeLog;
import org.birdbro.lumen.patient.entity.PatientNumberRule;
import org.birdbro.lumen.patient.mapper.PatientEventOutboxMapper;
import org.birdbro.lumen.patient.mapper.PatientIdentifierMapper;
import org.birdbro.lumen.patient.mapper.PatientMasterMapper;
import org.birdbro.lumen.patient.mapper.PatientMergeLogMapper;
import org.birdbro.lumen.patient.mapper.PatientNumberRuleMapper;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Data-layer proof (patient-domain task 2.1): the five MyBatis-Plus entities/mappers
 * work against the team test server - per-entity insert/select round trips, the shared
 * {@code EntityBase @TableLogic} soft-delete transition, the outbox {@code payload}
 * jsonb round trip as a REAL jsonb OBJECT (b2-review amendment route: Map field +
 * {@code PatientJsonbTypeHandler} + {@code autoResultMap = true} - the handler writes
 * {@code setObject(Types.OTHER)}, so no URL {@code stringtype} flag is involved), and
 * the {@code stamp_value} NULL pre-issuance state (B1 amendment).
 *
 * <p>Fixture discipline (brief decision 9): dedicated fixture families only - rule
 * types prefixed {@code b2_}, master {@code pat_id}s prefixed {@code B2T-}, merge
 * reasons prefixed {@code b2_}, outbox event types prefixed {@code b2_}; the seeded
 * {@code pat_id} rule row is NEVER touched. Every sweep is a HARD DELETE (user ruling
 * 2026-11-07: shared-test-DB residue is always hard deleted, never soft-deleted) -
 * run before each test (a crashed prior JVM must not collide with
 * {@code uk_patient_number_rule_active} or leave stale serial counters) and once
 * after the class.</p>
 *
 * @author birdbro
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PatientDataLayerTests extends PatientIntegrationTestBase {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PatientMasterMapper patientMasterMapper;

    @Autowired
    private PatientIdentifierMapper patientIdentifierMapper;

    @Autowired
    private PatientNumberRuleMapper patientNumberRuleMapper;

    @Autowired
    private PatientEventOutboxMapper patientEventOutboxMapper;

    @Autowired
    private PatientMergeLogMapper patientMergeLogMapper;

    @BeforeEach
    void cleanFixtureFamiliesBeforeTest() {
        sweepFixtureFamilies();
    }

    @AfterAll
    void sweepFixtureFamiliesAfterClass() {
        sweepFixtureFamilies();
    }

    /** Hard-DELETE of every {@code b2_}/{@code B2T-} fixture family this class creates. */
    private void sweepFixtureFamilies() {
        jdbcTemplate.update("DELETE FROM patient_event_outbox WHERE event_type LIKE 'b2\\_%'");
        jdbcTemplate.update("DELETE FROM patient_merge_log WHERE reason LIKE 'b2\\_%'");
        jdbcTemplate.update("DELETE FROM patient_identifier WHERE id_no LIKE 'B2T-%'");
        jdbcTemplate.update("DELETE FROM patient_master WHERE pat_id LIKE 'B2T-%'");
        jdbcTemplate.update("DELETE FROM patient_number_rule WHERE rule_type LIKE 'b2\\_%'");
    }

    @Test
    void patientMasterInsertSelectAndSoftDeleteRoundTrip() {
        long stamp = System.currentTimeMillis();
        PatientMaster master = PatientMaster.builder()
                .patId("B2T-" + stamp)
                .idType("01")
                .idNo("B2TID" + stamp)
                .name("批次二患者")
                .gender(1)
                .birthDate(birthDate(1990, 7, 15))
                .phone("13800000000")
                .address("测试地址")
                .build();
        assertEquals(1, patientMasterMapper.insert(master), "insert must affect one row");
        assertNotNull(master.getId(), "IdType.AUTO must backfill the database-generated id");

        PatientMaster loaded = patientMasterMapper.selectById(master.getId());
        assertNotNull(loaded, "the inserted master must select back");
        assertEquals("B2T-" + stamp, loaded.getPatId());
        assertEquals("01", loaded.getIdType());
        assertEquals("B2TID" + stamp, loaded.getIdNo());
        assertEquals("批次二患者", loaded.getName());
        assertEquals(Integer.valueOf(1), loaded.getGender());
        assertEquals("1990-07-15", formatDate(loaded.getBirthDate()),
                "birth_date must round-trip as a typed date, never a shifted instant");
        assertEquals("PROVISIONAL", loaded.getArchiveStatus(),
                "the DB default archive_status must surface when the insert omits it");
        assertNull(loaded.getMergedInto(), "a fresh master is unmerged");
        assertNull(loaded.getOid(), "oid stays NULL until the issuer org is known");
        assertEquals(Integer.valueOf(0), loaded.getDeleted(),
                "the DB default deleted = 0 must surface on the read-back");
        assertNotNull(loaded.getCreateTime(),
                "create_time must come from the DB default (EntityBase declares no fill on it)");

        // Logic-delete transition (EntityBase @TableLogic): MyBatis-Plus SELECTs hide
        // the row while the physical row stays with deleted = 1.
        assertEquals(1, patientMasterMapper.deleteById(master.getId()),
                "the logical delete must affect one row");
        assertNull(patientMasterMapper.selectById(master.getId()),
                "a logically deleted master must no longer select back");
        Integer physicalDeleted = jdbcTemplate.queryForObject(
                "SELECT deleted FROM patient_master WHERE id = ?", Integer.class, master.getId());
        assertEquals(Integer.valueOf(1), physicalDeleted,
                "deleteById must UPDATE deleted = 1, not remove the row");
    }

    @Test
    void patientIdentifierRoundTripCarriesManyRowsPerMaster() {
        long stamp = System.currentTimeMillis();
        PatientMaster master = PatientMaster.builder()
                .patId("B2T-" + stamp)
                .name("批次二卡片患者")
                .build();
        patientMasterMapper.insert(master);

        PatientIdentifier card = PatientIdentifier.builder()
                .patientId(master.getId())
                .idType("CARD")
                .idNo("B2T-CARD-" + stamp)
                .build();
        assertEquals(1, patientIdentifierMapper.insert(card), "identifier insert must affect one row");
        assertNotNull(card.getId(), "IdType.AUTO must backfill the database-generated id");

        PatientIdentifier loaded = patientIdentifierMapper.selectById(card.getId());
        assertNotNull(loaded, "the inserted identifier must select back");
        assertEquals(master.getId(), loaded.getPatientId(), "the identifier must link its master");
        assertEquals("CARD", loaded.getIdType());
        assertEquals("B2T-CARD-" + stamp, loaded.getIdNo());

        // Design D4: patient_identifier deliberately has NO unique index - a second
        // additional number on the same master must coexist.
        PatientIdentifier second = PatientIdentifier.builder()
                .patientId(master.getId())
                .idType("VISIT")
                .idNo("B2T-VISIT-" + stamp)
                .build();
        patientIdentifierMapper.insert(second);
        List<PatientIdentifier> rows = patientIdentifierMapper.selectList(
                new LambdaQueryWrapper<PatientIdentifier>()
                        .eq(PatientIdentifier::getPatientId, master.getId()));
        assertEquals(2, rows.size(), "both additional numbers of the master must list back");
    }

    @Test
    void patientNumberRuleRoundTripKeepsStampValueNullBeforeFirstIssuance() {
        PatientNumberRule rule = PatientNumberRule.builder()
                .ruleType("b2_dl")
                .prefix("B2D-")
                .serialLength(4)
                .serialCur(0)
                .isStamp(1)
                .stamp("yyMMdd")
                // stamp_value deliberately unset: NULL until the first issuance (B1 amendment)
                .build();
        assertEquals(1, patientNumberRuleMapper.insert(rule), "rule insert must affect one row");
        assertNotNull(rule.getId(), "IdType.AUTO must backfill the database-generated id");

        PatientNumberRule loaded = patientNumberRuleMapper.selectById(rule.getId());
        assertNotNull(loaded, "the inserted rule must select back");
        assertEquals("b2_dl", loaded.getRuleType());
        assertEquals("B2D-", loaded.getPrefix());
        assertEquals("", loaded.getSuffix(), "the DB default '' suffix must surface when omitted");
        assertEquals(Integer.valueOf(4), loaded.getSerialLength());
        assertEquals(Integer.valueOf(0), loaded.getSerialCur());
        assertEquals(Integer.valueOf(1), loaded.getIsStamp());
        assertEquals("yyMMdd", loaded.getStamp());
        assertNull(loaded.getStampValue(), "stamp_value must stay NULL until the first issuance");
        assertEquals(Integer.valueOf(1), loaded.getActive(),
                "the DB default active = 1 must surface when omitted");
    }

    @Test
    void patientEventOutboxPayloadJsonbRoundTrip() {
        long stamp = System.currentTimeMillis();
        PatientMaster master = PatientMaster.builder()
                .patId("B2T-" + stamp)
                .name("批次二事件患者")
                .build();
        patientMasterMapper.insert(master);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("patientId", master.getId());
        payload.put("name", "张三");
        payload.put("tags", Arrays.asList("v1", "v2"));
        PatientEventOutbox event = PatientEventOutbox.builder()
                .eventType("b2_ECHO")
                .patientId(master.getId())
                .payload(payload)
                .build();
        assertEquals(1, patientEventOutboxMapper.insert(event), "outbox insert must affect one row");
        assertNotNull(event.getId(), "IdType.AUTO must backfill the database-generated id");

        PatientEventOutbox loaded = patientEventOutboxMapper.selectById(event.getId());
        assertNotNull(loaded, "the inserted outbox row must select back");
        assertEquals("b2_ECHO", loaded.getEventType());
        assertEquals(master.getId(), loaded.getPatientId());
        assertEquals("PENDING", loaded.getStatus(),
                "the DB default status must surface when the insert omits it (append-only, D2)");
        assertEquals(payload, loaded.getPayload(),
                "the jsonb payload must round-trip as a REAL OBJECT (Map-equal), not a "
                        + "double-encoded quoted string");
        assertEquals("张三",
                jdbcTemplate.queryForObject(
                        "SELECT payload->>'name' FROM patient_event_outbox WHERE id = ?",
                        String.class, event.getId()),
                "payload->> must reach INTO the object - a quoted scalar string would "
                        + "return NULL here");
    }

    @Test
    void patientMergeLogRoundTrip() {
        long stamp = System.currentTimeMillis();
        PatientMaster absorbed = PatientMaster.builder()
                .patId("B2T-" + stamp + "A")
                .name("批次二被吸收者")
                .build();
        PatientMaster survivor = PatientMaster.builder()
                .patId("B2T-" + stamp + "B")
                .name("批次二存续者")
                .build();
        patientMasterMapper.insert(absorbed);
        patientMasterMapper.insert(survivor);

        PatientMergeLog log = PatientMergeLog.builder()
                .mergedId(absorbed.getId())
                .survivorId(survivor.getId())
                .reason("b2_merge-" + stamp)
                .build();
        assertEquals(1, patientMergeLogMapper.insert(log), "merge-log insert must affect one row");
        assertNotNull(log.getId(), "IdType.AUTO must backfill the database-generated id");

        PatientMergeLog loaded = patientMergeLogMapper.selectById(log.getId());
        assertNotNull(loaded, "the inserted merge log must select back");
        assertEquals(absorbed.getId(), loaded.getMergedId());
        assertEquals(survivor.getId(), loaded.getSurvivorId());
        assertEquals("b2_merge-" + stamp, loaded.getReason());
    }

    /** A local-midnight {@link Date} for the typed {@code birth_date} column. */
    private static Date birthDate(int year, int month, int day) {
        Calendar calendar = Calendar.getInstance();
        calendar.clear();
        calendar.set(year, month - 1, day);
        return calendar.getTime();
    }

    private static String formatDate(Date date) {
        return new SimpleDateFormat("yyyy-MM-dd").format(date);
    }
}
