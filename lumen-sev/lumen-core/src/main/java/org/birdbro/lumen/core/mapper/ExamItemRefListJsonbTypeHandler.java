package org.birdbro.lumen.core.mapper;

import java.io.IOException;
import java.util.Arrays;

import org.birdbro.lumen.core.entity.ExamItemRef;

/**
 * TYPED jsonb read handler for the kernel's {@code List<ExamItemRef>} columns
 * ({@code exam_order.exam_items} / {@code exam_apply_cache.target} - the mapper
 * package houses the kernel's jsonb machinery, the PatientJsonbTypeHandler
 * precedent). MyBatis-Plus' {@code JacksonTypeHandler} constructs field handlers
 * through the raw {@code (Class)} constructor - no generic info survives for a
 * collection field, so the parent parses {@code List<LinkedHashMap>} and any typed
 * element access dies with a ClassCastException. Hit for real when the accession
 * modality derivation (number-rule-modality-scope task 2.2, design D5) became the
 * kernel's FIRST element access on the mapper-read path (the HIS two-phase confirm
 * feeds the cache target straight into {@code registerOrder}). This subclass pins
 * the element type so reads decode TYPED refs; writes stay on the parent (Jackson
 * serializes the beans exactly as before - the wire shape is unchanged).
 *
 * @author birdbro
 */
public class ExamItemRefListJsonbTypeHandler extends KernelJsonbTypeHandler {

    /** MyBatis constructs field handlers with the mapped field's type (unused here). */
    public ExamItemRefListJsonbTypeHandler(Class<?> type) {
        super(type);
    }

    /**
     * Parses pinned to the {@code ExamItemRef[]} element type (the MP 3.5.3.1 raw-class
     * constructor limitation makes the generic handler yield {@code List<LinkedHashMap>}).
     * NOTE: the returned list is fixed-size ({@code Arrays.asList}) - mapper-read lists
     * are read-only projections; do not mutate them (no mutator exists today).
     */
    @Override
    protected Object parse(String json) {
        try {
            return Arrays.asList(getObjectMapper().readValue(json, ExamItemRef[].class));
        } catch (IOException e) {
            throw new IllegalArgumentException("检查项目引用 jsonb 解析失败: " + e.getMessage(), e);
        }
    }
}
