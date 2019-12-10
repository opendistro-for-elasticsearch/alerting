package com.amazon.elasticsearch.ad.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Jacoco will ignore the annotated code.
 * Similar to Lombok Generated annotation. Create this similar annotation as we don't involve Lombok.
 */
@Target({ElementType.CONSTRUCTOR, ElementType.METHOD, ElementType.FIELD, ElementType.TYPE})
@Retention(RetentionPolicy.CLASS)
public @interface Generated {
}
