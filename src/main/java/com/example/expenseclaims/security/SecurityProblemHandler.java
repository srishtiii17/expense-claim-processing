package com.example.expenseclaims.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerExceptionResolver;

import java.io.IOException;

/**
 * Spring Security rejects requests in a filter, before any controller runs, so ApiExceptionHandler
 * would normally never see those errors. This class hands them to Spring MVC's exception resolver,
 * which calls ApiExceptionHandler. Result: 401 and 403 responses use the same problem-details
 * format as every other error, and there is still only one exception handler.
 */
@Component
public class SecurityProblemHandler implements AuthenticationEntryPoint, AccessDeniedHandler {

    private final HandlerExceptionResolver handlerExceptionResolver;

    public SecurityProblemHandler(@Qualifier("handlerExceptionResolver") HandlerExceptionResolver handlerExceptionResolver) {
        this.handlerExceptionResolver = handlerExceptionResolver;
    }

    // No token, or a token that is malformed, expired, tampered with or for an unknown employee -> 401.
    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException exception) throws IOException {
        if (handlerExceptionResolver.resolveException(request, response, null, exception) == null) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
        }
    }

    // Valid token, but the role is not allowed to do this -> 403.
    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException exception) throws IOException {
        if (handlerExceptionResolver.resolveException(request, response, null, exception) == null) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN);
        }
    }
}
