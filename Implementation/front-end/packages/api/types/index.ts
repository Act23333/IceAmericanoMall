/**
 * TypeScript 类型 — 与后端 VO/Req **逐字** 对齐（字段名 = 线上 JSON 键名）
 *
 * 来源:
 *   - project-docs/contract/05-API-Specification.md（契约）
 *   - Implementation/back-end/<service>/.../adapter/{request,response}/*.java（真实报文，字段名以此为准）
 *
 * ────────────────────────────────────────────────────────────────
 * 命名策略（本仓约定，全前端一致，不要各处另立一套）
 * ────────────────────────────────────────────────────────────────
 * 后端 15 个服务均配置 `spring.jackson.property-naming-strategy: SNAKE_CASE`，
 * 因此除显式 `@JsonProperty` 外，**响应体与请求体的 JSON 键名一律 snake_case**。
 *
 *   ✅ 映射后端报文的字段名 → snake_case（本文件所有 DTO、hook 内联 DTO、页面取值点）
 *   ✅ 前端内部标识符（变量名 / 函数名 / 组件 props 名 / 本地 state / queryKey）→ camelCase
 *   ❌ 不做全局自动 snake→camel 转换层：字段名漂移会静默变 `undefined`，掩盖真实缺陷
 *
 * ⚠️ **query 参数的线上名取决于后端「怎么绑定」，分两类**（不能只看 `@RequestParam`）：
 *
 *    ① `@RequestParam(name = "snake_name")` —— 线上名 = 注解里的名字。平台决策：**线上字段与
 *       查询参数一律 snake_case**（Stripe/GitHub 风格），后端已统一显式钉名。
 *       前端发出的查询串 key：`coupon_id` / **`spu_id`** / `flash_id` / `address_id` /
 *       `page` / `size` / `refresh_token` …
 *       ⚠️ **没有 `product_id`**：线格式禁止 `product_id`（ADR-059 §2，歧义源）。凡是
 *       「代表 SPU 实体」的标识，线上名一律 `spu_id` 且为 **32-hex `String`**（契约 §1.8）。
 *       曾用 `product_id` 发送 → 后端 `@RequestParam(name = "spu_id")` 为必填 ⇒ **400**
 *       （不是静默忽略，是硬失败）；响应侧读到 `product_id` 则恒 `undefined`（静默失效）。
 *
 *    ② `@QueryModel XxxReq` —— 形参交给 Jackson（全局 `property-naming-strategy: SNAKE_CASE`）
 *       反序列化，线上名 = **snake_case(Java 字段名)**，未知属性静默忽略。即同样是 snake_case：
 *       `category_id`（`SpuPageReq.categoryId`）/ `has_media`（`ReviewFilterReq.hasMedia`）…
 *       **`@QueryModel` 不存在 camelCase 兼容写法**，发 camelCase 会被静默忽略、字段恒 null。
 *
 *    （反例，勿模仿：裸 POJO 形参 `XxxPageReq req` 走 Spring `WebDataBinder`，线上名 = Java
 *      属性名即 camelCase，Jackson 策略覆盖不到 —— 故「POJO 绑定」必须区分这两个注解。）
 *
 *    判据以**后端实际绑定方式**为准；机器校验见
 *    `Implementation/front-end/scripts/check-query-param-naming.mjs`（前端发送名 × 后端线上名 ×
 *    契约 Query 列三方交叉断言）。
 *
 * ⚠️ **边界显式转换**：唯一允许「后端字段 → 前端 camelCase 域模型」的映射点是
 *    `apps/web/app/spu/[spuId]/add-to-cart.tsx`（SkuVO → 本地 cart-store.CartItem）。
 *    本地购物车是前端自有域模型（Zustand + localStorage），不属于报文，故保持 camelCase。
 */

// ===== 通用 =====

/**
 * 分页信封。
 * 后端两种形状字段名一致：MyBatis-Plus `IPage` 与 search-service `SearchPageVO`
 * 均为 `{records,total,size,current,pages}`。
 */
export interface PageResult<T> {
  records: T[];
  total: number;
  size: number;
  current: number;
  pages: number;
}

/** 统一响应信封 `Result<T>`。 */
export interface ApiResult<T> {
  code: number;
  msg: string;
  data: T;
}

// ===== 认证 (authorization-service) =====
// 契约 §4.1.1；后端 adapter/request/WebLoginReq.java、RegisterReq.java、response/LoginVO.java

/** A1 `POST /api/auth/web/login` 请求体。 */
export interface LoginReq {
  /** 身份类型：PHONE / USERNAME / EMAIL */
  identity_type: 'PHONE' | 'USERNAME' | 'EMAIL';
  /** 凭证类型（可缺省 = PASSWORD）。服务端按「身份 + 凭证」组合路由登录策略。 */
  credential_type?: 'PASSWORD' | 'SMS_CODE' | 'EMAIL_CODE';
  account: string;
  credential: string;
  device_id?: string;
}

/** A2 `POST /api/auth/register` 请求体。 */
export interface RegisterReq {
  phone: string;
  password: string;
  code: string;
  username?: string;
  device_id?: string;
}

/**
 * A1/A2/A6/A9 响应 `LoginVO`。
 * 注意：后端**不返回** `username`（用户信息请取 `GET /api/user/info`）。
 */
export interface LoginResp {
  access_token: string;
  token_type: string;
  expires_in: number;
  refresh_token: string;
  /** 用户 ID（Long）。A3 刷新令牌的 `OAuth2TokenResp` 不含该字段。 */
  user_id: number;
}

/** A3 `POST /api/auth/refresh?refresh_token=` 响应 `OAuth2TokenVO`（不含 user_id）。 */
export interface OAuth2TokenResp {
  access_token: string;
  token_type: string;
  expires_in: number;
  refresh_token: string;
}

/** A7 `GET /api/auth/check-session` 响应 `SessionStatusVO`。 */
export interface SessionStatusVO {
  valid: boolean;
  ttl_seconds: number;
  idle_timeout_minutes: number;
}

/** A10 / U5 公共发码 `POST /api/verification/codes` 请求体（verification-service，ADR-016 收口）。 */
export interface VerificationCodeIssueReq {
  /** 通道：SMS / EMAIL */
  channel: 'SMS' | 'EMAIL';
  /** 目标：手机号（SMS）/ 邮箱（EMAIL） */
  target: string;
  /** 业务场景：LOGIN / REGISTER / RESET_PASSWORD / USER_CODE ... */
  scene: string;
  request_id?: string;
  captcha_provider?: string;
  /** 人机验证参数集（厂商中立，key 由厂商 schema 决定）。 */
  captcha_params?: Record<string, string>;
}

/** `POST /api/verification/codes` 响应 `VerificationCodeIssueRespCO`。 */
export interface VerificationCodeIssueResp {
  success: boolean;
  reason: string;
  expire_seconds: number;
}

// ===== 用户 (user-service) =====
// 契约 §4.2.1；后端 user-service adapter/response/UserInfoResp.java

/**
 * U1/U2 `GET|PATCH /api/user/*` 响应 `UserInfoResp`。
 * V5.4 起**不含** `balance`（余额归 wallet-service，见 `GET /api/wallet`）。
 */
export interface UserInfoResp {
  /** 业务 ID（字符串，非自增主键）。 */
  user_id: string;
  username: string;
  /** 掩码手机号。 */
  phone: string | null;
  email?: string | null;
  avatar: string | null;
  gender?: number | null;
  birthday?: string | null;
  /** 会员等级 NORMAL / SILVER / GOLD / PLATINUM */
  member_level?: string | null;
  status: number | null;
  register_time: string | null;
  role_type?: number | null;
  roles?: string[];
  permissions?: string[];
}

/** U6/U8 `GET /api/user/address(/list|/{id})` 响应 `AddressResp`（V5.5 起不含 `update_time`）。 */
export interface AddressResp {
  id: number;
  user_id: number;
  receiver: string;
  phone: string;
  province: string;
  city: string;
  district: string;
  street: string;
  detail: string;
  defaulted: boolean;
  label?: string | null;
  longitude?: number | null;
  latitude?: number | null;
  create_time: string;
}

/** U7/U9 `POST /api/user/address/add`、`PUT /api/user/address/update/{id}` 请求体。 */
export interface AddressReq {
  receiver: string;
  phone: string;
  province: string;
  city: string;
  district: string;
  street: string;
  detail: string;
  defaulted?: boolean;
  label?: string;
  longitude?: number;
  latitude?: number;
}

/**
 * U20 `GET /api/user/favorite` 响应 `FavoriteVO`（契约 §4.2.6 / §8）。
 *
 * 2026-10-15 后端补齐商品展示摘要：`spu_name` / `main_image` / `price` / `status`
 * 由应用层读模型按 ID 批量回填（ADR-057，fail-open）——收藏表不存商品快照。
 * 回填缺失（商品已删除 / 数据源降级）时四个字段为 `null`，收藏行照常返回，**不缩行**。
 *
 * ⚠️ **BREAKING（B3，ADR-061）**：字段名 `product_id` / `product_name` → `spu_id` / `spu_name`，
 * 且 `spu_id` 由 `number` 改 **32-hex `String`**（后端 `FavoriteVO.spuId: String`）。
 * 旧名在报文里**不存在** ⇒ 读到的恒 `undefined`（静默失效，无报错）。
 *
 * ✅ **B8（2026-10-18）回链恢复**：P2 路径变量已改为 SPU 业务键 ⇒ `spu_id` 可**直接**
 * 寻址 `/spu/{spu_id}`（B3 期「业务键不可寻址」的限制已解除）。缺 `spu_id` 时**不得**渲染空链接。
 */
export interface FavoriteVO {
  id: number;
  user_id: number;
  /** SPU 业务键（32-hex）。与 `POST|DELETE /api/user/favorite?spu_id=` 入参**同型同义**。 */
  spu_id: string;
  /** SPU 名称（读时回填；缺失为 null）。 */
  spu_name: string | null;
  /** SPU 主图（读时回填；缺失为 null）。 */
  main_image: string | null;
  /** 商品价格（分；在售 SKU 最低价，缺失为 null）。 */
  price: number | null;
  /** 商品状态：1=上架 2=下架 3=删除（读时回填；缺失为 null）。 */
  status: number | null;
  create_time: string;
}

/**
 * U22 `GET /api/user/history` 响应 `HistoryVO`（多词键名由后端显式 `@JsonProperty` 固定）。
 *
 * ⚠️ **本 VO 是 `spu_name` 收口的唯一例外**（务必不要「顺手改对」）：
 * 后端 `HistoryVO.productName` 的 `@JsonProperty` **仍是 `product_name`**
 * （B1 批次的 14 处 `spu_name` 清单**不含**足迹端点，B2 未获授权改键名，
 * 见 `user-service/adapter/response/HistoryVO.java` javadoc 与契约 §十二 12.2【① 命名收口】）。
 * 因此本端点的线上键就是 `product_name` —— 改成 `spu_name` 会让足迹商品名整体变 `undefined`。
 *
 * ✅ **B8（2026-10-18）回链恢复**：`spu_id` 现可**直接**寻址 `/spu/{spu_id}`
 * （P2 路径变量已由技术键改为业务键）。⚠️ 但**名称键仍是 `product_name`**（见上）——
 * 回链恢复**不等于**可以顺手把它改成 `spu_name`。
 */
export interface HistoryVO {
  id: number;
  /** SPU 业务键（32-hex）。与 `POST /api/user/history?spu_id=` 入参**同型同义**。 */
  spu_id: string;
  view_time: string;
  /** SPU 名称。线上键**仍为 `product_name`**（后端未随 B2 改名，见上方说明）。 */
  product_name: string;
  /** SPU 主图。 */
  main_image: string;
  brand: string;
  /** 商品最低价（分；无在售 SKU 时为 null）。 */
  price: number | null;
}

// ===== 商品 (item-service) =====

/**
 * `GET /api/item/category/tree` 响应 —— 后端实际类型是 **`CategoryTreeVO`**
 * （注意**不是** `CategoryVO`：后者是 admin 创建/更新的出参，字段为 `id/name/parent_id/level/sort_order`，
 * **没有** `children`；两者同名不同义，本仓的 TS 名沿用了旧称）。
 * 线上键与后端 `CategoryTreeVO` 逐字一致：`id` / `name` / `sort_order` / `children`（B3 已核对）。
 */
export interface CategoryVO {
  id: number;
  name: string;
  sort_order: number;
  children: CategoryVO[];
}

/**
 * `GET /api/item/spu/*` 响应 `SpuVO`（契约 §P1/P2；标识口径 §1.8；资源段 §1.9）。
 *
 * ⚠️ **BREAKING（B3，ADR-061）**：`product_id` → **`spu_id`**（32-hex `String`）。
 * 旧名在报文里不存在 ⇒ 恒 `undefined`（静默失效）。
 *
 * ⚠️ **B8（2026-10-18，ADR-062）**：资源段 `/api/item/product/**` → `/api/item/spu/**`，
 * 且**路径变量 `{spuId}` 已由技术键 `Long` 改为 SPU 业务键 32-hex `String`**
 * ⇒ `spu_id` **现在可以直接寻址详情页**（`/spu/{spu_id}` / `GET /api/item/spu/{spu_id}`）。
 * `id` 保留为资源行主键（§1.8 例外条款，仅作渲染 key / 比对自身），与寻址无关。
 *
 * ⚠️ **名称键名（2026-10-18「甲b」，契约 §1.8④）**：本 VO 的商品名线上键是 **`spu_name`**
 * （后端 `SpuVO.spuName` 经全局 `SNAKE_CASE` 渲染），主图是 **`main_image`**。
 *
 * > 历史教义已作废（勿再引用）：本段曾断言「商品名线上键是 `name`、不是 `spu_name`」。
 * > 裁决「甲」把**凡报文中表示某个 SPU 名字的键**统一为 `spu_name` —— 因为裁决前同一概念
 * > 有两个线上键（`SpuVO` 用 `name`，回填旁路 VO 用 `spu_name`），前端按任一键取值都会在
 * > 另一侧**静默变 `undefined`**（不报错、只是显示空白），本会话多数「数据不显示」缺陷即此成因。
 * > 唯一保留的旧键是 `HistoryVO.product_name`（契约 §1.9④ 登记的例外，勿顺手统一）。
 * > 非 SPU 实体的 `name`（`CategoryVO` / `CouponVO` / `PromotionTagVO` / `RoleVO` …）**一律不动**。
 */
export interface ProductVO {
  /** 资源行主键（技术键）。§1.8 例外条款 —— **仅**作渲染 key / 自身比对，不用于寻址。 */
  id: number;
  /** SPU 业务键（32-hex）。**路由 `/spu/{spuId}` 与 P2 详情端点路径变量取此值。** */
  spu_id: string;
  seller_id: number;
  category_id: number;
  /** SPU 名 —— 线上键 `spu_name`（契约 §1.8④；后端 `SpuVO.spuName`）。 */
  spu_name: string;
  main_image: string;
  description: string;
  brand: string;
  sold_count: number;
  comment_count: number;
  /** 列表页展示价（在售 SKU 最低价，分）；无在售 SKU 时 null。 */
  min_price?: number | null;
  status: number;
  publish_time: string;
  freight_template_id?: number | null;
  /** 仅详情页返回完整 SKU 列表。 */
  skus?: SkuVO[] | null;
}

/**
 * `SkuVO`（契约 §1.8；ADR-061 已把 `sku_id` / `spu_id` 双双收口为业务键）。
 *
 * ⚠️ **BREAKING（B3）**：`product_id` → `spu_id`（32-hex `String`，**不是**数字）。
 * `spu_name` 为 B2 新增的 SPU 名回填（原 `productName`）。
 */
export interface SkuVO {
  /** 资源行主键（技术键，§1.8 例外）。 */
  id: number;
  /** SKU 业务键（32-hex）。与 `/api/cart/item` 系列入参、`CartItemVO.sku_id` 同型同义。 */
  sku_id: string;
  /** SPU 业务键（32-hex）。 */
  spu_id: string;
  /** SPU 名（回填）。 */
  spu_name?: string | null;
  spec: string;
  /** 现价（分）。 */
  price: number;
  /** 划线原价（分）。 */
  original_price?: number | null;
  stock: number;
  image: string;
  sold_count?: number;
  status?: number;
  stock_type?: number;
  is_hot?: boolean;
  hot_reason?: string | null;
  sales_tags?: string | null;
}

/**
 * `GET /api/search/spu` 响应 `SpuSearchVO`（注意 `price` = 最低 SKU 价，分）。
 * ⚠️ 后端**无** `ProductSearchVO` 类名，实现为 `SpuSearchVO`；线上键为 `spu_id`。
 * 资源段 `search/spu`（2026-10-18 B8；旧 `search/product` 已废止）。
 * ⚠️ 名字里带 `product` 的**非 SPU 段**不属本口径、照旧保留（实测均在 trade-service）：
 * `/api/trade/seller/dashboard/product-stats`（`SellerDashboardController`）、
 * `/api/trade/admin/stats/top-products`（`AdminOrderController`）。
 * ⚠️ **名称键（2026-10-18「甲b」，契约 §1.8④）**：SPU 名线上键为 **`spu_name`**
 * （后端字段 `SpuSearchVO.spuName`）—— 与 `ProductVO`、`SkuVO`、`CartItemVO` 等**同一个键**，
 * 前端不必为「搜索页 / 详情页」维护两套取值（那正是静默 `undefined` 的来源）。
 */
export interface ProductSearchVO {
  /** 资源行主键（技术键，§1.8 例外）。 */
  id: number;
  /** SPU 业务键（32-hex）。 */
  spu_id: string;
  category_id: number;
  /** SPU 名 —— 线上键 `spu_name`（契约 §1.8④；后端 `SpuSearchVO.spuName`）。 */
  spu_name: string;
  description: string;
  brand: string;
  main_image: string;
  price: number;
  sold_count: number;
}

/**
 * `GET /api/item/review/spu/{spuId}(/filter)` 响应 `ReviewVO`。
 *
 * ⚠️ **BREAKING（B3）**：`product_id` → **`spu_id`**；`spu_id` / `sku_id` 由 `number` 改
 * **32-hex `String`**（B2.5 收口，ADR-061 §8.2 处置 #1–#2）。
 * `spu_id` 为**业务键回填**（整页批量，fail-open）→ 解析未命中时为 `null`（**不回落到技术键**）。
 *
 * ⚠️ 路径变量口径（**2026-10-18 B8 已收口**）：`/api/item/review/spu/{spuId}` 的 `{spuId}`
 * 现为 **32-hex 业务键 `String`**（`ReviewController` + `IdGenerator.requireWire`），
 * 与 `ReviewVO.spu_id` **同型同义** —— 旧「资源段 `review/product/` + 技术键 `Long`」已废止
 * （ADR-062 §决策 2/3）。故前端可把列表项的 `spu_id` 直接用于寻址。
 */
export interface ReviewVO {
  id: number;
  user_id: number;
  username?: string | null;
  avatar?: string | null;
  /** SPU 业务键（32-hex）；回填未命中为 null。 */
  spu_id: string | null;
  /** 关联订单号（32 位 hex）。 */
  order_no: string;
  /** SKU 业务键（32-hex）；回填未命中为 null。 */
  sku_id?: string | null;
  rating: number;
  content: string;
  /** 逗号分隔的图片 URL 串。 */
  images?: string | null;
  /** JSON 数组串，如 `["质量好","物流快"]`。 */
  tags?: string | null;
  reply?: string | null;
  reply_time?: string | null;
  append_content?: string | null;
  append_media_urls?: string | null;
  append_time?: string | null;
  like_count?: number | null;
  liked_by_me?: boolean | null;
  create_time: string;
}

/**
 * `POST /api/item/review` 请求体 `ReviewCreateReq`（字段名与契约 §P12 一致）。
 *
 * ⚠️ **BREAKING（B3）**：请求体键为 **`spu_id`**（不是 `product_id`）；
 * `spu_id` / `sku_id` 均为 **32-hex `String`**。后端解析不到业务键 → `PARAM_ERROR` fail-fast
 * （不静默落指向不存在商品的评价）。
 */
export interface ReviewCreateReq {
  /** SPU 业务键（32-hex）。 */
  spu_id: string;
  order_no: string;
  /** SKU 业务键（32-hex）。 */
  sku_id?: string;
  rating: number;
  content: string;
  images?: string;
}

/** `GET /api/home/config` 响应 `HomeConfigVO`。 */
export interface HomeConfigVO {
  id: number;
  /** 位置标识（banner / recommend ...）。 */
  slot: string;
  type: number;
  title: string;
  /**
   * 关联商品**业务键**列表（快照串）。
   * ⚠️ 键名为 **`spu_ids`**（后端 `HomeConfigVO.spuIds`）—— 旧名 `product_ids` 在报文里不存在。
   */
  spu_ids?: string | null;
  image?: string | null;
  link_url?: string | null;
  sort_order: number;
  status: number;
}

// ===== 购物车 (cart-service) =====
// 后端 CartVO/CartItemVO 无 @JsonProperty → JSON 为 snake_case

/** `GET /api/cart` 响应 `CartVO`。 */
export interface CartVO {
  items: CartItemVO[];
  /** 全部商品总价（分）。 */
  total_price: number;
  /** 选中商品总价（分）。 */
  selected_price: number;
  all_selected: boolean;
}

/**
 * `CartVO.items[]` 元素 `CartItemVO`（契约 §4.5.1；标识口径 §1.8）。
 *
 * ⚠️ **BREAKING（B3，ADR-061）**：
 *   1. `sku_id` 由 `number` 改 **SKU 业务键 32-hex `String`** —— 必须与
 *      `DELETE /api/cart/item/{skuId}`、`PATCH /api/cart/item/{skuId}/selected`
 *      的**路径变量同型同义**（C4/C5/C10 已随 B2 按业务键收口），否则「从列表操作自己的行」
 *      在类型层不成立；
 *   2. `product_name` → **`spu_name`**（B2 已落码为 `spuName`）；
 *   3. 新增 `spu_id`（SPU 业务键，B2 新增字段，`CartItemVO.spuId`）。
 *
 * ⚠️ 本 VO **没有** `category_id` —— 故结算页的「券品类范围预过滤」无法在本端求值
 * （见 `useCheckoutCoupons` 的 fail-open 口径）。
 */
export interface CartItemVO {
  /** SKU 业务键（32-hex）。 */
  sku_id: string;
  /** SPU 业务键（32-hex）；SKU 已删除/回填降级时为 null。可回链 `/spu/{spu_id}`（B8）。 */
  spu_id: string | null;
  /**
   * SPU 名称。2026-10-15 后端修正 `SkuCO` 四字段恒 null 的缺陷（spuName/sellerId/
   * categoryId/freightTemplateId 现均有值）→ 本字段**现在真的有值**，渲染时勿再用
   * 「商品名恒空」的兜底把真值盖掉。
   * 残留边界：SKU 已删除/回填为空时后端 `CartManager.enrich` 早退，字段仍可能为 null
   * （与展示字段 fail-open 同口径）。
   */
  spu_name: string | null;
  spec: string;
  image: string;
  price: number;
  quantity: number;
  selected: boolean;
  /** 小计（分）= price × quantity。 */
  sub_total: number;
}

/** `POST /api/cart/item` 请求体 `CartAddReq`（`sku_id` 为 **SKU 业务键 32-hex**）。 */
export interface CartAddReq {
  sku_id: string;
  quantity?: number;
}

/** `PUT /api/cart/item` 请求体 `CartUpdateReq`（quantity ≤ 0 时删除该购物车项）。 */
export interface CartUpdateReq {
  sku_id: string;
  quantity: number;
}

// ===== 订单 (trade-service) =====

/** `GET|POST /api/trade/order*` 响应 `OrderVO`（详情才装配 `items`）。 */
export interface OrderVO {
  order_no: string;
  user_id: number;
  seller_id: number;
  /** 商品总额（分）= Σ 订单项小计。 */
  total_amount: number;
  /** 应付金额（分）= total_amount − discount_amount + post_fee。 */
  pay_amount: number;
  discount_amount: number;
  post_fee?: number;
  remark?: string | null;
  display_currency?: string | null;
  display_rate_scaled?: number | null;
  /** 见 OrderStatusEnum：1-待付款 2-待发货 3-待收货 4-已完成 5-已取消 6-待评价。 */
  status: number;
  order_type?: number;
  receiver_name: string;
  receiver_phone: string;
  receiver_address: string;
  create_time: string;
  pay_time?: string | null;
  consign_time?: string | null;
  end_time?: string | null;
  items?: OrderItemVO[] | null;
}

/**
 * `OrderVO.items[]` 元素 `OrderItemVO` —— **业务键标识 + 下单时点快照**（ADR-061 §决策 1）。
 *
 * ⚠️ **BREAKING（B3，ADR-061）**：
 *   - `product_name` → **`spu_name`**（快照列已改名，`Initialize.sql` L357）；
 *   - `sku_id` 由 `number` 改 **SKU 业务键 32-hex `String`**；
 *   - **新增 `spu_id`**（SPU 业务键 32-hex）—— 订单项自持商品源头标识，即使 SKU 行已删除也可落到 SPU 粒度。
 *
 * ── 比价 / 失效提示：**本批不消费任何比价字段**（契约 §十二 12.2） ──
 * 后端**未**把比价结果出线格式（`OrderCurrentStateEnricher` 的产出现无对外消费点，
 * ADR-061 §决策 3「B2 落地形态」第 5 条）；建议形态 `current_price` / `compare_state` 等
 * **未定稿**且「前端不得对接」。故本接口**刻意不声明**任何比价字段。
 *
 * 前端据此按**降级态**实现渲染准备（`OrderItemRow`）：报文无任何比价键时，
 * 必须与「**无法比对**」同款渲染 —— 只显快照，**不报错、不缩行、不骨架屏卡死、
 * 不回落显示技术 id**，且**不得**把「缺字段」推断为「已下架」（ADR-061 §决策 3 比对语义首行）。
 */
export interface OrderItemVO {
  /** 订单项行主键（技术键，§1.8 例外；仅作渲染 key）。 */
  id: number;
  /** SKU 业务键（32-hex）。 */
  sku_id: string;
  /** SPU 业务键（32-hex）—— 商品源头回链（`/spu/{spu_id}`）与比价的锚点。 */
  spu_id: string;
  /** SPU 名**快照**（下单时点事实，绝不回查实时表）。 */
  spu_name: string;
  /** 规格快照。 */
  sku_spec: string;
  /** 单价快照（分）。 */
  price: number;
  quantity: number;
  /** 小计快照（分）。 */
  sub_total: number;
  /** 图片快照。 */
  image: string;
  package_no?: number | null;
}

/**
 * `POST /api/trade/order` 请求体 `CreateOrderReq`。
 *
 * ⚠️ 后端**不接受** `cart_item_ids`（V5.4 删除并保持废弃，V5.5 复核确认 —— 传了会被忽略）
 * —— 下单商品**恒取服务端购物车（`/api/cart`）的已勾选项**（契约 §4.6.1 T1）。
 *
 * ⇒ 前端**必须**把加购/改量/勾选落到 `/api/cart/**`（见 `apps/web/app/stores/cart-model.ts`），
 *   本地购物车在已登录时只是视图；未登录时的本地购物车在登录后上行合并。
 * 多券叠加契约尚未支持（后端 `user_coupon_id` 为单券标量）。
 */
export interface CreateOrderReq {
  address_id: number;
  user_coupon_id?: number;
  remark?: string;
  display_currency?: string;
}

// ===== 交易统计 / 看板（trade-service，批次 D：Map → 类型化 VO） =====

/*
 * ── 批次 D 说明（2026-10-18）────────────────────────────────────────────────
 * 后端把这 6 个统计端点从 `Result<Map<String,Object>>` / `Result<List<Map>>` 换成强类型 VO
 * （08-agents/09-constraints「Controller 返回 Map」反模式收口）。**线上键统一 snake_case**
 * （全局 `spring.jackson.property-naming-strategy: SNAKE_CASE`）。
 *
 * ⚠️ **线上键 BREAKING**：改造前这几个端点的 Map 键是 **camelCase 字面量**
 * （`todayOrderCount` / `sellerId` / `totalUsers` …），改造后由 VO 字段名 + 命名策略产出
 * `today_order_count` / `seller_id` / `total_users` —— 与契约 §4.3.4 / §4.6.5 / §4.13.4
 * 早已声明的键**对齐**（此前是实现与契约不符）。若曾在别处按 camelCase 取值，
 * 会静默变 `undefined`，故**统一以本文件的类型为准**。
 *
 * ⚠️ **金额单位一律「分」**（`Integer`）—— 这 9 个端点里 `revenue` 的单位此前从未定义，
 * 本批钉死为「分」，前端展示时除以 100（`formatPrice`）。
 *
 * 消费现状（实测）：`apps/web` 目前**未消费**下列任何端点，故本批对 Web 无行为影响；
 * 商家端/管理端前端（`apps/seller`、`apps/admin`）后续接入时直接用这里导出的类型。
 */

/** `GET /api/trade/seller/dashboard`（S10）响应 `SellerDashboardVO`。 */
export interface SellerDashboardVO {
  /** 今日订单数（`create_time >= 今日零点`，**全状态**，单位：笔）。 */
  today_order_count: number;
  /** 待发货订单数（`status = 2 PENDING_SHIPMENT`，单位：笔）。 */
  pending_ship_count: number;
  /** 本月营收（本月已完成订单 `total_amount` 合计）—— **单位：分**（商品原价口径，不含运费、未减优惠）。 */
  monthly_revenue: number;
}

/**
 * `GET /api/trade/seller/dashboard/product-stats`（S11）与
 * `GET /api/trade/admin/stats/top-products`（AD14）**共用**的元素类型 `SpuStatVO`。
 *
 * ⚠️ 聚合键是 `order_item.spu_name` **快照**：同一 SPU 若存在多个历史快照名会被拆成多行，
 * 不同 SPU 同名会合并 —— 属后端既有口径（本批只换类型，未改算法）。
 */
export interface SpuStatVO {
  /** SPU 名称（线上键恒为 `spu_name`，契约 §1.8④）。 */
  spu_name: string;
  /** 销量（`order_item.quantity` 累加，单位：件）。 */
  quantity: number;
  /** 销售额（`order_item.sub_total` 累加，成交快照口径）—— **单位：分**。 */
  revenue: number;
}

/**
 * `GET /api/trade/admin/dashboard`（AD11）响应 `AdminDashboardVO`。
 *
 * ⚠️ 与契约 AD11 样例的差异（后端实测为准，契约已按此订正）：
 * 契约样例里的 `today_new_users` / `pending_seller_count` **代码从未产出**（本批不新增业务指标）；
 * 反之本类型里的 `completed_orders` 契约样例原先漏写。
 */
export interface AdminDashboardVO {
  /** 平台注册用户总数（跨服务读取，user-service 不可用时 fail-open 为 0）。 */
  total_users: number;
  /** 平台订单总数（全状态，单位：笔）。 */
  total_orders: number;
  /** 已完成订单数（`status = 4 COMPLETED`，单位：笔）。 */
  completed_orders: number;
  /** 累计营收 = 全部已完成订单 `total_amount` 合计 —— **单位：分**（原价口径）。 */
  total_revenue: number;
  /** 今日订单数（全状态，单位：笔）。 */
  today_orders: number;
  /** 今日营收 = 今日已完成订单 `total_amount` 合计 —— **单位：分**。 */
  today_revenue: number;
}

/** `GET /api/trade/admin/stats/trend`（AD13）响应元素 `OrderTrendVO`（近 N 天，升序）。 */
export interface OrderTrendVO {
  /** 统计日期 `yyyy-MM-dd`（「当天」取服务端 JVM 默认时区）。 */
  date: string;
  /** 当日下单数（全状态，单位：笔）。 */
  orders: number;
  /** 当日营收（当日已完成订单 `total_amount` 合计）—— **单位：分**。 */
  revenue: number;
}

/**
 * `GET /api/trade/admin/stats/sales?from=&to=`（AD15）响应元素 `SalesByDateVO`。
 *
 * 与 `OrderTrendVO` 字段同形但语义不同：本类型的区间由 `from`/`to` 显式给出（**左闭右闭**，
 * 逐日枚举含端点）；`OrderTrendVO` 是「最近 N 天，以今天为终点」。
 */
export interface SalesByDateVO {
  /** 统计日期 `yyyy-MM-dd`。 */
  date: string;
  /** 当日下单数（全状态，单位：笔）。 */
  orders: number;
  /** 当日营收（当日已完成订单 `total_amount` 合计）—— **单位：分**。 */
  revenue: number;
}

/** `GET /api/seller/finance/balance`（T22）响应 `SellerBalanceVO`。 */
export interface SellerBalanceVO {
  /** 商家标识 = 商家账号 `user_id`（**不是** `seller.id` 技术主键）。 */
  seller_id: number;
  /**
   * 可提现余额 = 所有「已结算未打款」结算单 `settlement_amount` 合计 —— **单位：分**。
   * 已扣 5% 佣金（结算单 `totalAmount` 取买家实付 `payAmount`，含运费、已减优惠），
   * 与看板 `revenue`（`total_amount` 原价口径）**不同源**；且**未扣减**在途提现申请。
   */
  balance: number;
}

// ===== 支付 (pay-service) =====

export type PayChannel = 'WECHAT' | 'ALIPAY' | 'BALANCE';

/** 支付渠道枚举值 → 展示名。 */
export const PAY_CHANNEL_TEXT: Record<PayChannel, string> = {
  WECHAT: '微信支付',
  ALIPAY: '支付宝',
  BALANCE: '余额支付',
};

/** `POST /api/pay/order/{orderNo}`、`GET /api/pay/order/{payOrderNo}/status` 响应 `PayOrderVO`。 */
export interface PayOrderVO {
  pay_order_no: string;
  biz_order_no: string;
  pay_channel_code: string;
  /** 支付金额（分）。 */
  amount: number;
  /** 支付方式（随渠道派生：NATIVE / BALANCE）。 */
  pay_type: number;
  /** 1-待支付 2-超时取消 3-成功。 */
  status: number;
  /** 支付链接（余额支付为 null）。 */
  qr_code_url?: string | null;
  pay_over_time?: string | null;
  pay_success_time?: string | null;
}

// ===== 营销 (marketing-service) =====

/** `GET /api/coupon/template` 响应 `CouponVO`（券模板；主键是字符串 `coupon_id`，无 `id`）。 */
export interface CouponVO {
  coupon_id: string;
  name: string;
  /**
   * @deprecated 历史字段 `coupon.type`，自 V4.0 起 DEPRECATED（契约 §4.10.1 明确不纳入契约，
   * 后端 `UserCouponVO` 已不投影）。**前端不得据此渲染** —— 折扣类型唯一口径为 `discount_type`
   * （券模板接口仍回传该字段仅为报文形状兼容，本仓 Web 端已 0 消费）。
   */
  type?: number | null;
  /**
   * 券面值 —— **单位随 `discount_type` 变化**：`1`/`3` 为**分**，`2` 为**百分比**（`85` = 8.5 折）。
   * ⚠️ 展示必须经 `@icedmall/utils` 的 `formatCouponFace()`，**禁止**直接 `formatPrice(value)`。
   */
  value: number;
  min_amount: number;
  seller_id?: number | null;
  total_qty?: number | null;
  issued_qty?: number | null;
  start_time: string;
  end_time: string;
  status?: number | null;
  /** 折扣类型明细口径见 `UserCouponVO.discount_type`（`1` 满减 / `2` 折扣 / `3` 代金券）。 */
  discount_type?: number | null;
  coupon_category?: number | null;
  grant_type?: number | null;
  stock_type?: number | null;
  grab_type?: number | null;
  /**
   * 付费券**售价**（分；`grant_type=PAID_PURCHASE` 时必填，见 `backend/04-Data-Model.md`）。
   * ⚠️ 不是券面额 —— 券面展示取 `value` + `discount_type`，勿用本字段当面额渲染。
   */
  price_in_cents?: number | null;
  scope_type?: number | null;
  scope_values?: string | null;
  stack_rule?: number | null;
  stack_group?: string | null;
}

/**
 * `GET /api/coupon/available`、`/used`、`POST /coupon/claim`、`/coupon/grab` 响应 `UserCouponVO`
 * （契约 §4.10.1 / §8.9）。
 *
 * 2026-10-15 后端补齐：券面四字段 `name` / `value` / `min_amount` / `discount_type` 已**随本 VO
 * 一并返回**（同库本地 join 回填 + fail-open）→ 结算页/我的券**不再需要** `GET /api/coupon/template`
 * 做前端本地 join；面额渲染口径由 `discount_type` 决定（见下方字段注释）。
 *
 * 双 ID 口径（不可混用，ADR-002 / ADR-050）：
 *   - `id`        = 持券**技术主键**（`user_coupon.id`），即下单 `CreateOrderReq.user_coupon_id`；
 *   - `coupon_id` = 券**业务券号**（`coupon.coupon_id` 的 32-hex 形态），与 `CouponVO.coupon_id`
 *                   同型同义；技术外键（`user_coupon.coupon_id` → `coupon.id`）**不再出 VO**。
 */
export interface UserCouponVO {
  id: number;
  /** 券业务券号（32-hex）；券模板已删除 / 回填降级时为 null（fail-open，持券行照常返回）。 */
  coupon_id: string | null;
  /** 1-未使用 2-已使用 3-已过期（后端 `UserCouponStatusEnum`，与 `user_coupon.status` 同口径）。 */
  status: number;
  used_order_no?: string | null;
  use_time?: string | null;
  create_time?: string;
  /** 券名称（券面回填；缺失为 null）。 */
  name: string | null;
  /**
   * 券面值（券面回填；缺失为 null）—— **单位随 `discount_type` 变化**：
   * `discount_type=1`/`3` 为**分**（`3000` = 30 元）；`discount_type=2` 为**百分比**（`85` = 8.5 折）。
   * ⚠️ 展示必须经 `@icedmall/utils` 的 `formatCouponFace()`，**禁止**直接 `formatPrice(value)`。
   */
  value: number | null;
  /** 最低消费金额（分；缺失为 null）。 */
  min_amount: number | null;
  /**
   * 折扣类型（2026-10-15 后端新增投影，契约 §4.10.1）：
   * `1`=FIXED 满减（`value` 为**分**）/ `2`=PERCENTAGE 折扣（`value` 为**百分比**，`85`=8.5 折）
   * / `3`=CASH_COUPON 代金券（`value` 为**分**）。
   *
   * `null` = 未知（券模板缺失 / 读模型降级，fail-open，与券面其余字段同口径）→
   * 前端**降级为「优惠券」文案**，绝不可按满减口径渲染。
   *
   * ⚠️ `coupon.type` 自 V4.0 起 DEPRECATED（后端不投影）→ 折扣类型唯一口径是本字段。
   */
  discount_type: number | null;
  /**
   * 券适用范围类型（B2 已补齐投影，契约 §4.10.1 / §十二 12.2【④ 券范围投影】）：
   * `1`=ALL 全场 / `2`=CATEGORY 指定品类 / `3`=PRODUCT 指定商品。
   *
   * ⚠️ **`null` 必须按 `1`（ALL）放行** —— 后端 fail-open 口径：券模板已删除 / 读模型降级时
   * `scope_type` 与 `scope_values` **同为 `null`**（不回落为 1、不回落为空串）。
   * 把 `null` 当「不适用」滤掉 = 在最需要容错时错滤可用券。
   */
  scope_type: number | null;
  /**
   * 券适用范围值（B2 已归一为 **CSV 串**，两 VO 同型同义）：
   * `scope_type=2` → 品类 id CSV；`scope_type=3` → **SPU 业务键 32-hex** CSV；`=1` → `null`。
   *
   * ⚠️ 本字段**只是体验优化**（本地预过滤），**不是安全边界** ——
   * 核销时服务端 `CouponScope.accepts` 仍做权威校验，前端预过滤错/漏都不改变能否使用。
   */
  scope_values: string | null;
}

/**
 * `GET /api/flash` 响应 `FlashSaleVO`（契约 §4.10.2 / §8.9）。
 *
 * 2026-10-15 后端补齐商品展示字段，**2026-10-17 B2 改名收口**：商品名字段为 `spu_name`、
 * 主图为 **`main_image`**（`spu_image` / `product_image` 两种旧方言均已废弃 ——
 * 理由见 ADR-061 §决策 5「为何是 `main_image`」表）；`progress_percent`
 * 由场次聚合推导，**分母是秒杀总库存 `stock`**（不是 `stock + sold_count`）。
 *
 * ⚠️ **BREAKING（B3）**：`product_id` → `spu_id`（32-hex `String`）、
 * `sku_id` 由 `number` 改 32-hex `String`、`product_name` → `spu_name`、
 * `product_image` → `main_image`。
 */
export interface FlashSaleVO {
  id: number;
  /**
   * SPU 业务键（32-hex）。
   * ⚠️ 2026-10-18 B8：P2 路径变量已改为业务键 ⇒ 本字段**现在可直接回链** `/spu/{spu_id}`。
   * （B3 期因「路径变量为技术键」而登记为不可寻址，该限制已随 ADR-062 §决策 3 解除。）
   */
  spu_id: string;
  /** SKU 业务键（32-hex）。 */
  sku_id?: string | null;
  /** SPU 名称（读时回填；缺失为 null）。 */
  spu_name: string | null;
  /** SPU 主图（读时回填；缺失为 null）。 */
  main_image: string | null;
  /** 秒杀价（分）。 */
  flash_price?: number | null;
  /** 划线原价（分；读时回填，缺失为 null）。 */
  original_price?: number | null;
  /** 秒杀**总**库存（即 `progress_percent` 的分母；剩余量 = stock − sold_count）。 */
  stock?: number | null;
  sold_count?: number | null;
  start_time: string;
  end_time: string;
  status?: number | null;
  /**
   * 已售进度百分比（整数 0–100）——后端口径 `sold_count / stock`（分母为秒杀总库存），
   * 整数向下取整、超卖夹取到 100、`stock <= 0` → 0。
   * ⚠️ 前端**不得**用 `sold/(stock+sold)` 自算（口径不符）。
   */
  progress_percent: number | null;
}

/** `POST /api/flash/buy?flash_id=&address_id=` 响应 `FlashBuyVO`（⚠️ `product_id` → `spu_id`）。 */
export interface FlashBuyVO {
  success: boolean;
  order_no?: string | null;
  flash_id?: number | null;
  flash_price?: number | null;
  /** SPU 业务键（32-hex）。 */
  spu_id?: string | null;
  remaining_stock?: number | null;
}

/** `GET /api/points/balance` 响应 `PointsBalanceVO`。 */
export interface PointsBalanceVO {
  user_id: number;
  balance: number;
}

/** `GET /api/points/history` 响应 `PointsLogVO`。 */
export interface PointsLogVO {
  id: number;
  user_id: number;
  /** 积分变动（正=获得，负=消耗）。 */
  points: number;
  /** 1-签到 2-下单 3-任务 4-兑换消耗 5-过期。 */
  type: number;
  source: string;
  create_time: string;
}

/** `POST /api/sign`、`GET /api/sign/status` 响应 `SignResultVO`。 */
export interface SignResultVO {
  /** 本次是否为当日首次签到（重复签到为 false）。 */
  signed: boolean;
  /** 本次签到获得的积分（重复签到为 0）。 */
  earned_points: number;
  /** 当月累计签到次数。 */
  month_count: number;
  /** 当前连续签到天数。 */
  continuous_days: number;
}

/**
 * `GET /api/coupon/count`（M7）响应 `UserCouponCountVO` —— 我的券三态计数。
 *
 * 批次 D：后端由 `Result<Map<String, Integer>>` 改为强类型 VO；三个线上键
 * `available` / `used` / `expired` **逐字节不变**（单词键，snake_case 无差异）⇒ **无 BREAKING**。
 * 口径与 `GET /api/coupon/available`、`/used` 的列表查询同源（`user_coupon.status` 落库态
 * 1/2/3，三者互斥且穷尽）；**0 是合法业务态**。
 */
export interface UserCouponCountVO {
  /** 未使用券数（`status = 1`，单位：张）。 */
  available: number;
  /** 已使用券数（`status = 2`，单位：张）。 */
  used: number;
  /** 已过期券数（`status = 3`，单位：张）。 */
  expired: number;
}

// ===== 通知 (notification-service) =====

/** `GET /api/notifications` 响应 `InAppMessageVO`（分页信封为 `IPage`）。 */
export interface InAppMessageVO {
  id: number;
  recipient_id: number;
  sender_id?: number | null;
  sender_name?: string | null;
  sender_avatar?: string | null;
  type: string;
  title: string;
  content?: string | null;
  link_url?: string | null;
  /** 0-未读 1-已读。 */
  is_read: number;
  create_time: string;
  // ⚠️ 曾在此声明 `unread?: boolean`（注释称「派生自 is_read」）—— **报文里没有这个键**
  //（后端 `InAppMessageVO` 仅到 `createTime`，见 `check-*` 三方交叉与 B3 全量审计）。
  // 派生字段属**视图模型**，不应挂在报文 DTO 上（会诱使调用方读到一个恒 `undefined` 的键）。
  // 未读**计数**请用 `UnreadCountVO`（`GET /api/notifications/unread-count` → `{"unread": n}`）。
  // 已按 item-4「同类静默失效」口径删除该字段（B3）。
}

/**
 * `GET /api/notifications/unread-count`（U29）响应 `UnreadCountVO` —— `{"unread": n}`。
 *
 * 批次 D：后端由 `Result<Map<String, Integer>>` 改为强类型 VO（`UnreadCountVO.of(...)` 装配）；
 * 线上键 `unread` 不变 ⇒ **无 BREAKING**（本文件与小程序 `pages/profile` 的读取点均无需改动）。
 */
export interface UnreadCountVO {
  /** 未读条数（`notification.is_read = 0`，非负；0 为合法业务态）。 */
  unread: number;
}

// ===== 售后 (trade-service) =====

/** `GET|POST /api/after-sale` 响应 `AfterSaleVO`。 */
export interface AfterSaleVO {
  id: number;
  order_no: string;
  user_id: number;
  /** 1-退货退款 2-仅退款。 */
  type: number;
  reason: string;
  status: number;
  admin_remark?: string | null;
  refund_amount?: number | null;
  logistics_number?: string | null;
  create_time: string;
}

/** `POST /api/after-sale` 请求体 `AfterSaleApplyReq`。 */
export interface AfterSaleApplyReq {
  order_no: string;
  type: number;
  reason?: string;
  refund_amount?: number;
  logistics_number?: string;
}

// ===== 商家 / 店铺 (merchant-service) =====

/** `GET /api/shop/{sellerId}`、`/api/shops/{shopId}`、`GET /api/seller/shops` 响应 `ShopVO`。 */
export interface ShopVO {
  shop_id: number;
  /** 对外 sellerId 口径 = 商家 user_id。 */
  seller_id: number;
  shop_name: string;
  shop_logo?: string | null;
  contact_phone?: string | null;
  /** 省市区摘要。 */
  address?: string | null;
  /**
   * 店铺关注数（读时回填，ADR-057；同源 `GET /api/shop/follow/count`）。
   * ⚠️ **null ≠ 0**：null = 未知（Redis 不可用），0 = 确实无人关注 → 展示层 null 时**不得**显示 0。
   */
  follower_count: number | null;
  /** 0-审核中 1-正常 2-冻结。 */
  status?: number | null;
}
