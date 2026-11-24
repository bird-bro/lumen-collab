package org.birdbro.lumen.core.mapper;

import java.io.IOException;
import java.util.Arrays;

import org.birdbro.lumen.core.entity.SlotWindow;

/**
 * TYPED jsonb read handler for {@code exam_slot_config.open_windows} (the
 * {@code List<SlotWindow>} column - the ExamItemRefListJsonbTypeHandler precedent):
 * MyBatis-Plus' {@code JacksonTypeHandler} constructs field handlers through the raw
 * {@code (Class)} constructor, so the generic parent parses
 * {@code List<LinkedHashMap>} and typed element access dies with a ClassCastException.
 * This subclass pins the {@code SlotWindow[]} element type for reads; writes stay on
 * the parent (Jackson serializes the beans to the documented wire shape).
 *
 * @author birdbro
 */
public class SlotWindowListJsonbTypeHandler extends KernelJsonbTypeHandler {

    /** MyBatis constructs field handlers with the mapped field's type (unused here). */
    public SlotWindowListJsonbTypeHandler(Class<?> type) {
        super(type);
    }

    /**
     * Parses pinned to the {@code SlotWindow[]} element type (the MP 3.5.3.1 raw-class
     * constructor limitation). Malformed jsonb fails the parse LOUDLY - the capacity
     * validation treats an unparseable window set as fail-closed (design Risks row 4).
     */
    @Override
    protected Object parse(String json) {
        try {
            return Arrays.asList(getObjectMapper().readValue(json, SlotWindow[].class));
        } catch (IOException e) {
            throw new IllegalArgumentException("开台时间窗 jsonb 解析失败: " + e.getMessage(), e);
        }
    }
}
