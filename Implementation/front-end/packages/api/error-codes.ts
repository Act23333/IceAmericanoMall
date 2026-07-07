/**
 * 后端 ErrorCode → 用户可读中文消息映射
 */
const ERROR_MAP: Record<number, string> = {
  // 系统级
  1000: '服务器繁忙，请稍后重试',
  1004: '操作过于频繁，请稍后再试',
  // 参数
  1101: '请检查输入内容',
  1102: '账号不存在',
  // 登录/注册
  3001: '验证码错误',
  3002: '验证码已过期',
  3003: '该手机号已被注册',
  3004: '该用户名已被占用',
  3007: '密码错误，请重试',
  3008: '账号已被禁用',
};

/** 将 ApiError 转为用户可读消息 */
export function getUserMessage(err: { code?: number; message?: string }): string {
  if (err.code && ERROR_MAP[err.code]) return ERROR_MAP[err.code];
  // 后端返回的 msg 优先 (如 "登录密码错误" 等动态消息)
  if (err.message && !/^HTTP\s\d{3}$/.test(err.message)) return err.message;
  return '网络异常，请检查连接后重试';
}
