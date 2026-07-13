/**
 * Query key 工厂
 *
 * 集中管理所有 query key，遵循 @tanstack/react-query 推荐的结构化 key 规范。
 * 作用：invalidate 时精确定位，避免字符串拼写错误。
 */

export const queryKeys = {
  // 商品
  products: {
    all: ['products'] as const,
    lists: () => [...queryKeys.products.all, 'list'] as const,
    list: (filters: Record<string, unknown>) =>
      [...queryKeys.products.lists(), filters] as const,
    details: () => [...queryKeys.products.all, 'detail'] as const,
    detail: (id: string) => [...queryKeys.products.details(), id] as const,
  },

  // 购物车
  cart: {
    all: ['cart'] as const,
    mine: () => [...queryKeys.cart.all, 'mine'] as const,
  },

  // 订单
  orders: {
    all: ['orders'] as const,
    lists: () => [...queryKeys.orders.all, 'list'] as const,
    list: (filters: Record<string, unknown>) =>
      [...queryKeys.orders.lists(), filters] as const,
    details: () => [...queryKeys.orders.all, 'detail'] as const,
    detail: (id: string) => [...queryKeys.orders.details(), id] as const,
  },

  // 用户
  user: {
    all: ['user'] as const,
    profile: () => [...queryKeys.user.all, 'profile'] as const,
    addresses: () => [...queryKeys.user.all, 'addresses'] as const,
  },

  // 分类
  categories: {
    all: ['categories'] as const,
    tree: () => [...queryKeys.categories.all, 'tree'] as const,
  },

  // 余额
  balance: {
    all: ['balance'] as const,
  },

  // 支付
  pay: {
    all: ['pay'] as const,
    status: (payOrderNo: string) => [...queryKeys.pay.all, 'status', payOrderNo] as const,
  },
};
