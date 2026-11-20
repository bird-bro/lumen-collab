package org.birdbro.lumen.patient.service;

import com.baomidou.mybatisplus.core.metadata.IPage;

import org.birdbro.lumen.patient.dto.PatientArchivePageQuery;
import org.birdbro.lumen.patient.dto.PatientArchiveSaveRequest;
import org.birdbro.lumen.patient.dto.PatientArchiveUpdateRequest;
import org.birdbro.lumen.patient.dto.PatientResolveRequest;
import org.birdbro.lumen.patient.dto.PatientResolveResult;
import org.birdbro.lumen.patient.entity.PatientMaster;

/**
 * Patient master archive (patient-domain tasks 3.1-3.4): the EMPI service over
 * {@code patient_master} - create / update / fetch / list, the identity resolve
 * chain, and the minimal merge. Contract highlights:
 *
 * <ul>
 *   <li><b>Create</b> issues the {@code pat_id} number (REQUIRES_NEW burn semantics -
 *       a failure after issuance burns the serial, never a reuse) and generates the
 *       server pinyin; {@code id_type} is REQUIRED whenever {@code id_no} is present
 *       (A0410 - the identity index does not pin id_type, the service check is the
 *       only NULL-id_type guard); a duplicate id_no on any LIVE archive
 *       ({@code deleted=0 AND merged_into IS NULL}, same id_type) is an A0715
 *       conflict naming the duplicate archive's id + pat_id (the partial unique
 *       index is the concurrency backstop for the same payload).</li>
 *   <li><b>Update</b> is demographics-only (no archive_status field exists to
 *       violate the one-way PROVISIONAL -&gt; RESOLVED machine); a merged row is
 *       immutable history and rejects; a successful update writes PATIENT_CHANGED
 *       (payload = the UPDATED archive) in the same transaction.</li>
 *   <li><b>Fetch/list</b>: by-id returns the row REGARDLESS of merge state (merged
 *       stays retrievable; {@code merged_into} tells the caller); the default list
 *       filters live unmerged archives only.</li>
 *   <li><b>Resolve</b>: exact (id_type, id_no) -&gt; card_no (exactly ONE live hit =
 *       MATCHED, 2+ = CANDIDATES, card_no is NOT unique) -&gt; soft union of
 *       (name+phone) and (name+gender+birth) -&gt; create-new per the provisional
 *       flag; a soft match returns a candidate LIST, never an error.</li>
 *   <li><b>Resolve-or-merge</b>: minimal merge (merged_into + RESOLVED survivor +
 *       merge_log row + PATIENT_MERGED naming both ids); an already-merged row is
 *       idempotent - returns the EXISTING survivor and writes NOTHING.</li>
 * </ul>
 *
 * @author birdbro
 */
public interface PatientArchiveService {

    /**
     * Creates an archive: identity guard (A0410/A0715) -&gt; pat_id issuance (burn
     * semantics) -&gt; server pinyin -&gt; master insert (PROVISIONAL per the flag,
     * default RESOLVED) -&gt; identifier rows for the additional numbers carried.
     *
     * @param request the archive request (identity + demographics + identifiers +
     *        provisional flag)
     * @return the persisted archive (id + pat_id + server pinyin filled)
     * @throws org.birdbro.common.exception.BusinessException code {@code A0410} when
     *         id_no comes without id_type; code {@code A0715} naming the duplicate
     *         (archive id + pat_id) when a live archive already holds the identity;
     *         code {@code B0001}/{@code A0400} surfaced through issuance
     */
    PatientMaster create(PatientArchiveSaveRequest request);

    /**
     * OID-CARRYING CREATE (patient-domain b4 amendment 1 - ADDITIVE overload, the
     * house never-modify rule): identical flow to {@link #create(PatientArchiveSaveRequest)}
     * plus the issuer organization id (spec: "the archive SHALL carry the issuer
     * organization id"). The value comes from the JWT principal's
     * {@code UserSign.entry.oid} - NEVER from the request body (a client-supplied
     * issuer is spoofable); {@code null} means unknown ("filled when known", the V1
     * column comment). The one-arg method delegates with a null oid, so every b3
     * caller and test keeps its exact behavior.
     *
     * @param request the archive request (identity + demographics + identifiers +
     *        provisional flag)
     * @param oid the issuer organization id from the authenticated principal (nullable)
     * @return the persisted archive (id + pat_id + server pinyin + oid filled)
     * @throws org.birdbro.common.exception.BusinessException the same codes as the
     *         one-arg create
     */
    PatientMaster create(PatientArchiveSaveRequest request, Integer oid);

    /**
     * Updates demographics (identity document included; pinyin regenerated on a name
     * change) and writes PATIENT_CHANGED with the UPDATED archive as payload.
     *
     * @param id the archive id (a merged row rejects - immutable history; a missing
     *        row is B0031 per the house update-target precedent)
     * @param request the demographics-only patch (no archive_status field)
     * @return the UPDATED archive (re-read after the patch)
     * @throws org.birdbro.common.exception.BusinessException code {@code B0031} when
     *         the target does not exist; {@code B0003} when it is already merged;
     *         {@code A0410}/{@code A0715} for an identity change that is typeless or
     *         conflicts with another live archive
     */
    PatientMaster update(Integer id, PatientArchiveUpdateRequest request);

    /**
     * Fetches an archive by id REGARDLESS of merge state (spec: a merged row stays
     * retrievable by id; {@code merged_into} present tells the caller where it went).
     *
     * @param id the archive id
     * @return the archive row, or {@code null} when absent (house getById precedent -
     *         the caller's 404 semantics live in the batch-4 controller)
     */
    PatientMaster getById(Integer id);

    /**
     * Paged list over LIVE UNMERGED archives (deleted = 0 via {@code @TableLogic},
     * merged_into IS NULL), ordered id desc; filters: name contains, pinyin prefix
     * (LIKE 'x%'), pat_id exact, id_no exact, phone contains.
     *
     * @param query the filter + pagination query
     * @return the paged archives
     */
    IPage<PatientMaster> page(PatientArchivePageQuery query);

    /**
     * Runs the identity resolve chain (task 3.3): (i) exact (id_type, id_no) on a
     * live archive -&gt; decisive MATCHED; (ii) exact card_no among live identifier
     * rows (id_type = 'card') - exactly ONE live hit is decisive MATCHED, 2+ hits
     * are CANDIDATES (card_no is NOT unique); (iii) soft union of (name+phone) and
     * (name+gender+birth) exact matches -&gt; CANDIDATES (never an error); (iv)
     * nothing -&gt; CREATE (provisional flag: PROVISIONAL/RESOLVED) -&gt; CREATED.
     * A resolve-created archive persists the request's card_no as an identifier row
     * so the next scan with the same card converges instead of duplicating.
     *
     * @param request the resolve form (identity/card/soft keys + provisional flag)
     * @return the chain result ({@code patient} on MATCHED/CREATED, {@code
     *         candidates} on CANDIDATES)
     * @throws org.birdbro.common.exception.BusinessException code {@code A0410} when
     *         creation runs with an id_no but no id_type
     */
    PatientResolveResult resolve(PatientResolveRequest request);

    /**
     * Minimal merge (task 3.4): sets {@code merged_into} on the absorbed row (the
     * survivor stays RESOLVED - no-op touch), writes the merge_log row (reason +
     * operator via MetaObjectConfig), and appends PATIENT_MERGED whose payload names
     * BOTH ids ({@code {"survivorId":..,"mergedId":..}}).
     *
     * <p>Guards: self-merge and a PROVISIONAL (or merged) survivor are A0400; a
     * missing/deleted absorbed row is B0031. An ALREADY-MERGED row is IDEMPOTENT
     * history: the call returns the EXISTING survivor (the requested survivorId is
     * irrelevant to that replay) and writes NOTHING.</p>
     *
     * @param id the archive absorbed into the survivor
     * @param survivorId the surviving archive (must exist, be live, be RESOLVED)
     * @param reason the audit reason (may be null)
     * @return the survivor archive
     * @throws org.birdbro.common.exception.BusinessException code {@code A0400} for
     *         self-merge / non-live / non-RESOLVED survivor; {@code B0031} when the
     *         absorbed archive does not exist
     */
    PatientMaster resolveOrMerge(Integer id, Integer survivorId, String reason);

    /**
     * The resolve-or-register COMPOSITE (us-station-registration-sev task 2.2, design
     * D4; spec patient-domain "Internal Service Surface"): the registration consumers'
     * entry point that atomically runs the EXISTING resolution chain ({@link #resolve}
     * - its exact/card/soft lookups and the fallback archive creation share ONE
     * transaction, no chain rewrite) and surfaces the IDENTITY RACE with the existing
     * {@code A0715} semantics. ADDITIVE method - the {@code create(request, oid)}
     * overload precedent; every existing member is untouched.
     *
     * <p>The race case: a concurrent create commits the same identity between the
     * chain's lookups and the fallback INSERT, so the master INSERT dies on the
     * partial unique index. The create path's own backstop lookup runs INSIDE the
     * aborted transaction (PostgreSQL 25P02) and can never name the duplicate - this
     * composite re-reads the winner in a FRESH (REQUIRES_NEW) transaction after the
     * aborted one and throws the SAME A0715 payload ({@code identityConflict} shape:
     * archive id + pat_id), never a rewritten code. When no winner is visible (the
     * ultra-narrow window where the competing transaction has not committed yet), the
     * raw data-integrity failure propagates as before.</p>
     *
     * @param request the resolve form (identity/card/soft keys + provisional flag)
     * @return the chain result ({@code patient} on MATCHED/CREATED, {@code
     *         candidates} on CANDIDATES)
     * @throws org.birdbro.common.exception.BusinessException the same codes as
     *         {@link #resolve(PatientResolveRequest)} - {@code A0410} for a typeless
     *         id_no on creation, {@code A0715} naming the identity-race winner
     */
    PatientResolveResult resolveOrRegister(PatientResolveRequest request);
}
