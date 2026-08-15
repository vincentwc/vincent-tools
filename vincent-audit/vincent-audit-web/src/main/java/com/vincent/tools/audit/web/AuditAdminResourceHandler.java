package com.vincent.tools.audit.web;

import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.HttpResource;
import org.springframework.web.servlet.resource.PathResourceResolver;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

public class AuditAdminResourceHandler implements WebMvcConfigurer {
    static final String RESOURCE_LOCATION = "classpath:/META-INF/resources/audit-admin/";
    private static final Pattern HASHED_CSS_OR_JS =
            Pattern.compile(".*[-.][a-z0-9]{8,}\\.(js|css)(\\.map)?");
    private static final CacheControl HASHED_ASSET_CACHE =
            CacheControl.maxAge(365, TimeUnit.DAYS).cachePublic();
    private static final CacheControl HTML_CACHE = CacheControl.noStore();

    private final String basePath;
    private final String apiPath;

    public AuditAdminResourceHandler(String basePath) {
        this(basePath, "/vincent/audit/admin/api/v1");
    }

    public AuditAdminResourceHandler(String basePath, String apiPath) {
        this.basePath = basePath;
        this.apiPath = apiPath == null || apiPath.length() == 0 ? "/vincent/audit/admin/api/v1" : apiPath;
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler(resourcePattern())
                .addResourceLocations(resourceLocation())
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

    private static CacheControl cacheControlFor(String resourcePath) {
        return isHashedCssOrJs(resourcePath) ? HASHED_ASSET_CACHE : HTML_CACHE;
    }

    private static boolean isHashedCssOrJs(String resourcePath) {
        return HASHED_CSS_OR_JS.matcher(fileNameOf(resourcePath).toLowerCase(Locale.ROOT)).matches();
    }

    private static String fileNameOf(String resourcePath) {
        int slash = resourcePath.lastIndexOf('/');
        return slash >= 0 ? resourcePath.substring(slash + 1) : resourcePath;
    }

    private String historyBase() {
        if (basePath == null || basePath.length() == 0) {
            return "/audit-admin";
        }
        if (basePath.endsWith("/") && basePath.length() > 1) {
            return basePath.substring(0, basePath.length() - 1);
        }
        return basePath;
    }

    private final class SpaIndexFallbackResolver extends PathResourceResolver {
        @Override
        protected Resource getResource(String resourcePath, Resource location) throws IOException {
            Resource resource = super.getResource(resourcePath, location);
            if (resource != null) {
                return CacheControlledResource.wrap(resource, resourcePath, apiPath, historyBase());
            }
            if (looksLikeStaticAsset(resourcePath)) {
                return null;
            }
            return CacheControlledResource.wrap(location.createRelative("index.html"), "index.html", apiPath, historyBase());
        }

        private boolean looksLikeStaticAsset(String path) {
            String lower = path.toLowerCase(Locale.ROOT);
            return lower.endsWith(".js") || lower.endsWith(".css") || lower.endsWith(".map")
                    || lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg")
                    || lower.endsWith(".svg") || lower.endsWith(".ico") || lower.endsWith(".woff")
                    || lower.endsWith(".woff2");
        }
    }

    private static final class CacheControlledResource implements HttpResource {
        private final Resource delegate;
        private final CacheControl cacheControl;
        private final String apiPath;
        private final String historyBase;
        private final boolean injectConfig;
        private byte[] injected;

        private CacheControlledResource(Resource delegate, CacheControl cacheControl,
                                        String apiPath, String historyBase, boolean injectConfig) {
            this.delegate = delegate;
            this.cacheControl = cacheControl;
            this.apiPath = apiPath;
            this.historyBase = historyBase;
            this.injectConfig = injectConfig;
        }

        static Resource wrap(Resource resource, String resourcePath, String apiPath, String historyBase) {
            boolean inject = "index.html".equals(fileNameOf(resourcePath));
            return new CacheControlledResource(resource, cacheControlFor(resourcePath), apiPath, historyBase, inject);
        }

        private byte[] injectedBody() throws IOException {
            if (!injectConfig) {
                return null;
            }
            if (injected == null) {
                injected = AuditAdminSpaHtml.injectBytes(delegate, apiPath, historyBase);
            }
            return injected;
        }

        @Override
        public HttpHeaders getResponseHeaders() {
            HttpHeaders headers = new HttpHeaders();
            if (delegate instanceof HttpResource) {
                headers.putAll(((HttpResource) delegate).getResponseHeaders());
            }
            headers.setCacheControl(cacheControl);
            return headers;
        }

        @Override
        public boolean exists() {
            return delegate.exists();
        }

        @Override
        public boolean isReadable() {
            return delegate.isReadable();
        }

        @Override
        public boolean isOpen() {
            return delegate.isOpen();
        }

        @Override
        public boolean isFile() {
            return !injectConfig && delegate.isFile();
        }

        @Override
        public URL getURL() throws IOException {
            return delegate.getURL();
        }

        @Override
        public URI getURI() throws IOException {
            return delegate.getURI();
        }

        @Override
        public File getFile() throws IOException {
            return delegate.getFile();
        }

        @Override
        public long contentLength() throws IOException {
            byte[] body = injectedBody();
            return body != null ? body.length : delegate.contentLength();
        }

        @Override
        public long lastModified() throws IOException {
            return delegate.lastModified();
        }

        @Override
        public Resource createRelative(String relativePath) throws IOException {
            return delegate.createRelative(relativePath);
        }

        @Override
        public String getFilename() {
            return delegate.getFilename();
        }

        @Override
        public String getDescription() {
            return delegate.getDescription();
        }

        @Override
        public InputStream getInputStream() throws IOException {
            byte[] body = injectedBody();
            if (body != null) {
                return new ByteArrayInputStream(body);
            }
            return delegate.getInputStream();
        }
    }
}
