# ADR-001: 采用 Alibaba 轻量化 DDD 分层架构

## 背景

需要在传统 MVC 三层（Controller-Service-DAO）和完整 DDD 之间选择项目的基础架构分层方案。项目初期为中小型微服务，但预留演进到复杂中台的可能性。

## 决策

采用 **Alibaba 轻量化 DDD 精简版**：

```
Controller → Manager → Service → Domain → Mapper
```

精简版包结构（中小服务）：

```
controller / manager / domain(dto+entity+vo) / mapper / config / exception / util
```

完整版（复杂中台才启用）：application / domain(aggregate+entity+valueObject+service+event+repository) / converter / infra

## 备选方案

| 方案                 | 优点          | 缺点                    |
| ------------------ | ----------- | --------------------- |
| 传统 MVC 三层          | 简单，上手快      | Service 层臃肿，分布式逻辑无处安放 |
| 完整 DDD             | 架构严谨，适合复杂业务 | 过度设计，学习成本高，代码量大       |
| **Alibaba 轻量 DDD** | 够用不臃肿，预留扩展点 | Manager 层需要团队理解其职责    |

## 后果

- ✅ 小项目（如 user-service）不臃肿，4-5 个包足够
- ✅ 复杂项目（如 trade-service 未来）可逐步开启 application/converter/infra 层
- ✅ 分布式锁/限流/Feign 编排 → 统一收口到 Manager
- ⚠️ 新成员需理解 Manager 与 Service 的分工：Manager=编排，Service=原子业务

## 日期

2026-03-01

## 参考

- 《阿里 Java 开发手册》
- `doc/knowledge/架构分层、工程结构、微服务规范、分布式设计选择.md`
- `doc/knowledge/冰美商城的最终架构.md`
