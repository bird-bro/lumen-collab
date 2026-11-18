package org.birdbro.lumen.setting.entity;

import java.util.List;

import javax.validation.Valid;
import javax.validation.constraints.NotBlank;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * One section of {@link RepTempContent} (design D2): a free-text 节名 anchor plus
 * the section's 节内所见 and optional measurement rows. measures is OPTIONAL - a
 * bare section (e.g. an endoscopy 节 with no measurement rows) carries
 * {@code null} finding/measures verbatim (round-trip keeps nulls, never []).
 *
 * @author birdbro
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class RepTempSection {

    /** 节名（自由文本,必填 - 超声/内镜/放射共用,无器官词表绑定）. */
    @NotBlank(message = "分节 name 不能为空")
    private String name;

    /** 节内所见（可空）. */
    private String finding;

    /** 测量项行（可空/可空数组 - 任何节可挂）. */
    @Valid
    private List<RepTempMeasure> measures;
}
