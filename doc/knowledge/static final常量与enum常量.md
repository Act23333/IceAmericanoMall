在 Java 中，`static final` 常量和 `enum`（枚举）都用于定义常量，但它们的适用场景和设计哲学不同。大厂会根据具体需求选择，而不是盲目推荐某一种。下面详细分析两者的区别及实践建议。

---

## 1. `static final` 常量的特点

```java
public class Constants {
    public static final String DEFAULT_USER_AVATAR = "default.png";
    public static final int MAX_RETRY_COUNT = 3;
    public static final String SMS_CAPTCHA_PREFIX = "sms:captcha:";
}
```

- **编译时常量**：值在编译期确定，会被内联到使用处（如果基本类型或 `String`），性能极佳。
- **轻量**：仅是一个内存地址引用，无额外对象开销。
- **简单**：适合表示简单的数值、字符串等。
- **缺乏类型安全**：比如 `int` 类型的常量，方法参数接受 `int` 时，任何整数值都能传入，无法在编译期限制取值范围。

---

## 2. `enum` 枚举的特点

```java
public enum ErrorCode {
    SUCCESS(0, "成功"),
    USER_NOT_FOUND(1001, "用户不存在"),
    CAPTCHA_ERROR(1002, "验证码错误");

    private final int code;
    private final String message;

    ErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }

    // getters...
}
```

- **类型安全**：枚举是类，方法参数限定为枚举类型，只能传入预定义的值，编译器会检查。
- **可携带更多信息**：每个枚举实例可以有字段、方法，能包含关联数据（如错误码、描述、行为）。
- **面向对象**：可以重写方法，实现接口，支持多态。
- **内存开销稍大**：每个枚举实例是单例对象，JVM 会为每个枚举值分配对象。

---

## 3. 两者的选择标准

| 场景 | 推荐方案 | 理由 |
|------|----------|------|
| 简单的全局常量（如配置键、URL前缀） | `static final` | 性能好，无额外对象开销 |
| 有限集合的值（如状态、类型、错误码） | `enum` | 类型安全，便于维护和扩展 |
| 需要关联数据（如错误码+消息） | `enum` | 可以将数据和逻辑封装在一起 |
| 常量需要被频繁使用且值简单 | `static final` | 编译期内联，运行效率高 |
| 常量集合可能会扩展或需要统一行为 | `enum` | 枚举天然支持迭代、switch 等 |

---

## 4. 大厂实践

大厂通常会制定详细的编码规范，对常量使用有明确指引。例如：

### 阿里巴巴 Java 开发手册
- **推荐**：如果变量值在有限范围内且需要类型安全，使用 `enum`。
- **不推荐**：使用枚举来表示简单的、不会变化的常量（如 `public enum Color { RED, GREEN }`），但如果是业务状态流转，应使用枚举。
- 对于常量类，通常使用 `static final`，并集中管理（如 `Constants` 类）。

### Google Java Style Guide
- 枚举应被视为类，只有在确实需要一组固定常量时才使用。
- 对于简单常量，使用 `static final` 即可。

### 常见大厂实践总结

1. **简单常量**：使用 `static final`，放在专门的常量类或接口中（但接口常量反模式现在较少，因为接口会暴露实现细节）。  
   例如：`public static final String REDIS_KEY_PREFIX = "user:";`

2. **状态/类型枚举**：使用 `enum`，并常包含 `code` 和 `description` 字段，用于前端交互或数据库存储。  
   例如：订单状态、用户类型、错误码。

3. **策略或行为枚举**：使用 `enum` 并实现抽象方法，用于策略模式。  
   例如：`enum Operation { PLUS { int apply(int a, int b) { return a + b; } }, MINUS {...} }`

4. **常量类设计**：通常使用 `final` 类，私有构造器，防止实例化；常量用 `public static final` 修饰。

---

## 5. 示例：大厂风格常量与枚举混用

```java
// 简单常量 - 使用 static final
public final class RedisKeys {
    private RedisKeys() {}
    public static final String USER_CACHE_PREFIX = "user:";
    public static final String SMS_CODE_PREFIX = "sms:code:";
    public static final long DEFAULT_EXPIRE_SECONDS = 300;
}

// 业务状态 - 使用 enum
public enum OrderStatus {
    PENDING(0, "待支付"),
    PAID(1, "已支付"),
    SHIPPED(2, "已发货"),
    COMPLETED(3, "已完成"),
    CANCELLED(4, "已取消");

    private final int code;
    private final String desc;

    OrderStatus(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    // getters...
}
```

---

## 6. 何时不推荐使用 `enum`

- 常量值会频繁变化（例如动态配置），因为枚举是编译期确定的，无法动态添加。
- 简单的字符串或数字常量，且不需要类型安全时，用 `static final` 更轻量。
- 如果枚举值过多（几十上百），编译生成的类会较大，可能影响启动性能（但通常可接受）。

---

## 总结

- **`static final`**：用于简单、稳定的常量，性能优先，无类型安全需求。
- **`enum`**：用于有限集合、有类型安全需求、需要携带额外信息或行为的常量。

大厂并非“推荐使用 enum”取代所有常量，而是**根据场景选择最合适的工具**。实际代码中，两者往往共存，各司其职。