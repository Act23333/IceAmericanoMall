Spring 提供了多种方式定义 Bean 的作用域，其中 `@RequestScope`、`@SessionScope` 和 `@Scope` 是最常用的。它们的核心区别在于**默认是否启用作用域代理**。

---

## 1. 注解来源与默认行为

| 注解 | 来源模块 | 默认 `proxyMode` | 等价写法 |
|------|----------|------------------|----------|
| `@RequestScope` | `spring-web` | `ScopedProxyMode.TARGET_CLASS` | `@Scope(value = "request", proxyMode = TARGET_CLASS)` |
| `@SessionScope` | `spring-web` | `ScopedProxyMode.TARGET_CLASS` | `@Scope(value = "session", proxyMode = TARGET_CLASS)` |
| `@Scope("request")` | `spring-context` | `ScopedProxyMode.NO` | 仅指定作用域，不启用代理 |
| `@Scope("session")` | `spring-context` | `ScopedProxyMode.NO` | 仅指定作用域，不启用代理 |

---

## 2. 作用与使用场景

### 2.1 `@RequestScope` / `@SessionScope`（默认代理）

**作用**：将 Bean 的作用域定义为当前 HTTP 请求（`request`）或 HTTP 会话（`session`），并**自动启用作用域代理**。

**适用场景**：  
- 当这个短生命周期的 Bean **可能被注入到长生命周期 Bean**（如单例 `@Service`、`@Component`）中时，代理是必需的。
- 例如，一个 `UserContext` 需要在多个单例 Service 中使用，且每个请求/会话的用户信息不同。

**示例**：
```java
@RequestScope
@Component
public class UserContext {
    private String username;
    // getter/setter
}

@Service
public class UserService {
    @Autowired
    private UserContext userContext;  // 注入的是代理对象
    public void doWork() {
        String user = userContext.getUsername(); // 每次获取当前请求的用户
    }
}
```

### 2.2 `@Scope("request")` / `@Scope("session")`（默认不代理）

**作用**：仅定义 Bean 的作用域，不自动创建代理。Bean 就是一个普通的 `request`/`session` 作用域对象。

**适用场景**：  
- 当该 Bean **只在同作用域内被使用**（例如一个 `@Controller` 注入另一个 `request` 作用域的 Bean），或者你希望手动控制代理的开启。
- 如果将此 Bean 注入到单例中，会因为作用域不匹配导致运行时异常（如 `IllegalStateException`），因为单例初始化时无法获取当前请求的实例。

**示例**：
```java
@Component
@Scope("request")  // 无代理
public class RequestScopedData { ... }

@Controller
@Scope("request")  // 同为 request 作用域
public class MyController {
    @Autowired
    private RequestScopedData data;  // 直接注入真实实例，安全
}
```

如果错误地将 `RequestScopedData` 注入到单例 Service：
```java
@Service
public class SingletonService {
    @Autowired
    private RequestScopedData data;  // ❌ 启动时可能报错：No thread-bound request found
}
```

---

## 3. 如何手动控制代理？

### 3.1 让 `@Scope` 启用代理
如果你希望使用 `@Scope("request")` 但需要代理，可以显式设置 `proxyMode`：
```java
@Component
@Scope(value = "request", proxyMode = ScopedProxyMode.TARGET_CLASS)
public class MyBean { ... }
```

### 3.2 让快捷注解关闭代理
如果你确定只在同作用域内使用，可以关闭代理以提升性能：
```java
@RequestScope(proxyMode = ScopedProxyMode.NO)
@Component
public class MyBean { ... }
```

---

## 4. 总结

- **默认代理的注解**：`@RequestScope`、`@SessionScope` → 适合跨作用域注入（安全、方便）。
- **默认不代理的注解**：`@Scope("request")`、`@Scope("session")` → 适合同作用域注入（轻量、无额外开销）。
- **核心原则**：只有当短生命周期 Bean 需要被长生命周期 Bean 持有时，才必须启用代理。否则可以关闭代理，避免不必要的 AOP 性能损耗。

根据实际使用场景选择合适的注解和配置，既能保证功能正确，又能保持代码简洁高效。