package io.github.caoxin.aigateway.autoconfigure;

import io.github.caoxin.aigateway.core.context.AiUserContext;
import jakarta.servlet.http.HttpServletRequest;

public interface AiUserContextResolver {

    AiUserContext resolve(HttpServletRequest request);
}

