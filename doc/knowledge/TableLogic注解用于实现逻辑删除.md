

在 **MyBatis-Plus** 中，`@TableLogic` 注解用于实现**逻辑删除**。当你在实体类的字段上标注了该注解，MyBatis-Plus 在执行删除操作时，**并不会真正执行 `DELETE` SQL 语句**，而是会执行 **UPDATE** 操作，将该字段的值从“正常”状态修改为“已删除”状态。

### 默认行为
- **未删除状态**：字段值为 `0`（这是默认值，表示数据有效）
- **已删除状态**：字段值变为 `1`（这是默认值，表示数据已被逻辑删除）

所以，当你调用 `deleteById` 或 `delete` 方法时，MyBatis-Plus 会自动将对应记录的该字段值更新为 `1`。

### 示例
```java
// 实体类
@TableName("user")
public class User {
    @TableId
    private Long id;
    private String name;
    
    @TableLogic
    private Integer deleted;  // 0: 未删除, 1: 已删除
}

// 执行删除
userMapper.deleteById(1L);
```

实际执行的 SQL 语句为：
```sql
UPDATE user SET deleted = 1 WHERE id = 1 AND deleted = 0
```

### 自定义配置
如果你需要修改默认值（比如用 `0` 表示已删除，`1` 表示未删除，或者使用其他字段值），可以通过注解属性或全局配置进行自定义：

**1. 通过注解属性：**
```java
@TableLogic(value = "0", delval = "1")
private Integer deleted;
```
- `value`：未删除时的值（默认 `"0"`）
- `delval`：删除后的值（默认 `"1"`）

**2. 通过全局配置文件（application.yml）：**
```yaml
mybatis-plus:
  global-config:
    db-config:
      logic-delete-field: deleted   # 全局逻辑删除字段名
      logic-delete-value: 1         # 逻辑已删除值
      logic-not-delete-value: 0     # 逻辑未删除值
```

### 总结
默认情况下，使用 `@TableLogic` 注解后，删除操作确实只会将该字段的值从 `0` 改为 `1`，而不会物理删除数据。但具体的值（`0` 和 `1`）是可以通过配置更改的。

当你在 MyBatis 的 XML 配置文件中直接编写 SQL 语句执行删除时（例如 `<delete id="deleteUser">DELETE FROM user WHERE id = #{id}</delete>`），**`@TableLogic` 注解不会生效**，会执行物理删除（即真实地从数据库中删除记录）。

### 原因
`@TableLogic` 是 MyBatis-Plus 提供的功能，它通过 MyBatis-Plus 的**拦截器**（`MybatisPlusInterceptor`）对 `delete` 操作进行增强。只有通过 MyBatis-Plus 的 API（如 `mapper.deleteById()`、`mapper.delete()`、`mapper.deleteBatchIds()` 等）执行的删除才会被自动转换为更新语句。如果你直接编写原生 SQL，MyBatis-Plus 不会拦截该语句，因此会按照你写的 SQL 执行物理删除。

### 如何让自定义 SQL 也支持逻辑删除？
如果你希望自定义的 SQL 也能遵循逻辑删除规则，有以下几种方式：

#### 1. 使用 MyBatis-Plus 的 `@Update` 注解或 XML 中手动写 UPDATE 语句
在 XML 或注解中直接编写 UPDATE 语句，手动将逻辑删除字段置为删除值，同时带上“未删除”的条件。

```xml
<update id="logicDeleteById">
    UPDATE user SET deleted = 1 WHERE id = #{id} AND deleted = 0
</update>
```

#### 2. 在自定义 SQL 中使用 `ew`（条件构造器）
MyBatis-Plus 允许你在自定义方法中接收 `Wrapper` 参数，利用 `ew` 自动拼接逻辑删除条件。

```java
// Mapper 接口
int deleteByWrapper(@Param(Constants.WRAPPER) Wrapper<User> wrapper);
```

```xml
<delete id="deleteByWrapper">
    DELETE FROM user ${ew.customSqlSegment}
</delete>
```

但注意：这种方式下，如果 MyBatis-Plus 的拦截器检测到是删除操作，依然会转换为更新吗？实际上，如果使用 `delete` 标签且传入了 `ew`，MyBatis-Plus 会将其识别为删除操作并应用逻辑删除。但前提是该 `delete` 方法是在 MyBatis-Plus 的 `BaseMapper` 中已经定义好的，或者是通过继承 `BaseMapper` 获得的。对于完全自定义的 XML 中的 `delete` 标签，即使使用了 `ew`，拦截器是否生效取决于配置（通常需要开启 `delete` 方法的逻辑删除支持，但更推荐直接写 UPDATE）。

#### 3. 使用 MyBatis-Plus 的 `@SqlParser` 注解（不推荐）
旧版本中可以通过 `@SqlParser(filter = true)` 来过滤，但新版本已不推荐，且不适用于此场景。

#### 4. 推荐做法：将自定义删除改为 UPDATE 操作
如果你需要自定义删除逻辑，最安全且符合逻辑删除语义的做法是**直接编写 UPDATE 语句**，显式设置逻辑删除字段，并加上未删除条件。

### 总结
- **通过 MyBatis-Plus API 删除**：自动应用逻辑删除（UPDATE）。
- **通过 XML 或注解写 DELETE 语句**：执行物理删除，逻辑删除不生效。
- 如需在自定义 SQL 中实现逻辑删除，请使用 UPDATE 语句或借助 MyBatis-Plus 的条件构造器并确保方法被拦截器处理。