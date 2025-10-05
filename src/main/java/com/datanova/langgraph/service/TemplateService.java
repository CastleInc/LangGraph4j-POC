package com.datanova.langgraph.service;

import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.util.List;
import java.util.Map;

/**
 * Service for rendering AIT tech stack data using Thymeleaf templates.
 *
 * @author DataNova
 * @version 1.0
 */
@Service
public class TemplateService {

    private final TemplateEngine templateEngine;

    public TemplateService(TemplateEngine templateEngine) {
        this.templateEngine = templateEngine;
    }

    /**
     * Render a list of AITs (already mapped to simple Maps) into a Markdown/HTML table,
     * optionally annotated with the component/query text used to gather them.
     * Expects a Thymeleaf template named "techstack_aits" on the classpath.
     *
     * @param list List of AIT records as Maps
     * @param queryOrComponent The component or query text used to find these AITs
     * @return Rendered template string
     */
    public String renderAitTechStackFromMaps(List<Map<String, Object>> list, String queryOrComponent) {
        Context ctx = new Context();
        ctx.setVariable("list", list);
        ctx.setVariable("component", queryOrComponent);
        return templateEngine.process("techstack_aits", ctx);
    }
}
