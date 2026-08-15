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
        String normalizedHistoryBase = normalizeHistoryBase(historyBase);
        String injectedHead = "<base href=\"" + htmlAttribute(normalizeBaseHref(normalizedHistoryBase)) + "\">"
                + "<script>window.__VIN_DICT_CONFIG__={"
                + "\"apiPath\":" + jsonString(apiPath)
                + ",\"historyBase\":" + jsonString(normalizedHistoryBase)
                + "};</script>";
        int headOpen = indexOfIgnoreCase(html, "<head");
        if (headOpen >= 0) {
            int headTagEnd = html.indexOf('>', headOpen);
            if (headTagEnd >= 0) {
                int insertAt = headTagEnd + 1;
                return html.substring(0, insertAt) + injectedHead + html.substring(insertAt);
            }
        }
        int scriptTag = indexOfIgnoreCase(html, "<script");
        if (scriptTag >= 0) {
            return html.substring(0, scriptTag) + injectedHead + html.substring(scriptTag);
        }
        int headEnd = indexOfIgnoreCase(html, "</head>");
        if (headEnd >= 0) {
            return html.substring(0, headEnd) + injectedHead + html.substring(headEnd);
        }
        return injectedHead + html;
    }

    private static String normalizeHistoryBase(String historyBase) {
        String base = historyBase == null || historyBase.length() == 0 ? "/dict-admin" : historyBase;
        if (base.endsWith("/") && base.length() > 1) {
            return base.substring(0, base.length() - 1);
        }
        return base;
    }

    private static String normalizeBaseHref(String historyBase) {
        String base = normalizeHistoryBase(historyBase);
        return base.endsWith("/") ? base : base + "/";
    }

    private static String htmlAttribute(String value) {
        StringBuilder builder = new StringBuilder(value.length());
        for (int index = 0; index < value.length(); index++) {
            char ch = value.charAt(index);
            switch (ch) {
                case '&':
                    builder.append("&amp;");
                    break;
                case '"':
                    builder.append("&quot;");
                    break;
                default:
                    builder.append(ch);
            }
        }
        return builder.toString();
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
