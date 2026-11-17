package org.birdbro.lumen.patient;

import java.util.Map;

import org.birdbro.common.exception.BusinessException;
import org.birdbro.lumen.patient.dto.PatientArchiveSaveRequest;
import org.birdbro.lumen.patient.entity.PatientEventOutbox;
import org.birdbro.lumen.patient.entity.PatientMaster;
import org.birdbro.lumen.patient.mapper.PatientEventOutboxMapper;
import org.birdbro.lumen.patient.mapper.PatientMergeLogMapper;
import org.birdbro.lumen.patient.mapper.PatientMasterMapper;
import org.birdbro.lumen.patient.service.PatientArchiveService;
import org.birdbro.lumen.patient.service.PatientEventOutboxService;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Resolve-or-merge proof (patient-domain task 3.4): the minimal merge sets
 * {@code merged_into} on the absorbed row + writes the merge_log row + the
 * PATIENT_MERGED outbox event whose payload names BOTH ids, the survivor stays
 * RESOLVED, and an idempotent re-merge returns the same survivor WITHOUT duplicate
 * log/outbox rows. Guards: self-merge and a PROVISIONAL survivor are A0400 rejections.
 *
 * <p>Fixture discipline: {@link PatientB3Fixtures} (real serials, hard-DELETE sweep,
 * {@code b3} name marker).</p>
 *
 * @author birdbro
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PatientMergeServiceTests extends PatientIntegrationTestBase {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PatientArchiveService patientArchiveService;

    @Autowired
    private PatientMasterMapper patientMasterMapper;

    @Autowired
    private PatientEventOutboxMapper patientEventOutboxMapper;

    @Autowired
    private PatientMergeLogMapper patientMergeLogMapper;

    @BeforeEach
    void sweepBeforeTest() {
        PatientB3Fixtures.sweep(jdbcTemplate);
    }

    @AfterAll
    void sweepAfterClass() {
        PatientB3Fixtures.sweep(jdbcTemplate);
    }

    @Test
    void mergeWritesPointerLogAndPatientMergedEventNamingBothIds() {
        PatientMaster absorbed = patientArchiveService.create(saveRequest(
                PatientB3Fixtures.NAME_MARKER + "并入者", true));
        PatientMaster survivor = patientArchiveService.create(saveRequest(
                PatientB3Fixtures.NAME_MARKER + "存续者", false));
        PatientB3Fixtures.register(absorbed.getId());
        PatientB3Fixtures.register(survivor.getId());
        String reason = "b3_最小合并-" + absorbed.getId();

        PatientMaster returned = patientArchiveService.resolveOrMerge(
                absorbed.getId(), survivor.getId(), reason);

        assertEquals(survivor.getId(), returned.getId(), "the merge returns the survivor");

        PatientMaster absorbedAfter = patientMasterMapper.selectById(absorbed.getId());
        assertEquals(survivor.getId(), absorbedAfter.getMergedInto(),
                "the minimal merge sets merged_into on the absorbed row");

        Map<String, Object> log = jdbcTemplate.queryForMap(
                "SELECT merged_id, survivor_id, reason FROM patient_merge_log "
                        + "WHERE merged_id = ?", absorbed.getId());
        assertEquals(survivor.getId(), ((Number) log.get("survivor_id")).intValue(),
                "the merge log records both ids");
        assertEquals(reason, log.get("reason"));

        PatientEventOutbox event = patientEventOutboxMapper.selectOne(
                new LambdaQueryWrapper<PatientEventOutbox>()
                        .eq(PatientEventOutbox::getPatientId, absorbed.getId())
                        .eq(PatientEventOutbox::getEventType,
                                PatientEventOutboxService.EVENT_PATIENT_MERGED));
        assertNotNull(event, "the minimal merge writes the PATIENT_MERGED row");
        assertEquals("PENDING", event.getStatus());
        assertEquals(survivor.getId(), ((Number) event.getPayload().get("survivorId")).intValue(),
                "the outbox payload names the survivor id");
        assertEquals(absorbed.getId(), ((Number) event.getPayload().get("mergedId")).intValue(),
                "the outbox payload names the merged id");
        assertEquals(String.valueOf(survivor.getId()),
                jdbcTemplate.queryForObject(
                        "SELECT payload->>'survivorId' FROM patient_event_outbox WHERE id = ?",
                        String.class, event.getId()),
                "the payload is a REAL jsonb object - payload->> reaches inside "
                        + "(a double-encoded scalar string would return NULL)");
    }

    @Test
    void survivorStaysResolved() {
        PatientMaster absorbed = patientArchiveService.create(saveRequest(
                PatientB3Fixtures.NAME_MARKER + "状态并入", true));
        PatientMaster survivor = patientArchiveService.create(saveRequest(
                PatientB3Fixtures.NAME_MARKER + "状态存续", false));
        PatientB3Fixtures.register(absorbed.getId());
        PatientB3Fixtures.register(survivor.getId());

        patientArchiveService.resolveOrMerge(absorbed.getId(), survivor.getId(), "b3_状态");

        assertEquals("RESOLVED", patientMasterMapper.selectById(survivor.getId())
                .getArchiveStatus(), "the survivor stays RESOLVED (no-op touch)");
    }

    @Test
    void idempotentReMergeReturnsSameSurvivorWithoutDuplicateRows() {
        PatientMaster absorbed = patientArchiveService.create(saveRequest(
                PatientB3Fixtures.NAME_MARKER + "幂等并入", true));
        PatientMaster survivor = patientArchiveService.create(saveRequest(
                PatientB3Fixtures.NAME_MARKER + "幂等存续", false));
        PatientB3Fixtures.register(absorbed.getId());
        PatientB3Fixtures.register(survivor.getId());
        patientArchiveService.resolveOrMerge(absorbed.getId(), survivor.getId(), "b3_幂等一次");
        long logsBefore = countMergeLogs(absorbed.getId());
        long eventsBefore = countMergeEvents(absorbed.getId());

        PatientMaster returned = patientArchiveService.resolveOrMerge(
                absorbed.getId(), survivor.getId(), "b3_幂等重放");

        assertEquals(survivor.getId(), returned.getId(),
                "the re-merge returns the SAME survivor");
        assertEquals(1L, countMergeLogs(absorbed.getId()),
                "an already-merged row writes NO second merge_log row");
        assertEquals(1L, countMergeEvents(absorbed.getId()),
                "an already-merged row writes NO second PATIENT_MERGED row");
        assertEquals(logsBefore, countMergeLogs(absorbed.getId()));
        assertEquals(eventsBefore, countMergeEvents(absorbed.getId()));
    }

    @Test
    void selfMergeIsRejectedWithA0400() {
        PatientMaster archive = patientArchiveService.create(saveRequest(
                PatientB3Fixtures.NAME_MARKER + "自合并", false));
        PatientB3Fixtures.register(archive);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> patientArchiveService.resolveOrMerge(archive.getId(), archive.getId(), "b3_自合并"));
        assertEquals("A0400", exception.getCode());
    }

    @Test
    void provisionalSurvivorIsRejectedWithA0400() {
        PatientMaster absorbed = patientArchiveService.create(saveRequest(
                PatientB3Fixtures.NAME_MARKER + "占位存续并入", false));
        PatientMaster provisionalSurvivor = patientArchiveService.create(saveRequest(
                PatientB3Fixtures.NAME_MARKER + "占位存续", true));
        PatientB3Fixtures.register(absorbed.getId());
        PatientB3Fixtures.register(provisionalSurvivor.getId());

        BusinessException exception = assertThrows(BusinessException.class,
                () -> patientArchiveService.resolveOrMerge(absorbed.getId(),
                        provisionalSurvivor.getId(), "b3_占位存续"),
                "merging into a PROVISIONAL placeholder is a request-parameter error");
        assertEquals("A0400", exception.getCode());

        assertNullMergedInto(absorbed.getId());
    }

    /** A create request with just the name and the PROVISIONAL flag. */
    private static PatientArchiveSaveRequest saveRequest(String name, boolean provisional) {
        PatientArchiveSaveRequest request = new PatientArchiveSaveRequest();
        request.setName(name);
        request.setProvisional(provisional);
        return request;
    }

    private long countMergeLogs(Integer mergedId) {
        return jdbcTemplate.queryForObject(
                "SELECT count(*) FROM patient_merge_log WHERE merged_id = ?",
                Long.class, mergedId);
    }

    private long countMergeEvents(Integer patientId) {
        return jdbcTemplate.queryForObject(
                "SELECT count(*) FROM patient_event_outbox WHERE patient_id = ? "
                        + "AND event_type = '" + PatientEventOutboxService.EVENT_PATIENT_MERGED + "'",
                Long.class, patientId);
    }

    private void assertNullMergedInto(Integer archiveId) {
        assertNull(patientMasterMapper.selectById(archiveId).getMergedInto(),
                "a rejected merge must not write the pointer");
    }
}
