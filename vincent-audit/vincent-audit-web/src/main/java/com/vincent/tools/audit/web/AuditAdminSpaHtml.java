package com.vincent.tools.audit.web;

import com.vincent.tools.common.web.VincentAdminSpaHtml;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

final class AuditAdminSpaHtml {
    private static final String CONFIG_GLOBAL_NAME = "__VIN_AUDIT_CONFIG__";
    static final String RESOURCE_PATH = "META-INF/resources/audit-admin/index.html";

    private AuditAdminSpaHtml() {
    }

    static String readAndInject(String apiPath, String historyBase) throws IOException {
        return inject(read(new ClassPathResource(RESOURCE_PATH)), apiPath, historyBase);
    }

    static byte[] injectBytes(Resource resource, String apiPath, String historyBase) throws IOException {
        return VincentAdminSpaHtml.injectBytes(resource, CONFIG_GLOBAL_NAME, apiPath, historyBase);
    }

    static String inject(String html, String apiPath, String historyBase) {
        return VincentAdminSpaHtml.inject(html, CONFIG_GLOBAL_NAME, apiPath, historyBase);
    }

    private static String read(Resource resource) throws IOException {
        InputStream input = resource.getInputStream();
        try {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int read;
            while ((read = input.read(buffer)) >= 0) {
                output.write(buffer, 0, read);
            }
            return new String(output.toByteArray(), StandardCharsets.UTF_8);
        } finally {
            input.close();
        }
    }
}
