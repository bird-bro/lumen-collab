# Batch 1 Brief: 后端迁移 + 数据模型(tasks 1.1–1.4)

Change: rep-template-structured-content · Stack: **仅 lumen-sev**(跨域禁令:不碰 lumen-web / openspec/specs / V38 / exam-method 相关文件——那些属于另一在途会话)

## 必读(按序)
1. `openspec/changes/rep-template-structured-content/design.md` — D1(互斥)、D2(形状)、D3(校验)、D4(jsonb handler)、D5(迁移)、D8(目录拒 content)
2. `openspec/changes/rep-template-structured-content/specs/exam-setting/spec.md` — delta 场景 = 验收标准
3. jsonb handler 先例:`lumen-sev/lumen-patient/src/main/java/org/birdbro/lumen/patient/mapper/PatientJsonbTypeHandler.java`(Types.OTHER 写路线;reads 走父类)
4. 现状代码:`lumen-sev/lumen-system/src/main/java/org/birdbro/lumen/setting/` 下 `entity/RepTemp.java`、`service/impl/RepTempServiceImpl.java`、`dto/TempCreateRequest.java`、`dto/TempUpdateRequest.java`、`service/RepTempService.java`
5. 测试习惯:`lumen-sev/lumen-system/src/test/java/org/birdbro/lumen/setting/AuthRepTempApiTests.java`(现有 rep-temp api 测试 idiom + 测试库连接方式)

## 任务

**1.1 迁移** `lumen-sev/lumen-system/src/main/resources/db/migration/V39__rep_temp_content.sql`(V38 已被另一会话占用,勿动):
```sql
ALTER TABLE rep_temp ADD COLUMN IF NOT EXISTS content jsonb;
CREATE INDEX IF NOT EXISTS rep_temp_content_gin ON rep_temp USING gin (content);
```

**1.2 实体** `RepTemp` 加 `content` 字段:
- 类型 = 新内容模型 `RepTempContent`(见 1.3);`@TableField(typeHandler = RepTempContentJsonbTypeHandler.class)`
- 新 handler `setting/mapper/RepTempContentJsonbTypeHandler` 按 PatientJsonbTypeHandler 同型(setNonNullParameter → setObject(i, toJson(parameter), Types.OTHER);构造器带 Class<?>)
- Lombok 组合纪律:内容模型类 `@Data @Builder @AllArgsConstructor @NoArgsConstructor`(@Builder 单用会杀 no-arg ctor)

**1.3 内容模型 + DTO 接线**(单源共用,BeanUtil.copyProperties 1:1 house idiom 要求同型):
- `setting/entity/RepTempContent.java`:`Integer version`(必填)、`List<RepTempSection> sections`(必填)、`String impression`
- `RepTempSection.java`:`String name`(必填)、`String finding`、`List<RepTempMeasure> measures`
- `RepTempMeasure.java`:`String code`(必填)、`String label`(必填)、`String unit`、`String normal`
- javax.validation 注解放模型上:@NotNull version、@NotEmpty sections、@NotBlank name、@NotBlank code/label(@Valid 级联)
- `TempCreateRequest`/`TempUpdateRequest` 加 `@Valid RepTempContent content`
- **注意**:content 模型类放 entity 包的细化理由 = 契约对象 RepTemp(page 响应)直接用实体序列化(现状),content 即契约面;这是 D3 的落地形态,review 时按此核

**1.4 服务层校验 + 互斥**(`RepTempServiceImpl.create/update`,TDD):
- content != null 时:version 必须等于 1(服务层断言,BusinessException A0400,message 命名字段,如 "content.version 必须为 1");sections 非空(@NotEmpty 已挡);**flag=0 目录节点带 content → A0400 拒绝**;写路径**置空 finding/impression 两列**(D1 互斥)
- content == null 时:走现 prose 路径,且 entity.content 置 null(update 是全量替换——structured 行传 null content 转 prose,sections 丢弃,spec 已声明为破坏性)
- update 分支注意:现有 LambdaUpdateWrapper 逐列 .set——需按 content 有无双分支(content 有 → set content + 两列 NULL;无 → set 两列 + content NULL)

## TDD 顺序(铁律)
1. 先在 `AuthRepTempApiTests`(或同目录新 `RepTempContentApiTests`,跟随现有 idiom)写失败测试覆盖 delta 场景:
   - 分节模板 create → page 往返 content verbatim、两列为 NULL
   - 无效 content:空节名 / 空 measure code / version=2 / 目录带 content → 4xx 且 message 命名字段
   - structured 行 update content=null → 转 prose
   - tree 响应不含 content
2. 跑测试确认 **RED**(失败原因 = 功能缺失,记录到报告)
3. 最小实现 → **GREEN** → refactor 保持绿
4. 每次只跑受影响测试类,不全量

## 构建/测试命令
```bash
export JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-11.jdk/Contents/Home
cd lumen-sev && ./mvnw -pl lumen-system -am test -q   # 从 lumen-sev/ 跑(reactor-only 规则)
```
输出重定向日志文件,报告只引 exit code + 计数 + 关键失败行。测试库走共享测试服务器(.env-test.local);若连接断连/夹具互删,重试一次后仍失败即报 BLOCKED(勿盲目重试)。

## 提交
完成并通过受影响测试后提交(跟随仓库 git log 风格,如 `✅ test(sev): ...` / `✨ feat(sev): ...` 单提交包含迁移+实现+测试)。

## 报告
写入 `openspec/sdd/reports/rep-template-structured-content-batch1.md`(<80 行):实现内容、RED/GREEN 证据(命令+exit+计数)、改动文件清单、自评、concerns。回报 ≤15 行:Status(DONE|DONE_WITH_CONCERNS|BLOCKED|NEEDS_CONTEXT)+ 一行摘要 + 报告路径。
