package org.birdbro.lumen.us.dto;

import java.math.BigDecimal;
import java.util.List;

import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import org.birdbro.lumen.core.entity.ExamItemRef;
import org.birdbro.lumen.us.entity.UsRegistrationExt;

/**
 * The US create-order body (us-station-registration-sev task 3.2, design D6; specs/us-station
 * "Registration Surface / Create a booked order"): the identity-or-demographics the
 * patient resolution chain walks server-side through the adapter (id_no &rarr; card_no
 * &rarr; soft &rarr; register-new - the create path is ALWAYS resolve-or-register; the
 * lookup-only passthrough is the separate resolve endpoint), the 检查项目 references
 * (kernel {@link ExamItemRef} shape - the catalog id + the name snapshot taken at order
 * time; the kernel holds references + snapshots, not the catalog), the optional 急诊
 * flag, and the optional US extension block. The entry state is FIXED BOOKED (the
 * booking surface; walk-in/REGISTERED entry belongs to a later flow).
 *
 * <p>The exam-time demographics (年龄/患者来源/住院号/床号/身高/体重) ride THIS body
 * (patient decision #4: the exam-time snapshot is taken at registration) - the us create
 * service overlays them onto the adapter's delivered archive fields to build the order's
 * jsonb snapshot.</p>
 *
 * @author birdbro
 */
@Data
public class RegistrationCreateRequest {

    // ---- identity-or-demographics (the resolution chain's keys) ----

    @ApiModelProperty("证件类型（与 idNo 成对参与精确匹配）")
    private String idType;

    @ApiModelProperty("证件号码（精确匹配活档案身份锚点）")
    private String idNo;

    @ApiModelProperty("就诊卡号（软解析键）")
    private String cardNo;

    @ApiModelProperty("姓名（软匹配键之一）")
    private String name;

    @ApiModelProperty("性别 0 未知 / 1 男 / 2 女（软匹配键之一）")
    private Integer gender;

    @ApiModelProperty("联系电话（软匹配键之一）")
    private String phone;

    // ---- exam-time demographics (the snapshot riders, patient decision #4) ----

    @ApiModelProperty("检查时年龄")
    private String age;

    @ApiModelProperty("患者来源（门诊/住院/急诊/体检...）")
    private String patientSource;

    @ApiModelProperty("住院号")
    private String admissionNumber;

    @ApiModelProperty("床号")
    private String bedNumber;

    @ApiModelProperty("身高 (cm)")
    private BigDecimal height;

    @ApiModelProperty("体重 (kg)")
    private BigDecimal weight;

    // ---- order payload ----

    @ApiModelProperty("检查项目引用+名称快照（setting 域目录）")
    private List<ExamItemRef> examItems;

    @ApiModelProperty("急诊标志（展示/通知语义，缺省 0）")
    private Integer emgFlag;

    @ApiModelProperty("US 扩展块（妊娠状态/孕周/备注，可选）")
    private UsRegistrationExt ext;
}
