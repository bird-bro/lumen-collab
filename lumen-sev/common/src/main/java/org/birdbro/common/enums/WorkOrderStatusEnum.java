package org.birdbro.common.enums;

/**
 * @author birdbro
 * @Date 2024-08-07 14:14
 */
public enum WorkOrderStatusEnum {

    WAIT_PROCESS(104101, "待处理"),

    PROCESSING(104102, "处理中"),

    FINISHED(104103, "已完成"),

    CLOSED(104104, "已关闭");

    private Integer code;

    private String value;

    WorkOrderStatusEnum(Integer code, String value) {
        this.code = code;
        this.value = value;
    }

    public Integer getCode() {
        return code;
    }

    public void setCode(Integer code) {
        this.code = code;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }
}
