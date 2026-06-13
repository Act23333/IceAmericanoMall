要生成用于 RS256（RSA 签名算法 + SHA-256）的 JKS（Java KeyStore）文件，最直接的方法是使用 Java 自带的 `keytool` 工具。以下是具体步骤：

---

### 1. 使用 `keytool` 生成密钥对并创建 JKS
在命令行中执行以下命令：

```bash
keytool -genkeypair -alias mykey -keyalg RSA -keysize 2048 -sigalg SHA256withRSA -keystore keystore.jks -validity 365
```

**参数说明：**
- `-genkeypair`：生成密钥对（私钥+公钥）并存储。
- `-alias mykey`：为该密钥对指定一个别名（可自定义）。
- `-keyalg RSA`：密钥算法为 RSA。
- `-keysize 2048`：密钥长度（推荐 2048 或更高）。
- `-sigalg SHA256withRSA`：指定签名算法为 RS256（Java 中对应 `SHA256withRSA`）。
- `-keystore keystore.jks`：生成的 JKS 文件名。
- `-validity 365`：证书有效期（天数）。

执行后会依次提示你输入：
- **密钥库密码**：设置 JKS 文件的密码（至少 6 位，记住它）。
- **密钥密码**：私钥的密码（可回车与密钥库密码相同）。
- **姓名、组织等 X.500 信息**：根据提示填写（至少提供“姓名”即 CN）。

完成后，当前目录下就会生成 `keystore.jks` 文件，其中包含一个自签名的 RSA 证书和对应的私钥。

---

### 2. 验证 JKS 内容
用以下命令查看密钥库中的条目：

```bash
keytool -list -v -keystore keystore.jks
```

输入密钥库密码后，可看到别名 `mykey` 的证书详情，其中签名算法应显示为 `SHA256withRSA`。

---

### 3. 导出公钥证书（可选）
如果需要将公钥分发给其他应用（如用于验证 JWT），可以导出为证书文件：

```bash
keytool -exportcert -alias mykey -keystore keystore.jks -file mycert.crt -rfc
```
- `-rfc` 表示输出 PEM 格式（文本），若不指定则输出 DER 二进制格式。

导出的 `mycert.crt` 就是公钥证书，可用于验证 RS256 签名。

---

### 4. 注意事项
- **密码安全**：请妥善保管密钥库密码和私钥密码，切勿硬编码在代码中。
- **证书类型**：上述生成的是自签名证书，适合测试环境。生产环境建议使用 CA 签发的证书，可将 CA 签发的证书链导入 JKS（用 `keytool -importcert`）。
- **别名管理**：一个 JKS 可存储多个密钥对，通过不同别名区分。
- **Java 版本**：`keytool` 随 JDK 提供，确保 Java 环境变量已配置。

---

### 扩展：从现有私钥和证书导入 JKS
如果你已有 PEM 格式的 RSA 私钥（如 `private.pem`）和证书（如 `cert.pem`），可通过 `openssl` 转换为 PKCS12，再转为 JKS：

```bash
# 1. 生成 PKCS12 文件
openssl pkcs12 -export -in cert.pem -inkey private.pem -out keystore.p12 -name mykey

# 2. 将 PKCS12 转换为 JKS
keytool -importkeystore -srckeystore keystore.p12 -srcstoretype PKCS12 -destkeystore keystore.jks -deststoretype JKS
```

此时 JKS 中的密钥对就包含了外部导入的私钥和证书。

---
