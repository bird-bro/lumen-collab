package org.birdbro.lumen.setting.entity;

import java.util.List;

import javax.validation.Valid;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Structured section content of a report template ({@code rep_temp.content},
 * rep-template-structured-content design D2): the department-AGNOSTIC section
 * model - a section's {@code name} is free text (超声填 心脏/肝脏, 内镜填
 * 食道/胃, 放射填 右肺上叶 - no organ/part vocabulary binding) and any section of
 * any modality MAY carry measures. Lives in the entity package ON PURPOSE: the
 * contract object {@link RepTemp} (the page response) serializes the entity
 * directly, so this shape IS the contract face. D1 exclusivity: a row is EITHER
 * prose ({@code content} NULL - finding/impression) OR structured (this object -
 * the two columns stored NULL and not read); enforced service-side on the write
 * path, not by a DB constraint. {@code version} is reserved for future structure
 * evolution and is pinned to 1 (service-layer assert).
 *
 * <p>Validation rides the Bean Validation cascade (@Valid on the DTOs): section
 * non-emptiness + nested name/code/label blanks; the D3 service layer asserts the
 * two rules Bean Validation cannot place (version pin, directory refusal).</p>
 *
 * @author birdbro
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class RepTempContent {

    /** 结构版本（当前恒为 1 - 服务层断言,为将来结构演进预留）. */
    @NotNull(message = "content.version 不能为空")
    private Integer version;

    /** 分节列表（content 携带时必填非空 - @NotEmpty）. */
    @Valid
    @NotEmpty(message = "content.sections 不能为空")
    private List<RepTempSection> sections;

    /** 全局影像学意见（可空 - 分节内所见落在各 section.finding）. */
    private String impression;
}
