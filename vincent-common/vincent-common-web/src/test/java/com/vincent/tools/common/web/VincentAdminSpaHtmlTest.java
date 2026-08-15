package com.vincent.tools.common.web;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class VincentAdminSpaHtmlTest {
    private static final String CONFIG_GLOBAL_NAME = "__VIN_DICT_CONFIG__";

    @Test
    void injects_base_href_and_config_after_head_open() {
        String html = "<html><head><title>Admin</title></head><body></body></html>";

        String injected = VincentAdminSpaHtml.inject(
                html,
                CONFIG_GLOBAL_NAME,
                "/vincent/dict/admin/api/v1",
                "/dict-admin");

        assertThat(injected).startsWith("<html><head>");
        assertThat(injected).contains("<base href=\"/dict-admin/\">");
        assertThat(injected).contains("window.__VIN_DICT_CONFIG__={");
        assertThat(injected).contains("\"apiPath\":\"/vincent/dict/admin/api/v1\"");
        assertThat(injected).contains("\"historyBase\":\"/dict-admin\"");
        assertThat(injected).contains("<title>Admin</title>");
    }

    @Test
    void strips_trailing_slash_from_history_base() {
        String html = "<html><head></head><body></body></html>";

        String injected = VincentAdminSpaHtml.inject(
                html,
                CONFIG_GLOBAL_NAME,
                "/api/v1",
                "/dict-admin/");

        assertThat(injected).contains("<base href=\"/dict-admin/\">");
        assertThat(injected).contains("\"historyBase\":\"/dict-admin\"");
    }

    @Test
    void escapes_json_special_characters_in_api_path() {
        String html = "<html><head></head><body></body></html>";
        String apiPath = "/api/v1/tenants/\"acme\"\nline";

        String injected = VincentAdminSpaHtml.inject(
                html,
                CONFIG_GLOBAL_NAME,
                apiPath,
                "/dict-admin");

        assertThat(injected).contains("\"apiPath\":\"/api/v1/tenants/\\\"acme\\\"\\nline\"");
    }

    @Test
    void escapes_html_attribute_characters_in_base_href() {
        String html = "<html><head></head><body></body></html>";

        String injected = VincentAdminSpaHtml.inject(
                html,
                CONFIG_GLOBAL_NAME,
                "/api/v1",
                "/admin?tenant=\"a&b\"");

        assertThat(injected).contains("<base href=\"/admin?tenant=&quot;a&amp;b&quot;/\">");
    }

    @Test
    void injectBytes_reads_resource_and_injects() throws Exception {
        String html = "<html><head></head><body></body></html>";
        ByteArrayResource resource = new ByteArrayResource(html.getBytes(StandardCharsets.UTF_8));

        byte[] injected = VincentAdminSpaHtml.injectBytes(
                resource,
                CONFIG_GLOBAL_NAME,
                "/vincent/dict/admin/api/v1",
                "/dict-admin");

        String text = new String(injected, StandardCharsets.UTF_8);
        assertThat(text).contains("<base href=\"/dict-admin/\">");
        assertThat(text).contains("window.__VIN_DICT_CONFIG__");
    }
}
