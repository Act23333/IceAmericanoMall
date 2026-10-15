/**
 * TanStack Query hooks — V5.0
 *
 * 按 domain 组织，每个 hook 对应一个 API 端点。
 * 契约来源: project-docs/contract/05-API-Specification.md
 * 字段命名策略: 映射报文的字段一律 snake_case，前端内部标识符保持 camelCase
 *              （详见 ../types/index.ts 头部说明）。
 */
export { useCategories } from './use-categories';
export { useProducts, useProduct } from './use-products';
export { useCart, useAddToCart, useUpdateCartItem, useRemoveCartItem, useToggleSelect, useClearCart } from './use-cart';
export { useOrders, useOrder, useCreateOrder, useCancelOrder, useConfirmOrder } from './use-orders';
export { useUserInfo } from './use-user';
export { useAddresses, useAddAddress, useUpdateAddress, useDeleteAddress } from './use-address';
export { useSearch, useHotKeywords, useSearchHistory } from './use-search';
export { useHomeConfig } from './use-home-config';
export { useInitiatePay, usePayStatus } from './use-pay';
export { useBalance, useRecharge } from './use-balance';
export {
  useAvailableCoupons,
  useUsedCoupons,
  useCouponTemplates,
  useClaimCoupon,
  useGrabCoupon,
  useCheckoutCoupons,
} from './use-coupons';
export type { CheckoutCoupon } from './use-coupons';
export { useProductReviews, useCreateReview } from './use-reviews';
export type { ReviewFilter } from './use-reviews';
export { useFlashSales, useFlashBuy } from './use-flash-sale';
export { useMyAfterSales, useCreateAfterSale } from './use-after-sale';
export { sendVerificationCode, useSendVerificationCode } from './use-verification';
