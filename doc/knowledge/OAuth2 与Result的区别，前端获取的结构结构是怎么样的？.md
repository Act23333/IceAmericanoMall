OAuth2 标准响应与自定义 `Result<T>` 是两种不同的接口返回格式，它们在字段结构、使用场景和前端处理方式上存在明显差异。下面详细对比，并说明前端如何分别处理。

---

## 一、OAuth2 标准响应格式

### 典型结构（登录成功）

```json
{
  "access_token": "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9...",
  "token_type": "Bearer",
  "expires_in": 3600,
  "refresh_token": "tGzv3JOkF0XG5Qx2TlKWIA",
  "scope": "read write"
}
```

### 字段说明

| 字段 | 必选 | 含义 |
|------|------|------|
| `access_token` | ✅ | 访问令牌，客户端后续请求携带 |
| `token_type` | ✅ | 令牌类型，固定 `Bearer` |
| `expires_in` | ✅ | 有效期（秒） |
| `refresh_token` | ❌ | 用于刷新 `access_token` |
| `scope` | ❌ | 权限范围 |

**特点**：
- 字段名使用**下划线**风格（`access_token`）。
- **不包含**业务数据（如 `userId`、`username`），若需要用户信息，客户端需再调用 `/userinfo` 端点。
- 专为 OAuth2 协议设计，兼容第三方客户端。

---

## 二、自定义 `Result<T>` 响应格式

### 典型结构（业务接口）

```json
{
  "code": 200,
  "msg": "success",
  "data": {
    "userId": 10001,
    "username": "张三",
    "avatar": "https://..."
  }
}
```

### 字段说明

| 字段 | 含义 |
|------|------|
| `code` | 业务状态码（如 200 成功，400 参数错误，401 未登录） |
| `msg` | 提示信息 |
| `data` | 实际业务数据（可以是对象、数组或 null） |

**特点**：
- 字段名通常使用**驼峰**风格（`userId`）。
- 统一包装，前端根据 `code` 判断成功/失败，从 `data` 中取数据。
- 适合内部业务 API，方便统一错误处理。

---

## 三、两种格式的主要区别

| 对比维度 | OAuth2 标准 | Result<T> |
|----------|-------------|-----------|
| **设计目的** | 协议兼容，供客户端获取令牌 | 业务数据封装，统一错误处理 |
| **字段风格** | 下划线（`access_token`） | 驼峰（`userId`）或自定义 |
| **是否包含业务数据** | 否，需额外调用 `/userinfo` | 是，直接返回 |
| **错误表达** | HTTP 状态码（如 400、401） | `code` + `msg`，HTTP 状态码通常为 200 |
| **使用场景** | 认证服务器（登录、令牌刷新） | 业务服务（用户信息、订单等） |

---

## 四、前端如何获取结构？

### 1. 登录请求（认证服务器）

前端调用 `POST /api/auth/login`，得到 OAuth2 标准响应：

```javascript
const response = await fetch('/api/auth/login', {
  method: 'POST',
  body: JSON.stringify(loginData)
});
const data = await response.json();

// 提取令牌
const accessToken = data.access_token;
const tokenType = data.token_type;   // "Bearer"
const expiresIn = data.expires_in;   // 3600

// 存储令牌（例如 localStorage 或 cookie）
localStorage.setItem('access_token', accessToken);
```

如果需要用户信息，再调用 `/userinfo`：

```javascript
const userInfoRes = await fetch('/api/user/me', {
  headers: {
    'Authorization': `Bearer ${accessToken}`
  }
});
const userInfo = await userInfoRes.json();  // 假设这里返回 Result<UserVO>
if (userInfo.code === 200) {
  console.log(userInfo.data.username);
}
```

### 2. 业务请求（用户服务）

前端携带令牌访问业务接口，网关验证后转发，响应为 `Result<T>`：

```javascript
const response = await fetch('/api/user/me', {
  headers: {
    'Authorization': `Bearer ${accessToken}`
  }
});
const result = await response.json();

if (result.code === 200) {
  const user = result.data;
  console.log(user.username);
} else {
  // 统一错误处理
  alert(result.msg);
}
```

---

## 五、为什么两种格式可以共存？

- **职责分离**：认证服务器负责令牌颁发，遵循标准协议；业务服务器负责具体业务，自定义响应格式。
- **网关隔离**：前端访问不同服务时，通过网关路由到不同后端，对前端来说只是不同接口路径。
- **扩展性**：未来需要对接第三方 OAuth2 客户端时，认证服务器接口无需改动；内部业务接口也不受影响。

---

## 六、总结

- **OAuth2 标准响应**：用于登录、令牌刷新等认证接口，字段为下划线，不包含业务数据。
- **Result<T> 响应**：用于业务接口，字段为驼峰，统一包装 `code`、`msg`、`data`。
- **前端处理**：根据接口类型分别解析，登录时取 `access_token`，业务接口时取 `data`。
- **建议**：保持认证服务器遵循 OAuth2，业务服务使用 `Result<T>`，既兼容标准又统一内部风格。

这种混合模式已被大量微服务项目采用，兼顾了规范性和开发效率。