package org.birdbro.lumen.core.service.impl;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Date;
import java.util.List;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;

import org.birdbro.common.enums.ExceptionEnum;
import org.birdbro.common.exception.BusinessException;
import org.birdbro.lumen.core.entity.ExamSlotConfig;
import org.birdbro.lumen.core.entity.SlotWindow;
import org.birdbro.lumen.core.entity.SlotWindowBounds;
import org.birdbro.lumen.core.mapper.ExamSlotConfigMapper;
import org.birdbro.lumen.core.service.ExamSlotCapacityService;
import org.birdbro.lumen.core.service.support.PreRegistrationPolicy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Slot-capacity validation implementation (kernel-pre-registration tasks 2.3-2.5,
 * design D2/D4/D7). Recorded decisions:
 *
 * <ol>
 *   <li>Row-lock serialization (D7): the config row is read {@code FOR UPDATE} so
 *       concurrent same-modality creates serialize on the config row inside their
 *       own create transactions - the count+insert race is mitigated at the ONE
 *       cheap lock the kernel owns. {@code REQUIRES_NEW} is deliberately NOT used:
 *       the lock must be held by the CALLER's transaction (the create flow's), so
 *       {@code MANDATORY} enforces the caller contract loudly - a validate call
 *       outside a transaction is a programming error, not a silently-unserialized
 *       create.</li>
 *   <li>The now/past boundary rides the DB-server clock (design Risks row 2) via
 *       {@code SELECT now()::timestamp} - never an app clock.</li>
 *   <li>Refusal order: unconfigured → past (delegated to the policy, the D5 token's
 *       single home) → window → capacity - each names its D5 token and nothing
 *       persists on any refusal (the caller's transaction sees only reads before
 *       the throw).</li>
 *   <li>Window math (D4): the booking's time-of-day floors onto the granularity
 *       grid; the slot's START must fall inside an open window ({@code start <=
 *       slotStart < end}, end exclusive). Malformed window entries (non-HH:mm,
 *       null fields, end <= start - no cross-midnight windows in v1) fail CLOSED,
 *       same as an empty window set.</li>
 * </ol>
 *
 * @author birdbro
 */
@Service
public class ExamSlotCapacityServiceImpl implements ExamSlotCapacityService {

    /** The D5 message tokens - refusal wording is contract, tests pin them. The past
     * token lives on the policy (the entry-semantics home, see {@link PreRegistrationPolicy}). */
    static final String MSG_UNCONFIGURED = "该检查类型未开通预登记";
    static final String MSG_OUTSIDE_WINDOW = "预登记时间不在开台时间窗内";
    static final String MSG_NO_WINDOWS = "该检查类型未设置开台时间窗，预登记暂不可用";
    static final String MSG_INVALID_WINDOWS = "开台时间窗配置无效，预登记暂不可用";
    static final String MSG_INVALID_GRANULARITY = "开台时段粒度配置无效，预登记暂不可用";
    static final String MSG_FULL = "该时段已满，请选择其他时段";

    private static final DateTimeFormatter HH_MM = DateTimeFormatter.ofPattern("HH:mm");

    private final ExamSlotConfigMapper examSlotConfigMapper;
    private final PreRegistrationPolicy preRegistrationPolicy;

    public ExamSlotCapacityServiceImpl(ExamSlotConfigMapper examSlotConfigMapper,
            PreRegistrationPolicy preRegistrationPolicy) {
        this.examSlotConfigMapper = examSlotConfigMapper;
        this.preRegistrationPolicy = preRegistrationPolicy;
    }

    @Override
    public SlotWindowBounds resolveSlotWindow(ExamSlotConfig config, Date bookingTime) {
        if (config == null) {
            // the composed caller flow reads the config itself and hands it here BEFORE
            // validate runs - an unconfigured modality must fail CLOSED at the resolve
            // surface too (the ① "Unconfigured modality refuses" scenario applied to
            // both public surfaces), never an NPE masquerading as a 500
            throw new BusinessException(ExceptionEnum.ERROR_A0400, MSG_UNCONFIGURED);
        }
        Integer granularity = config.getGranularityMinutes();
        if (granularity == null || granularity <= 0) {
            throw new BusinessException(ExceptionEnum.ERROR_A0400, MSG_INVALID_GRANULARITY);
        }
        List<SlotWindow> windows = config.getOpenWindows();
        if (CollUtil.isEmpty(windows)) {
            throw new BusinessException(ExceptionEnum.ERROR_A0400, MSG_NO_WINDOWS);
        }

        LocalDateTime booking = LocalDateTime.ofInstant(bookingTime.toInstant(),
                ZoneId.systemDefault());
        int minutesOfDay = booking.getHour() * 60 + booking.getMinute();
        int floored = (minutesOfDay / granularity) * granularity;
        LocalDateTime slotStart = booking.toLocalDate().atStartOfDay().plusMinutes(floored);
        LocalTime slotStartTime = slotStart.toLocalTime();

        for (SlotWindow window : windows) {
            LocalTime start = parseHhMm(window == null ? null : window.getStart());
            LocalTime end = parseHhMm(window == null ? null : window.getEnd());
            if (start == null || end == null || !end.isAfter(start)) {
                // malformed entry (incl. end<=start - no cross-midnight windows in v1):
                // fail CLOSED, same as an empty window set (design Risks row 4)
                throw new BusinessException(ExceptionEnum.ERROR_A0400, MSG_INVALID_WINDOWS);
            }
            if (!slotStartTime.isBefore(start) && slotStartTime.isBefore(end)) {
                LocalDateTime slotEnd = slotStart.plusMinutes(granularity);
                return SlotWindowBounds.builder()
                        .windowStart(Date.from(slotStart.atZone(ZoneId.systemDefault()).toInstant()))
                        .windowEnd(Date.from(slotEnd.atZone(ZoneId.systemDefault()).toInstant()))
                        .build();
            }
        }
        throw new BusinessException(ExceptionEnum.ERROR_A0400, MSG_OUTSIDE_WINDOW);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public SlotWindowBounds validatePreregistration(String modality, Date bookingTime,
            int occupancyCount) {
        // design D7: the config row lock is the same-slot serialization point - the
        // lock must live in the CALLER's create transaction (MANDATORY), so the count
        // query and the order insert serialize per modality behind it. The wrapper
        // .last("FOR UPDATE") idiom keeps the autoResultMap typed-jsonb mapping.
        ExamSlotConfig config = examSlotConfigMapper.selectOne(
                new LambdaQueryWrapper<ExamSlotConfig>()
                        .eq(ExamSlotConfig::getModality, modality)
                        .last("FOR UPDATE"));
        if (config == null) {
            // fail-closed: pre-registration is opt-in per modality (the safe default)
            throw new BusinessException(ExceptionEnum.ERROR_A0400, MSG_UNCONFIGURED);
        }
        Date dbNow = examSlotConfigMapper.selectDbNow();
        // future-only enforcement (design D4): the past token's single home is the
        // policy (entry semantics) - this kernel-side guard keeps a caller that
        // skipped its own derivation/guard from booking the past, fail-closed
        preRegistrationPolicy.requireFutureBooking(bookingTime, dbNow);
        SlotWindowBounds bounds = resolveSlotWindow(config, bookingTime);
        if (occupancyCount >= config.getSlotCapacity()) {
            throw new BusinessException(ExceptionEnum.ERROR_A0400, MSG_FULL);
        }
        return bounds;
    }

    /** Parses an {@code HH:mm} window bound; {@code null} when blank/unparseable (fail-closed). */
    private LocalTime parseHhMm(String raw) {
        if (StrUtil.isBlank(raw)) {
            return null;
        }
        try {
            return LocalTime.parse(raw.trim(), HH_MM);
        } catch (DateTimeParseException e) {
            return null;
        }
    }
}
