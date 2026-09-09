## MapStruct 集合映射行为详解

### 核心答案

| 问题 | 答案 |
|------|------|
| **mapList 会自动生成吗？** | **是的**，只要在 Mapper 接口中声明返回 `List<T>` 的方法，MapStruct 会自动生成实现代码 |
| **源为 null 时返回什么？** | **返回 null**（默认行为） |
| **源为空集合时返回什么？** | **返回空集合**（如 `new ArrayList<>()`） |
| **目标类型集合的含义** | 指映射结果类型为集合，如 `List<UserDTO>`、`Set<EmployeeDTO>` 等 |


### 1. mapList 自动生成机制

在 Mapper 接口中声明集合映射方法后，MapStruct 在编译期会自动生成实现代码：

```java
@Mapper
public interface UserMapper {
    // 单对象映射
    UserDTO map(User user);
    
    // 集合映射 - 自动生成实现
    List<UserDTO> mapList(List<User> users);
}
```

**生成的实现代码大致如下**（MapStruct 自动生成）：
```java
@Override
public List<UserDTO> mapList(List<User> users) {
    if (users == null) {
        return null;           // 源为 null → 返回 null
    }
    
    List<UserDTO> list = new ArrayList<>(users.size());
    for (User user : users) {
        list.add(map(user));   // 复用单对象映射方法
    }
    return list;               // 源为空集合 → 返回空 ArrayList
}
```

### 2. 返回值策略详解

| 源集合状态 | 返回值 | 说明 |
|-----------|--------|------|
| `null` | `null` | 默认行为，可通过配置修改 |
| 空集合（如 `new ArrayList<>()`） | 新创建的空集合（如 `new ArrayList<>()`） | 不是 `null`，而是空集合对象 |
| 有元素的集合 | 映射后的新集合 | 每个元素通过单对象映射方法转换 |

### 3. 空值处理策略配置

如果希望源为 `null` 时返回空集合而非 `null`，可以通过 `nullValueMappingStrategy` 配置：

```java
@Mapper(nullValueMappingStrategy = NullValueMappingStrategy.RETURN_DEFAULT)
public interface UserMapper {
    List<UserDTO> mapList(List<User> users);
}
```

配置后，源为 `null` 时将返回空集合（如 `new ArrayList<>()`）。

### 4. 目标类型集合的含义

"目标类型集合"指映射结果的集合类型，MapStruct 支持多种集合类型：

```java
@Mapper
public interface UserMapper {
    // List → List
    List<UserDTO> mapList(List<User> users);
    
    // Set → Set
    Set<UserDTO> mapSet(Set<User> users);
    
    // Map → Map
    Map<String, UserDTO> mapMap(Map<String, User> userMap);
}
```

**默认实现类型**：
- `List` → `ArrayList`
- `Set` → `HashSet`
- `Map` → `HashMap`

### 总结

MapStruct 的设计非常合理：**null 进 null 出，空集合进空集合出**。这符合大多数业务场景的预期——空集合和 null 在语义上是不同的，前者表示"有这个东西但没内容"，后者表示"不存在"。如果你的业务需要统一返回空集合，可以通过 `nullValueMappingStrategy` 配置修改。