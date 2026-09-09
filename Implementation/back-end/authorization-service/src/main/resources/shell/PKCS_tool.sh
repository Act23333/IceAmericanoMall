# 生成 RSA 密钥对并存入 PKCS#12 密钥库
keytool -genkeypair -alias jwt-rsa \
  -keyalg RSA -keysize 2048 \
  -keystore authorization-server.p12 \
  -storetype PKCS12 \
  -validity 3650 \
  -storepass your_keystore_password \
  -keypass your_private_key_password