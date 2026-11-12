package org.birdbro.lumen.setting.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

import org.birdbro.common.core.pojo.user.UserHeader;
import org.birdbro.common.enums.ExceptionEnum;
import org.birdbro.common.exception.BusinessException;
import org.birdbro.common.utils.SecurityUtils;
import org.birdbro.lumen.setting.dto.examsetting.TempCreateRequest;
import org.birdbro.lumen.setting.dto.examsetting.TempPageQuery;
import org.birdbro.lumen.setting.dto.examsetting.TempUpdateRequest;
import org.birdbro.lumen.setting.entity.RepTemp;
import org.birdbro.lumen.setting.mapper.RepTempMapper;
import org.springframework.stereotype.Service;

/**
 * Report-template knowledge-base management (exam-setting-api task 5.1; concrete
 * service): the ONE-tree model over {@code rep_temp} (flag 0 目录 / 1 模板) scoped per
 * 检查类型 and split by {@code type} (1 公共 / 2 个人). THE enforcement surface:
 *
 * <ul>
 *   <li>private (type=2) creates bind {@code owner_uid} SERVER-SIDE from the session's
 *       UserHeader - the request body's ownerUid is ignored;</li>
 *   <li>every type=2 read/write is owner-scoped - another user's private rows never
 *       render anywhere and their update/delete refuses A0400 without leaking
 *       content;</li>
 *   <li>public (type=1) rows ride the menu perms (the {@code @PreAuthorize} gates).</li>
 * </ul>
 *
 * @author birdbro
 */
@Service
public class RepTempService {

    /** Scope value of the PUBLIC tree (the shared knowledge base). */
    public static final int TYPE_PUBLIC = 1;
    /** Scope value of the PRIVATE tree (one user's own phrases). */
    public static final int TYPE_PRIVATE = 2;

    private final RepTempMapper repTempMapper;
    private final ExamBodyGroupService examBodyGroupService;

    public RepTempService(RepTempMapper repTempMapper,
            ExamBodyGroupService examBodyGroupService) {
        this.repTempMapper = repTempMapper;
        this.examBodyGroupService = examBodyGroupService;
    }

    /**
     * The scope's full tree for one modality: private requests resolve to the caller's
     * OWN rows only; public requests return the shared tree. The tree assembles
     * service-side from the up_id references (roots = up_id-less nodes).
     */
    public List<Map<String, Object>> getTree(String modality, Integer type) {
        examBodyGroupService.requireModality(modality);
        List<RepTemp> rows = repTempMapper.selectList(
                scopeWrapper(modality, type).orderByAsc(RepTemp::getId));

        Map<Integer, Map<String, Object>> nodesById = new LinkedHashMap<>();
        List<Map<String, Object>> roots = new ArrayList<>();
        for (RepTemp row : rows) {
            Map<String, Object> node = new LinkedHashMap<>();
            node.put("id", row.getId());
            node.put("name", row.getName());
            node.put("flag", row.getFlag());
            node.put("upId", row.getUpId());
            node.put("children", new ArrayList<Map<String, Object>>());
            nodesById.put(row.getId(), node);
        }
        for (RepTemp row : rows) {
            Map<String, Object> parent = row.getUpId() != null
                    ? nodesById.get(row.getUpId()) : null;
            if (parent != null) {
                ((List<Map<String, Object>>) parent.get("children")).add(nodesById.get(row.getId()));
            } else {
                roots.add(nodesById.get(row.getId()));
            }
        }
        return roots;
    }

    /**
     * The templates (flag=1) under one parent node of one scope, name-filtered and
     * paged - the right-pane table feed.
     */
    public IPage<RepTemp> getPage(TempPageQuery query) {
        examBodyGroupService.requireModality(query.getModality());
        LambdaQueryWrapper<RepTemp> wrapper = scopeWrapper(query.getModality(), query.getType())
                .eq(RepTemp::getFlag, 1)
                .eq(RepTemp::getUpId, query.getUpId());
        if (query.getName() != null && !query.getName().isBlank()) {
            wrapper.like(RepTemp::getName, query.getName());
        }
        wrapper.orderByAsc(RepTemp::getId);
        return repTempMapper.selectPage(new Page<>(query.getPageNum(), query.getPageSize()),
                wrapper);
    }

    /**
     * Creates a tree node; for type=2 the owner binds from the SESSION (the request
     * body never decides ownership); returns the database-generated id.
     */
    public Integer create(TempCreateRequest request) {
        examBodyGroupService.requireModality(request.getModality());

        RepTemp temp = new RepTemp();
        BeanUtil.copyProperties(request, temp, true);
        temp.setType(request.getType());
        if (request.getType() == TYPE_PRIVATE) {
            temp.setOwnerUid(requireCurrentUid());
        } else {
            temp.setOwnerUid(null);
        }
        repTempMapper.insert(temp);
        return temp.getId();
    }

    /**
     * Full-replaces the editable surface (D9 null-aware). Scope/ownership stay as
     * stored (they are not editable); a private row refuses a non-owner.
     */
    public Boolean update(Integer id, TempUpdateRequest request) {
        RepTemp current = requireOwnedRow(id);
        examBodyGroupService.requireModality(request.getModality());

        repTempMapper.update(null, new LambdaUpdateWrapper<RepTemp>()
                .eq(RepTemp::getId, id)
                .set(RepTemp::getModality, request.getModality())
                .set(RepTemp::getFlag, request.getFlag())
                .set(RepTemp::getUpId, request.getUpId())
                .set(RepTemp::getName, request.getName())
                .set(RepTemp::getFinding, request.getFinding())
                .set(RepTemp::getImpression, request.getImpression()));
        return Boolean.TRUE;
    }

    /** Logical delete; a private row refuses a non-owner. */
    public Boolean delete(Integer id) {
        requireOwnedRow(id);
        repTempMapper.deleteById(id);
        return Boolean.TRUE;
    }

    // ===================== enforcement =====================

    /**
     * The scope wrapper: type=1 is the shared tree; type=2 ALWAYS adds the caller's
     * owner_uid - the caller's identity comes from the bridged UserHeader, so another
     * user's rows are structurally unreachable, not merely hidden.
     */
    private LambdaQueryWrapper<RepTemp> scopeWrapper(String modality, Integer type) {
        LambdaQueryWrapper<RepTemp> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(RepTemp::getModality, modality);
        if (type != null && type == TYPE_PRIVATE) {
            wrapper.eq(RepTemp::getType, TYPE_PRIVATE)
                    .eq(RepTemp::getOwnerUid, requireCurrentUid());
        } else {
            wrapper.eq(RepTemp::getType, TYPE_PUBLIC);
        }
        return wrapper;
    }

    /** The write anchor: the row exists AND (private → the caller owns it). */
    private RepTemp requireOwnedRow(Integer id) {
        RepTemp current = repTempMapper.selectById(id);
        if (current == null) {
            throw new BusinessException(ExceptionEnum.ERROR_A0400,
                    "template node id " + id + " does not exist");
        }
        if (current.getType() == TYPE_PRIVATE
                && !current.getOwnerUid().equals(requireCurrentUid())) {
            // The refusal carries NO row content - ownership is not leaked.
            throw new BusinessException(ExceptionEnum.ERROR_A0400,
                    "个人模板仅归属人可操作");
        }
        return current;
    }

    /** The session identity (UserHeaderBridgeFilter derives it from the minted session). */
    private Integer requireCurrentUid() {
        UserHeader header = SecurityUtils.getUserHeader();
        if (header == null || header.getUid() == null) {
            throw new BusinessException(ExceptionEnum.ERROR_A0400,
                    "当前会话缺少用户身份");
        }
        return header.getUid();
    }
}
