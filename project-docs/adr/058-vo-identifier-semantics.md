# ADR-058: VO 标识字段的语义唯一性 —— 技术主键 / 外键禁止出 VO

## 状态

已实施（marketing-service `UserCouponVO.coupon_id`；口径适用于全部服务的 VO/DTO）

## 背景

2026-10-15 修一个「前端按 `coupon_id` 本地 join 永远匹配不上」的缺陷时，暴露出一类**系统性风险**：

| 层 | 字段 | 实际承载 |
| --- | --- | --- |
| `coupon` 表 | `id` | 技术主键（BIGINT 自增） |
| `coupon` 表 | `coupon_id` | **业务唯一标识**（`BINARY(16)` UUIDv7，ADR-050） |
| `user_coupon` 表 | `coupon_id` | **技术外键**（BIGINT → `coupon.id`） |

于是同一个字段名 `coupon_id` 在**两张表里语义相反**，并直接导致：

- `UserCouponVO.couponId`（`Long`，投影技术主键）与 `CouponVO.couponId`（`String` 业务券号）**同名异型异义**
  → 前端 `Map` join 永不命中（`Long` 与 `String` 不相等）；
- `UserCouponVO` **暴露了技术主键**，违反既有约束「VO 不得暴露技术字段」；
- 契约样例（字符串业务券号）**本是对的**，是代码偏离了契约。

## 决策

1. **线格式（VO/DTO）中凡是与业务标识同名的字段，一律承载业务标识**（ADR-050 规定的形态：`BINARY(16)` 对应的 **32-hex**）；技术主键 / 技术外键**只保留在 PO 与领域层**，不得出现在任何 VO/DTO。
2. **同一实体在所有 DTO 中的标识必须同型同义** —— 否则跨接口关联（前端本地 join、调用方拼接）在类型层面就不可能成立。
3. **跨聚合读模型端口在按技术键批量取数时，必须顺带返回业务标识**（沿用 ADR-057 的「应用层端口 + 按 ID 批量 + fail-open」）；不得让调用方为拿业务标识再打一次库或另开端点。
4. **MapStruct 跨层同名属性若语义不同（技术外键 vs 业务标识），必须显式 `@Mapping(target = "…", ignore = true)`** 并由应用层装配器赋值；**禁止依赖自动转换**（`Long 5 → "5"` 会被静默写入，且看起来"有值"，最难排查）。
5. **业务标识的编解码只有一个出口**：`ia-common` 的 `IdGenerator`（`toHexString(byte[])` 等，全仓 20+ 处同型调用）。禁止各服务自造转换。

## 后果

**正面**：跨接口标识可 join；不再暴露可枚举的自增主键（遍历/爬取风险）；「同名异义」这类最难排查的缺陷被机械护栏拦住。
**负面 / 成本**：读模型需多带一列业务标识（同一条 SQL，成本近零）；跨层同名属性需显式 `ignore` 并写装配代码（略增样板，但换来确定性）。

## 守卫（防复发）

| 守卫 | 断言 |
| --- | --- |
| `IdGenerator` 唯一出口 | 业务标识编解码不得绕开（`check-id-invariants.sh` 管生成出口与 SQL 字面量禁令） |
| **join 兼容性测试** | 以**同一物理行**为源跑两条链路，断言两侧业务标识**字符串逐位相等**且形态合法（32-hex）；**并做变异探针**（临时改回旧语义，测试必须挂）证明测试有区分力 |
| 断言强度 | 禁止用 `assertNotNull` 了事；必须断言**具体值或形态** |
| MapStruct | 跨层同名异义处必须有 `@Mapping(ignore = true)` |

## 参考

- ADR-002（双主键）、**ADR-050**（业务标识 `BINARY(16)`+UUIDv7 + `IdGenerator` 唯一出口）
- ADR-057（展示字段的应用层读模型端口 + 批量 + fail-open）
- `contract/05-API-Specification.md` §4.10.1（`id` 与 `coupon_id` 的两 ID 口径注记）
- `collaboration/09-constraints.md`（VO 不得暴露技术字段）
