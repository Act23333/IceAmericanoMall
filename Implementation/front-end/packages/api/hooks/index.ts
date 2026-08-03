/**
 * TanStack Query hooks — V5.0
 *
 * 按 domain 组织，每个 hook 对应一个 API 端点。
 * 参考: project-docs/05-API-Specification.md
 */
export { useCategories } from './use-categories';
export { useProducts, useProduct } from './use-products';
export { useCart, useAddToCart, useUpdateCartItem, useRemoveCartItem, useToggleSelect, useClearCart } from './use-cart';
export { useOrders, useOrder, useCreateOrder, useCancelOrder, useConfirmOrder } from './use-orders';
export { useUserInfo } from './use-user';
export { useAddresses, useAddAddress, useUpdateAddress, useDeleteAddress } from './use-address';
export { useSearch, useHotKeywords, useSearchHistory } from './use-search';
export { useHomeConfig } from './use-home-config';
export type { HomeConfigItem } from './use-home-config';
export { useInitiatePay, usePayStatus } from './use-pay';
export { useBalance, useRecharge } from './use-balance';
export { useAvailableCoupons, useUsedCoupons, useCouponTemplates, useClaimCoupon, useGrabCoupon, useCouponFilter } from './use-coupons';
export type { CouponFilterReq } from './use-coupons';
export { useProductReviews, useCreateReview } from './use-reviews';
export { useFlashSales, useFlashBuy } from './use-flash-sale';
export { useMyAfterSales, useCreateAfterSale } from './use-after-sale';
