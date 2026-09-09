```java

package org.noLazy.common.domain;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.noLazy.common.utils.BeanUtils;
import org.springframework.util.CollectionUtils;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

/**
 * @ClassName: PageDTO
 * @Description: TODO
 * @Author: noLazy
 * @Date: 2026/3/22 14:07
 * @Version: 1.0.0
 * @ProjectName: IcedAmericanoMall
 * @Package: org.noLazy.common.domain
 */

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PageDTO<T> {
    //总记录数
    protected Long total;
    //总页数
    protected Long pages;
    //查询指定页的记录
    protected List<T> list;

    public static <T> PageDTO<T> empty(Long total, Long pages) {
        return new PageDTO<>(total, pages, Collections.emptyList());
    }

    public static <T> PageDTO<T> empty(Page<?> page) {
        return new PageDTO<>(page.getTotal(), page.getPages(), Collections.emptyList());
    }
    public static <T> PageDTO<T> of(Page<T> page) {
        if (Objects.isNull(page)) {
            return new PageDTO<>();
        }
        if (CollectionUtils.isEmpty(page.getRecords())) {
            return empty(page);
        }
        return new PageDTO<>(page.getTotal(), page.getPages(), page.getRecords());
    }
    public static <T, R> PageDTO<R> of(Page<T> page, Function<T, R> mapper) {
        if (Objects.isNull(page)) {
            return new PageDTO<>();
        }
        if (CollectionUtils.isEmpty(page.getRecords())) {
            return empty(page);
        }
        return new PageDTO<>(page.getTotal(), page.getPages(), page.getRecords().stream().map(mapper).toList());
    }
    public static <T> PageDTO<T> of(Page<?> page, List<T> list) {
        return new PageDTO<>(page.getTotal(), page.getPages(), list);
    }
    public static <T, R> PageDTO<R> of(Page<T> page, Class<R> clazz) {
        return new PageDTO<>(page.getTotal(), page.getPages(), BeanUtils.copyList(page.getRecords(), clazz));
    }



}
```

为什么使用？，有什么更好的解决方案，还有什么需要优化的地方，只知道这些属性可以分页查询吗
这段代码是一个通用分页 DTO（`PageDTO`），主要用于在 **持久层分页对象（MyBatis-Plus 的 `Page<T>`）** 与 **应用层或 API 层** 之间进行数据转换。下面从**设计意图、替代方案、优化建议、功能完整性**四个方面进行详细分析。

---

## 1. 为什么使用 `PageDTO`？

### 1.1 解耦持久层与业务层
- MyBatis-Plus 的 `Page<T>` 是持久层实现细节，如果直接返回给前端，会暴露框架内部属性（如 `optimizeCountSql`、`searchCount` 等），增加前后端耦合。
- `PageDTO` 只包含分页最核心的三个字段：`total`（总记录数）、`pages`（总页数）、`list`（当前页数据），符合接口返回的通用约定。

### 1.2 支持类型转换
- 提供了多种 `of` 方法，可以将 `Page<Entity>` 转换为 `PageDTO<DTO>`，支持通过 **函数映射** 或 **反射拷贝**（`BeanUtils.copyList`）来转换记录列表，避免在业务代码中重复编写转换逻辑。

### 1.3 统一空分页处理
- `empty` 方法在无数据时返回一个 `total`、`pages` 正确但 `list` 为空的 `PageDTO`，避免了 `null` 传播，降低调用方空指针风险。

---

## 2. 有什么更好的解决方案？

### 2.1 使用 Java Record（JDK 14+）
如果项目已升级到 JDK 14+，可以用 **record** 替代 `@Data`，使 DTO 更简洁且不可变：
```java
public record PageDTO<T>(Long total, Long pages, List<T> list) {
    // 静态工厂方法类似
}
```

### 2.2 集成 Spring Data 的 `Page` 接口
如果项目同时使用 Spring Data JPA，可以统一使用 Spring 的 `Page<T>` 作为接口返回类型，它的属性与 `PageDTO` 基本一致，无需额外定义。但 Spring 的 `Page` 仍携带一些分页上下文（如 `Pageable`），可能会造成依赖泄露。

### 2.3 使用更强大的通用分页工具
- **MyBatis-Plus 自带转换**：MyBatis-Plus 提供了 `Page` 的 `convert` 方法，可以直接进行类型转换：
  ```java
  Page<DTO> dtoPage = entityPage.convert(entity -> convertToDTO(entity));
  ```
  但 `convert` 返回的仍是 `Page<DTO>`，若想彻底解耦，仍需转为自定义 DTO。
- **使用 MapStruct**：对于复杂的类型转换，可以结合 MapStruct 等编译期映射工具，替代反射（`BeanUtils`），提升性能。

---

## 3. 还有什么需要优化的地方？

### 3.1 性能优化
- `BeanUtils.copyList` 内部使用反射，在大数据量时可能成为瓶颈。可以考虑：
  - 改用 **MapStruct** 或 **ModelMapper**（但 ModelMapper 也依赖反射）。
  - 如果确定只在单线程中使用，且转换逻辑简单，可以接受反射开销；但若频繁调用，建议预编译映射器。
- `page.getRecords().stream().map(mapper).toList()` 在 Java 16+ 中直接使用 `Stream.toList()`，返回不可变列表，但若期望可修改列表，应使用 `collect(Collectors.toList())`。

### 3.2 空值处理
- `of(Page<T> page, Class<R> clazz)` 方法中，若 `page.getRecords()` 不为空但 `BeanUtils.copyList` 返回的列表为 `null`（例如拷贝失败），会直接赋值给 `list` 字段，导致 `list` 为 `null`。建议统一使用 `Collections.emptyList()` 兜底：
  ```java
  List<R> copied = BeanUtils.copyList(page.getRecords(), clazz);
  return new PageDTO<>(page.getTotal(), page.getPages(), 
          copied != null ? copied : Collections.emptyList());
  ```

### 3.3 扩展分页信息
- 当前只包含 `total`、`pages`、`list`，但前端可能需要 **当前页码**、**每页大小** 等信息，方便重新发起请求。可以考虑增加 `current` 和 `size` 字段，或通过构造函数/工厂方法传入。

### 3.4 线程安全与不可变性
- `PageDTO` 是可变对象（`@Data` 生成 setter），在多线程环境下共享时可能出现并发修改问题。如果作为 API 返回对象，通常不会被修改，可以考虑将字段设为 `final` 并移除 setter，使用构造器创建。

### 3.5 泛型通配符简化
- 部分 `of` 方法使用了通配符 `Page<?>`，但内部又返回 `PageDTO<T>`，这可能导致调用时类型推断混乱。可以统一为 `Page<?> page` 但返回 `PageDTO<T>`，调用方需明确指定泛型，或者增加重载方法。

---

## 4. 只知道这些属性可以分页查询吗？

**不能。** `PageDTO` 仅作为**分页查询的结果封装**，它不包含分页请求参数（如 `pageNum`、`pageSize`）。要执行分页查询，还需要：
- 一个**分页请求对象**（例如 `PageRequest` 或 `PageParam`），包含 `current`（当前页）、`size`（每页条数）等信息。
- 在持久层使用 MyBatis-Plus 的 `Page<T>` 传入分页参数，执行查询后得到结果，再转换为 `PageDTO`。

因此，`PageDTO` 只是**分页响应**的一部分，不能单独完成分页查询。完整的流程通常如下：
```java
// 1. 接收前端分页参数
PageParam param = new PageParam(pageNum, pageSize);

// 2. 构建 MyBatis-Plus 的 Page 对象
Page<Entity> mpPage = new Page<>(param.getCurrent(), param.getSize());

// 3. 执行查询
myService.page(mpPage, queryWrapper);

// 4. 转换为 PageDTO
return PageDTO.of(mpPage, dtoClass);
```

---

## 总结

| 方面 | 评价与建议 |
|------|-----------|
| **设计目的** | 合理，解耦持久层与上层，统一分页响应格式 |
| **替代方案** | 可使用 Java Record、Spring Data `Page`、MapStruct 等增强 |
| **优化点** | 反射性能、空列表兜底、增加分页请求字段、不可变性改造 |
| **功能范围** | 仅封装响应，不能替代分页请求；需配合分页请求对象使用 |

如果需要进一步改进，可以结合项目实际需求，将 `PageDTO` 改造为不可变 record，增加分页请求参数，并引入更高效的转换工具。