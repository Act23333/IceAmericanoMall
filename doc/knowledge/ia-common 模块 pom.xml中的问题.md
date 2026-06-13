### 1. 重复的 `<dependencies>` 块

您的 `ia-common` 中有两个 `<dependencies>` 块，Maven 会合并它们，但建议合并成一个，避免歧义。

### 2. 注解处理器顺序

在 `maven-compiler-plugin` 的配置中，`annotationProcessorPaths` 的顺序很重要：**Lombok 必须在 MapStruct 之前**，因为 Lombok 会生成代码，MapStruct 需要基于这些生成代码进行映射。您的配置顺序是正确的（Lombok 在前，MapStruct 在后），但版本号使用了 `${lombok.version}`，而父工程中并没有定义 `lombok.version` 属性。这会导致编译失败。

**解决方案**：  
在父工程的 `<properties>` 中添加 Lombok 版本：

xml

<lombok.version>1.18.36</lombok.version>

然后在 `ia-common` 的插件配置中引用该属性。如果不想在父工程中定义，可以直接写死版本，但统一管理更佳。

### 3. `spring-boot-starter-web` 和 `mybatis-plus-boot-starter` 的引入

`ia-common` 模块作为公共基础模块，通常**不应该**引入 `spring-boot-starter-web` 和 `mybatis-plus-boot-starter` 这类会触发自动配置的 starter。因为：

- `spring-boot-starter-web` 会引入 Tomcat 和 Spring MVC，而 common 模块可能只需要注解或部分工具，引入后会导致所有业务服务都间接依赖 Web 环境（即使它们可能不需要）。
    
- `mybatis-plus-boot-starter` 同样会触发 MyBatis 自动配置，而 common 模块可能只需要 MyBatis-Plus 的注解（如 `@TableId`），不应引入整个 starter。
    

**建议**：

- 如果 common 只需要 Spring 的注解，可以只引入 `spring-web`（provided）和 `mybatis-plus-annotation`（provided）。
    
- 如果需要 Web 相关的工具类（如 `HttpServletRequest`），可以只引入 `jakarta.servlet-api`（provided）。
    
- 将这两个 starter 移到具体的业务服务模块（如 `user-service`）中。

**注意**：子模块中不再需要重复配置 `maven-compiler-plugin` 的注解处理器，因为父工程已经通过 `pluginManagement` 定义了，子模块只需引用插件即可（甚至可以不写，因为父工程在 `<pluginManagement>` 中定义了但未强制继承，子模块如果不写则不会自动使用，所以建议子模块在 `<build><plugins>` 中显式声明 `<plugin>` 并继承配置，或者父工程直接将插件放在 `<plugins>` 中强制继承）。推荐在父工程 `<build><plugins>` 中直接定义 `maven-compiler-plugin`，这样所有子模块都会自动继承。但要注意父工程本身不需要编译（packaging=pom），所以这样定义是安全的。