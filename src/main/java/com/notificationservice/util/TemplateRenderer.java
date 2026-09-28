package com.notificationservice.util;

import com.notificationservice.exception.MissingTemplateVariableException;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Renders {{variable}} placeholders in a tenant-defined template string. Kept as a standalone,
 * dependency-free utility so it's trivially unit-testable without a Spring context.
 */
public final class TemplateRenderer {

    private static final Pattern VARIABLE_PATTERN = Pattern.compile("\\{\\{\\s*([a-zA-Z0-9_]+)\\s*}}");

    private TemplateRenderer() {
    }

    public static String render(String template, Map<String, String> variables) {
        if (template == null) {
            return null;
        }
        Map<String, String> safeVariables = variables == null ? Map.of() : variables;
        Matcher matcher = VARIABLE_PATTERN.matcher(template);
        StringBuilder result = new StringBuilder();
        while (matcher.find()) {
            String key = matcher.group(1);
            String value = safeVariables.get(key);
            if (value == null) {
                throw new MissingTemplateVariableException(key);
            }
            matcher.appendReplacement(result, Matcher.quoteReplacement(value));
        }
        matcher.appendTail(result);
        return result.toString();
    }
}
