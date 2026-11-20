# Tasks — rep-template-ux-polish-sev

## 1. Tests first (red)

- [x] 1.1 `RepTempContentApiTests`：create/update 两处 blank-code 断言翻转为
      可写通 + 无 code 键新用例
- [x] 1.2 targeted run RED（A0400 测量项 code 不能为空，1 failed，
      /tmp/ux-sev-red.log）

## 2. Relax (green)

- [x] 2.1 `RepTempMeasure.code` 移除 @NotBlank（label 保留）+ javadoc 重写
- [x] 2.2 targeted GREEN（7/7，/tmp/ux-sev-green3.log）

## 3. Verification

- [x] 3.1 lumen-system -am 全量绿（319/0/0，/tmp/ux-sev-full.log）

## 4. Spec sync readiness

- [x] 4.1 delta superset：MODIFIED Structured Template Content（4 既有场景保留
      +「Invalid content rejected」措辞去 code + 新增 code-writes-through 场景）
