package org.birdbro.lumen.setting.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * Read-only lookup over the SHARED dict tables (the design-level public-schema
 * exception - every service role may read them; exam-setting-api D5). The setting
 * domain owns NO modality vocabulary of its own: every catalog/template write
 * validates its {@code modality} against the shared modality catalog 1024
 * (「设备类型」).
 *
 * <p><b>Anchored on the catalog ID 1024 (dict-lookup-by-id ruling 2026-11-19):</b>
 * the id never moves - the seeds are deterministic and ON CONFLICT-guarded, and
 * {@code AuthDeviceRoomSeedTests} guards 1024 as the single modality catalog - while
 * the operator may rename the catalog freely (「设备类型」 is display text, never an
 * anchor). The former name anchor (「检查类型」) resolved a catalog the V27
 * unification had retired, blinding every modality path against a spec-true
 * database. A plain per-write SELECT keeps the check always-fresh - catalog writes
 * are rare, and a cache would trade a guaranteed truth for an invalidation risk.</p>
 *
 * <p>The unqualified {@code dict_*} names resolve through the connection's
 * {@code search_path = setting, public} onto {@code public.dict_*}.</p>
 *
 * @author birdbro
 */
@Mapper
public interface DictModalityMapper {

    /** 1 when the value is a live, enabled detail of the modality catalog id 1024. */
    @Select("SELECT COUNT(1) FROM dict_detail d "
            + "JOIN dict_catal c ON c.id = d.ca_id "
            + "WHERE c.id = 1024 AND c.deleted = 0 "
            + "AND d.value = #{modality} AND d.enable = 1 AND d.deleted = 0")
    int countLiveModality(@Param("modality") String modality);
}
