package org.birdbro.framework.config;

import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;

/**
 * 翻页页码
 *
 * @author birdbro
 * @date 15:06 2022-12-16
 **/
@EqualsAndHashCode(callSuper = false)
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PageSizeConfig {

    @ApiModelProperty(value = "当前页码", required = true)
    @NotNull(message = "当前页码--[pageNum] constraint is declared as NOT NULL！")
    @Min(value = 1, message = "当前页码需大于1")
    private int pageNum = 1;

    @ApiModelProperty(value = "当前页显示数量", required = true)
    @NotNull(message = "当前页显示数量--[pageSize] constraint is declared as NOT NULL！")
    @Min(value = 1, message = "当前页显示数量需大于1条")
    @Max(value = 100, message = "当前页显示数量不能大于100")
    private int pageSize = 20;


}
