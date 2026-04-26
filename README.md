# Java Business AI Gateway

面向已有 Spring Boot 后端项目的自然语言业务操作网关。

这个项目的第一阶段目标很窄：让业务方通过 `@AiModule` 和 `@AiIntent` 显式声明可被 AI 调用的业务能力，然后由网关完成路由、参数绑定、权限判断、确认和审计。

## 当前骨架包含

- `ai-gateway-annotations`：业务能力声明注解。
- `ai-gateway-core`：能力注册表、路由、参数绑定、权限、确认、审计和调用链。
- `ai-gateway-spring-boot-autoconfigure`：Spring Boot 自动配置和 `/ai/*` HTTP 入口。
- `ai-gateway-spring-boot-starter`：业务项目接入用 starter。
- `ai-gateway-example-order`：订单查询和取消订单示例。

## 运行示例

当前环境还没有安装 Java 和 Maven。安装 JDK 17+ 和 Maven 后，在项目根目录执行：

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

