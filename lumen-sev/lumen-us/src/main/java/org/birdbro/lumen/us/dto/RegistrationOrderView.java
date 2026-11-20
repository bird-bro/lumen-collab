package org.birdbro.lumen.us.dto;

import java.util.Date;
import java.util.List;

import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import org.birdbro.lumen.core.entity.ExamItemRef;
import org.birdbro.lumen.core.entity.ExamOrderStatus;
import org.birdbro.lumen.core.entity.PatientSnapshot;

/**
 * The order-list view row (us-station-registration-sev task 3.2, design D6/D7; specs/us-station
 * "Order List" + "US Registration Extension" - the query path returns the extension): the
 * fields the 登记 page renders (accession, patient identity, item summary, state tag, 急诊
 * marker) plus the 1:1 US extension fields, flattened from the order document + the ext
 * row. Jsonb columns serialize as their natural payloads ({@link PatientSnapshot} object,
 * {@link ExamItemRef} array - never a double-encoded string, the kernel handler contract
 * holds on the read path too).
 *
 * @author birdbro
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class RegistrationOrderView {

    // ---- the order document ----

    @ApiModelProperty("检查单 id")
    private Integer orderId;

    @ApiModelProperty("检查号")
    private String accessionNo;

    @ApiModelProperty("状态（状态机词汇）")
    private ExamOrderStatus status;

    @ApiModelProperty("患者引用（patient 域 pat_uid）")
    private Integer patientUid;

    @ApiModelProperty("患者快照（jsonb）")
    private PatientSnapshot patientSnapshot;

    @ApiModelProperty("检查项目引用+名称快照（jsonb 数组）")
    private List<ExamItemRef> examItems;

    @ApiModelProperty("急诊标志")
    private Integer emgFlag;

    @ApiModelProperty("取消原因（取消分支携带）")
    private String cancelReason;

    @ApiModelProperty("创建时间")
    private Date createTime;

    // ---- the 1:1 US extension ----

    @ApiModelProperty("妊娠状态（US 扩展，无行时为 null）")
    private String pregnancyStatus;

    @ApiModelProperty("孕周（US 扩展，无行时为 null）")
    private Integer gestationalWeeks;

    @ApiModelProperty("备注（US 扩展，无行时为 null）")
    private String note;
}
