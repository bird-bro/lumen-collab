<!-- web-us-station-redesign delta. 拍板 06-A + the menu-placement ruling (预约设置 under
     检查配置; the leaf + perms seeded by ②'s V51 — this page lights them up). -->

## ADDED Requirements

### Requirement: 预约设置 Page

The console SHALL render the 预约设置 page (menu component `setting/exam-slot/index`, the
检查配置 family leaf ② seeded) behind `setting:slot:list`, listing the per-modality
pre-registration policies from the real `/api/v1/us/slot-configs`: columns 模态 / 开台时段
粒度（分钟）/ 每时段容量 / 开台时间窗 (the windows rendered as the structured `08:00–12:00`
pair list). 新建/编辑 SHALL open ONE drawer (平铺单列分节, no Tabs — the drawer rulings:
closable end-top-right, maskClosable false, the Switch 一行式 where applicable,
ConsoleFormHint for the fail-closed semantics) collecting the modality token (a dict-1024
SELECT; immutable on edit with the stored token shown disabled), the granularity (default
30) and capacity (default 4), and the open windows (a start/end time-pair editor; empty =
未开台). Every affordance SHALL gate on `setting:slot:{create,update,delete}` (the button
nodes ② seeded); the backend's A0400 refusals (the duplicate modality, the immutable token)
render verbatim and keep the drawer open.

#### Scenario: The page lists the live policies

- **WHEN** a user holding `setting:slot:list` opens the 预约设置 leaf
- **THEN** the live per-modality policies render with the modality tokens, the
  granularity/capacity values, and the decoded window pairs from the real backend

#### Scenario: Create saves a trimmed policy

- **WHEN** the operator creates a policy (dict-picked modality, granularity, capacity,
  windows)
- **THEN** the drawer posts to the slot-configs create endpoint and the refreshed list shows
  the trimmed token with its windows

#### Scenario: The modality token is immutable on edit

- **WHEN** the operator edits an existing policy
- **THEN** the modality SELECT renders disabled with the stored token and only the policy
  fields submit

#### Scenario: Delete soft-deletes with confirmation

- **WHEN** the operator deletes a policy with the `setting:slot:delete` perm
- **THEN** a confirmation runs first, the row leaves the live list, and the modality's
  pre-registration fail-closes server-side (the safe default the UI surfaces as a hint)

#### Scenario: Affordances hide without perms

- **WHEN** the user lacks the matching `setting:slot:{create,update,delete}` perm
- **THEN** the corresponding affordance is hidden and the backend refusal path is never
  reached
