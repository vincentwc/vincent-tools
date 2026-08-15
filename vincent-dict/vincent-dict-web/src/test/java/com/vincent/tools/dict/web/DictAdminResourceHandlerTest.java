package com.vincent.tools.dict.web;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mock.web.MockServletContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class DictAdminResourceHandlerTest {
    private AnnotationConfigWebApplicationContext context;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        context = new AnnotationConfigWebApplicationContext();
        context.setServletContext(new MockServletContext());
        context.register(ResourceConfig.class);
        context.refresh();
        mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    @AfterEach
    void tearDown() {
        context.close();
    }

    @Test
    void hashed_css_and_js_are_publicly_cached_for_one_year() throws Exception {
        mockMvc.perform(get("/dict-admin/assets/app-D7nK8xYz.js"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "max-age=31536000, public"));
        mockMvc.perform(get("/dict-admin/assets/app-D7nK8xYz.css"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "max-age=31536000, public"));
    }

    @Test
    void index_html_and_spa_fallback_are_not_stored() throws Exception {
        mockMvc.perform(get("/dict-admin/index.html"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Vincent Dict Admin")))
                .andExpect(content().string(containsString("<base href=\"/dict-admin/\">")))
                .andExpect(content().string(containsString("window.__VIN_DICT_CONFIG__")))
                .andExpect(content().string(containsString("\"apiPath\":\"/vincent/dict/admin/api/v1\"")))
                .andExpect(content().string(containsString("\"historyBase\":\"/dict-admin\"")))
                .andExpect(header().string("Cache-Control", "no-store"));
        mockMvc.perform(get("/dict-admin/dicts/10"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Vincent Dict Admin")))
                .andExpect(content().string(containsString("window.__VIN_DICT_CONFIG__")))
                .andExpect(header().string("Cache-Control", "no-store"));
    }

    @Configuration
    @EnableWebMvc
    static class ResourceConfig {
        @Bean
        WebMvcConfigurer dictAdminResources() {
            return new DictAdminResourceHandler("/dict-admin");
        }
    }
}
