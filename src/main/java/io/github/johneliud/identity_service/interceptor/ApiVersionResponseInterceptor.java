package io.github.johneliud.identity_service.interceptor;

import org.jspecify.annotations.NonNull;
import org.springframework.web.accept.ApiVersionStrategy;
import org.springframework.web.servlet.HandlerInterceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public class ApiVersionResponseInterceptor implements HandlerInterceptor {

    static final String RESPONSE_HEADER = "X-API-Version";

    private final ApiVersionStrategy apiVersionStrategy;
    private final String defaultVersion;

    public ApiVersionResponseInterceptor(ApiVersionStrategy apiVersionStrategy, String defaultVersion) {
        this.apiVersionStrategy = apiVersionStrategy;
        this.defaultVersion = defaultVersion;
    }

    @Override
    public boolean preHandle(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull Object handler) {

        String version = apiVersionStrategy.resolveVersion(request);

        if (version == null) {
            version = defaultVersion;
        }

        if (version != null) {
            response.setHeader(RESPONSE_HEADER, normalise(version));
        }

        return true;
    }

    static String normalise(String version) {
        if (version != null && version.length() > 1
                && (version.charAt(0) == 'v' || version.charAt(0) == 'V')) {
            return version.substring(1);
        }
        return version;
    }
}
