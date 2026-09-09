# 拆分 Service 后，Mapper / 数据库 / Controller 要不要全部单开？

**不需要全部单开！严格区分三层拆分规则**，直接给你固定模板：

## 1. Mapper、数据库层（DAO）

- **用户本体表 `user`** → 共用 `UserMapper`，**不拆分**
- **签到如果需要持久化历史**（月度归档、年度记录）→ 单独建 `SignMapper`、`user_sign` 表
- 纯 Redis 签到（你用的 Bitmap 方案）**甚至不需要数据库表、不需要 Mapper**

## 2. Service 层（业务层）

**衍生业务全部单开独立 Service**，这是硬性规范

表格

|业务|独立 Service|是否单开|
|---|---|---|
|用户基础信息|UserService|核心主 Service|
|用户签到|UserSignService|✅ 单开|
|用户积分|UserPointService|✅ 单开|
|用户等级成长值|UserLevelService|✅ 单开|
|登录防护、失败限流|LoginProtectService|✅ 单开|
|用户黑名单|UserBlackService|✅ 单开|

## 3. Controller 层（接口层）

**可合可分，二选一，看项目大小**

### 方案 A（中小型项目、你当前项目推荐）

全部接口收敛在 **`UserController`**

plaintext

```
/user/info       用户信息
/user/login      登录
/user/sign       签到接口
```

不用新开 `SignController`，省事、网关路由也好管理。

### 方案 B（大型企业项目）

独立业务单独开 Controller

`SignController`、`PointController`，路径独立。