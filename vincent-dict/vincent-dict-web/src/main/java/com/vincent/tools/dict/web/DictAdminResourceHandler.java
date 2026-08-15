package com.vincent.tools.dict.web;

import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

import java.io.IOException;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

public class DictAdminResourceHandler implements WebMvcConfigurer {
    static final String RESOURCE_LOCATION = "classpath:/META-INF/resources/dict-admin/";

    private final String basePath;

    public DictAdminResourceHandler(String basePath) {
        this.basePath = basePath;
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler(resourcePattern())
                .addResourceLocations(resourceLocation())
                .setCacheControl(CacheControl.maxAge(365, TimeUnit.DAYS).cachePublic())
                .resourceChain(true)
                .addResolver(new SpaIndexFallbackResolver());
    }

    String resourcePattern() {
        return normalizedBasePath() + "/**";
    }

    String resourceLocation() {
        return RESOURCE_LOCATION;
    }

    private String normalizedBasePath() {
        if (basePath == null || basePath.length() == 0 || "/".equals(basePath)) {
            return "";
        }
        if (basePath.endsWith("/")) {
            return basePath.substring(0, basePath.length() - 1);
        }
        return basePath;
    }

    private static final class SpaIndexFallbackResolver extends PathResourceResolver {
        @Override
        protected Resource getResource(String resourcePath, Resource location) throws IOException {
            Resource resource = super.getResource(resourcePath, location);
            if (resource != null) {
                return resource;
            }
            if (looksLikeStaticAsset(resourcePath)) {
                return null;
            }
            return location.createRelative("index.html");
        }

        private static boolean looksLikeStaticAsset(String path) {
            String lower = path.toLowerCase(Locale.ROOT);
            return lower.endsWith(".js") || lower.endsWith(".css") || lower.endsWith(".map")
                    || lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg")
                    || lower.endsWith(".svg") || lower.endsWith(".ico") || lower.endsWith(".woff")
                    || lower.endsWith(".woff2");
        }
    }
}
