package org.birdbro.lumen.patient;

import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

import org.birdbro.common.exception.BusinessException;
import org.birdbro.lumen.patient.dto.PatientArchivePageQuery;
import org.birdbro.lumen.patient.dto.PatientArchiveSaveRequest;
import org.birdbro.lumen.patient.dto.PatientArchiveUpdateRequest;
import org.birdbro.lumen.patient.dto.PatientResolveRequest;
import org.birdbro.lumen.patient.dto.PatientResolveResult;
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

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Archive service proof (patient-domain tasks 3.1/3.2/3.3): create (pat_id issuance +
 * server pinyin + the PROVISIONAL option), the identity uniqueness rules (duplicate
 * id_no conflict, card_no softness, merged rows dropping out of the identity key),
* the demographics update writing PATIENT_CHANGED, fetch-by-id of merged rows, the
 * default list excluding merged rows, and every resolve-chain branch (exact id_no ->
 * card_no -> soft candidates -> create-new).
 *
 * <p>Fixture discipline: {@link PatientB3Fixtures} captures every issued archive id
 * and hard-DELETEs the families (before each test + after the class); the seeded
 * {@code pat_id} rule row is only ever consumed (real serials, gap-tolerant).</p>
 *
 * @author birdbro
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PatientArchiveServiceTests extends PatientIntegrationTestBase {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PatientArchiveService patientArchiveService;

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

    // ------------------------------------------------------------- 3.1 create

    @Test
    void createIssuesPatIdServerPinyinAndReturnsIdAndNumber() {
        PatientMaster created = patientArchiveService.create(saveRequest("01",
                "B3ID-" + stamp(), request -> {
                    request.setName(PatientB3Fixtures.NAME_MARKER + "检验张三");
                    request.setGender(1);
                    request.setPhone("13811110000");
                }));
        PatientB3Fixtures.register(created);

        assertNotNull(created.getId(), "the created archive must carry its database id");
        assertNotNull(created.getPatId(), "create must issue the pat_id number");

        PatientMaster loaded = patientMasterMapper.selectById(created.getId());
        assertNotNull(loaded);
        assertEquals("RESOLVED", loaded.getArchiveStatus(), "a create without the flag is RESOLVED");
        assertEquals("01", loaded.getIdType());
        assertNotNull(loaded.getPinyin(), "pinyin is generated server-side for a Chinese name");
        assertEquals(loaded.getPinyin(), loaded.getPinyin().toLowerCase(),
                "the helper stores the NORMALIZED lowercase pinyin (b4 decision 11)");
        assertFalse(loaded.getPinyin().contains(" "),
                "the normalized pinyin is space-stripped: " + loaded.getPinyin());
        assertTrue(loaded.getPinyin().contains("zhang") && loaded.getPinyin().contains("san"),
                "张三 must convert to its full pinyin, got: " + loaded.getPinyin());
    }

    @Test
    void createProvisionalUnnamedStillGetsANumber() {
        PatientMaster created = patientArchiveService.create(saveRequest(null, null,
                request -> {
                    request.setName(null);
                    request.setProvisional(true);
                }));
        PatientB3Fixtures.register(created);

        PatientMaster loaded = patientMasterMapper.selectById(created.getId());
        assertNotNull(loaded, "an unnamed emergency placeholder must still persist");
        assertNotNull(loaded.getPatId(), "the PROVISIONAL placeholder carries a pat_id");
        assertNull(loaded.getName(), "the placeholder is unnamed");
        assertNull(loaded.getPinyin(), "NULL name converts to NULL pinyin");
        assertNull(loaded.getIdNo());
        assertEquals("PROVISIONAL", loaded.getArchiveStatus());
    }

    // ------------------------------------------------------------- 3.2 identity

    @Test
    void createRejectsIdNoWithoutIdType() {
        PatientArchiveSaveRequest request = saveRequest(null, "B3ID-" + stamp(), r -> { });
        BusinessException exception = assertThrows(BusinessException.class,
                () -> patientArchiveService.create(request),
                "id_type is REQUIRED whenever id_no is present (b1 review carry-forward)");
        assertEquals("A0410", exception.getCode());
    }

    @Test
    void createRejectsDuplicateIdNoNamingTheDuplicateWithoutANewRow() {
        PatientMaster first = patientArchiveService.create(saveRequest("01",
                "B3ID-DUP-" + stamp(), request -> {
                    request.setName(PatientB3Fixtures.NAME_MARKER + "证件冲突原主");
                }));
        PatientB3Fixtures.register(first);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> patientArchiveService.create(saveRequest("01", first.getIdNo(), r -> { })));
        assertEquals("A0715", exception.getCode(), "a live-archive id_no duplicate is a conflict");
        assertTrue(exception.getMsg().contains(String.valueOf(first.getId()))
                        && exception.getMsg().contains(first.getPatId()),
                "the conflict detail must name the duplicate archive's id and pat_id: "
                        + exception.getMsg());

        Long rows = countMastersByIdNo(first.getIdNo());
        assertEquals(Long.valueOf(1), rows, "the rejected create must not add an archive row");
    }

    @Test
    void createPersistsSameCardNoOnTwoArchives() {
        String sharedCard = "B3CARD-SHARED-" + stamp();
        PatientMaster first = patientArchiveService.create(saveRequest(null, null,
                request -> request.setCardNo(sharedCard)));
        PatientMaster second = patientArchiveService.create(saveRequest(null, null,
                request -> request.setCardNo(sharedCard)));
        PatientB3Fixtures.register(first.getId());
        PatientB3Fixtures.register(second.getId());

        Long rows = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM patient_identifier WHERE id_type = 'card' AND id_no = ?",
                Long.class, sharedCard);
        assertEquals(Long.valueOf(2), rows,
                "card_no is a soft resolution key - two archives may share it (no unique index)");
    }

    @Test
    void createWithMergedArchivesIdNoIsNotBlocked() {
        PatientMaster absorbed = patientArchiveService.create(saveRequest("01",
                "B3ID-MERGED-" + stamp(), request -> {
                    request.setName(PatientB3Fixtures.NAME_MARKER + "并入后重登");
                }));
        PatientMaster survivor = patientArchiveService.create(saveRequest(null, null,
                request -> request.setName(PatientB3Fixtures.NAME_MARKER + "并入存续")));
        PatientB3Fixtures.register(absorbed.getId());
        PatientB3Fixtures.register(survivor.getId());
        patientArchiveService.resolveOrMerge(absorbed.getId(), survivor.getId(),
                "b3_合并后证件重登");

        // the merged row dropped out of the identity key - the same document registers again
        PatientMaster reRegistered = patientArchiveService.create(saveRequest("01",
                absorbed.getIdNo(), r -> { }));
        PatientB3Fixtures.register(reRegistered);
        assertTrue(reRegistered.getId() != absorbed.getId(),
                "the new archive must be a fresh row, not the merged one");
    }

    // ------------------------------------------------------------- 3.1 update

    @Test
    void updateDemographicsWritesPatientChangedWithUpdatedPayload() {
        PatientMaster created = patientArchiveService.create(saveRequest(null, null,
                request -> {
                    request.setName(PatientB3Fixtures.NAME_MARKER + "改名前");
                    request.setPhone("13800001111");
                }));
        PatientB3Fixtures.register(created);

        PatientArchiveUpdateRequest update = new PatientArchiveUpdateRequest();
        update.setName(PatientB3Fixtures.NAME_MARKER + "改名后");
        update.setPhone("13800002222");
        PatientMaster updated = patientArchiveService.update(created.getId(), update);

        assertEquals("b3改名后", updated.getName());
        assertEquals("13800002222", updated.getPhone());
        assertNotNull(updated.getPinyin(), "a name change regenerates the server pinyin");

        PatientEventOutbox event = patientEventOutboxMapper.selectOne(
                new LambdaQueryWrapper<PatientEventOutbox>()
                        .eq(PatientEventOutbox::getPatientId, created.getId())
                        .eq(PatientEventOutbox::getEventType,
                                PatientEventOutboxService.EVENT_PATIENT_CHANGED));
        assertNotNull(event, "a demographics update must write the PATIENT_CHANGED row");
        assertEquals("PENDING", event.getStatus(), "outbox rows accumulate PENDING (append-only)");
        assertEquals(created.getId(), event.getPayload().get("id"),
                "the payload names the changed archive");
        assertEquals("b3改名后", event.getPayload().get("name"),
                "the payload carries the UPDATED archive");
    }

    @Test
    void updateRejectsIdNoChangedToADuplicate() {
        PatientMaster first = patientArchiveService.create(saveRequest("01",
                "B3ID-U1-" + stamp(), r -> { }));
        PatientMaster second = patientArchiveService.create(saveRequest("01",
                "B3ID-U2-" + stamp(), r -> { }));
        PatientB3Fixtures.register(first.getId());
        PatientB3Fixtures.register(second.getId());

        PatientArchiveUpdateRequest update = new PatientArchiveUpdateRequest();
        update.setIdNo(second.getIdNo());
        update.setIdType(second.getIdType());
        BusinessException exception = assertThrows(BusinessException.class,
                () -> patientArchiveService.update(first.getId(), update),
                "the uniqueness re-check excludes the row itself, not its neighbours");
        assertEquals("A0715", exception.getCode());
    }

    // ------------------------------------------------------- 3.1 fetch + list

    @Test
    void getByIdReturnsAMergedRow() {
        PatientMaster absorbed = patientArchiveService.create(saveRequest(null, null,
                request -> request.setName(PatientB3Fixtures.NAME_MARKER + "取回被并入")));
        PatientMaster survivor = patientArchiveService.create(saveRequest(null, null,
                request -> request.setName(PatientB3Fixtures.NAME_MARKER + "取回存续")));
        PatientB3Fixtures.register(absorbed.getId());
        PatientB3Fixtures.register(survivor.getId());
        patientArchiveService.resolveOrMerge(absorbed.getId(), survivor.getId(), "b3_取回");

        PatientMaster fetched = patientArchiveService.getById(absorbed.getId());
        assertNotNull(fetched, "a merged row stays retrievable by id");
        assertEquals(survivor.getId(), fetched.getMergedInto(),
                "merged_into present tells the caller where the row went");
    }

    @Test
    void defaultListExcludesMergedRows() {
        PatientMaster absorbed = patientArchiveService.create(saveRequest(null, null,
                request -> request.setName(PatientB3Fixtures.NAME_MARKER + "列表被并入")));
        PatientMaster survivor = patientArchiveService.create(saveRequest(null, null,
                request -> request.setName(PatientB3Fixtures.NAME_MARKER + "列表存活")));
        PatientB3Fixtures.register(absorbed.getId());
        PatientB3Fixtures.register(survivor.getId());
        patientArchiveService.resolveOrMerge(absorbed.getId(), survivor.getId(), "b3_列表");

        PatientArchivePageQuery query = new PatientArchivePageQuery();
        query.setName(PatientB3Fixtures.NAME_MARKER + "列表");
        List<Integer> listedIds = patientArchiveService.page(query).getRecords().stream()
                .map(PatientMaster::getId).collect(Collectors.toList());

        assertTrue(listedIds.contains(survivor.getId()), "the live archive lists");
        assertFalse(listedIds.contains(absorbed.getId()),
                "the default list excludes merged rows");
    }

    @Test
    void pagePinyinPrefixFilterFindsTheArchive() {
        PatientMaster created = patientArchiveService.create(saveRequest(null, null,
                request -> request.setName(PatientB3Fixtures.NAME_MARKER + "拼音过滤")));
        PatientB3Fixtures.register(created);
        String pinyinPrefix = patientMasterMapper.selectById(created.getId()).getPinyin()
                .substring(0, 3);

        PatientArchivePageQuery query = new PatientArchivePageQuery();
        query.setPinyin(pinyinPrefix);
        List<Integer> listedIds = patientArchiveService.page(query).getRecords().stream()
                .map(PatientMaster::getId).collect(Collectors.toList());
        assertTrue(listedIds.contains(created.getId()),
                "the pinyin prefix (LIKE 'x%') filter finds the archive");
    }

    // --------------------------------------------------------- 3.3 resolve chain

    @Test
    void resolveExactIdNoMatchesWithoutCreatingARow() {
        PatientMaster existing = patientArchiveService.create(saveRequest("01",
                "B3ID-EXACT-" + stamp(), r -> { }));
        PatientB3Fixtures.register(existing);

        Long before = countMastersByIdNo(existing.getIdNo());
        PatientResolveResult result = patientArchiveService.resolve(resolveRequest(r -> {
            r.setIdType("01");
            r.setIdNo(existing.getIdNo());
        }));

        assertEquals(PatientResolveResult.RESULT_MATCHED, result.getResult());
        assertEquals(existing.getId(), result.getPatient().getId());
        assertNull(result.getCandidates());
        assertEquals(before, countMastersByIdNo(existing.getIdNo()),
                "a decisive match must not create a row");
    }

    @Test
    void resolveSingleCardHitMatches() {
        String card = "B3CARD-ONE-" + stamp();
        PatientMaster existing = patientArchiveService.create(saveRequest(null, null,
                request -> request.setCardNo(card)));
        PatientB3Fixtures.register(existing);

        PatientResolveResult result = patientArchiveService.resolve(
                resolveRequest(r -> r.setCardNo(card)));

        assertEquals(PatientResolveResult.RESULT_MATCHED, result.getResult());
        assertEquals(existing.getId(), result.getPatient().getId());
    }

    @Test
    void resolveTwoCardHitsReturnsCandidates() {
        String card = "B3CARD-TWO-" + stamp();
        PatientMaster first = patientArchiveService.create(saveRequest(null, null,
                request -> request.setCardNo(card)));
        PatientMaster second = patientArchiveService.create(saveRequest(null, null,
                request -> request.setCardNo(card)));
        PatientB3Fixtures.register(first.getId());
        PatientB3Fixtures.register(second.getId());

        PatientResolveResult result = patientArchiveService.resolve(
                resolveRequest(r -> r.setCardNo(card)));

        assertEquals(PatientResolveResult.RESULT_CANDIDATES, result.getResult(),
                "card_no is NOT unique - ambiguity demotes the hit to candidates");
        assertEquals(2, result.getCandidates().size());
        assertTrue(result.getCandidates().stream().map(PatientMaster::getId)
                .collect(Collectors.toList()).containsAll(List.of(first.getId(), second.getId())));
    }

    @Test
    void resolveNamePhoneSoftMatchReturnsCandidates() {
        PatientMaster first = patientArchiveService.create(saveRequest(null, null,
                request -> {
                    request.setName(PatientB3Fixtures.NAME_MARKER + "软匹配同号");
                    request.setPhone("13900001111");
                }));
        PatientMaster second = patientArchiveService.create(saveRequest(null, null,
                request -> {
                    request.setName(PatientB3Fixtures.NAME_MARKER + "软匹配同号");
                    request.setPhone("13900001111");
                }));
        PatientB3Fixtures.register(first.getId());
        PatientB3Fixtures.register(second.getId());

        PatientResolveResult result = patientArchiveService.resolve(resolveRequest(r -> {
            r.setName(PatientB3Fixtures.NAME_MARKER + "软匹配同号");
            r.setPhone("13900001111");
        }));

        assertEquals(PatientResolveResult.RESULT_CANDIDATES, result.getResult(),
                "a soft match returns a candidate LIST, never an error");
        assertEquals(2, result.getCandidates().size(), "the spec's 2-row soft case");
    }

    @Test
    void resolveNameGenderBirthSoftMatchReturnsCandidates() {
        PatientMaster existing = patientArchiveService.create(saveRequest(null, null,
                request -> {
                    request.setName(PatientB3Fixtures.NAME_MARKER + "软匹配生日");
                    request.setGender(2);
                    request.setBirthDate(birthDate(1992, 3, 5));
                }));
        PatientB3Fixtures.register(existing);

        PatientResolveResult result = patientArchiveService.resolve(resolveRequest(r -> {
            r.setName(PatientB3Fixtures.NAME_MARKER + "软匹配生日");
            r.setGender(2);
            r.setBirthDate(birthDate(1992, 3, 5));
        }));

        assertEquals(PatientResolveResult.RESULT_CANDIDATES, result.getResult());
        assertTrue(result.getCandidates().stream().map(PatientMaster::getId)
                .collect(Collectors.toList()).contains(existing.getId()));
    }

    @Test
    void resolveNoMatchWithFormalFlagCreatesResolvedArchive() {
        String idNo = "B3ID-NEWFORMAL-" + stamp();
        PatientResolveResult result = patientArchiveService.resolve(resolveRequest(r -> {
            r.setIdType("01");
            r.setIdNo(idNo);
            r.setName(PatientB3Fixtures.NAME_MARKER + "新建正式");
        }));

        assertEquals(PatientResolveResult.RESULT_CREATED, result.getResult());
        PatientB3Fixtures.register(result.getPatient());
        assertNotNull(result.getPatient().getPatId(), "the created archive carries an issued number");
        assertEquals("RESOLVED", result.getPatient().getArchiveStatus());
        assertEquals(Long.valueOf(1), countMastersByIdNo(idNo), "the created archive persists");
    }

    @Test
    void resolveNoMatchWithProvisionalFlagCreatesProvisionalArchive() {
        PatientResolveResult result = patientArchiveService.resolve(resolveRequest(r -> {
            r.setName(PatientB3Fixtures.NAME_MARKER + "新建占位");
            r.setProvisional(true);
        }));

        assertEquals(PatientResolveResult.RESULT_CREATED, result.getResult());
        PatientB3Fixtures.register(result.getPatient());
        assertNotNull(result.getPatient().getPatId());
        assertEquals("PROVISIONAL", result.getPatient().getArchiveStatus());
    }

    // ---------------------------------------------------------------- helpers

    /** A save request with the identity pair set and a field-shape hook. */
    private static PatientArchiveSaveRequest saveRequest(String idType, String idNo,
            java.util.function.Consumer<PatientArchiveSaveRequest> shaper) {
        PatientArchiveSaveRequest request = new PatientArchiveSaveRequest();
        request.setIdType(idType);
        request.setIdNo(idNo);
        shaper.accept(request);
        return request;
    }

    private static PatientResolveRequest resolveRequest(
            java.util.function.Consumer<PatientResolveRequest> shaper) {
        PatientResolveRequest request = new PatientResolveRequest();
        shaper.accept(request);
        return request;
    }

    private Long countMastersByIdNo(String idNo) {
        return patientMasterMapper.selectCount(new LambdaQueryWrapper<PatientMaster>()
                .eq(PatientMaster::getIdNo, idNo));
    }

    private long stamp() {
        return System.currentTimeMillis();
    }

    /** A local-midnight {@link Date} for the typed {@code birth_date} column. */
    private static Date birthDate(int year, int month, int day) {
        Calendar calendar = Calendar.getInstance();
        calendar.clear();
        calendar.set(year, month - 1, day);
        return calendar.getTime();
    }
}
