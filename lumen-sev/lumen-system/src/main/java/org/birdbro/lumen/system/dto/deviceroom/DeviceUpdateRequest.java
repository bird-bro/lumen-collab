package org.birdbro.lumen.system.dto.deviceroom;

import java.util.Date;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

import com.fasterxml.jackson.annotation.JsonFormat;

import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

/**
 * Update-device request body ({@code PUT /api/v1/system/org-devices/{id}},
 * org-device-room-api): the FULL editable surface, full-replace semantics. The NOT NULL
 * columns (code/name/type/status/tag/oid) are required - the service's null-aware
 * full update (design D9) writes every editable column, so absent OPTIONAL fields CLEAR
 * them while absent required fields would violate the schema; requiring them here makes
 * that explicit. A missing device id rejects A0400.
 *
 * @author birdbro
 */
@Data
public class DeviceUpdateRequest {

    @NotBlank(message = "code must not be blank")
    @Size(max = 32, message = "code must not exceed 32 characters")
    @ApiModelProperty("设备编号")
    private String code;

    @NotBlank(message = "name must not be blank")
    @Size(max = 128, message = "name must not exceed 128 characters")
    @ApiModelProperty("设备名称")
    private String name;

    @NotNull(message = "type must not be null")
    @ApiModelProperty("设备类型 (dict 1024 code)")
    private String type;

    @NotNull(message = "status must not be null")
    @ApiModelProperty("设备状态 (dict 1025 code)")
    private Integer status;

    @NotNull(message = "tag must not be null")
    @ApiModelProperty("归属类型 (dict 1026 code)")
    private Integer tag;

    @NotNull(message = "oid must not be null")
    @ApiModelProperty("所属机构 (org_info.id)")
    private Integer oid;

    @ApiModelProperty("所属部门 (null clears the department anchor)")
    private Integer did;

    @Size(max = 64, message = "brand must not exceed 64 characters")
    @ApiModelProperty("品牌")
    private String brand;

    @Size(max = 64, message = "model must not exceed 64 characters")
    @ApiModelProperty("型号")
    private String model;

    @Size(max = 64, message = "spec must not exceed 64 characters")
    @ApiModelProperty("规格")
    private String spec;

    @Size(max = 64, message = "serialNum must not exceed 64 characters")
    @ApiModelProperty("序列号")
    private String serialNum;

    @Size(max = 128, message = "manufacturer must not exceed 128 characters")
    @ApiModelProperty("生产厂商")
    private String manufacturer;

    @JsonFormat(pattern = "yyyy-MM-dd", timezone = "GMT+8")
    @ApiModelProperty("生产日期")
    private Date productionDate;

    @JsonFormat(pattern = "yyyy-MM-dd", timezone = "GMT+8")
    @ApiModelProperty("采购日期")
    private Date purchaseDate;

    @JsonFormat(pattern = "yyyy-MM-dd", timezone = "GMT+8")
    @ApiModelProperty("验收日期")
    private Date acceptanceDate;

    @Size(max = 64, message = "responsible must not exceed 64 characters")
    @ApiModelProperty("负责人")
    private String responsible;

    @Size(max = 32, message = "responsiblePhone must not exceed 32 characters")
    @ApiModelProperty("负责人电话")
    private String responsiblePhone;

    @JsonFormat(pattern = "yyyy-MM-dd", timezone = "GMT+8")
    @ApiModelProperty("使用开始日期")
    private Date startDate;

    @JsonFormat(pattern = "yyyy-MM-dd", timezone = "GMT+8")
    @ApiModelProperty("使用结束日期")
    private Date endDate;

    @Size(max = 255, message = "remark must not exceed 255 characters")
    @ApiModelProperty("备注")
    private String remark;

    @Size(max = 64, message = "ae must not exceed 64 characters")
    @ApiModelProperty("DICOM AE")
    private String ae;

    @Size(max = 64, message = "ip must not exceed 64 characters")
    @ApiModelProperty("设备 IP")
    private String ip;

    @Size(max = 16, message = "port must not exceed 16 characters")
    @ApiModelProperty("设备 port")
    private String port;

    @Size(max = 64, message = "boxAdapter must not exceed 64 characters")
    @ApiModelProperty("Box 适配器")
    private String boxAdapter;

    @Size(max = 64, message = "boxIp must not exceed 64 characters")
    @ApiModelProperty("Box IP")
    private String boxIp;

    @Size(max = 16, message = "boxPort must not exceed 16 characters")
    @ApiModelProperty("Box port")
    private String boxPort;

    @Size(max = 64, message = "boxMac must not exceed 64 characters")
    @ApiModelProperty("Box MAC")
    private String boxMac;

    @Size(max = 128, message = "boxPrint must not exceed 128 characters")
    @ApiModelProperty("Box 打印机")
    private String boxPrint;

    @ApiModelProperty("图像采集宽")
    private Integer captureW;

    @ApiModelProperty("图像采集高")
    private Integer captureH;

    @ApiModelProperty("是否剪裁")
    private Boolean prune;

    @ApiModelProperty("剪裁上")
    private Integer pruneU;

    @ApiModelProperty("剪裁下")
    private Integer pruneD;

    @ApiModelProperty("剪裁左")
    private Integer pruneL;

    @ApiModelProperty("剪裁右")
    private Integer pruneR;
}
