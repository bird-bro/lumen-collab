package org.birdbro.lumen.setting.service.impl;

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
import org.birdbro.lumen.setting.dto.TempCreateRequest;
import org.birdbro.lumen.setting.dto.TempPageQuery;
import org.birdbro.lumen.setting.dto.TempUpdateRequest;
import org.birdbro.lumen.setting.entity.RepTemp;
import org.birdbro.lumen.setting.mapper.RepTempMapper;
import org.birdbro.lumen.setting.service.ExamBodyGroupService;
import org.birdbro.lumen.setting.service.RepTempService;
import org.springframework.stereotype.Service;

/**
 * {@link RepTempService} implementation - the body is unchanged from the former
 * concrete class (2026-11-13 all-services interface+impl ruling; injection points keep
 * autowiring the interface FQN, so controllers and tests are untouched). The scope
 * constants resolve through the implemented interface.
 *
 * @author birdbro
 */
@Service
public class RepTempServiceImpl implements RepTempService {

    private final RepTempMapper repTempMapper;
    private final ExamBodyGroupService examBodyGroupService;

    public RepTempServiceImpl(RepTempMapper repTempMapper,
            ExamBodyGroupService examBodyGroupService) {
        this.repTempMapper = repTempMapper;
        this.examBodyGroupService = examBodyGroupService;
    }

    @Override
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

    @Override
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

    @Override
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

    @Override
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

    @Override
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
