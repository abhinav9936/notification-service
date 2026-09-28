package com.notificationservice.util;

import com.notificationservice.exception.MissingTemplateVariableException;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TemplateRendererTest {

    @Test
    void substitutesAllPlaceholders() {
        String result = TemplateRenderer.render(
                "Hi {{firstName}}, your order {{orderId}} has shipped.",
                Map.of("firstName", "Ada", "orderId", "A123")
        );

        assertThat(result).isEqualTo("Hi Ada, your order A123 has shipped.");
    }

    @Test
    void toleratesWhitespaceInsidePlaceholders() {
        String result = TemplateRenderer.render("Hello {{ name }}!", Map.of("name", "Grace"));

        assertThat(result).isEqualTo("Hello Grace!");
    }

    @Test
    void returnsTemplateUnchangedWhenNoPlaceholders() {
        String result = TemplateRenderer.render("No variables here.", Map.of());

        assertThat(result).isEqualTo("No variables here.");
    }

    @Test
    void throwsWhenAVariableIsMissing() {
        assertThatThrownBy(() -> TemplateRenderer.render("Hi {{firstName}}", Map.of()))
                .isInstanceOf(MissingTemplateVariableException.class)
                .hasMessageContaining("firstName");
    }

    @Test
    void returnsNullWhenTemplateIsNull() {
        assertThat(TemplateRenderer.render(null, Map.of())).isNull();
    }

    @Test
    void treatsNullVariableMapAsEmpty() {
        assertThatThrownBy(() -> TemplateRenderer.render("Hi {{name}}", null))
                .isInstanceOf(MissingTemplateVariableException.class);
    }
}
