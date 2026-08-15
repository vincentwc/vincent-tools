package com.vincent.tools.dict.web;

import com.vincent.tools.dict.application.admin.DictAdminPermission;
import com.vincent.tools.dict.application.admin.PermissionProvider;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Locale;
import java.util.Optional;

public class DictAdminPageAuthFilter implements Filter {
    private final PermissionProvider permissionProvider;
    private final String basePath;

    public DictAdminPageAuthFilter(PermissionProvider permissionProvider, String basePath) {
        this.permissionProvider = permissionProvider;
        this.basePath = normalizeBase(basePath);
    }

    @Override
    public void init(FilterConfig filterConfig) {
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        if (isProtectedHtml(pathOf(httpRequest))
                && !permissionProvider.hasPermission(DictAdminPermission.DICT_VIEW, Optional.<String>empty())) {
            HttpServletResponse httpResponse = (HttpServletResponse) response;
            httpResponse.setStatus(HttpServletResponse.SC_FORBIDDEN);
            httpResponse.setCharacterEncoding("UTF-8");
            httpResponse.setContentType("application/json;charset=UTF-8");
            httpResponse.getWriter().write(
                    "{\"success\":false,\"code\":\"PERMISSION_DENIED\",\"message\":\"permission denied\"}");
            return;
        }
        chain.doFilter(request, response);
    }

    @Override
    public void destroy() {
    }

    private boolean isProtectedHtml(String uri) {
        if (uri.equals(basePath) || uri.equals(basePath + "/") || uri.equals(basePath + "/index.html")) {
            return true;
        }
        return uri.startsWith(basePath + "/") && !looksLikeStaticAsset(uri);
    }

    private static boolean looksLikeStaticAsset(String path) {
        String lower = path.toLowerCase(Locale.ROOT);
        return lower.endsWith(".js") || lower.endsWith(".css") || lower.endsWith(".map")
                || lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg")
                || lower.endsWith(".svg") || lower.endsWith(".ico") || lower.endsWith(".woff")
                || lower.endsWith(".woff2");
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
            return "/dict-admin";
        }
        if (basePath.endsWith("/") && basePath.length() > 1) {
            return basePath.substring(0, basePath.length() - 1);
        }
        return basePath;
    }
}
