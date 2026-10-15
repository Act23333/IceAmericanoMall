'use client';

import { useMutation } from '@tanstack/react-query';
import { apiClient, type VerificationCodeIssueReq, type VerificationCodeIssueResp } from '@icedmall/api';

/**
 * 签发验证码（短信 / 邮件）—— 公开端点 `POST /api/verification/codes`。
 *
 * ★ ADR-016 收口后，发码职责统一由 verification-service 承担：
 *   - 原 user-service `POST /api/user/code` 与 authorization-service `POST /api/auth/email/code`
 *     均已下线，前端旧调用指向的是**不存在的路径**。
 *
 * 请求体 `VerificationCodeIssueReq` 无 `@JsonProperty`，故全平台 SNAKE_CASE 下键名为
 * `channel` / `target` / `scene` / `request_id` / `captcha_provider` / `captcha_params`。
 * 人机验证四要素封装在 `captcha_params`（厂商中立 Map，key 由厂商 schema 决定）。
 *
 * 响应 `{ success, reason, expire_seconds }`；失败以 `BizException` 抛出，
 * 由全局异常处理器包装为 `Result`（前端只需处理 `ApiError`）。
 */
export async function sendVerificationCode(req: VerificationCodeIssueReq): Promise<VerificationCodeIssueResp> {
  return apiClient<VerificationCodeIssueResp>('/api/verification/codes', {
    method: 'POST',
    body: JSON.stringify(req),
  });
}

/** 发送验证码（mutation 形式，便于在表单里拿 isPending 做倒计时）。 */
export function useSendVerificationCode() {
  return useMutation({ mutationFn: sendVerificationCode });
}
