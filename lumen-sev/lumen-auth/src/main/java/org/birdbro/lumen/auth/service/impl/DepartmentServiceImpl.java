package org.birdbro.lumen.auth.service.impl;

import java.util.List;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;

import org.birdbro.common.enums.ExceptionEnum;
import org.birdbro.common.exception.BusinessException;
import org.birdbro.lumen.auth.dto.CreateDepartmentRequest;
import org.birdbro.lumen.auth.dto.UpdateDepartmentRequest;
import org.birdbro.lumen.auth.entity.Department;
import org.birdbro.lumen.auth.entity.UnitInfo;
import org.birdbro.lumen.auth.mapper.DepartmentMapper;
import org.birdbro.lumen.auth.mapper.UnitInfoMapper;
import org.birdbro.lumen.auth.service.DepartmentService;
import org.springframework.stereotype.Service;

/**
 * Department management implementation (auth-rbac-foundation task 4.2).
 *
 * <p>Error-code mapping (recorded decision): a referenced-but-missing unit is a request
 * parameter error ({@code A0400} - the caller asked for an impossible FK; the V1
 * database FK stays as the backstop for anything that slips past, e.g. a concurrent
 * delete); a duplicate live {@code code} is {@code A0712} 组织编码已存在 (the partial
 * unique index {@code uk_department_code} stays the backstop); an update on a row that
 * is no longer live is {@code B0031}. Logical delete is a bare {@code deleteById}
 * ({@code @TableLogic} makes it {@code UPDATE deleted = 1}).</p>
 *
 * <p>Audit identity ({@code create_by}/{@code update_by}) is deliberately NOT set here:
 * the copied {@code MetaObjectConfig} fill owns it, fed by the lumen
 * {@code UserHeaderBridgeFilter} from the JWT principal.</p>
 *
 * @author birdbro
 */
@Service
public class DepartmentServiceImpl implements DepartmentService {

    private final DepartmentMapper departmentMapper;
    private final UnitInfoMapper unitInfoMapper;

    public DepartmentServiceImpl(DepartmentMapper departmentMapper, UnitInfoMapper unitInfoMapper) {
        this.departmentMapper = departmentMapper;
        this.unitInfoMapper = unitInfoMapper;
    }

    @Override
    public Integer create(CreateDepartmentRequest request) {
        requireLiveUnit(request.getUnitId());
        rejectDuplicateCode(request.getCode(), null);

        Department department = new Department();
        department.setUnitId(request.getUnitId());
        department.setCode(request.getCode());
        department.setName(request.getName());
        departmentMapper.insert(department);
        return department.getId();
    }

    @Override
    public Boolean update(Integer id, UpdateDepartmentRequest request) {
        if (departmentMapper.selectById(id) == null) {
            throw new BusinessException(ExceptionEnum.ERROR_B0031,
                    "department id " + id + " does not exist");
        }
        if (request.getUnitId() != null) {
            requireLiveUnit(request.getUnitId());
        }
        if (request.getCode() != null) {
            rejectDuplicateCode(request.getCode(), id);
        }

        Department patch = new Department();
        patch.setId(id);
        patch.setUnitId(request.getUnitId());
        patch.setCode(request.getCode());
        patch.setName(request.getName());
        departmentMapper.updateById(patch);
        return Boolean.TRUE;
    }

    @Override
    public Boolean delete(Integer id) {
        // @TableLogic turns this into UPDATE deleted = 1 (EntityBase contract).
        departmentMapper.deleteById(id);
        return Boolean.TRUE;
    }

    @Override
    public List<Department> list(Integer unitId) {
        LambdaQueryWrapper<Department> wrapper = new LambdaQueryWrapper<>();
        if (unitId != null) {
            wrapper.eq(Department::getUnitId, unitId);
        }
        wrapper.orderByAsc(Department::getId);
        // @TableLogic appends deleted = 0.
        return departmentMapper.selectList(wrapper);
    }

    /** The referenced unit must be live ({@code @TableLogic}: deleted units count as missing). */
    private void requireLiveUnit(Integer unitId) {
        UnitInfo unit = unitInfoMapper.selectById(unitId);
        if (unit == null) {
            throw new BusinessException(ExceptionEnum.ERROR_A0400,
                    "unit does not exist: unit id " + unitId);
        }
    }

    /** The stable code is unique among live rows; {@code excludeId} spares the updated row itself. */
    private void rejectDuplicateCode(String code, Integer excludeId) {
        LambdaQueryWrapper<Department> wrapper = new LambdaQueryWrapper<Department>()
                .eq(Department::getCode, code);
        if (excludeId != null) {
            wrapper.ne(Department::getId, excludeId);
        }
        if (departmentMapper.selectCount(wrapper) > 0) {
            throw new BusinessException(ExceptionEnum.ERROR_A0712,
                    "department code already exists: " + code);
        }
    }
}
