package org.birdbro.lumen.patient;

import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.birdbro.lumen.patient.dto.PatientArchiveSaveRequest;
import org.birdbro.lumen.patient.dto.PatientArchiveUpdateRequest;
import org.birdbro.lumen.patient.entity.PatientEventOutbox;
import org.birdbro.lumen.patient.entity.PatientMaster;
import org.birdbro.lumen.patient.mapper.PatientEventOutboxMapper;
import org.birdbro.lumen.patient.mapper.PatientMasterMapper;
import org.birdbro.lumen.patient.service.PatientArchiveService;
import org.birdbro.lumen.patient.service.PatientEventOutboxService;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Outbox append-only guarantee (patient-domain task 3.5, design D2): rows are ONLY
 * ever INSERTed and accumulate in PENDING - v1 publishes to NO message broker and
 * runs NO poller (Redis + PostgreSQL are the only infrastructure this suite needs;
 * the suite run itself IS the no-broker proof, asserted explicitly).
 *
 * <p>Shape assertions (coordinator call, recorded in the b3 report): the append-only
 * guarantee is pinned by REFLECTION - the {@link PatientEventOutboxService} interface
 * exposes exactly ONE write method ({@code append}) and the outbox mapper declares NO
 * custom statement (the inherited BaseMapper insert is the only write path; the
 * interface has no update/delete members to call).</p>
 *
 * @author birdbro
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PatientOutboxTests extends PatientIntegrationTestBase {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PatientArchiveService patientArchiveService;

    @Autowired
    private PatientEventOutboxService patientEventOutboxService;

    @Autowired
    private PatientMasterMapper patientMasterMapper;

    @Autowired
    private PatientEventOutboxMapper patientEventOutboxMapper;

    @BeforeEach
    void sweepBeforeTest() {
        PatientB3Fixtures.sweep(jdbcTemplate);
    }

    @AfterAll
    void sweepAfterClass() {
        PatientB3Fixtures.sweep(jdbcTemplate);
    }

    @Test
    void appendsAccumulatePendingRowsWithoutAnyBroker() {
        PatientMaster carrier = patientArchiveService.create(saveRequest(
                PatientB3Fixtures.NAME_MARKER + "outbox载体"));
        PatientB3Fixtures.register(carrier);
        int appends = 3;
        for (int i = 0; i < appends; i++) {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("seq", i);
            patientEventOutboxService.append("b3_ECHO", carrier.getId(), payload);
        }

        List<String> statuses = jdbcTemplate.queryForList(
                "SELECT status FROM patient_event_outbox WHERE patient_id = ? "
                        + "AND event_type = 'b3_ECHO' ORDER BY id", String.class, carrier.getId());
        assertEquals(appends, statuses.size(), "N appends must leave exactly N rows");
        assertTrue(statuses.stream().allMatch("PENDING"::equals),
                "every row stays PENDING - no broker, no poller, no status flips");
    }

    @Test
    void archiveFlowLeavesRowsAccumulatingPending() {
        PatientMaster survivor = patientArchiveService.create(saveRequest(
                PatientB3Fixtures.NAME_MARKER + "outbox存续"));
        PatientMaster absorbed = patientArchiveService.create(saveRequest(
                PatientB3Fixtures.NAME_MARKER + "outbox并入"));
        PatientB3Fixtures.register(survivor.getId());
        PatientB3Fixtures.register(absorbed.getId());

        // the full archive/merge flow: update -> PATIENT_CHANGED, merge -> PATIENT_MERGED
        PatientArchiveUpdateRequest update = new PatientArchiveUpdateRequest();
        update.setPhone("13877770000");
        patientArchiveService.update(absorbed.getId(), update);
        patientArchiveService.resolveOrMerge(absorbed.getId(), survivor.getId(), "b3_outbox流");

        List<String> statuses = jdbcTemplate.queryForList(
                "SELECT status FROM patient_event_outbox WHERE patient_id IN (?, ?) "
                        + "ORDER BY id", String.class, survivor.getId(), absorbed.getId());
        assertEquals(2, statuses.size(),
                "the flow wrote exactly PATIENT_CHANGED + PATIENT_MERGED");
        assertTrue(statuses.stream().allMatch("PENDING"::equals),
                "the full create/update/merge flow succeeds with NO broker and every "
                        + "row accumulates PENDING (append-only, D2)");
    }

    @Test
    void outboxServiceInterfaceIsAppendOnly() {
        Method[] declared = PatientEventOutboxService.class.getDeclaredMethods();
        assertEquals(1, declared.length,
                "the interface exposes exactly ONE write method - append-only BY INTERFACE");
        assertEquals("append", declared[0].getName());
        assertEquals(void.class, declared[0].getReturnType());

        assertEquals(0, PatientEventOutboxMapper.class.getDeclaredMethods().length,
                "the outbox mapper declares NO custom statement - no non-INSERT SQL exists "
                        + "on the module's outbox path");
    }

    private static PatientArchiveSaveRequest saveRequest(String name) {
        PatientArchiveSaveRequest request = new PatientArchiveSaveRequest();
        request.setName(name);
        return request;
    }
}
