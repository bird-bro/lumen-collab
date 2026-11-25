## ADDED Requirements

### Requirement: 检查类型 List Filter

The 预约设置 list SHALL offer a 检查类型 filter in the tool row, sourced from the
same dict-1024 items the drawer's select uses. The filter SHALL narrow the table
client-side (no additional list request); clearing the filter SHALL restore the
full list.

#### Scenario: Selecting a type narrows the list in memory

- **WHEN** the operator picks a 检查类型 in the filter
- **THEN** the table shows only that type's policy row
- **AND** no additional `GET /us/slot-configs` request is issued (the narrowing
  rides the mount-time payload)

#### Scenario: Clearing the filter restores the full list

- **WHEN** the operator clears the filter (allowClear)
- **THEN** every configured policy row renders again
