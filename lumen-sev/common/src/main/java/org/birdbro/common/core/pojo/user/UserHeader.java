package org.birdbro.common.core.pojo.user;

import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * @author birdbro
 * @date 11:22 2024-08-12
 **/
@Data
@EqualsAndHashCode(callSuper = false)
public class UserHeader {

    @ApiModelProperty("UID")
    private Integer uid;

    @ApiModelProperty("姓名")
    private String name;

    @ApiModelProperty("机构ID")
    private Integer oid;

    private String oName;

    @ApiModelProperty("部门ID")
    private String did;

    private String dName;

    private String modalitys;

    private Integer repAudit;

}
