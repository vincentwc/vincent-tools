package com.vincent.tools.audit.web;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

public class AuditAdminDisabledResourceFilter implements Filter {
    private final String basePath;

    public AuditAdminDisabledResourceFilter(String basePath) {
        this.basePath = normalizeBase(basePath);
    }

    @Override
    public void init(FilterConfig filterConfig) {
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        if (isAdminPath(pathOf(httpRequest))) {
            HttpServletResponse httpResponse = (HttpServletResponse) response;
            httpResponse.setStatus(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        chain.doFilter(request, response);
    }

    @Override
    public void destroy() {
    }

    private boolean isAdminPath(String uri) {
        return uri.equals(basePath) || uri.startsWith(basePath + "/");
    }

    private static String pathOf(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String context = request.getContextPath();
        if (context != null && context.length() > 0 && uri.startsWith(context)) {
            return uri.substring(context.length());
        }
        return uri;
    }

    private static String normalizeBase(String basePath) {
        if (basePath == null || basePath.length() == 0 || "/".equals(basePath)) {
            return "/audit-admin";
        }
        if (basePath.endsWith("/") && basePath.length() > 1) {
            return basePath.substring(0, basePath.length() - 1);
        }
        return basePath;
    }
}
