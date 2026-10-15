# 前端对接清单（V5.14-V5.18 增量）

> 最后更新: 2026-10-15 | 版本: V1.1
>
> 归属域: product | Owner: product-manager | 更新触发条件: 联调范围/契约变更时
>
> 面向前端（Web H5 / 微信小程序）的契约对接清单，覆盖 V5.14-V5.18 五个批次的全部新增端点与行为约定。
> 契约正文（报文细节/错误码）以 `contract/05-API-Specification.md` 为唯一真相源，本清单是**按页面场景组织的对接视图**。
> 通用约定：响应壳 `Result<T>`（`code=200` 为成功）；金额一律**整数分**；JWT 经 `Authorization: Bearer`；网关错误透出口径见 ADR-004。

---

## 一、登录（小程序专属）

| 端点 | 说明 |
|---|---|
| `POST /api/auth/wechat/mini/login` | 小程序 `wx.login()` 取 code → 换 JWT 与用户信息（V5.1 已有，直接对接） |

- `wx_openid` 由后端落 `user_auth`；后续支付拉起所需 openid 与登录同源（见 §五 PAY7）。
- **验证码场景（ADR-051，Web 端短信/邮箱流程适用）**：发码 `POST /code/issue` 的 `scene` **必填**且必须与页面流程一致（注册页 `REGISTER` / 登录页 `LOGIN` / 重置密码页 `RESET_PASSWORD`）——场景参与码池隔离，**错场景的验证码校验必然失败**；同一手机号各场景重发限流（60s）与失败计数（5 次作废）独立计算。注册接口对已注册（带密码）手机号直接返回 `PHONE_ALREADY_REGISTERED`（前端引导跳登录，无需再发码）。

## 二、拼团（V5.14）

| 场景 | 端点 | 要点 |
|---|---|---|
| 活动浏览 | `GET /api/group-buy/activities`（🌐）、`GET /api/group-buy/activity/{id}`（🌐，含进行中团列表） | 团列表**不含他人 userId** |
| 团进度 | `GET /api/group-buy/team/{teamNo}`（🌐） | status：1拼团中/2已成团/3已失败 |
| 开团/参团下单 | `POST /api/trade/order/group-buy`（🔒） | Body 含 `activityId`/`teamNo?`（空=开团）/`addressId`/`remark?`/`displayCurrency?`；**quantity 恒 1**；下单不占团位（未支付 30 分钟自动取消） |
| 支付成功后 | 自动入团（I53），前端无需调用 | 参团失败 → 后端自动退款闭环，前端经订单状态（已取消）+ 退款站内信感知 |
| 管理端 | `AD54-56 /api/admin/group-buy` | 创建/编辑（null-safe）/删除（进行中禁删） |

## 三、发票（V5.15/V5.16.1）

| 场景 | 端点 | 要点 |
|---|---|---|
| 常用抬头 | `INV6 GET /api/invoice/title/list`、`INV7 POST /api/invoice/title`、`INV8 PUT /api/invoice/title/{id}`、`INV9 DELETE /api/invoice/title/{id}`（🔒） | 税号只出 mask；`isDefault=1` 服务端自动清旧默认；删除默认抬头不递补 |
| 申请开票 | `INV1 POST /api/invoice/apply`（🔒） | `titleId` 引用抬头或 `title/taxNo` 现填；仅**已完成订单** + 90 天窗；一单一有效票（重复 → 400） |
| 查询 | `INV2 GET /api/invoice/history`、`INV3 GET /api/invoice/{invoiceNo}`（🔒） | `pdfKey` 仅已开具返回（30 分钟签名 URL） |
| 撤回/催开 | `INV4 PUT .../cancel`、`INV5 PUT .../urge`（🔒） | 撤回仅待开具；催开 **7 天门槛 + 24h 限频**（未到 7 天 → 400） |
| 退款联动 | 无前端动作 | 全额退款自动冲红/作废；部分退款 → `partialRefundFlag=1`（管理端人工处理，列表可筛） |
| 管理端 | `AD57-60 /api/admin/invoice` | 分页筛选（partialRefund/urgeOnly）/开具/作废/冲红（原因必填） |

## 四、知识图谱搭配推荐（V5.16/V5.16.1）

| 场景 | 端点 | 要点 |
|---|---|---|
| 商品详情搭配卡 | `P35 GET /api/item/product/{id}/pairing`（🌐，`limit` 缺省 6 ≤ 10） | 响应 `PairingCardVO { source: KG\|FALLBACK\|NONE, items: List<ProductCardVO> }`；`source=KG` 图关系命中（可展示"搭配推荐"）、`FALLBACK` 向量相似（可展示"猜你喜欢"）、`NONE` 空列表不渲染；与 P27 相似卡**同页共存**（位次随 16-Frontend 设计） |
| 管理端治理 | `AD61-65 /api/admin/kg` | 规则分页/创建/编辑（启停即时生效）/**AD65 邻居核查视图**（depth ≤ 2，>2 → 400）+ AD64 全量重建 |

## 五、多币种展示（V5.17）

| 场景 | 端点/约定 | 要点 |
|---|---|---|
| 币种列表 | `P36 GET /api/currency/list`（🌐） | 仅启用币种：`{ code, name, symbol, exponent, rateScaled, effectTime }`；60s 缓存 |
| 本币参考价换算 | **前端本地执行** | 公式：`ref = round(amountCNY × rateScaled × 10^exponent ÷ 10⁸)`（half-up 到目标币最小单位）；**主价恒 CNY**，参考价旁常驻免责文案「汇率仅供参考，实际支付金额以人民币（CNY）为准」（法务审校 O10） |
| 换算基准 | **整单以 `payAmount` 为基准** | 行价各自换算仅作参考，禁止行价求和当总额（±1 最小单位差异） |
| 偏好 | 浏览器 localStorage + locale 推断 | 未启用/未配置币种回落 CNY；接口失败仅 CNY 展示不报错 |
| 下单快照锁汇 | T1/T6/T27 请求增量 `displayCurrency?` | 传则响应 OrderVO 回显 `displayCurrency`/`displayRateScaled`（订单详情按**下单时汇率**展示参考价）；未传/降级 → null（仅 CNY） |
| 支付 | 恒 CNY | 支付页金额 = `payAmount ÷ 100`；多币种不进任何支付报文 |
| 管理端 | `AD66-68 /api/admin/currency` | 分页（含 source/effectTime，stale 高亮）/汇率修正（定点整数直传）/启停 |

**共享换算测试向量**（前端实现必须与后端未来服务端换算一致）：

| 输入 | 输出 |
|---|---|
| 12800 分（¥128.00），USD rateScaled=140300，exponent=2 | **$17.96**（1796 美分） |
| 1000 分（¥10.00），JPY rateScaled=20910000，exponent=0 | **209 円**（日元无小数位） |
| 1000 分，KRW rateScaled=190000000，exponent=0 | **190000 원** |

## 六、小程序支付拉起（V5.18）

| 场景 | 端点 | 要点 |
|---|---|---|
| 小程序内拉起支付 | `PAY7 POST /api/pay/wechat/jsapi`（🔒） | Body `{ orderNo, openid }`（openid = 登录同源）；响应六字段 `appId/timeStamp/nonceStr/packageVal/signType/paySign` **直传 `wx.requestPayment`**；`packageVal` 即 `package` 字段 |
| 支付结果 | 既有 PAY2 回调 + 轮询 | 后端回调确认订单状态；前端按订单状态轮询/订阅消息感知 |
| 既有 Native 扫码 | PAY1 不变 | Web H5 继续扫码口径；两通道共用同一 pay_order 与回调链路 |

> **联调前置**：`wechat.pay.app-id` 必须配置为**小程序 appid**（与登录 openid 同源），否则微信侧直接拒绝（appid/openid 不匹配）。

## 七、商品分享小程序码（V5.18）

| 场景 | 端点 | 要点 |
|---|---|---|
| 商品分享码 | `P37 GET /api/item/product/{id}/wxa-code`（🌐） | 返回 `WxaCodeVO { url }`（30 分钟预签名 URL）；scene=`p={productId}`，落地页 `pages/product/detail/index`；MinIO 缓存（同商品复用）；商品不存在/下架 → 404 |

## 八、既有高频契约（零改动提醒）

登录（密码/短信/邮箱/微信网页 + `mini/login`）、商品/搜索（含 SE5 联想、SE6 facets、P27 相似、P2/P1 促销标签）、购物车、订单全生命周期（T1-T5/T12/T13）、支付（PAY1-PAY5）、售后（T17-T19/AD17/AD47）、优惠券/秒杀/积分/签到/任务中心/活动中心、收藏/历史/笔记（P28-P34）、IM 客服（CH1-CH5，AI 自动转人工）、通知中心（U28-U32）。全部契约见 05 各节，**本轮零改动**。

## 契约收紧注记（ADR-050 修订，2026-10-14）

- **券业务码（管理端）**：创建券时 `couponId` 可省略（服务端生成 32-hex）；如填写必须为 32 位 hex 或 36 位连字符 UUID（统一归一 32-hex 存储），`CPN-001` 这类可读编码会被拒绝（`PARAM_ERROR`）。C 端领取/查询接口的 `couponId` 一律为 32-hex；非法值按「券不存在」处理。
- **AI 会话 ID**：`conversationId` 首次调用省略，由服务端生成（32-hex）并随响应返回；续聊时回传服务端返回值（用户侧唯一约定）。`ai_conversation`/`ai_message` 预留表列已按 `BINARY(16)` 建模，未来接入时写入值必须为该 32-hex。
- **商品评价（P12）**：请求字段由 `orderId`(Long) 改为 `orderNo`（32 位 hex，直接取订单详情 `OrderVO.orderNo`，原字段前端本无从获得、已移除）；响应 `ReviewVO.orderNo` 同步。

---

## 变更记录

| 版本 | 日期 | 变更内容 |
| :-- | :-- | :-- |
| V1.1 | 2026-10-15 | 补齐元信息头（归属域 / Owner / 更新触发条件 / 版本）——由 ADR-053 归属 product-manager；正文各节未改（V5.14-V5.18 对接内容与契约收紧注记保持原样） |
| V1.0 | 2026-10-06 | 初版：V5.14-V5.18 全部契约增量按页面场景组织（小程序登录/拼团/发票/知识图谱搭配/多币种展示/小程序支付拉起/分享小程序码）＋多币种共享换算测试向量 ＋ ADR-050 契约收紧注记 |
