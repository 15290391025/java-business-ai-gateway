package io.github.caoxin.aigateway.security.spring;

import io.github.caoxin.aigateway.core.context.AiUserContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SpringSecurityAiUserContextResolverTest {

    private final SpringSecurityAiUserContextResolver resolver = new SpringSecurityAiUserContextResolver();

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void resolvesPrincipalAuthoritiesAndTenant() {
        UsernamePasswordAuthenticationToken authentication = UsernamePasswordAuthenticationToken.authenticated(
            "evan",
            "n/a",
            List.of(
                new SimpleGrantedAuthority("SCOPE_order:read"),
                new SimpleGrantedAuthority("order:cancel")
            )
        );
        SecurityContextHolder.getContext().setAuthentication(authentication);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Tenant-Id", "tenant-1");

        AiUserContext context = resolver.resolve(request);

        assertThat(context.tenantId()).isEqualTo("tenant-1");
        assertThat(context.userId()).isEqualTo("evan");
        assertThat(context.permissions()).contains("SCOPE_order:read", "order:read", "order:cancel");
        assertThat(context.attributes()).containsEntry("security", "spring");
    }

    @Test
    void resolvesAnonymousWhenSecurityContextIsEmpty() {
        AiUserContext context = resolver.resolve(new MockHttpServletRequest());

        assertThat(context.tenantId()).isEqualTo("default");
        assertThat(context.userId()).isEqualTo("anonymous");
        assertThat(context.permissions()).isEmpty();
    }
}
