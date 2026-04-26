package io.github.caoxin.aigateway.security.spring;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;

import java.util.LinkedHashSet;
import java.util.Set;

final class SpringSecurityAuthorityUtils {

    private static final String SCOPE_PREFIX = "SCOPE_";

    private SpringSecurityAuthorityUtils() {
    }

    static Set<String> permissions(Authentication authentication) {
        if (authentication == null) {
            return Set.of();
        }

        Set<String> permissions = new LinkedHashSet<>();
        for (GrantedAuthority authority : authentication.getAuthorities()) {
            String value = authority.getAuthority();
            if (value == null || value.isBlank()) {
                continue;
            }
            permissions.add(value);
            if (value.startsWith(SCOPE_PREFIX) && value.length() > SCOPE_PREFIX.length()) {
                permissions.add(value.substring(SCOPE_PREFIX.length()));
            }
        }
        return Set.copyOf(permissions);
    }
}
