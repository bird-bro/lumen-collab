# Tasks: us-booking-slot-picker-web (retrospective — all landed, evidence per task)

## 1. Trigger + dialog

- [x] 1.1 The 预约时间 cell collapses to one trigger (选择预约时段) opening the dialog with the always-visible mini calendar (D+1 domain, today grayed with 即到即检) — evidence: test:594-604; page suite green
- [x] 1.2 Day-level remaining badges aggregate client-side over the month's slot-options reads (余 N / 满; unconfigured/failed days carry none) — evidence: test:628-632 (余 5 badge)
- [x] 1.3 The slot grid groups 上午/下午; cards carry range + segmented capacity meter + remaining; full = grayed 已满 disabled; 1-remaining = 仅剩 1 号 — evidence: test:634-637

## 2. Submit semantics

- [x] 2.1 确定 writes bookingTime = `<date> <START>:00` (date ≥ tomorrow) — evidence: test:594-610
- [x] 2.2 清除预约时段 (or closing without confirming) submits a null bookingTime — walk-in path unchanged — evidence: test:656-666
- [x] 2.3 Unconfigured modality / failed lookup disables the trigger (未开通预约) without blocking walk-ins — evidence: test:671+

## 3. Verification hooks

- [x] 3.1 `pnpm build` exit 0; `pnpm test` full suite green (36 files / 504 tests; one unrelated load-flake re-run clean) — evidence: verify.md L1/L3
- [x] 3.2 `openspec validate us-booking-slot-picker-web` passes — evidence: the synced ADDED requirement (Booking Slot Dialog)
