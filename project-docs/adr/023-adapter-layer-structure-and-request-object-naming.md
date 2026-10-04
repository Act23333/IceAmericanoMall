# ADR-023: 四层服务 adapter 层结构统一（按受众分子包 + 入参对象化 `XxxReq`）

> **修订指针（2026-10-04）**：决策 1 目录示意中的转换器子包 `convert/` 已由 [ADR-038](./038-full-ddd-migration.md)（用户裁决）更名为 **`assembler/`** 并全仓统一，下方目录树已同步更新；其余决策（按受众分子包、入参对象化 `XxxReq`、响应对象归位 `response/`、测试包对齐）继续有效。

## 背景

四层 DDD 服务（authorization / user / merchant / verification / notification / wallet）的 `adapter/` 包在同一代码库里存在三套并存的组织方式：

1. **按受众分子包**（authorization 的 `web/` + `admin/` + `internal/`）；
2. **按聚合名分子包**（merchant 的 `seller/` + `open/`；user 的 `user/` + `admin/` + `internal/`）；
3. **控制器直接平铺在 `adapter/` 根下**（verification、notification、wallet）。

同时存在两类具体缺陷：

- **响应对象与控制器同级混放**：authorization 的 `RoleVO` / `PermissionVO` 放在 `adapter/admin/` 里与 Controller 同级，而其余 VO 在 `adapter/response/`；
- **入站对象形式不一**：authorization `RoleController` 用 `@RequestBody Map<String,Object>` 裸入参（字段靠字符串 key 取值、无校验注解、拼错不报错）；
- **测试包与主包错位**：authorization 的 `LoginRequestAssemblerTest` 在 `adapter.web.convertor`，被测试类在 `adapter.convertor`；策略/领域测试散落在 `auth.login` / `auth.domain` 等主包不存在的包下。

## 决策

**1）`adapter/` 只按「受众」这一个维度分子包**，全服务一致：

```
adapter/
├── web/       前台与开放接口 Controller（含 /api/seller/** 商家端、/api/shop/** 公开页）
├── admin/     管理端 Controller（/api/admin/**）
├── internal/  内部接口 Controller（/internal/**）
├── request/   全部 *Req（平铺；>15 个类才允许按聚合名再分一级）
├── response/  全部 *VO（平铺）
└── assembler/ 适配层转换器（如有）
```

`adapter/resolver/`（authorization 的 `DeviceTypeResolver`）保留：它是请求上下文解析器而非分组维度，且与架构规范中 `adapter/resolver` 的定位一致。

**2）入参全部对象化并统一后缀 `XxxReq`**：

- 所有 `@RequestBody` 必须是类型化 `*Req`（禁止 `Map` / `JsonObject` / 裸标量 body），带校验注解 + `@Valid`；
- GET 查询参数 ≥ 3 个的封装为 `*Req`（Spring 属性绑定，**query 参数名不变**）；
- `DELETE /{id}` 类无 body 不封装；
- **跨服务契约对象 `XxxCO`（ia-api）不作为 HTTP 入参规范的对象**：`*_CO` 是 Feign 契约，`/internal/**` 端点的请求体继续用 CO（改 CO 会破坏调用方）；公开端点若与内部端点共用同一 CO，则在适配层另声明 `*Req`（字段名与校验口径保持一致），对外报文不变。

**3）响应对象一律放 `adapter/response/`**，与 Controller 分包；VO 只保留对外所需字段。

**4）测试包与主包对齐**：测试类所在包 = 被测试类所在包。

## 备选方案

| 方案 | 优点 | 缺点 |
| :--- | :--- | :--- |
| 维持现状（三种组织方式并存） | 零改动 | 新代码无从参照，同类对象放哪全凭直觉；`Map` 入参无编译期保护 |
| 按「聚合名」分子包（`adapter/user`、`adapter/seller`、`adapter/shop`…） | 与 `domain/model/<聚合>` 同构 | 跨聚合的通用端点（登录、验证码）无处安放；`internal/**` 会散落到各聚合包 |
| **按「受众」分子包（本决策）** | 与网关路由/鉴权边界同构（`/api/**` vs `/api/admin/**` vs `/internal/**`，见 02-Architecture §路由）；一眼可判断新端点该放哪 | 一次性目录搬迁，需要同步测试包与 `scanBasePackages` |
| 入参继续用 `Map`，只加文档约定 | 无搬迁成本 | 字段名拼错编译期不报错、无法挂校验注解、Swagger 无法生成 schema |

## 后果

**正面**

- 新增端点有唯一解：先看受众（前台/管理端/内部），再看对象类型（Req/VO/转换器）；
- 适配层入参获得编译期保护 + 声明式校验（`@NotBlank` / `@Min` 等），非法值在进业务前被拦下并归一到 `PARAM_ERROR`(1101/HTTP 400)；
- `internal/**` 与 `admin/**` 的 Controller 在包结构上即被隔离，误把内部端点暴露为公开端点需要「改包名」才能发生，降低越权暴露风险。

**负面 / 风险**

- 目录搬迁是纯机械改动，但**必须同步**：测试包声明、`@MapperScan` / `scanBasePackages`（本次未受影响，均指向 `infrastructure/persistence`）、任何硬编码包名字符串；
- 搬迁期间与并行重构的其他服务存在「同一文件两处改」的合并冲突风险 —— 故本次**限定在授权组负责的服务**内执行，不跨组扩大改动面。

## 日期

2026-10-03

## 参考

- `project-docs/adr/003-object-isolation-do-dto-vo.md`（DO/DTO/VO 隔离）
- `project-docs/adr/015-package-and-contract-naming.md`（`app` 包名与 `XxxCO` 契约命名）
- `project-docs/adr/009-package-namespace-unification.md`（`scanBasePackages` / `imports` 二选一）
- `project-docs/backend/02-Architecture.md`（§路由与分层）
- 涉及服务：authorization / user / merchant / verification / notification / wallet
