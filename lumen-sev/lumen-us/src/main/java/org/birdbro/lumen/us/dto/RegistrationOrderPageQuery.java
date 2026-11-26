package org.birdbro.lumen.us.dto;

import java.util.Date;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;

import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;

import org.birdbro.framework.config.PageSizeConfig;

/**
 * The order page query (us-station-registration-sev task 3.2, design D6; widened by
 * us-registration-redesign-api design D4 - the 拍板 03-B RCP∪EIS wide-table union, the
 * two-state workbench's query row): status MULTI-select (comma-separated IN semantics;
 * empty = all), accession 检查号 substring, the patient-name substring (jsonb snapshot
 * name), the patient-id / patient-source / card-no EXACT snapshot conditions, the
 * exam-item name fuzzy (per-element over the exam_items jsonb array), and the apply-time
 * range (= create_time, the registration moment). {@code deleted = 0} rides the framework
 * {@code @TableLogic}.
 *
 * @author birdbro
 */
@Data
@EqualsAndHashCode(callSuper = false)
public class RegistrationOrderPageQuery extends PageSizeConfig {

    @ApiModelProperty("状态多选（逗号分隔，IN 语义；空=全部。BOOKED/REGISTERED/IN_PROGRESS/COMPLETED/BOOKING_CANCELLED/CANCELLED）")
    private List<org.birdbro.lumen.core.entity.ExamOrderStatus> status;

    @ApiModelProperty("检查号包含过滤（LIKE contains）")
    private String accessionNo;

    @ApiModelProperty("患者姓名包含过滤（jsonb 快照 name，LIKE contains）")
    private String patientName;

    @ApiModelProperty("患者影像号精确过滤（jsonb 快照 patId）")
    private String patientId;

    @ApiModelProperty("患者来源精确过滤（jsonb 快照 patientSource）")
    private String patientSource;

    @ApiModelProperty("就诊卡号精确过滤（jsonb 快照 cardNo）")
    private String cardNo;

    @ApiModelProperty("检查项目名称模糊过滤（exam_items jsonb 数组逐元素 itemName LIKE contains）")
    private String examItemName;

    @ApiModelProperty("申请时间起（=create_time，含）")
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    /**
     * The rail quick-search (user ruling 2026-11-26): ONE text box OR-matching the
     * identity handles - patient name, accession (检查号; the v1 影像号 rides the
     * accession family), snapshot patId/cardNo - AND-composed with the other wide
     * conditions as usual.
     */
    private String keyword;

    private Date applyTimeStart;

    @ApiModelProperty("申请时间止（=create_time，含）")
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date applyTimeEnd;
}
