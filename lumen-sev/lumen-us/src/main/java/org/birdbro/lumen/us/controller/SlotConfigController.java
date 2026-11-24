package org.birdbro.lumen.us.controller;

import java.util.List;

import io.swagger.annotations.ApiOperation;

import org.birdbro.lumen.us.dto.SlotConfigRequest;
import org.birdbro.lumen.us.dto.SlotConfigView;
import org.birdbro.lumen.us.service.SlotConfigService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The slot-config administration surface (us-registration-redesign-api tasks 4.1-4.3,
 * design D2): the 预约设置 console page's CRUD - served by the US SCHEMA owner (the D7
 * serialization requires same-schema), while the page itself lives in the 检查配置
 * family (proposal ③) behind the {@code setting:slot:*} permission family. The URL
 * family stays the schema owner's ({@code /api/v1/us/**}) - perm strings are
 * menu-bound, independent of the URL service.
 *
 * @author birdbro
 */
@RestController
@RequestMapping("/api/v1/us")
public class SlotConfigController {

    private final SlotConfigService slotConfigService;

    public SlotConfigController(SlotConfigService slotConfigService) {
        this.slotConfigService = slotConfigService;
    }

    @ApiOperation("预约设置列表（结构化开台时间窗）")
    @PreAuthorize("hasAuthority('setting:slot:list')")
    @GetMapping("/slot-configs")
    public List<SlotConfigView> list() {
        return slotConfigService.list();
    }

    @ApiOperation("新建预约设置（模态 live-unique，token trim 存原文）")
    @PreAuthorize("hasAuthority('setting:slot:create')")
    @PostMapping("/slot-configs")
    public SlotConfigView create(@RequestBody SlotConfigRequest request) {
        return slotConfigService.create(request);
    }

    @ApiOperation("编辑预约设置（策略字段；模态范围不可变更）")
    @PreAuthorize("hasAuthority('setting:slot:update')")
    @PutMapping("/slot-configs/{id}")
    public SlotConfigView update(@PathVariable Integer id,
            @RequestBody SlotConfigRequest request) {
        return slotConfigService.update(id, request);
    }

    @ApiOperation("删除预约设置（软删；该模态预登记随即 fail-closed）")
    @PreAuthorize("hasAuthority('setting:slot:delete')")
    @DeleteMapping("/slot-configs/{id}")
    public void delete(@PathVariable Integer id) {
        slotConfigService.delete(id);
    }
}
