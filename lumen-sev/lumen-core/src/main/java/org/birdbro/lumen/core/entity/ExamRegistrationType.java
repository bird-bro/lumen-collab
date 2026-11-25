package org.birdbro.lumen.core.entity;

/**
 * 登记类别 (spec exam-workflow-kernel "Exam-Order Aggregate" - Registration type
 * records the entry category; user ruling 2026-11-24 预约=预登记): WALK_IN is the
 * counter registration (现场单, no booking_time), PRE_REGISTERED is the pre-registered
 * booking (预登记, carries the booking_time slot anchor). The category is a BUSINESS
 * FACT stamped SERVER-SIDE by the create branch - it is never accepted from a client
 * body - and it is the discriminator the queue system (排队叫号) consumes instead of a
 * booking_time NULL/NOT NULL inference. Stored as the enum name in the varchar column
 * (the ExamChannel precedent).
 *
 * @author birdbro
 */
public enum ExamRegistrationType {
    /** 现场登记 (default) - the walk-in counter registration. */
    WALK_IN,
    /** 预约预登记 - the pre-registered booking with a future slot anchor. */
    PRE_REGISTERED
}
