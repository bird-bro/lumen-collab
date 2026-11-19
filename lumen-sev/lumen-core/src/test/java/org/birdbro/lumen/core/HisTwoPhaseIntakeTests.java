package org.birdbro.lumen.core;

import java.util.Arrays;
import java.util.Map;

import org.birdbro.common.exception.BusinessException;
import org.birdbro.lumen.core.entity.ExamApplyCacheStatus;
import org.birdbro.lumen.core.entity.ExamItemRef;
import org.birdbro.lumen.core.entity.ExamOrderStatus;
import org.birdbro.lumen.core.patient.PatientRegistrationRequest;
import org.birdbro.lumen.core.service.HisTwoPhaseIntakeService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * HIS two-phase intake proof (lumen-core-registration-kernel task 6.1; spec
 * exam-workflow-kernel "HIS Two-Phase Intake" - all four scenarios). The intake is the
 * protocol-neutral contract: the HL7v2 adapter (lumen-integration, future change) feeds
 * it; the kernel owns the cache model, the drop rule, and the manual confirm.
 *
 * @author birdbro
 */
class HisTwoPhaseIntakeTests extends KernelIntegrationTestBase {

    @Autowired
    private HisTwoPhaseIntakeService intakeService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /** One matchable + one unmatchable item (the adapter's mapping output). */
    private java.util.List<ExamItemRef> mixedItems() {
        return Arrays.asList(
                ExamItemRef.builder().itemId(201).itemName("腹部超声").build(),
                ExamItemRef.builder().itemId(null).itemName("字典外项目").build());
    }

    private Integer cacheMixedApply() {
        return intakeService.cacheApply("HIS-DOC-1", "APPLY-9", Map.of("raw", "payload"),
                mixedItems(), Map.of("name", "王五", "phone", "13800000000"));
    }

    @Test
    @Transactional
    void cacheStoresMatchedApplyAndDropsUnmatchedItems() {
        Integer cacheId = cacheMixedApply();
        String target = jdbcTemplate.queryForObject(
                "SELECT target::text FROM exam_apply_cache WHERE id = ?", String.class, cacheId);
        assertTrue(target.contains("腹部超声"), "the matched item persists with its snapshot");
        assertTrue(!target.contains("字典外项目"),
                "the unmatchable item is dropped at cache time: " + target);
        String status = jdbcTemplate.queryForObject(
                "SELECT status FROM exam_apply_cache WHERE id = ?", String.class, cacheId);
        assertEquals("CACHED", status, "the row starts CACHED");
    }

    @Test
    @Transactional
    void noAutoCommitFromTheCacheStage() {
        Integer cacheId = cacheMixedApply();
        Integer orderCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM exam_order WHERE his_id = 'HIS-DOC-1'", Integer.class);
        assertEquals(0, orderCount,
                "caching an apply creates NO order - the confirm is the only order path");
        String status = jdbcTemplate.queryForObject(
                "SELECT status FROM exam_apply_cache WHERE id = ?", String.class, cacheId);
        assertEquals(ExamApplyCacheStatus.CACHED.name(), status);
    }

    @Test
    @Transactional
    void manualConfirmCreatesTheOrderAtRegistered() {
        Integer cacheId = cacheMixedApply();
        Integer orderId = intakeService.confirmApply(cacheId,
                PatientRegistrationRequest.builder().name("王五").gender(1).age("45").build());

        String status = jdbcTemplate.queryForObject(
                "SELECT status FROM exam_order WHERE id = ?", String.class, orderId);
        assertEquals(ExamOrderStatus.REGISTERED.name(), status,
                "the HIS confirm enters the REGISTERED entry state");
        String carried = jdbcTemplate.queryForObject(
                "SELECT apply_no || '/' || his_id FROM exam_order WHERE id = ?",
                String.class, orderId);
        assertEquals("APPLY-9/HIS-DOC-1", carried, "applyNo and hisId ride onto the order");
        String items = jdbcTemplate.queryForObject(
                "SELECT exam_items::text FROM exam_order WHERE id = ?", String.class, orderId);
        assertTrue(items.contains("腹部超声"), "the cached (matched) items ride onto the order");
        String cacheStatus = jdbcTemplate.queryForObject(
                "SELECT status FROM exam_apply_cache WHERE id = ?", String.class, cacheId);
        assertEquals(ExamApplyCacheStatus.COMMITTED.name(), cacheStatus,
                "the cache row is marked committed in the same transaction");
    }

    @Test
    @Transactional
    void cachedApplyCorrectedBeforeCommit() {
        Integer cacheId = cacheMixedApply();

        // correction while CACHED: the target re-maps (same drop rule)
        intakeService.correctApply(cacheId, Arrays.asList(
                ExamItemRef.builder().itemId(202).itemName("甲状腺超声").build()), null);
        String target = jdbcTemplate.queryForObject(
                "SELECT target::text FROM exam_apply_cache WHERE id = ?", String.class, cacheId);
        assertTrue(target.contains("甲状腺超声"), "the corrected target persists");
        assertTrue(!target.contains("腹部超声"), "the old target is replaced");

        // correction on a COMMITTED row: refused
        intakeService.confirmApply(cacheId,
                PatientRegistrationRequest.builder().name("王五").build());
        assertThrows(BusinessException.class,
                () -> intakeService.correctApply(cacheId, mixedItems(), null),
                "a committed cache row refuses corrections");
        assertThrows(BusinessException.class,
                () -> intakeService.confirmApply(cacheId,
                        PatientRegistrationRequest.builder().name("王五").build()),
                "a committed cache row refuses a second confirm");
    }
}
