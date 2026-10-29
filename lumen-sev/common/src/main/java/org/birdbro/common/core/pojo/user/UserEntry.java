package org.birdbro.common.core.pojo.user;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Date;

/**
 * 入职信息 user_join
 *
 * @author birdbro
 * @date 9:36 2024-08-08
 **/
@Data
@EqualsAndHashCode(callSuper = false)
public class UserEntry {

    private Integer entryId;

    @ApiModelProperty("机构ID")
    private Integer oid;

    private String oName;

    @ApiModelProperty("部门ID")
    private String did;

    private String dName;

    @ApiModelProperty("钉钉 userId")
    private String job;

    private String jobValue;

    @JsonFormat(pattern = "yyyy-MM-dd", timezone = "GMT+8")
    @ApiModelProperty("加入时间")
    private Date joinIn;

    @ApiModelProperty("默认机构")
    private Integer orgDef;

    private String remark;

    private String modalitys;

    private Integer repAudit;
}
