package org.birdbro.lumen.system.controller;

import java.util.List;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;

import org.birdbro.lumen.system.entity.DictCityEntity;
import org.birdbro.lumen.system.service.IDictCityService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Administrative-divisions reference endpoints (dict-city-reference task 2.1, design
 * D3): READ-ONLY queries over the seeded {@code public.dict_city} lineage snapshot,
 * under the {@code /api/v1/system} namespace nginx routes to this service (Gateway
 * Path Convention). All endpoints stay token-guarded by the copied security chain
 * ({@code anyRequest().authenticated()}).
 *
 * <p>Deliberately NO POST/PUT/DELETE and NO {@code @Log}: static reference data has no
 * write path - updates arrive as later seed migrations (V5+), never through the API
 * (user decision 2026-11-03: no frontend CRUD pages either).</p>
 *
 * <p>The copied {@code GlobalResponseAdvice} wraps every return into the standard
 * envelope ({@code code:"200"}).</p>
 *
 * @author birdbro
 */
@Api(tags = "省市区字典")
@RestController
@RequestMapping("/api/v1/system/dicts/city")
public class CityController {

    private final IDictCityService dictCityService;

    public CityController(IDictCityService dictCityService) {
        this.dictCityService = dictCityService;
    }

    /** The assembled divisions tree (root = 中国, children nested by {@code upId}). */
    @ApiOperation("省市区树（中国根，按 upId 装配）")
    @GetMapping("/tree")
    public List<DictCityEntity> tree() {
        return dictCityService.tree();
    }

    /** Flat divisions with both filters optional (e.g. level=1 provinces, upId=110100
     *  = 北京市's districts). */
    @ApiOperation("省市区列表（level/upId 双可选过滤）")
    @GetMapping("/list")
    public List<DictCityEntity> list(@RequestParam(required = false) Integer level,
                                     @RequestParam(required = false) Integer upId) {
        return dictCityService.list(level, upId);
    }
}
