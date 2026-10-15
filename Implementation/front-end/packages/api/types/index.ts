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
 * ⚠️ **query 参数不受 Jackson 策略影响** —— 由各 Controller 的
 *    `@RequestParam(name = "…")` 显式命名决定。平台决策：**线上字段与查询参数一律
 *    snake_case**（Stripe/GitHub 风格），后端已将 `@RequestParam` 统一为
 *    `@RequestParam(name = "snake_name")`（Java 形参名仍保持 camelCase）。
 *    故前端发出的查询串 key 一律 snake_case：`coupon_id` / `product_id` / `flash_id` /
 *    `address_id` / `page_num` / `page_size` / `refresh_token` …
 *
 *    **例外**（不走 `@RequestParam`，而是 POJO 属性绑定，不属于本决策的收敛范围）：
 *    `ProductPageReq` / `SearchPageReq` 的 `categoryId`、`ReviewFilterReq` 的 `hasMedia`。
 *    这两处前端暂按现名发送，待契约与后端一并定夺（见交接摘要「待确认项」）。
 *
 * ⚠️ **边界显式转换**：唯一允许「后端字段 → 前端 camelCase 域模型」的映射点是
 *    `apps/web/app/product/[id]/add-to-cart.tsx`（SkuVO → 本地 cart-store.CartItem）。
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
 * 2026-10-15 后端补齐商品展示摘要：`product_name` / `main_image` / `price` / `status`
 * 由应用层读模型按 ID 批量回填（ADR-057，fail-open）——收藏表不存商品快照。
 * 回填缺失（商品已删除 / 数据源降级）时四个字段为 `null`，收藏行照常返回，**不缩行**。
 */
export interface FavoriteVO {
  id: number;
  user_id: number;
  product_id: number;
  /** 商品名称（读时回填；缺失为 null）。 */
  product_name: string | null;
  /** 商品主图（读时回填；缺失为 null）。 */
  main_image: string | null;
  /** 商品价格（分；在售 SKU 最低价，缺失为 null）。 */
  price: number | null;
  /** 商品状态：1=上架 2=下架 3=删除（读时回填；缺失为 null）。 */
  status: number | null;
  create_time: string;
}

/** U22 `GET /api/user/history` 响应 `HistoryVO`（多词键名由显式 `@JsonProperty` 固定）。 */
export interface HistoryVO {
  id: number;
  product_id: number;
  view_time: string;
  product_name: string;
  main_image: string;
  brand: string;
  /** 商品最低价（分；无在售 SKU 时为 null）。 */
  price: number | null;
}

// ===== 商品 (item-service) =====

/** `GET /api/item/category/tree` 响应 `CategoryTreeVO`。 */
export interface CategoryVO {
  id: number;
  name: string;
  sort_order: number;
  children: CategoryVO[];
}

/** `GET /api/item/product/*` 响应 `ProductVO`。列表页仅含 `min_price`，详情页才装配 `skus`。 */
export interface ProductVO {
  id: number;
  product_id: string;
  seller_id: number;
  category_id: number;
  name: string;
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

/** `SkuVO`。 */
export interface SkuVO {
  id: number;
  sku_id: string;
  product_id: number;
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

/** `GET /api/search/product` 响应 `ProductSearchVO`（注意 `price` = 最低 SKU 价，分）。 */
export interface ProductSearchVO {
  id: number;
  product_id: string;
  category_id: number;
  name: string;
  description: string;
  brand: string;
  main_image: string;
  price: number;
  sold_count: number;
}

/** `GET /api/item/review/product/{id}(/filter)` 响应 `ReviewVO`。 */
export interface ReviewVO {
  id: number;
  user_id: number;
  username?: string | null;
  avatar?: string | null;
  product_id: number;
  /** 关联订单号（32 位 hex）。 */
  order_no: string;
  sku_id?: number | null;
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

/** `POST /api/item/review` 请求体 `ReviewCreateReq`（字段名与契约 §4.4 一致）。 */
export interface ReviewCreateReq {
  product_id: number;
  order_no: string;
  sku_id?: number;
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
  /** 关联商品 ID（快照串）。 */
  product_ids?: string | null;
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

/** `CartVO.items[]` 元素 `CartItemVO`。 */
export interface CartItemVO {
  sku_id: number;
  /**
   * 商品名称。2026-10-15 后端修正 `SkuCO` 四字段恒 null 的缺陷（productName/sellerId/
   * categoryId/freightTemplateId 现均有值）→ 本字段**现在真的有值**，渲染时勿再用
   * 「商品名恒空」的兜底把真值盖掉。
   * 残留边界：SKU 已删除/回填为空时后端 `CartManager.enrich` 早退，字段仍可能为 null
   * （与展示字段 fail-open 同口径）。
   */
  product_name: string;
  spec: string;
  image: string;
  price: number;
  quantity: number;
  selected: boolean;
  /** 小计（分）= price × quantity。 */
  sub_total: number;
}

/** `POST /api/cart/item` 请求体 `CartAddReq`。 */
export interface CartAddReq {
  sku_id: number;
  quantity?: number;
}

/** `PUT /api/cart/item` 请求体 `CartUpdateReq`（quantity ≤ 0 时删除该购物车项）。 */
export interface CartUpdateReq {
  sku_id: number;
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

/** `OrderVO.items[]` 元素 `OrderItemVO`。 */
export interface OrderItemVO {
  id: number;
  sku_id: number;
  product_name: string;
  sku_spec: string;
  price: number;
  quantity: number;
  sub_total: number;
  image: string;
  package_no?: number | null;
}

/**
 * `POST /api/trade/order` 请求体 `CreateOrderReq`。
 *
 * ⚠️ 后端**不接受** `cart_item_ids` —— 下单从服务端购物车（`/api/cart`）取件，
 * 本端只传地址 / 券 / 备注 / 展示币种。本地 Zustand 购物车与 `/api/cart` 的同步
 * 是独立缺口，见【变更交接摘要】「发现但未改」。
 * 多券叠加契约尚未支持（后端 `user_coupon_id` 为单券标量）。
 */
export interface CreateOrderReq {
  address_id: number;
  user_coupon_id?: number;
  remark?: string;
  display_currency?: string;
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
  /** 类型：1-满减 2-折扣。 */
  type: number;
  /** 面额（分）或折扣值。 */
  value: number;
  min_amount: number;
  seller_id?: number | null;
  total_qty?: number | null;
  issued_qty?: number | null;
  start_time: string;
  end_time: string;
  status?: number | null;
  discount_type?: number | null;
  coupon_category?: number | null;
  grant_type?: number | null;
  stock_type?: number | null;
  grab_type?: number | null;
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
 * 2026-10-15 后端补齐：券面三字段 `name` / `value` / `min_amount` 已**随本 VO 一并返回**
 * （同库本地 join 回填 + fail-open）→ 结算页/我的券**不再需要** `GET /api/coupon/template`
 * 做前端本地 join。
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
  /** 券面值（分；满减=减免金额，折扣=百分比口径；缺失为 null）。 */
  value: number | null;
  /** 最低消费金额（分；缺失为 null）。 */
  min_amount: number | null;
}

/**
 * `GET /api/flash` 响应 `FlashSaleVO`（契约 §4.10.2 / §8.9）。
 *
 * 2026-10-15 后端补齐商品展示字段：`product_name` / `product_image` / `original_price`
 * 由应用层读模型按 SKU ID 批量回填（ADR-057，fail-open → null）；`progress_percent`
 * 由场次聚合推导，**分母是秒杀总库存 `stock`**（不是 `stock + sold_count`）。
 */
export interface FlashSaleVO {
  id: number;
  /** 商品 ID（DB `flash_sale.product_id` NOT NULL；详情跳转依赖）。 */
  product_id: number;
  sku_id?: number | null;
  /** 商品名称（读时回填；缺失为 null）。 */
  product_name: string | null;
  /** 商品主图（读时回填；缺失为 null）。 */
  product_image: string | null;
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

/** `POST /api/flash/buy?flash_id=&address_id=` 响应 `FlashBuyVO`。 */
export interface FlashBuyVO {
  success: boolean;
  order_no?: string | null;
  flash_id?: number | null;
  flash_price?: number | null;
  product_id?: number | null;
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
  /** 派生字段（派生自 is_read）。 */
  unread?: boolean;
}

/** `GET /api/notifications/unread-count` 响应 `Map<String,Integer>` → `{"unread": n}`。 */
export interface UnreadCountVO {
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
