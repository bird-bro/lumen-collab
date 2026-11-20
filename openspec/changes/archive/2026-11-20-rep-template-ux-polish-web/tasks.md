# Tasks — rep-template-ux-polish-web

## 1. Tests (adjust with implementation)

- [x] 1.1 改名 sed（影像所见/影像意见）+ 项目 typing 删行 + payload code null 翻转
- [x] 1.2 标题回显测试重写为就地编辑断言（head 内 borderless input + 节 N 尾标 +
      body 无独立行）
- [x] 1.3 预览两测改弹框流（footer 预览按钮 /预 览|预览/ 正则 → modal 断言）

## 2. Page (green)

- [x] 2.1 卡片整块重写（就地节名/icon 化/去项目/影像所见/无插入按钮）
- [x] 2.2 预览 Modal 化 + footer 按钮；序列化 code passthrough；类型 code 可空
- [x] 2.3 targeted GREEN（42/42，/tmp/ux-web-run2.log；中途 3 红全修：PE 断言/
      预 览 空格/…）

## 3. Verification

- [x] 3.1 full web suite green（34 files / 448 tests，exit=0，/tmp/ux-web-full.log）
- [x] 3.2 `pnpm build` exit=0（/tmp/ux-web-build.log）

## 4. Spec sync readiness

- [x] 4.1 delta superset ×2：Report Template Page（13 场景：标题回显场景重写为
      就地编辑，其余保留）+ Template Placeholder Convention（4 场景随弹框化/
      改名改写）
