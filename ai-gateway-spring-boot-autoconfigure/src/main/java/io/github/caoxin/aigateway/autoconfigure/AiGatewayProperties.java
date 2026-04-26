package io.github.caoxin.aigateway.autoconfigure;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "ai.gateway")
public class AiGatewayProperties {

    private boolean enabled = true;

    private Router router = new Router();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Router getRouter() {
        return router;
    }

    public void setRouter(Router router) {
        this.router = router;
    }

    public static class Router {

        private RouterType type = RouterType.AUTO;

        public RouterType getType() {
            return type;
        }

        public void setType(RouterType type) {
            this.type = type;
        }
    }

    public enum RouterType {
        AUTO,
        KEYWORD,
        MODEL
    }
}
