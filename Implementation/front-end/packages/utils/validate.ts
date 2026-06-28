/**
 * Zod 校验 schema — 前后端共享校验规则
 */

import { z } from 'zod';

/** 手机号 */
export const phoneSchema = z
  .string()
  .regex(/^1[3-9]\d{9}$/, '请输入正确的手机号');

/** 密码 (8-32位，至少含数字+字母) */
export const passwordSchema = z
  .string()
  .min(8, '密码至少8位')
  .max(32, '密码最多32位')
  .regex(/[a-zA-Z]/, '密码需包含字母')
  .regex(/\d/, '密码需包含数字');

/** 短信验证码 (6位数字) */
export const smsCodeSchema = z
  .string()
  .length(6, '验证码为6位数字')
  .regex(/^\d{6}$/, '验证码为6位数字');

/** 用户名 (3-20位，字母开头) */
export const usernameSchema = z
  .string()
  .min(3, '用户名至少3位')
  .max(20, '用户名最多20位')
  .regex(/^[a-zA-Z][a-zA-Z0-9_]*$/, '用户名需以字母开头，可包含数字和下划线');

/** 收货地址表单 */
export const addressSchema = z.object({
  receiverName: z.string().min(1, '请输入收货人姓名'),
  receiverPhone: phoneSchema,
  province: z.string().min(1, '请选择省份'),
  city: z.string().min(1, '请选择城市'),
  district: z.string().min(1, '请选择区县'),
  detail: z.string().min(1, '请输入详细地址'),
  isDefault: z.boolean().optional().default(false),
});
