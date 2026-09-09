# ADR-003: DO/DTO/VO 三层对象隔离

## 背景

需要确定数据在各层之间传递时使用什么类型的对象。有些项目直接透传 Entity 到 Controller，有的使用四层隔离（京东模式：DO/BO/DTO/VO）。

## 决策

采用 **三层对象隔离**：

| 对象              | 使用范围                              | 说明                  |
| --------------- | --------------------------------- | ------------------- |
| **DO** (Entity) | Mapper / Infra                    | 数据库实体，@TableName 映射 |
| **DTO**         | Service ↔ Controller 入参, Feign 调用 | 传输对象                |
| **VO**          | Controller → 前端                   | 视图对象，面向展示裁剪         |

**禁止**：

- ❌ Controller 直接返回 DO
- ❌ Service 入参/出参使用 DO
- ❌ Feign 直接传 DO
- ❌ VO 越过 Controller 传到 Service

## 备选方案

| 方案                 | 优点    | 缺点             |
| ------------------ | ----- | -------------- |
| 无隔离（全程 Entity）     | 代码少   | 密码泄露，字段混乱，铁定出事 |
| 京东四层(DO/BO/DTO/VO) | 最严格   | 过度设计，中小项目纯冗余   |
| **三层 DO/DTO/VO**   | 够用不冗余 | 需要维护 Converter |

## 后果

- ✅ 安全：密码哈希值不会泄露到前端
- ✅ 灵活：VO 可以按前端需求裁剪字段（列表页少字段，详情页多字段）
- ⚠️ 每个 Entity 需配套 Converter（MapStruct 自动生成，实际开发成本低）
- ⚠️ 严禁偷懒用 BeanUtils.copyProperties 跳过 Converter 定义

## 日期

2026-03-15

## 参考

- `doc/knowledge/架构分层、工程结构、微服务规范、分布式设计选择.md` §3(对象传输)
- `project-docs/04-Data-Model.md` §4 (数据隔离规则)
