/**
 * TypeScript 类型 — 与后端 VO/DTO 精确对齐
 * 来源: project-docs/05-API-Specification.md
 */

// ===== 通用 =====

export interface PageQuery {
  page: number;
  size: number;
}

export interface PageResult<T> {
  records: T[];
  total: number;
  size: number;
  current: number;
  pages: number;
}

// ===== 认证 =====

export interface LoginReq {
  identityType: 'PHONE' | 'USERNAME' | 'EMAIL';
  credentialType: 'PASSWORD' | 'SMS_CODE';
  account: string;
  credential: string;
}

export interface RegisterReq {
  phone: string;
  password: string;
  code: string;
  username?: string;
  deviceId?: string;
}

/** OAuth2TokenResp — 后端 @JsonProperty snake_case 序列化 */
export interface LoginResp {
  access_token: string;
  token_type: string;
  expires_in: number;
  refresh_token: string;
  user_id: number;
  username: string;
}

// ===== 用户 =====

export interface UserInfoResp {
  userId: string;
  username: string;
  phone: string;
  avatar: string;
  status: number;
  registerTime: string;
  balance: number;
}

export interface AddressResp {
  id: number;
  userId: number;
  receiver: string;
  phone: string;
  province: string;
  city: string;
  district: string;
  street: string;
  detail: string;
  defaulted: boolean;
  label?: string;
  longitude?: number;
  latitude?: number;
  createTime: string;
  updateTime: string;
}

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

// ===== 商品 =====

export interface CategoryVO {
  id: number;
  name: string;
  sortOrder: number;
  children: CategoryVO[];
}

export interface ProductVO {
  id: number;
  productId: string;
  sellerId: number;
  categoryId: number;
  name: string;
  mainImage: string;
  description: string;
  brand: string;
  soldCount: number;
  commentCount: number;
  status: number;
  publishTime: string;
  skus: SkuVO[];
}

export interface SkuVO {
  id: number;
  skuId: string;
  productId: number;
  spec: string;
  price: number;
  stock: number;
  image: string;
  soldCount: number;
  status: number;
}

// ===== 购物车 =====

export interface CartVO {
  items: CartItemResp[];
  totalPrice: number;
  selectedPrice: number;
  allSelected: boolean;
}

export interface CartItemResp {
  skuId: number;
  productName: string;
  spec: string;
  image: string;
  price: number;
  quantity: number;
  selected: boolean;
  subTotal: number;
}

export interface CartAddReq {
  skuId: number;
  quantity?: number;
}

export interface CartUpdateReq {
  skuId: number;
  quantity: number;
}

// ===== 订单 =====

export interface OrderVO {
  id: number;
  orderNo: string;
  userId: number;
  sellerId: number;
  totalAmount: number;
  payAmount: number;
  discountAmount: number;
  status: number;
  paymentType: number;
  receiverName: string;
  receiverPhone: string;
  receiverAddress: string;
  createTime: string;
  payTime?: string;
  consignTime?: string;
  endTime?: string;
  items: OrderItemVO[];
}

export interface OrderItemVO {
  id: number;
  skuId: number;
  productName: string;
  skuSpec: string;
  price: number;
  quantity: number;
  subTotal: number;
  image: string;
}

export interface CreateOrderReq {
  addressId?: number;
  cartItemIds: number[];
  remark?: string;
}

// ===== 搜索 =====

export interface ProductSearchVO {
  id: number;
  productId: string;
  categoryId: number;
  name: string;
  description: string;
  brand: string;
  mainImage: string;
  price: number;
  soldCount: number;
}

// ===== 优惠券 =====

export interface CouponEntity {
  id?: number;
  couponId?: string;
  name: string;
  type: number;
  value: number;
  minAmount: number;
  startTime: string;
  endTime: string;
  status?: number;
}

export interface UserCouponEntity {
  id: number;
  couponId: string;
  userId: number;
  status: number;
  usedTime?: string;
}

// ===== 秒杀 =====

export interface FlashSaleEntity {
  id: number;
  productId: number;
  skuId?: number;
  flashPrice?: number;
  price: number;
  stock: number;
  soldCount: number;
  startTime: string;
  endTime: string;
  status: number;
}

// ===== 支付 (V2.3 多渠道) =====

export type PayChannel = 'WECHAT' | 'ALIPAY' | 'BALANCE';

/** 支付渠道枚举值 → 展示名 */
export const PAY_CHANNEL_TEXT: Record<PayChannel, string> = {
  WECHAT: '微信支付',
  ALIPAY: '支付宝',
  BALANCE: '余额支付',
};

/** PayOrderVO — 后端支付单视图 */
export interface PayOrderVO {
  payOrderNo: string;
  bizOrderNo: string;
  bizUserId: number;
  payChannelCode: string;
  amount: number;
  payType: number;
  /** 0-待提交 1-待支付 2-超时取消 3-成功 */
  status: number;
  qrCodeUrl?: string;
  payOverTime?: string;
  paySuccessTime?: string;
}
