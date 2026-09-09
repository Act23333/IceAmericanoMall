在 Maven 多模块项目中，父工程的 `<build>` 配置对子模块的影响分为两种情况：

---

## 一、父工程 `<build>` 中的配置类型

### 1. `<pluginManagement>` – 插件管理
- **作用**：定义插件的版本、配置，但**不会自动应用到子模块**。
- **目的**：统一管理插件版本和默认配置，让子模块在引用时无需再指定版本，保持一致性。
- **继承方式**：子模块只需在 `<build><plugins>` 中声明插件，不写版本号，Maven 会自动从父工程的 `<pluginManagement>` 中查找对应配置。

### 2. `<plugins>` – 直接定义插件
- **作用**：将插件显式绑定到当前模块的构建生命周期。
- **继承方式**：如果父工程在 `<build><plugins>` 中直接定义了插件，**所有子模块都会继承该插件及其配置**，除非子模块显式覆盖或排除。

---

## 二、示例对比

### 父工程 `pom.xml`
```xml
<build>
    <pluginManagement>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
                <version>${spring-boot.version}</version>
                <configuration>
                    <mainClass>com.nolazy.Application</mainClass>
                </configuration>
            </plugin>
        </plugins>
    </pluginManagement>

    <plugins>
        <plugin>
            <groupId>org.apache.maven.plugins</groupId>
            <artifactId>maven-compiler-plugin</artifactId>
            <version>3.13.0</version>
            <configuration>
                <source>21</source>
                <target>21</target>
            </configuration>
        </plugin>
    </plugins>
</build>
```

### 子模块 `pom.xml`
```xml
<build>
    <plugins>
        <!-- 仅声明，无需版本号，从父的 pluginManagement 继承 -->
        <plugin>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-maven-plugin</artifactId>
        </plugin>
        <!-- maven-compiler-plugin 无需再声明，因为父已直接定义，自动继承 -->
    </plugins>
</build>
```

---

## 三、常用场景与建议

### ✅ 推荐：父工程管理插件版本，子模块按需引入
```xml
<!-- 父工程：只管理版本 -->
<pluginManagement>
    <plugins>
        <plugin>
            <groupId>org.apache.maven.plugins</groupId>
            <artifactId>maven-compiler-plugin</artifactId>
            <version>3.13.0</version>
            <configuration>
                <source>21</source>
                <target>21</target>
            </configuration>
        </plugin>
    </plugins>
</pluginManagement>
```
```xml
<!-- 子模块：需要时才添加 -->
<build>
    <plugins>
        <plugin>
            <groupId>org.apache.maven.plugins</groupId>
            <artifactId>maven-compiler-plugin</artifactId>
        </plugin>
    </plugins>
</build>
```
这样每个模块可灵活选择是否使用该插件，避免所有子模块强制继承。

### ⚠️ 不推荐：父工程直接定义插件（除非所有子模块都需要）
如果父工程 `<plugins>` 直接定义了 `spring-boot-maven-plugin`，那么所有子模块（包括 common 模块）都会继承它，而 common 模块本不需要打包成可执行 jar，可能导致构建问题。

---

## 四、Spring Boot 插件的特殊注意
Spring Boot 的 `spring-boot-maven-plugin` 通常只应在**可执行微服务模块**中使用（如 user-service、gateway-service）。因此在父工程中应仅通过 `<pluginManagement>` 管理其版本，子模块按需添加。

---

## 五、总结
- **插件版本管理**：父工程用 `<pluginManagement>` 统一版本，子模块引用。
- **强制继承**：父工程 `<plugins>` 中定义的插件，所有子模块自动继承。
- **最佳实践**：父工程只定义 `<pluginManagement>`，子模块根据需要显式引用，保持灵活性和可维护性。

这样既保证了版本一致性，又避免了子模块引入不必要的插件。