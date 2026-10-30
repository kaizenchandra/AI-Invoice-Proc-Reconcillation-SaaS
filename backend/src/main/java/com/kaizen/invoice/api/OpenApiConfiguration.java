package com.kaizen.invoice.api;

import io.swagger.v3.oas.models.media.StringSchema;
import java.math.BigDecimal;
import org.springdoc.core.customizers.PropertyCustomizer;
import org.springframework.context.annotation.*;

@Configuration
public class OpenApiConfiguration {
  @Bean
  PropertyCustomizer decimalContract() {
    return (property, type) -> {
      if (type.getType() == BigDecimal.class
          || (type.getType() instanceof com.fasterxml.jackson.databind.JavaType javaType
              && javaType.hasRawClass(BigDecimal.class)))
        return new StringSchema()
            .pattern("^-?[0-9]+(\\.[0-9]+)?$")
            .description("Exact decimal string; never a binary floating-point monetary value.")
            .example("590.00");
      return property;
    };
  }
}
