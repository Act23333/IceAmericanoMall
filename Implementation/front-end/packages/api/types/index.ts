/**
 * TypeScript 类型定义
 *
 * TODO: 从后端 Knife4j OpenAPI JSON 自动生成
 * 命令：npx openapi-typescript http://localhost:8080/v3/api-docs -o ./types/schema.ts
 */

// ===== 通用类型 =====

/** 分页请求参数 */
export interface PageQuery {
  page: number;
  size: number;
}

/** 分页响应 */
export interface PageResult<T> {
  records: T[];
  total: number;
  size: number;
  current: number;
}

// ===== 用户 =====
// (以下为示例，实际从 OpenAPI 生成)

export interface UserVO {
  id: number;
  userId: string;
  username: string;
  nickname: string;
  phone: string;
  avatar: string;
  role: string;
  status: number;
}

export interface AddressVO {
  id: number;
  userId: string;
  receiverName: string;
  receiverPhone: string;
  province: string;
  city: string;
  district: string;
  detail: string;
  isDefault: boolean;
}

// ===== 商品 =====

export interface CategoryVO {
  id: number;
  name: string;
  parentId: number | null;
  icon: string;
  sortOrder: number;
}

export interface ProductVO {
  id: number;
  productId: string;
  name: string;
  description: string;
  categoryId: number;
  mainImage: string;
  minPrice: number; // int (cents)
  maxPrice: number;
  sales: number;
  status: number;
}

export interface SkuVO {
  id: number;
  skuId: string;
  productId: string;
  spec: string;
  price: number; // int (cents)
  stock: number;
  image: string;
}

// ===== 购物车 =====

export interface CartItemVO {
  id: number;
  skuId: string;
  productName: string;
  spec: string;
  image: string;
  price: number; // int (cents)
  quantity: number;
  selected: boolean;
}

// ===== 订单 =====

export interface OrderVO {
  id: number;
  orderNo: string;
  totalAmount: number; // int (cents)
  status: string;
  items: OrderItemVO[];
  createTime: string;
}

export interface OrderItemVO {
  id: number;
  productName: string;
  spec: string;
  image: string;
  price: number; // int (cents)
  quantity: number;
  subTotal: number;
}
