/**
 * 站内信 `type` 取值域 + 标签页分组 —— 取值域**不是猜的**，逐个来自后端生产方常量
 *
 * ⚠️ 历史缺陷（本模块存在的唯一理由）：通知中心曾按 `LIKE / REPLY / FOLLOW / ORDER`
 * 开 4 个标签页，其中 `ORDER` **后端从不产生**（真源见下表）⇒ 该页恒空；而真正在产的
 * `PAY_SUCCESS / REFUND_SUCCESS / ORDER_SHIPPED / AFTER_SALE_REVIEWED / INVOICE_*`
 * **一个标签页都没有** ⇒ 用户只能靠「全部」看到它们。成因是照抄了 `notification` 表
 * 那条**过时**的列注释（`LIKE/REPLY/APPEND/FOLLOW/ORDER/SYSTEM`）。
 *
 * `notification-service` 自身**没有** type 枚举（`InAppMessage.type` / `InAppMessageVO.type`
 * 都是 `String`，见 `notification/domain/model/inapp/InAppMessage.java` 与
 * `adapter/response/InAppMessageVO.java`）。因此 type 的**真源是 4 个生产方的常量定义**：
 *
 * | type                 | 语义           | 生产方（文件:行，均为常量定义处）                                    |
 * | :------------------- | :------------- | :------------------------------------------------------------------ |
 * | `LIKE`               | 评价被点赞     | item `app/ReviewManager.java:49`（发出：`:223`）                    |
 * | `REPLY`              | 商家回复评价   | item `app/ReviewManager.java:48`（发出：`:252`）                    |
 * | `FOLLOW`             | 店铺新增关注   | merchant `app/seller/StoreFollowManager.java:25`（发出：`:66`）      |
 * | `ORDER_SHIPPED`      | 订单已发货     | trade `app/TradeEventNotifier.java:31`（发出：`:58`）                |
 * | `AFTER_SALE_REVIEWED`| 售后审核完成   | trade `app/TradeEventNotifier.java:32`（发出：`:78`）               |
 * | `INVOICE_ISSUED`     | 发票已开具     | trade `app/TradeEventNotifier.java:33`（发出：`:124`）              |
 * | `INVOICE_RED_FLUSHED`| 发票已红冲     | trade `app/TradeEventNotifier.java:34`（发出：`:144`）              |
 * | `INVOICE_VOIDED`     | 发票已作废     | trade `app/TradeEventNotifier.java:35`（发出：`:165`）              |
 * | `PAY_SUCCESS`        | 支付成功       | pay `app/PayEventNotifier.java:40`（发出：`:65`）                    |
 * | `REFUND_SUCCESS`     | 退款成功       | pay `app/PayEventNotifier.java:41`（发出：`:118`）                   |
 *
 * 契约口径：`contract/05-API-Specification.md` §4.2.9 U28 `Query: type?`(String 业务类型)
 * —— **契约没枚举取值**，故上游真源只能是上表的生产方常量。
 *
 * 硬规则：
 *   1. 每个后端取值都必须落在**某个**标签页里（编译期断言 + 运行期告警，见文件末尾）；
 *   2. 后端新增取值而前端没发版 → 记录**不丢弃**：文案回落原始值、标签页落「其他」；
 *   3. 标签页的服务端筛选只能按**精确单值**（`GET /api/notifications?type=` 是 `eq`），
 *      多值分组必须逐值取数后合并（见 `NOTIFICATION_TABS` 注释），不得用「发第一个值」糊过去。
 */

/** 站内信 type 取值 —— 逐值镜像上表的后端生产方常量。 */
export const NOTIFICATION_TYPE = {
  LIKE: 'LIKE',
  REPLY: 'REPLY',
  FOLLOW: 'FOLLOW',
  ORDER_SHIPPED: 'ORDER_SHIPPED',
  PAY_SUCCESS: 'PAY_SUCCESS',
  REFUND_SUCCESS: 'REFUND_SUCCESS',
  AFTER_SALE_REVIEWED: 'AFTER_SALE_REVIEWED',
  INVOICE_ISSUED: 'INVOICE_ISSUED',
  INVOICE_RED_FLUSHED: 'INVOICE_RED_FLUSHED',
  INVOICE_VOIDED: 'INVOICE_VOIDED',
} as const;

/** 站内信 type 联合类型。 */
export type NotificationType = (typeof NOTIFICATION_TYPE)[keyof typeof NOTIFICATION_TYPE];

/** 全部已知取值（**新增取值必须同时进 `NOTIFICATION_TYPE` 与下方 `NOTIFICATION_TABS`**）。 */
export const KNOWN_NOTIFICATION_TYPES: readonly NotificationType[] =
  Object.values(NOTIFICATION_TYPE);

/**
 * type → 中文文案（键域 = 后端取值本身）。
 * `Record<NotificationType, string>` ⇒ 漏一个取值即编译期报错。
 */
export const NOTIFICATION_TYPE_LABEL: Record<NotificationType, string> = {
  LIKE: '点赞',
  REPLY: '回复',
  FOLLOW: '关注',
  ORDER_SHIPPED: '订单发货',
  PAY_SUCCESS: '支付成功',
  REFUND_SUCCESS: '退款成功',
  AFTER_SALE_REVIEWED: '售后处理',
  INVOICE_ISSUED: '发票开具',
  INVOICE_RED_FLUSHED: '发票红冲',
  INVOICE_VOIDED: '发票作废',
};

/** 兜底分类 key（后端新增取值 / 脏数据 → 落这里，**不丢弃**）。 */
export const NOTIFICATION_TAB_OTHER = 'OTHER';

/** 不筛选任何 type 的标签页 key。 */
export const NOTIFICATION_TAB_ALL = 'ALL';

/** 一个标签页。 */
export interface NotificationTab {
  /** 标签页稳定 key（仅前端使用，不发往服务端）。 */
  key: string;
  /** 标签文案。 */
  label: string;
  /**
   * 本页覆盖的后端 type 取值。
   * - `[]` + `fallback !== true` → 不筛选（「全部」）
   * - `[]` + `fallback === true` → 兜底页（未知取值）
   * - `length > 0` → 逐值精确筛选后**合并**
   */
  types: readonly NotificationType[];
  /** 是否为「其他」兜底页（覆盖所有不在已知取值集合内的 type）。 */
  fallback?: boolean;
}

/**
 * 标签页定义。
 *
 * ⚠️ **服务端只能按单值精确筛选**（`InAppMessageRepositoryImpl.pageByRecipient` 里是
 * `wrapper.eq(type)`），所以 `types.length > 1` 的标签页由调用方**逐值取数再合并**
 * （见 `apps/web/app/user/notifications/use-notification-page.ts`），
 * 且页码必须按「每值取 `page_size × page` 条后合并再切片」的口径推进 ——
 * 否则合并结果的顺序与页码会错位（那正是另一种「恒空/串页」）。
 */
export const NOTIFICATION_TABS = [
  { key: NOTIFICATION_TAB_ALL, label: '全部', types: [] },
  {
    key: 'INTERACTION',
    label: '互动',
    types: [NOTIFICATION_TYPE.LIKE, NOTIFICATION_TYPE.REPLY, NOTIFICATION_TYPE.FOLLOW],
  },
  {
    key: 'TRADE',
    label: '交易',
    types: [
      NOTIFICATION_TYPE.ORDER_SHIPPED,
      NOTIFICATION_TYPE.PAY_SUCCESS,
      NOTIFICATION_TYPE.REFUND_SUCCESS,
    ],
  },
  {
    key: 'AFTER_SALE',
    label: '售后',
    types: [NOTIFICATION_TYPE.AFTER_SALE_REVIEWED],
  },
  {
    key: 'INVOICE',
    label: '发票',
    types: [
      NOTIFICATION_TYPE.INVOICE_ISSUED,
      NOTIFICATION_TYPE.INVOICE_RED_FLUSHED,
      NOTIFICATION_TYPE.INVOICE_VOIDED,
    ],
  },
  { key: NOTIFICATION_TAB_OTHER, label: '其他', types: [], fallback: true },
] as const satisfies readonly NotificationTab[];

/* ────────────────────────────────────────────────────────────────
   编译期完整性断言：标签页集合必须覆盖后端全部已知取值
   （后端新增取值 → 前端 tsc 直接报错，而不是"静默少了这个分类"）
   ──────────────────────────────────────────────────────────────── */

/** 标签页已覆盖的取值集合。 */
export type TabCoveredNotificationType = (typeof NOTIFICATION_TABS)[number]['types'][number];

/** 未被任何标签页覆盖的取值（理想值为 `never`）。 */
export type UncoveredNotificationType = Exclude<NotificationType, TabCoveredNotificationType>;

/**
 * `UncoveredNotificationType` 不为 `never` 时，本行类型不成立 ⇒ **tsc 报错**。
 * 报错即提示：新取值要同时加进 `NOTIFICATION_TYPE` 与 `NOTIFICATION_TABS`。
 */
export const NOTIFICATION_TAB_COVERAGE_ASSERT: [UncoveredNotificationType] extends [never]
  ? true
  : never = true;

/* ───────────────────────── 取值解释 ───────────────────────── */

/** 是否为已知取值（类型守卫）。 */
export function isKnownNotificationType(
  type: string | null | undefined,
): type is NotificationType {
  return type != null && (KNOWN_NOTIFICATION_TYPES as readonly string[]).includes(type);
}

/**
 * type → 展示文案（**所有** type 文案的唯一入口）。
 *
 * - 已知取值 → `点赞` / `支付成功` / …
 * - 未知取值（后端先上线、前端未发版 / 脏数据）→ **原样返回原始值**（不空白、不写"未知"吞掉）
 * - `null` / 空串 → `通知`（数据本身缺失，非取值未知）
 */
export function notificationTypeLabel(type: string | null | undefined): string {
  if (type == null || type === '') return '通知';
  const known = NOTIFICATION_TYPE_LABEL[type as NotificationType];
  return known ?? type;
}

/** type → 所属标签页 key（未知取值 → `OTHER`，即「其他」页可见，**不丢弃**）。 */
export function notificationTabOf(type: string | null | undefined): string {
  if (isKnownNotificationType(type)) {
    const tab = NOTIFICATION_TABS.find((t) =>
      (t.types as readonly string[]).includes(type),
    );
    if (tab) return tab.key;
  }
  return NOTIFICATION_TAB_OTHER;
}

/** 按 key 取标签页（找不到 → 「全部」）。 */
export function notificationTabByKey(key: string | null | undefined): NotificationTab {
  const found = NOTIFICATION_TABS.find((t) => t.key === key);
  return (found ?? NOTIFICATION_TABS[0]) as NotificationTab;
}

/** 是否为「全部」页（不筛选）。 */
export function isAllNotificationTab(tab: NotificationTab): boolean {
  return tab.types.length === 0 && tab.fallback !== true;
}

/** 是否为「其他」兜底页（未知取值只能在客户端筛出来）。 */
export function isFallbackNotificationTab(tab: NotificationTab): boolean {
  return tab.fallback === true;
}

/**
 * 运行期完整性告警（开发环境）—— 后端出现前端词表里没有的取值时**显式告警**。
 *
 * 与上面的编译期断言互补：断言管「已知取值漏挂标签页」，本函数管「后端多发了一个
 * 前端完全没见过的取值」。两者都不允许**静默**。生产环境不发 console（避免噪音），
 * 但记录本身仍走「其他」页 + 原始值文案，用户可见。
 */
export function warnUnknownNotificationTypes(
  records: readonly { type?: string | null }[],
): string[] {
  const unknown = Array.from(
    new Set(
      records
        .map((r) => r.type ?? '')
        .filter((t) => t !== '' && !isKnownNotificationType(t)),
    ),
  );
  if (unknown.length > 0 && process.env.NODE_ENV !== 'production') {
    // eslint-disable-next-line no-console
    console.warn(
      `[notifications] 后端返回了前端词表外的 type：${unknown.join(', ')} —— `
      + '请同步 packages/utils/notification-type.ts（取值域真源见该文件头表）',
    );
  }
  return unknown;
}
