# ADR-004: 内部接口异常透传 + 外部接口统一包装

## 背景

微服务间通过 Feign 调用时，如果被调用方返回 `Result<T>` 包装的业务异常，调用方无法直接拿到原始 HTTP 错误状态码，导致错误处理复杂化。

## 决策

采用 **路径前缀区分策略**：

- **外部接口**（`/api/**`）：异常由 `GlobalExceptionHandler` 包装为 `ResponseEntity<Result<Void>>`，HTTP 状态码由 ErrorCode 决定
- **内部接口**（`/internal/**`）：异常不包装为 Result，直接以 ErrorCode 的 HTTP 状态码应答，调用方通过 Feign Fallback 或 try-catch 处理原始异常

```java
// GlobalExceptionHandler 中的关键逻辑（V5.3 修正后的实现）
if (request != null && request.getRequestURI().contains("/internal/")) {
    // 内部接口：只给真实 HTTP 状态码 + 极简 JSON，不包装 Result
    return ResponseEntity.status(errorCode.getHttpStatus())
            .body(Map.of("code", errorCode.getCode(), "msg", e.getDisplayMessage()));
}
// 外部接口：包装为 ResponseEntity<Result<Object>>
return ResponseEntity.status(errorCode.getHttpStatus()).body(Result.error(e));
```

> **V5.3 修正（2026-10-03）**：早期实现写作 `throw e;`（想「原样抛出」），但
> `HandlerExceptionResolver` 捕获异常后不会二次解析处理器新抛出的异常 —— 容器兜底成 HTTP 500，
> 所有内部 `BizException`（含 429/400 等确定性失败）都退化为「服务不可用」，
> 与本 ADR 的决策意图正相反。现改为直接返回 `ResponseEntity`，见下方「修订」。

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

## 修订

### V5.3（2026-10-03）：rethrow 失效 → 直接返回真实状态码

**缺陷（P1）**：`GlobalExceptionHandler` 在 `/internal/**` 分支执行 `throw e;`，
期望「原样抛出」。但 Spring MVC 的 `ExceptionHandlerExceptionResolver` 只是调用该处理器方法，
处理器方法自身抛出的异常会继续向上冒泡到容器（不进入第二轮异常解析），最终被容器错误页兜底为
**HTTP 500**。后果：内部接口的一切业务失败（库存不足、余额不足、限流 429……）与「服务宕机」
在 Feign 调用方看来完全一样，只能一律按系统故障处理。

**修正**：改为直接返回 `ResponseEntity.status(errorCode.getHttpStatus())`，body 为极简
`{"code","msg"}`（刻意不使用 `Result` 类），保留「内部接口不返回 Result 信封」的语义。
判定集中在 `processResponse(...)`，所有 `CommonException` 子类的处理器共用同一规则，
外部 `/api/**` 行为完全不变。

**保留的注意事项（需后续决策）**：`ErrorCode` 中 3000-3039 段「业务逻辑错误」的
`HttpStatus` 是 **200**，因此内部接口上的这类失败会以 `200 + {"code":3012,...}` 返回。
声明返回 `Result<T>` 的内部 Feign 契约能读到 `code != 200` 从而识别失败；
但声明 `void`/基本类型的内部契约（如 `BalanceClient.deductBalance`）会把 200 视为成功而丢失失败信号。
建议后续评估：为内部接口提供一份业务码 → 非 2xx 的映射（或不共用 `ErrorCode` 的 HTTP 状态），
以保证「命令型内部端点」的失败永不被解释为成功。

## 参考

- `doc/knowledge/异常全局捕获返回ResponseEntity，而不是Result.md`
- `doc/knowledge/抛出给前端的异常情况.md`
