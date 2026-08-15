package com.vincent.tools.dict.web;

import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

final class DictAdminSpaHtml {
    static final String RESOURCE_PATH = "META-INF/resources/dict-admin/index.html";

    private DictAdminSpaHtml() {
    }

    static String readAndInject(String apiPath, String historyBase) throws IOException {
        return inject(read(new ClassPathResource(RESOURCE_PATH)), apiPath, historyBase);
    }

    static byte[] injectBytes(Resource resource, String apiPath, String historyBase) throws IOException {
        return inject(read(resource), apiPath, historyBase).getBytes(StandardCharsets.UTF_8);
    }

    static String inject(String html, String apiPath, String historyBase) {
        String script = "<script>window.__VIN_DICT_CONFIG__={"
                + "\"apiPath\":" + jsonString(apiPath)
                + ",\"historyBase\":" + jsonString(historyBase)
                + "};</script>";
        int scriptTag = indexOfIgnoreCase(html, "<script");
        if (scriptTag >= 0) {
            return html.substring(0, scriptTag) + script + html.substring(scriptTag);
        }
        int headEnd = indexOfIgnoreCase(html, "</head>");
        if (headEnd >= 0) {
            return html.substring(0, headEnd) + script + html.substring(headEnd);
        }
        return script + html;
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

    private static int indexOfIgnoreCase(String html, String token) {
        return html.toLowerCase(Locale.ROOT).indexOf(token.toLowerCase(Locale.ROOT));
    }

    private static String jsonString(String value) {
        String text = value == null ? "" : value;
        StringBuilder builder = new StringBuilder(text.length() + 2);
        builder.append('"');
        for (int index = 0; index < text.length(); index++) {
            char ch = text.charAt(index);
            switch (ch) {
                case '\\':
                case '"':
                    builder.append('\\').append(ch);
                    break;
                case '\n':
                    builder.append('\\').append('n');
                    break;
                case '\r':
                    builder.append('\\').append('r');
                    break;
                default:
                    builder.append(ch);
            }
        }
        builder.append('"');
        return builder.toString();
    }
}
