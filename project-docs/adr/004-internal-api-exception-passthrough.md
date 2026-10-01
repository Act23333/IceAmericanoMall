# ADR-004: 内部接口异常透传 + 外部接口统一包装

## 背景

微服务间通过 Feign 调用时，如果被调用方返回 `Result<T>` 包装的业务异常，调用方无法直接拿到原始 HTTP 错误状态码，导致错误处理复杂化。

## 决策

采用 **路径前缀区分策略**：

- **外部接口**（`/api/**`）：异常由 `GlobalExceptionHandler` 包装为 `ResponseEntity<Result<Void>>`，HTTP 状态码由 ErrorCode 决定
- **内部接口**（`/internal/**`）：异常不包装为 Result，直接向上抛出，调用方通过 Feign Fallback 或 try-catch 处理原始异常

```java
// GlobalExceptionHandler 中的关键逻辑
if (uri.contains("/internal/")) {
    throw e;  // 内部接口：不包装，直接抛
}
// 外部接口：包装为 ResponseEntity<Result<Void>>
return ResponseEntity.status(errorCode.getHttpStatus()).body(Result.error(e));
```

## 理由

- Feign 调用方需要感知的是「调用成功还是失败」，不需要解析 Result.code
- 直接抛出异常 → Feign 框架可以正确映射 HTTP 状态码到 `FeignException`
- 外部接口（前端调用）仍然享受统一的 Result 包装体验

## 后果

- ✅ 内部服务间调用错误处理简洁
- ✅ 前端统一体验不变
- ⚠️ Gateway 必须配置规则，禁止 `/internal/**` 被外部访问
- ⚠️ 新增内部接口必须遵循 `/internal/` 路径约定

## 日期

2026-03-20

## 参考

- `doc/knowledge/异常全局捕获返回ResponseEntity，而不是Result.md`
- `doc/knowledge/抛出给前端的异常情况.md`
