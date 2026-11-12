package org.birdbro.lumen.setting.dto.examsetting;

import lombok.Data;
import lombok.EqualsAndHashCode;

import org.birdbro.lumen.setting.entity.ExamItem;

/**
 * Item view ({@code GET /api/v1/setting/exam-items/page|/{id}}, exam-setting-api): the
 * item entity flattened with the SERVER-RESOLVED display names (D2 - the
 * anti-denormalization ruling: the body/group/method names are JOINED at read time,
 * never copied onto the item row, so a rename can never strand a stale string).
 *
 * @author birdbro
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ItemVO extends ExamItem {

    /** 检查部位名称 (joined from exam_body). */
    private String bodyName;

    /** 部位分组名称 (joined from exam_body_group through the body). */
    private String groupName;

    /** 检查方法名称串 (joined per binding, request-order, comma-joined for display). */
    private String methodNames;
}
