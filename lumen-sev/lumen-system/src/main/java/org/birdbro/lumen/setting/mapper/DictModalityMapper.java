package org.birdbro.lumen.setting.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * Read-only lookup over the SHARED dict tables (the design-level public-schema
 * exception - every service role may read them; exam-setting-api D5). The setting
 * domain owns NO modality vocabulary of its own: every catalog/template write
 * validates its {@code modality} against the shared 检查类型 catalog.
 *
 * <p><b>Anchored on the catalog NAME, not its id:</b> the platform's 检查类型 catalog
 * id has flip-flopped (V26 seeded it as 1021; a V27 draft moved it to 1027 and back)
 * - but {@code uk_dict_catal_name} makes the NAME unique. The join resolves the
 * catalog by name, so whichever id the platform settles on, the vocabulary check
 * stays true without another migration. A plain per-write SELECT keeps the check
 * always-fresh - catalog writes are rare, and a cache would trade a guaranteed truth
 * for an invalidation risk.</p>
 *
 * <p>The unqualified {@code dict_*} names resolve through the connection's
 * {@code search_path = setting, public} onto {@code public.dict_*}.</p>
 *
 * @author birdbro
 */
@Mapper
public interface DictModalityMapper {

    /** 1 when the value is a live, enabled detail of the unique 检查类型 catalog. */
    @Select("SELECT COUNT(1) FROM dict_detail d "
            + "JOIN dict_catal c ON c.id = d.ca_id "
            + "WHERE c.name = '检查类型' AND c.deleted = 0 "
            + "AND d.value = #{modality} AND d.enable = 1 AND d.deleted = 0")
    int countLiveModality(@Param("modality") String modality);
}
