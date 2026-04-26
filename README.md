# Java Business AI Gateway

面向已有 Spring Boot 后端项目的自然语言业务操作网关。

这个项目的第一阶段目标很窄：让业务方通过 `@AiModule` 和 `@AiIntent` 显式声明可被 AI 调用的业务能力，然后由网关完成路由、参数绑定、权限判断、确认和审计。

## 当前骨架包含

- `ai-gateway-annotations`：业务能力声明注解。
- `ai-gateway-core`：能力注册表、路由、参数绑定、权限、确认、审计和调用链。
- `ai-gateway-spring-boot-autoconfigure`：Spring Boot 自动配置和 `/ai/*` HTTP 入口。
- `ai-gateway-adapter-spring-ai`：Spring AI `ChatClient` 模型适配器。
- `ai-gateway-security-spring`：Spring Security 权限和用户上下文适配。
- `ai-gateway-jdbc`：确认快照、审计日志和技术 Trace 的 JDBC 持久化。
- `ai-gateway-spring-boot-starter`：业务项目接入用 starter。
- `ai-gateway-example-order`：订单查询和取消订单示例。

## 当前状态

这是早期 v0.5 骨架，已经打通确定性关键词路由和模型辅助路由两种模式。默认 `auto` 模式下，如果 Spring 容器中存在 `AiModelClient`，会使用 `ModelIntentRouter`；否则退回关键词路由器，保证示例项目不依赖外部模型也能运行。

已经覆盖的主链路：

- 显式声明业务能力。
- 启动时扫描能力并做用户权限过滤。
- 自然语言输入路由到单个 intent。
- 简单多步计划，支持 `previous.status == 'NOT_SHIPPED'` 这类前一步结果条件。
- 绑定 Command 参数。
- Bean Validation 参数校验。
- 会话追问闭环：参数缺失时保存待执行计划，下一轮同用户同 session 补参后继续执行。
- 权限校验和高风险确认。
- 可选 Spring Security 适配，从当前 `Authentication` 读取用户和 authorities。
- 确认时执行冻结后的 action snapshot。
- 审计记录。
- 技术 Trace，记录路由、确认、权限、策略、调用等阶段。
- 可选 JDBC 持久化确认快照、审计日志和 Trace。
- 可选 Spring AI `ChatClient` 适配为 `AiModelClient`。
- 可选模型路由器，基于当前用户可见能力生成结构化执行计划，并校验模型返回的模块和 intent 是否在授权能力列表中。

暂未实现：

- 生产级模型路由评测和回放。
- Redis 持久化确认和审计。
- Spring Security 方法安全表达式深度集成。
- Sa-Token 适配。
- OpenTelemetry 集成。

## 路由器配置

默认配置是 `auto`：

```yaml
ai:
  gateway:
    router:
      type: auto
```

可选值：

- `auto`：存在 `AiModelClient` 时使用模型路由器，否则使用关键词路由器。
- `keyword`：强制使用确定性关键词路由器，适合本地 demo 和无模型环境。
- `model`：强制使用模型路由器；如果没有 `AiModelClient`，启动时直接失败。

## Spring AI 适配

业务项目如果已经使用 Spring AI，可以额外引入：

```xml
<dependency>
    <groupId>io.github.caoxin</groupId>
    <artifactId>ai-gateway-adapter-spring-ai</artifactId>
    <version>0.1.0-SNAPSHOT</version>
</dependency>
```

当 Spring 容器中存在 `ChatClient` 或 `ChatClient.Builder` 时，该模块会自动注册：

- `SpringAiModelClient`：把项目内的 `AiModelClient` SPI 适配到 Spring AI `ChatClient`。

当前能力边界：

- 支持普通 chat 调用。
- 支持把模型返回的 JSON object 解析到 `AiModelResponse.structured()`。
- 如果请求携带 `responseSchema`，会追加结构化 JSON 输出提示。
- 与 `ai.gateway.router.type=auto` 配合时，可驱动 `ModelIntentRouter` 做能力选择和参数计划生成。
- 暂不支持 streaming、tool calling、vision 和原生 JSON Schema 强约束。

如果要关闭该适配：

```yaml
ai:
  gateway:
    spring-ai:
      enabled: false
```

## Spring Security 适配

业务项目如果已经使用 Spring Security，可以额外引入：

```xml
<dependency>
    <groupId>io.github.caoxin</groupId>
    <artifactId>ai-gateway-security-spring</artifactId>
    <version>0.1.0-SNAPSHOT</version>
</dependency>
```

该模块会在 Spring Security 存在时自动注册：

- `SpringSecurityAiPermissionEvaluator`：用当前 `Authentication` 的 authorities 校验 `@AiPermission`。
- `SpringSecurityAiUserContextResolver`：用当前认证用户生成 `AiUserContext`。

权限匹配规则：

- `@AiPermission("order:cancel")` 可以匹配 authority `order:cancel`。
- 也可以匹配 OAuth2 scope 风格的 authority `SCOPE_order:cancel`。
- 租户默认从请求头 `X-Tenant-Id` 读取。

如果要关闭该适配：

```yaml
ai:
  gateway:
    security:
      spring:
        enabled: false
```

## JDBC 持久化

默认实现使用内存保存确认快照、审计日志和 Trace。业务项目如果需要落库，可以额外引入：

```xml
<dependency>
    <groupId>io.github.caoxin</groupId>
    <artifactId>ai-gateway-jdbc</artifactId>
    <version>0.1.0-SNAPSHOT</version>
</dependency>
```

当 Spring 容器中存在 `DataSource` 时，该模块会自动注册：

- `JdbcAiConfirmationRepository`：保存 `ai_confirmation`。
- `JdbcAiAuditLogger`：保存 `ai_audit_log`。
- `JdbcAiTraceLogger`：保存 `ai_trace`。

表结构在模块内置的 `io/github/caoxin/aigateway/jdbc/schema.sql`。开发环境可以开启自动建表：

```yaml
ai:
  gateway:
    jdbc:
      initialize-schema: true
```

生产环境建议由 Flyway、Liquibase 或数据库变更流程管理表结构。

## Trace

Trace 面向排障和后续回放评测，不替代业务审计。当前会记录这些阶段：

- `ROUTE`：路由结果、置信度、步骤数量。
- `VALIDATION`：参数校验失败。
- `PERMISSION`：权限拒绝。
- `POLICY`：策略拒绝。
- `CONFIRMATION`：创建确认快照。
- `INVOKE`：业务能力调用成功或失败。
- `CONFIRMATION_*`：确认执行阶段的查询、校验和调用。

Web 入口提供调试接口：

```bash
curl http://localhost:8080/ai/trace
```

默认只记录结构化阶段信息和元数据，不记录完整 prompt / completion。

## 会话追问

当路由已经确定，但 Command 参数不完整时，网关会保存一份待补充的执行计划：

```text
用户：帮我取消订单
系统：缺少参数: orderId
用户：20260426001
系统：继续原 cancel_order 计划，并进入高风险确认
```

追问状态按 `tenantId + userId + sessionId` 隔离，默认内存保存，10 分钟过期。补参成功后会从缺参数的步骤继续执行，不会重新路由到其他 intent。

## 运行示例

安装 JDK 17+ 和 Maven 后，在项目根目录执行：

```bash
mvn clean package
mvn -pl ai-gateway-example-order spring-boot:run
```

查询订单：

```bash
curl -X POST http://localhost:8080/ai/chat \
  -H "Content-Type: application/json" \
  -d '{"sessionId":"s1","userInput":"帮我查一下订单 20260426001"}'
```

取消订单会先返回确认卡片：

```bash
curl -X POST http://localhost:8080/ai/chat \
  -H "Content-Type: application/json" \
  -d '{"sessionId":"s1","userInput":"帮我取消订单 20260426001"}'
```

条件取消会先查询订单状态，只有前一步结果满足 `NOT_SHIPPED` 时才创建取消确认：

```bash
curl -X POST http://localhost:8080/ai/chat \
  -H "Content-Type: application/json" \
  -d '{"sessionId":"s1","userInput":"帮我查一下订单 20260426001，如果没发货就取消"}'
```

再用返回的 `confirmationId` 执行确认：

```bash
curl -X POST http://localhost:8080/ai/confirm \
  -H "Content-Type: application/json" \
  -d '{"confirmationId":"替换为返回值"}'
```

## 核心原则

- 默认不暴露任何业务能力。
- 只暴露显式标注的 `@AiModule` / `@AiIntent`。
- 模型或路由器只能建议调用，不能决定权限。
- 高风险操作确认的是冻结后的 action snapshot，不是自然语言。
- 执行前后都要留下可审计记录。

## 下一步路线

1. v0.1：单步 intent 调用闭环和测试覆盖。
2. v0.2：权限、确认快照和审计闭环。
3. v0.3：简单多步计划和前一步结果引用。
4. v0.4：Spring AI adapter、Spring Security 方法安全表达式。
5. v0.5：模型路由器、OpenTelemetry、Redis persistence、回放和模型路由评测。
