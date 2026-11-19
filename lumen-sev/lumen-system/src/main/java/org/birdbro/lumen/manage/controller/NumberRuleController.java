package org.birdbro.lumen.manage.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;

import org.birdbro.common.annotation.Log;
import org.birdbro.lumen.manage.dto.NumberRuleSaveRequest;
import org.birdbro.lumen.manage.dto.SetNumberRuleActiveRequest;
import org.birdbro.lumen.manage.entity.NumberRule;
import org.birdbro.lumen.manage.service.NumberRuleService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;

/**
 * The platform number-rule CRUD surface (number-rule-unification-sev task 2.2, design
 * D1; spec number-rule "Number Rule Directory"). Class gate = the page perm
 * {@code system:number:list}; the mutating methods pin their own button perms (the
 * V15/V21 discipline). The MACHINE issue endpoint lives beside it
 * ({@code /issue}) behind the internal-key guard, NOT behind user perms - services
 * issue numbers, humans administer rules.
 *
 * @author birdbro
 */
@Api(tags = "号码规则")
@Validated
@PreAuthorize("hasAuthority('system:number:list')")
@RestController
@RequestMapping("/api/v1/system/number-rules")
public class NumberRuleController {

    @Autowired
    private NumberRuleService numberRuleService;

    @ApiOperation("分页查询号码规则目录")
    @GetMapping
    public Page<NumberRule> page(@RequestParam(defaultValue = "1") long pageNum,
            @RequestParam(defaultValue = "20") long pageSize) {
        return numberRuleService.page(pageNum, pageSize);
    }

    @Log(title = "号码规则", type = "新增")
    @ApiOperation("创建号码规则")
    @PreAuthorize("hasAuthority('system:number:create')")
    @PostMapping
    public Integer create(@Valid @RequestBody NumberRuleSaveRequest request) {
        return numberRuleService.create(request);
    }

    @Log(title = "号码规则", type = "修改")
    @ApiOperation("号码规则全量更新（规则类型不可改）")
    @PreAuthorize("hasAuthority('system:number:update')")
    @PutMapping("/{id}")
    public Boolean update(@PathVariable Integer id,
            @Valid @RequestBody NumberRuleSaveRequest request) {
        return numberRuleService.update(id, request);
    }

    @Log(title = "号码规则", type = "修改")
    @ApiOperation("号码规则启停（同类型仅一条启用，A0714 拒绝冲突激活）")
    @PreAuthorize("hasAuthority('system:number:update')")
    @PutMapping("/{id}/active")
    public Boolean setActive(@PathVariable Integer id,
            @Valid @RequestBody SetNumberRuleActiveRequest request) {
        return numberRuleService.setActive(id, request.getActive());
    }

    @Log(title = "号码规则", type = "删除")
    @ApiOperation("删除号码规则（启用的规则须先停用）")
    @PreAuthorize("hasAuthority('system:number:delete')")
    @DeleteMapping("/{id}")
    public Boolean delete(@PathVariable Integer id) {
        return numberRuleService.delete(id);
    }
}
