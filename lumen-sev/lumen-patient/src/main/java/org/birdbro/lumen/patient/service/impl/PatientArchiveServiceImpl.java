package org.birdbro.lumen.patient.service.impl;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.UncategorizedSQLException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import org.birdbro.common.enums.ExceptionEnum;
import org.birdbro.common.exception.BusinessException;
import org.birdbro.lumen.patient.dto.PatientArchivePageQuery;
import org.birdbro.lumen.patient.dto.PatientArchiveSaveRequest;
import org.birdbro.lumen.patient.dto.PatientArchiveUpdateRequest;
import org.birdbro.lumen.patient.dto.PatientResolveRequest;
import org.birdbro.lumen.patient.dto.PatientResolveResult;
import org.birdbro.lumen.patient.entity.PatientIdentifier;
import org.birdbro.lumen.patient.entity.PatientMaster;
import org.birdbro.lumen.patient.mapper.PatientIdentifierMapper;
import org.birdbro.lumen.patient.mapper.PatientMasterMapper;
import org.birdbro.lumen.patient.mapper.PatientMergeLogMapper;
import org.birdbro.lumen.patient.entity.PatientMergeLog;
import org.birdbro.lumen.patient.service.PatientArchiveService;
import org.birdbro.lumen.patient.service.PatientEventOutboxService;
import org.birdbro.lumen.patient.service.PatientNumberService;
import org.birdbro.lumen.patient.service.support.PatientPinyinHelper;

/**
 * Patient archive implementation (patient-domain tasks 3.1-3.4). Recorded decisions:
 *
 * <p>(1) CREATE flow order (brief decision 2, single {@code @Transactional} method):
 * (a0) id_type REQUIRED whenever id_no is present (A0410 - the identity index
 * predicate does not pin id_type, the service check is the only NULL-id_type guard);
 * (a) in-service uniqueness check - id_no present on any LIVE archive
 * ({@code deleted=0 AND merged_into IS NULL}, same id_type) is an A0715 conflict
 * naming the duplicate archive id + pat_id; (b) {@code patientNumberService.issue}
 * (REQUIRES_NEW - a failure after issuance burns the serial); (c) pinyin via the
 * {@code PatientPinyinHelper} (NULL name -&gt; NULL pinyin); (d) master insert
 * (PROVISIONAL per the flag, default RESOLVED); (e) identifier rows for the
 * additional numbers carried. (2) Index backstop (decision 3): two concurrent
 * creates passing the in-service check resolve on the partial unique index - the
 * {@link DuplicateKeyException} re-throws as the SAME A0715 payload
 * (in-service check = A-class path, index = backstop, V1__auth_schema house
 * pattern). (3) UPDATE (decision 4): demographics-only, merged rows are immutable
 * history (B0003), missing targets are B0031 (house update-target precedent); the
 * id_no uniqueness re-check excludes the row itself; PATIENT_CHANGED carries the
 * UPDATED archive through {@code BeanUtil.beanToMap} in the same transaction.
 * (4) RESOLVE chain (decision 6): exact identity -&gt; card_no (one live hit decisive,
 * 2+ demoted to candidates) -&gt; soft union (name+phone, name+gender+birth),
 * deduped by id -&gt; create-new; a resolve-created archive persists the card_no as
 * an identifier row so repeated scans converge. (5) MERGE (decision 7): guards are
 * A0400 (self-merge, non-live/PROVISIONAL survivor), B0031 (absorbed row missing);
 * already-merged = idempotent replay returning the EXISTING survivor, writing
 * NOTHING; the PATIENT_MERGED payload is the explicit two-id object. (6) Error
 * detail style: the A0715/B0003/A0400 details name the offending archive ids and
 * pat_ids (b2's {@code PatientNumberServiceImpl} detail precedent).</p>
 *
 * <p>The audit identity is NOT set here on purpose: the copied {@code MetaObjectConfig}
 * fill owns it (bridged from the JWT principal by the framework bridge filter).</p>
 *
 * @author birdbro
 */
@Service
public class PatientArchiveServiceImpl implements PatientArchiveService {

    private static final String ARCHIVE_STATUS_PROVISIONAL = "PROVISIONAL";
    private static final String ARCHIVE_STATUS_RESOLVED = "RESOLVED";

    /** The visit-card identifier type the resolve chain matches on (decision 6). */
    private static final String IDENTIFIER_TYPE_CARD = "card";

    /** The 影像号 number family (dict 编号类型 1028 token - seeds the master's pat_id). */
    private static final String NUMBER_RULE_PAT_ID = "影像号";

    private final PatientMasterMapper patientMasterMapper;
    private final PatientIdentifierMapper patientIdentifierMapper;
    private final PatientMergeLogMapper patientMergeLogMapper;
    private final PatientNumberService patientNumberService;
    private final PatientEventOutboxService patientEventOutboxService;
    private final PatientPinyinHelper patientPinyinHelper;

    public PatientArchiveServiceImpl(PatientMasterMapper patientMasterMapper,
            PatientIdentifierMapper patientIdentifierMapper,
            PatientMergeLogMapper patientMergeLogMapper,
            PatientNumberService patientNumberService,
            PatientEventOutboxService patientEventOutboxService,
            PatientPinyinHelper patientPinyinHelper) {
        this.patientMasterMapper = patientMasterMapper;
        this.patientIdentifierMapper = patientIdentifierMapper;
        this.patientMergeLogMapper = patientMergeLogMapper;
        this.patientNumberService = patientNumberService;
        this.patientEventOutboxService = patientEventOutboxService;
        this.patientPinyinHelper = patientPinyinHelper;
    }

    /**
     * The context transaction manager (us-station-registration-sev task 2.2): the
     * identity-race re-read of {@link #resolveOrRegister} needs a FRESH transaction
     * OUTSIDE the aborted one - the ADDITIVE-DI field precedent (the never-modify
     * rule: the existing constructor stays byte-identical, documented
     * {@code LogController} shape).
     */
    @Autowired
    private PlatformTransactionManager transactionManager;

    @Override
    @Transactional
    public PatientMaster create(PatientArchiveSaveRequest request) {
        // the one-arg form stays byte-compatible for the b3 callers: no issuer org
        return create(request, null);
    }

    @Override
    @Transactional
    public PatientMaster create(PatientArchiveSaveRequest request, Integer oid) {
        // (a0) the identity index does not pin id_type - the service check is the
        // only NULL-id_type guard (b1 review carry-forward)
        if (StrUtil.isNotBlank(request.getIdNo()) && StrUtil.isBlank(request.getIdType())) {
            throw new BusinessException(ExceptionEnum.ERROR_A0410,
                    "id_no requires id_type: " + request.getIdNo());
        }
        // (a) the A-class path: live-archive uniqueness
        PatientMaster existing = existingLiveByIdentity(request.getIdType(), request.getIdNo());
        if (existing != null) {
            throw identityConflict(existing);
        }

        // (b) REQUIRES_NEW issuance - a failure after this point burns the serial
        String patId = patientNumberService.issue(NUMBER_RULE_PAT_ID);

        PatientMaster master = new PatientMaster();
        BeanUtil.copyProperties(request, master, true);
        master.setPatId(patId);
        // the issuer org rides the authenticated principal (b4 amendment 1) - never
        // the request body; null = "filled when known" (the V1 column comment)
        master.setOid(oid);
        // (c) server pinyin; NULL name -> NULL pinyin (unnamed emergency is legal)
        master.setPinyin(patientPinyinHelper.toPinyin(request.getName()));
        master.setArchiveStatus(Boolean.TRUE.equals(request.getProvisional())
                ? ARCHIVE_STATUS_PROVISIONAL : ARCHIVE_STATUS_RESOLVED);
        try {
            // (d) master insert
            patientMasterMapper.insert(master);
        } catch (DuplicateKeyException e) {
            // (2) index backstop: a concurrent create passed the in-service check
            PatientMaster concurrent = existingLiveByIdentity(
                    request.getIdType(), request.getIdNo());
            if (concurrent != null) {
                throw identityConflict(concurrent);
            }
            throw e;
        }

        // (e) the additional numbers the request carries
        insertIdentifier(master.getId(), IDENTIFIER_TYPE_CARD, request.getCardNo());
        insertIdentifier(master.getId(), "physical", request.getPhysicalNo());
        insertIdentifier(master.getId(), "outpatient", request.getOutpatientNo());
        insertIdentifier(master.getId(), "inpatient", request.getInpatientNo());
        insertIdentifier(master.getId(), "his", request.getHisNo());
        return master;
    }

    @Override
    @Transactional
    public PatientMaster update(Integer id, PatientArchiveUpdateRequest request) {
        PatientMaster current = patientMasterMapper.selectById(id);
        if (current == null) {
            throw new BusinessException(ExceptionEnum.ERROR_B0031,
                    "archive id " + id + " does not exist");
        }
        if (current.getMergedInto() != null) {
            throw new BusinessException(ExceptionEnum.ERROR_B0003,
                    "archive already merged into id=" + current.getMergedInto()
                            + " (merged history is immutable): archive id " + id);
        }
        if (StrUtil.isNotBlank(request.getIdNo())) {
            // the effective pair: the request value, else the row's current id_type
            String idType = StrUtil.isNotBlank(request.getIdType())
                    ? request.getIdType() : current.getIdType();
            if (StrUtil.isBlank(idType)) {
                throw new BusinessException(ExceptionEnum.ERROR_A0410,
                        "id_no requires id_type: " + request.getIdNo());
            }
            PatientMaster existing = existingLiveByIdentity(idType, request.getIdNo());
            if (existing != null && !id.equals(existing.getId())) {
                throw identityConflict(existing);
            }
        }

        PatientMaster patch = new PatientMaster();
        BeanUtil.copyProperties(request, patch, true);
        if (request.getName() != null) {
            patch.setPinyin(patientPinyinHelper.toPinyin(request.getName()));
        }
        patch.setId(id);
        patientMasterMapper.updateById(patch);

        PatientMaster updated = patientMasterMapper.selectById(id);
        Map<String, Object> payload = BeanUtil.beanToMap(updated);
        patientEventOutboxService.append(
                PatientEventOutboxService.EVENT_PATIENT_CHANGED, id, payload);
        return updated;
    }

    @Override
    public PatientMaster getById(Integer id) {
        return patientMasterMapper.selectById(id);
    }

    @Override
    public IPage<PatientMaster> page(PatientArchivePageQuery query) {
        // MP wrapper args evaluate EAGERLY even for a false condition - compute the
        // prefix null-safely (the stored pinyin is NORMALIZED space-stripped lowercase,
        // decision 11; the query prefix matches it verbatim)
        String pinyinPrefix = StrUtil.isNotBlank(query.getPinyin())
                ? query.getPinyin() : null;
        LambdaQueryWrapper<PatientMaster> wrapper = new LambdaQueryWrapper<PatientMaster>()
                .like(StrUtil.isNotBlank(query.getName()), PatientMaster::getName, query.getName())
                .likeRight(pinyinPrefix != null, PatientMaster::getPinyin, pinyinPrefix)
                .eq(StrUtil.isNotBlank(query.getPatId()), PatientMaster::getPatId, query.getPatId())
                .eq(StrUtil.isNotBlank(query.getIdNo()), PatientMaster::getIdNo, query.getIdNo())
                .like(StrUtil.isNotBlank(query.getPhone()), PatientMaster::getPhone, query.getPhone())
                .isNull(PatientMaster::getMergedInto) // the default list hides merged rows
                .orderByDesc(PatientMaster::getId);
        return patientMasterMapper.selectPage(
                new Page<>(query.getPageNum(), query.getPageSize()), wrapper);
    }

    @Override
    @Transactional
    public PatientResolveResult resolve(PatientResolveRequest request) {
        // (i) exact identity anchor on a live archive
        if (StrUtil.isNotBlank(request.getIdType()) && StrUtil.isNotBlank(request.getIdNo())) {
            PatientMaster hit = existingLiveByIdentity(request.getIdType(), request.getIdNo());
            if (hit != null) {
                return PatientResolveResult.builder()
                        .result(PatientResolveResult.RESULT_MATCHED)
                        .patient(hit)
                        .build();
            }
        }

        // (ii) card_no among live archives' identifier rows (card_no is NOT unique:
        // exactly one live hit is decisive, ambiguity demotes to candidates)
        if (StrUtil.isNotBlank(request.getCardNo())) {
            List<PatientMaster> cardHits = liveArchivesByCardNo(request.getCardNo());
            if (cardHits.size() == 1) {
                return PatientResolveResult.builder()
                        .result(PatientResolveResult.RESULT_MATCHED)
                        .patient(cardHits.get(0))
                        .build();
            }
            if (cardHits.size() > 1) {
                return PatientResolveResult.builder()
                        .result(PatientResolveResult.RESULT_CANDIDATES)
                        .candidates(cardHits)
                        .build();
            }
        }

        // (iii) soft union of (name+phone) and (name+gender+birth), deduped by id -
        // a candidate LIST, never an error
        List<PatientMaster> candidates = new ArrayList<>();
        if (StrUtil.isNotBlank(request.getName())) {
            if (StrUtil.isNotBlank(request.getPhone())) {
                addCandidates(candidates, liveBy(request.getName(), request.getPhone()));
            }
            if (request.getGender() != null && request.getBirthDate() != null) {
                addCandidates(candidates, liveBy(request.getName(),
                        request.getGender(), request.getBirthDate()));
            }
        }
        if (!candidates.isEmpty()) {
            return PatientResolveResult.builder()
                    .result(PatientResolveResult.RESULT_CANDIDATES)
                    .candidates(candidates)
                    .build();
        }

        // (iv) nothing matched - create-new per the provisional flag
        PatientArchiveSaveRequest save = new PatientArchiveSaveRequest();
        BeanUtil.copyProperties(request, save, true); // cardNo rides into the identifier insert
        save.setProvisional(request.getProvisional());
        PatientMaster created = create(save);
        return PatientResolveResult.builder()
                .result(PatientResolveResult.RESULT_CREATED)
                .patient(created)
                .build();
    }

    /**
     * The minimal-merge enrichment (patient-resolve-demographics-enrichment task 1.2,
     * design D2/D3): field by field, the request value fills ONLY a null master field
     * (name/gender/id_type/id_no/phone/birth_date - never an overwrite); a request
     * card absent from the archive's live identifiers appends as a new card row (the
     * accumulation semantics, idempotent). The id_no's conflict safety rides the
     * chain's own ordering - a claimed id_no resolves to its owner decisively before
     * any card walk, so the id_no reaching here is unclaimed (design D2).
     */
    private void enrichMatchedArchive(PatientMaster master, PatientResolveRequest request) {
        if (master == null) {
            return;
        }
        boolean changed = false;
        if (master.getName() == null && StrUtil.isNotBlank(request.getName())) {
            master.setName(request.getName());
            changed = true;
        }
        if (master.getGender() == null && request.getGender() != null) {
            master.setGender(request.getGender());
            changed = true;
        }
        if (master.getIdType() == null && StrUtil.isNotBlank(request.getIdType())) {
            master.setIdType(request.getIdType());
            changed = true;
        }
        if (master.getIdNo() == null && StrUtil.isNotBlank(request.getIdNo())) {
            master.setIdNo(request.getIdNo());
            changed = true;
        }
        if (master.getPhone() == null && StrUtil.isNotBlank(request.getPhone())) {
            master.setPhone(request.getPhone());
            changed = true;
        }
        if (master.getBirthDate() == null && request.getBirthDate() != null) {
            master.setBirthDate(request.getBirthDate());
            changed = true;
        }
        if (changed) {
            patientMasterMapper.updateById(master);
        }
        if (StrUtil.isNotBlank(request.getCardNo())) {
            Long cardRows = patientIdentifierMapper.selectCount(
                    new LambdaQueryWrapper<PatientIdentifier>()
                            .eq(PatientIdentifier::getPatientId, master.getId())
                            .eq(PatientIdentifier::getIdType, IDENTIFIER_TYPE_CARD)
                            .eq(PatientIdentifier::getIdNo, request.getCardNo()));
            if (cardRows == null || cardRows == 0) {
                insertIdentifier(master.getId(), IDENTIFIER_TYPE_CARD, request.getCardNo());
            }
        }
    }

    @Override
    @Transactional
    public PatientResolveResult resolveOrRegister(PatientResolveRequest request) {
        try {
            PatientResolveResult result = resolve(request);
            // the composite's match enrichment (patient-resolve-demographics-enrichment):
            // a decisive hit fills the archive's NULL demographics from the request
            // (minimal-merge - never an overwrite) + appends an absent request card, so
            // the desk's typed information STICKS to the archive (the user ruling
            // 2026-11-26: card 00114's second registration stayed sparse forever). The
            // plain lookup path never runs this (the no-side-effect read).
            if (PatientResolveResult.RESULT_MATCHED.equals(result.getResult())) {
                enrichMatchedArchive(result.getPatient(), request);
            }
            return result;
        } catch (DuplicateKeyException | UncategorizedSQLException e) {
            // the identity race (us-station-registration-sev task 2.2): a concurrent
            // create committed the same identity between the chain's lookups and the
            // fallback INSERT. The create path's own backstop lookup cannot name the
            // duplicate - it runs INSIDE the already-aborted transaction, so
            // PostgreSQL rejects it with 25P02 and the original DuplicateKeyException
            // surfaces as an UncategorizedSQLException. EITHER shape lands here: this
            // composite re-reads the winner in a FRESH (REQUIRES_NEW) transaction and
            // surfaces the EXISTING A0715 payload verbatim; when NO live holder is
            // confirmed the ORIGINAL failure is rethrown untouched (the conversion
            // never masks an unrelated error).
            TransactionTemplate freshRead = new TransactionTemplate(transactionManager);
            freshRead.setPropagationBehavior(
                    TransactionDefinition.PROPAGATION_REQUIRES_NEW);
            PatientMaster winner = freshRead.execute(status ->
                    existingLiveByIdentity(request.getIdType(), request.getIdNo()));
            if (winner != null) {
                throw identityConflict(winner);
            }
            throw e;
        }
    }

    @Override
    @Transactional
    public PatientResolveResult lookup(PatientResolveRequest request) {
        // (i) exact identity anchor on a live archive - the SAME helper the public
        // resolve chain runs (the chain's lookup steps are REUSED, not rewritten)
        if (StrUtil.isNotBlank(request.getIdType()) && StrUtil.isNotBlank(request.getIdNo())) {
            PatientMaster hit = existingLiveByIdentity(request.getIdType(), request.getIdNo());
            if (hit != null) {
                return PatientResolveResult.builder()
                        .result(PatientResolveResult.RESULT_MATCHED)
                        .patient(hit)
                        .build();
            }
        }

        // (ii) card_no among live archives' identifier rows (one hit decisive, 2+
        // demoted to candidates) - the SAME helper again
        if (StrUtil.isNotBlank(request.getCardNo())) {
            List<PatientMaster> cardHits = liveArchivesByCardNo(request.getCardNo());
            if (cardHits.size() == 1) {
                return PatientResolveResult.builder()
                        .result(PatientResolveResult.RESULT_MATCHED)
                        .patient(cardHits.get(0))
                        .build();
            }
            if (cardHits.size() > 1) {
                return PatientResolveResult.builder()
                        .result(PatientResolveResult.RESULT_CANDIDATES)
                        .candidates(cardHits)
                        .build();
            }
        }

        // (iii) soft union of (name+phone) and (name+gender+birth), deduped by id - the
        // SAME helpers again; a candidate LIST, never an error
        List<PatientMaster> candidates = new ArrayList<>();
        if (StrUtil.isNotBlank(request.getName())) {
            if (StrUtil.isNotBlank(request.getPhone())) {
                addCandidates(candidates, liveBy(request.getName(), request.getPhone()));
            }
            if (request.getGender() != null && request.getBirthDate() != null) {
                addCandidates(candidates, liveBy(request.getName(),
                        request.getGender(), request.getBirthDate()));
            }
        }
        if (!candidates.isEmpty()) {
            return PatientResolveResult.builder()
                    .result(PatientResolveResult.RESULT_CANDIDATES)
                    .candidates(candidates)
                    .build();
        }

        // (iv) nothing matched - the ADDITIVE lookup-only empty marker. Deliberately NO
        // create fallback: the passthrough consumer must never write a side effect (the
        // creating resolve chain's step (iv) is the ONLY creation path).
        return PatientResolveResult.builder()
                .result(PatientResolveResult.RESULT_NONE)
                .build();
    }

    @Override
    @Transactional
    public PatientMaster resolveOrMerge(Integer id, Integer survivorId, String reason) {
        if (survivorId == null) {
            throw new BusinessException(ExceptionEnum.ERROR_A0400,
                    "merge survivorId is required");
        }
        if (id != null && id.equals(survivorId)) {
            throw new BusinessException(ExceptionEnum.ERROR_A0400,
                    "self-merge rejected: archive id " + id);
        }
        PatientMaster merged = patientMasterMapper.selectById(id);
        if (merged == null) {
            throw new BusinessException(ExceptionEnum.ERROR_B0031,
                    "archive id " + id + " does not exist");
        }
        if (merged.getMergedInto() != null) {
            // idempotent replay: an already-merged row is immutable history - report
            // the EXISTING survivor and write NOTHING (decision 7)
            return patientMasterMapper.selectById(merged.getMergedInto());
        }
        PatientMaster survivor = patientMasterMapper.selectById(survivorId);
        if (survivor == null || survivor.getMergedInto() != null) {
            throw new BusinessException(ExceptionEnum.ERROR_A0400,
                    "merge survivor must be a live archive: survivor id " + survivorId);
        }
        if (!ARCHIVE_STATUS_RESOLVED.equals(survivor.getArchiveStatus())) {
            throw new BusinessException(ExceptionEnum.ERROR_A0400,
                    "merge survivor must be RESOLVED, but was "
                            + survivor.getArchiveStatus() + ": survivor id " + survivorId);
        }

        PatientMaster pointer = new PatientMaster();
        pointer.setId(id);
        pointer.setMergedInto(survivorId);
        patientMasterMapper.updateById(pointer);

        patientMergeLogMapper.insert(PatientMergeLog.builder()
                .mergedId(id)
                .survivorId(survivorId)
                .reason(reason)
                .build());

        // the payload names BOTH ids explicitly (decision 7), patient_id = the
        // absorbed row (the row whose pointer changed)
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("survivorId", survivorId);
        payload.put("mergedId", id);
        patientEventOutboxService.append(
                PatientEventOutboxService.EVENT_PATIENT_MERGED, id, payload);
        return survivor;
    }

    // ---------------------------------------------------------------- helpers

    /**
     * The live-archive identity lookup: {@code deleted = 0} (via {@code @TableLogic})
     * + {@code merged_into IS NULL} + the exact (id_type, id_no) pair. NOTE: this does
     * NOT match the exact domain the partial unique index
     * {@code uk_patient_master_identity} pins - the index keys only on
     * {@code id_no IS NOT NULL AND merged_into IS NULL} (id_type unpinned, soft-deleted
     * rows keep occupying the key); the check is service-side only and at most one row
     * comes back under it in v1 (no archive delete path exists to create the
     * soft-deleted-duplicate case).
     */
    private PatientMaster existingLiveByIdentity(String idType, String idNo) {
        if (StrUtil.isBlank(idNo) || StrUtil.isBlank(idType)) {
            return null;
        }
        return patientMasterMapper.selectOne(new LambdaQueryWrapper<PatientMaster>()
                .eq(PatientMaster::getIdType, idType)
                .eq(PatientMaster::getIdNo, idNo)
                .isNull(PatientMaster::getMergedInto));
    }

    /** The A0715 conflict payload naming the duplicate: archive id + pat_id. */
    private BusinessException identityConflict(PatientMaster existing) {
        return new BusinessException(ExceptionEnum.ERROR_A0715,
                "档案 id=" + existing.getId()
                        + " (pat_id=" + existing.getPatId() + ") 已持有该证件号");
    }

    /** Identifier rows carrying the card_no key, filtered to LIVE archives only. */
    private List<PatientMaster> liveArchivesByCardNo(String cardNo) {
        List<Integer> patientIds = patientIdentifierMapper.selectList(
                        new LambdaQueryWrapper<PatientIdentifier>()
                                .eq(PatientIdentifier::getIdType, IDENTIFIER_TYPE_CARD)
                                .eq(PatientIdentifier::getIdNo, cardNo))
                .stream().map(PatientIdentifier::getPatientId)
                .distinct().collect(Collectors.toList());
        if (patientIds.isEmpty()) {
            return Collections.emptyList();
        }
        return patientMasterMapper.selectList(new LambdaQueryWrapper<PatientMaster>()
                .in(PatientMaster::getId, patientIds)
                .isNull(PatientMaster::getMergedInto));
    }

    /** The name+phone soft branch (exact matches on live archives). */
    private List<PatientMaster> liveBy(String name, String phone) {
        return patientMasterMapper.selectList(new LambdaQueryWrapper<PatientMaster>()
                .eq(PatientMaster::getName, name)
                .eq(PatientMaster::getPhone, phone)
                .isNull(PatientMaster::getMergedInto));
    }

    /** The name+gender+birth soft branch (exact matches on live archives). */
    private List<PatientMaster> liveBy(String name, Integer gender, Date birthDate) {
        return patientMasterMapper.selectList(new LambdaQueryWrapper<PatientMaster>()
                .eq(PatientMaster::getName, name)
                .eq(PatientMaster::getGender, gender)
                .eq(PatientMaster::getBirthDate, birthDate)
                .isNull(PatientMaster::getMergedInto));
    }

    /** Union-dedupes soft candidates by archive id. */
    private void addCandidates(List<PatientMaster> candidates, List<PatientMaster> hits) {
        for (PatientMaster hit : hits) {
            boolean seen = false;
            for (PatientMaster candidate : candidates) {
                if (candidate.getId().equals(hit.getId())) {
                    seen = true;
                    break;
                }
            }
            if (!seen) {
                candidates.add(hit);
            }
        }
    }

    /** Inserts one additional-number row when the request carries it. */
    private void insertIdentifier(Integer patientId, String idType, String idNo) {
        if (StrUtil.isNotBlank(idNo)) {
            patientIdentifierMapper.insert(PatientIdentifier.builder()
                    .patientId(patientId)
                    .idType(idType)
                    .idNo(idNo)
                    .build());
        }
    }
}
