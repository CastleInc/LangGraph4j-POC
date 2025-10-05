package com.datanova.langgraph.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

/**
 * Configuration for Thymeleaf to support Markdown template files.
 *
 * This configuration allows Thymeleaf to process .md files in addition to .html files,
 * enabling Markdown-formatted output from templates.
 *
 * @author DataNova
 * @version 1.0
 */
@Configuration
public class ThymeleafConfig {

    /**
     * Template resolver for Markdown files.
     * Processes .md files from the templates directory.
     */
    @Bean
    public ClassLoaderTemplateResolver markdownTemplateResolver() {
        ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("templates/");
        resolver.setSuffix(".md");
        resolver.setTemplateMode(TemplateMode.TEXT); // TEXT mode for Markdown
        resolver.setCharacterEncoding("UTF-8");
        resolver.setOrder(1); // Lower order = higher priority
        resolver.setCheckExistence(true);
        return resolver;
    }

    /**
     * Template resolver for HTML files.
     * Processes .html files from the templates directory.
     */
    @Bean
    public ClassLoaderTemplateResolver htmlTemplateResolver() {
        ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("templates/");
        resolver.setSuffix(".html");
        resolver.setTemplateMode(TemplateMode.HTML);
        resolver.setCharacterEncoding("UTF-8");
        resolver.setOrder(2); // Higher order = lower priority (fallback to HTML)
        resolver.setCheckExistence(true);
        return resolver;
    }
}

