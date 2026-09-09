根据国内大厂（如阿里、美团、京东）的实践，结合领域驱动设计和 RESTful 风格，我整理了一套通用且可落地的三层架构命名规范。这套规范强调**职责清晰、语义明确、易于协作**。

---

## 一、总体原则

1. **分层隔离**：每层方法名体现自身职责，避免跨层语义耦合。
2. **动词优先**：方法名以动词开头，准确表达操作意图。
3. **资源化**：Controller 层面向资源（REST），Service 层面向业务领域。
4. **统一风格**：团队内保持一致，推荐使用英文命名，避免拼音或缩写。

---

## 二、各层命名规范详解

### 1. DAO 层（Mapper）
**职责**：数据访问，仅做 CRUD 及简单查询。  
**命名规则**：`[操作前缀][实体名]` 或 `[操作前缀][条件]`，使用 MyBatis-Plus 提供的通用方法可直接继承。

| 操作类型 | 前缀 | 示例 |
|---------|------|------|
| 插入 | `insert` | `insert(User user)` |
| 删除 | `delete` | `deleteById(Long id)`、`deleteByPhone(String phone)` |
| 更新 | `update` | `updateById(User user)`、`updateStatusById(Long id, Integer status)` |
| 查询单条 | `select` | `selectById(Long id)`、`selectByPhone(String phone)` |
| 查询列表 | `selectList` | `selectListByStatus(Integer status)` |
| 分页查询 | `selectPage` | `selectPage(Page<User> page, LambdaQueryWrapper<User> wrapper)` |
| 统计 | `count` | `countByStatus(Integer status)` |

**示例**：
```java
public interface UserMapper extends BaseMapper<User> {
    User selectByPhone(String phone);
    List<User> selectActiveUsers();
    int updateStatusById(@Param("id") Long id, @Param("status") Integer status);
}
```

### 2. Service 层
**职责**：业务逻辑编排，事务管理，可调用多个 DAO。  
**命名规则**：`[业务动词][名词]`，动词体现业务动作，名词为领域对象。

| 业务动作  | 示例                   | 说明                     |
| ----- | -------------------- | ---------------------- |
| 注册    | `register`           | 用户注册                   |
| 登录    | `login`              | 用户登录，返回 token          |
| 创建/新增 | `create` 或 `add`     | `createUser`           |
| 修改    | `update` 或 `modify`  | `updateUser`           |
| 删除    | `delete` 或 `remove`  | `deleteUser`           |
| 查询单个  | `get` / `find`       | `getUserById`          |
| 查询列表  | `list` / `query`     | `listUsersByCondition` |
| 分页查询  | `page`               | `pageUsers`            |
| 状态变更  | `enable` / `disable` | `disableUser`          |
| 业务操作  | `doXxx`              | `doPasswordReset`      |

**注意**：Service 层方法名应体现**业务语义**，而不是数据库操作。例如 `deleteUser` 可能包含软删除逻辑，而不仅仅是调用 `deleteById`。

**示例**：
```java
public interface UserService {
    UserDTO register(UserRegisterDTO dto);
    String login(UserLoginDTO dto);
    UserDTO getUserById(Long id);
    PageDTO<UserDTO> pageUsers(PageRequest pageRequest);
    void updateUser(UserUpdateDTO dto);
    void deleteUser(Long id);
    void changePassword(PasswordChangeDTO dto);
    void disableUser(Long id);
}
```

### 3. Controller 层
**职责**：接收 HTTP 请求，参数校验，调用 Service，封装响应。  
**命名规则**：
- 方法名体现 **HTTP 动作 + 资源**，但通常使用 `@RequestMapping` 标注路径，方法名可简写。
- 常用动词：`get`、`query`、`create`、`add`、`update`、`modify`、`delete`、`remove`。

| HTTP 方法    | 方法名建议                         | 示例              |
| ---------- | ----------------------------- | --------------- |
| GET 单资源    | `get` + 资源名                   | `getUser`       |
| GET 列表/分页  | `list` / `page`               | `pageUsers`     |
| POST 创建    | `create` / `add` / `register` | `register`      |
| PUT 全量更新   | `update`                      | `updateUser`    |
| PATCH 部分更新 | `modify` / `patch`            | `modifyUser`    |
| DELETE 删除  | `delete` / `remove`           | `deleteUser`    |
| 登录/认证      | `login`                       | `login`         |
| 其他业务操作     | `action` + 资源                 | `resetPassword` |

**示例**：
```java
@RestController
@RequestMapping("/api/users")
public class UserController {

    @PostMapping("/register")
    public Result<UserDTO> register(@Valid @RequestBody UserRegisterDTO dto) {
        return Result.success(userService.register(dto));
    }

    @PostMapping("/login")
    public Result<String> login(@Valid @RequestBody UserLoginDTO dto) {
        return Result.success(userService.login(dto));
    }

    @GetMapping("/{id}")
    public Result<UserDTO> getUser(@PathVariable Long id) {
        return Result.success(userService.getUserById(id));
    }

    @GetMapping("/page")
    public Result<PageDTO<UserDTO>> pageUsers(PageRequest pageRequest) {
        return Result.success(userService.pageUsers(pageRequest));
    }

    @PutMapping
    public Result<Void> updateUser(@Valid @RequestBody UserUpdateDTO dto) {
        userService.updateUser(dto);
        return Result.success();
    }

    @DeleteMapping("/{id}")
    public Result<Void> deleteUser(@PathVariable Long id) {
        userService.deleteUser(id);
        return Result.success();
    }

    @PostMapping("/password")
    public Result<Void> changePassword(@Valid @RequestBody PasswordChangeDTO dto) {
        userService.changePassword(dto);
        return Result.success();
    }
}
```

---

## 三、特殊场景命名规范

### 1. 分页查询
- Controller：`pageXxx` 或 `listXxx`（如 `pageUsers`）
- Service：`pageXxx`
- DAO：直接使用 MyBatis-Plus 的 `selectPage`

### 2. 批量操作
- 批量删除：`batchDelete` 或 `deleteBatch`
- 批量更新：`batchUpdate`
- 批量插入：`batchInsert`

### 3. 状态变更
- 使用 `enable` / `disable` / `activate` / `deactivate`
- 例如：`disableUser`、`activateUser`

### 4. 查询条件复杂时
- 使用 `query` 代替 `list`，如 `queryUsersByCondition`

### 5. 幂等操作（如支付回调）
- 使用 `handle` 前缀，如 `handlePayCallback`

---

## 四、命名一致性检查清单

- [ ] Controller 方法名与 HTTP 方法语义一致
- [ ] Service 方法名体现业务，不含技术细节
- [ ] DAO 方法名体现数据操作，不使用业务动词
- [ ] 所有方法名均使用动词开头，名词在后
- [ ] 避免重复动词（如 `getUserById` 不写成 `getUserById` 已足够，无需 `getUserInfo` 混用）
- [ ] 同一团队统一术语（如都使用 `create` 而不是 `add` 和 `insert` 混用）

---

## 五、总结推荐

| 层级 | 动词前缀 | 示例 |
|------|----------|------|
| **DAO** | `select` / `insert` / `update` / `delete` | `selectByPhone`、`updateStatus` |
| **Service** | 业务动词 | `register`、`login`、`disableUser` |
| **Controller** | HTTP 动作简写 + 资源 | `register`、`getUser`、`updateUser` |

这套规范在阿里、美团等公司广泛应用，既能保证代码可读性，又能降低沟通成本。建议在项目初期就统一约定，并通过代码评审持续强化。