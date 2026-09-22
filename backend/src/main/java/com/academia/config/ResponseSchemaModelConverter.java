package com.academia.config;

import com.fasterxml.jackson.databind.JavaType;
import io.swagger.v3.core.converter.AnnotatedType;
import io.swagger.v3.core.converter.ModelConverter;
import io.swagger.v3.core.converter.ModelConverterContext;
import io.swagger.v3.oas.models.media.Schema;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.RecordComponent;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * Toda componente de un record de respuesta es {@code required}: Jackson serializa siempre
 * todas las componentes de un record, también las nulas (regla no negociable nº4, sin
 * {@code @JsonInclude(NON_NULL)}), así que la pregunta real es solo cuáles pueden ser
 * {@code null}. Esas se marcan en el propio DTO con {@link Nullable} de JSpecify y salen aquí
 * como {@code type: [X, "null"]} (o, si la propiedad es un {@code $ref}, envuelta en
 * {@code anyOf}: en OpenAPI 3.1 un {@code $ref} no admite palabras clave hermanas
 * contradictorias con el {@code type} del esquema referenciado).
 *
 * No toca los {@code *Request}: swagger-core ya les emite {@code required} a partir de
 * {@code @NotNull}/{@code @NotBlank}, y un campo opcional de una petición (PATCH) debe seguir
 * siendo opcional.
 *
 * Un sitio, no una anotación {@code @Schema(requiredMode = REQUIRED)} campo a campo: con esa
 * alternativa, un campo nuevo nacería opcional por olvido.
 *
 * Para un tipo con nombre (todo record salvo los anónimos), {@code chain.next()} no devuelve
 * el esquema con sus propiedades, sino un {@code $ref} a él; el esquema real, con
 * {@code properties}, vive en {@code context.getDefinedModels()} bajo el nombre del
 * {@code $ref} (que puede venir de {@code @Schema(name = ...)}, no siempre coincide con el
 * nombre simple de la clase Java). Por eso esta clase resuelve el nombre del modelo a partir
 * del propio {@code $ref} en vez de asumir el nombre de la clase.
 */
class ResponseSchemaModelConverter implements ModelConverter {

    @Override
    public @Nullable Schema<?> resolve(AnnotatedType type, ModelConverterContext context,
            Iterator<ModelConverter> chain) {
        Schema<?> resolved = chain.hasNext() ? chain.next().resolve(type, context, chain) : null;
        if (resolved == null) {
            return null;
        }

        Class<?> rawClass = rawClass(type.getType());
        if (rawClass == null || !rawClass.isRecord() || rawClass.getSimpleName().endsWith("Request")) {
            return resolved;
        }

        Schema<?> target = actualSchema(resolved, context);
        if (target == null || target.getProperties() == null) {
            return resolved;
        }

        Map<String, RecordComponent> componentsByName = new HashMap<>();
        for (RecordComponent component : rawClass.getRecordComponents()) {
            componentsByName.put(component.getName(), component);
        }

        List<String> required = new ArrayList<>();
        for (Map.Entry<String, Schema> entry : target.getProperties().entrySet()) {
            String propertyName = entry.getKey();
            required.add(propertyName);

            RecordComponent component = componentsByName.get(propertyName);
            if (component != null && component.getAnnotatedType().isAnnotationPresent(Nullable.class)) {
                target.getProperties().put(propertyName, markNullable(entry.getValue()));
            }
        }
        target.setRequired(required);

        return resolved;
    }

    /**
     * {@code chain.next()} puede devolver, para el mismo tipo, un {@code $ref} (el caso
     * habitual) o, en algún punto de la resolución del grafo, el propio esquema con sus
     * propiedades ya presentes. Se cubren ambos casos en vez de asumir siempre uno.
     */
    private @Nullable Schema<?> actualSchema(Schema<?> resolved, ModelConverterContext context) {
        if (resolved.getProperties() != null) {
            return resolved;
        }
        String ref = resolved.get$ref();
        if (ref == null) {
            return null;
        }
        String modelName = ref.substring(ref.lastIndexOf('/') + 1);
        return context.getDefinedModels().get(modelName);
    }

    private Schema<?> markNullable(Schema<?> propertySchema) {
        if (propertySchema.get$ref() != null) {
            return new Schema<>().anyOf(List.of(propertySchema, new Schema<>().types(Set.of("null"))));
        }
        if (propertySchema.getAnyOf() != null) {
            // Ya envuelto en una llamada anterior (este converter se invoca varias veces para
            // el mismo tipo, una por cada sitio de la API donde aparece): idempotente.
            return propertySchema;
        }

        Set<String> types = new LinkedHashSet<>();
        if (propertySchema.getTypes() != null) {
            types.addAll(propertySchema.getTypes());
        } else if (propertySchema.getType() != null) {
            types.add(propertySchema.getType());
        }
        types.add("null");
        propertySchema.setType(null);
        propertySchema.setTypes(types);
        return propertySchema;
    }

    private @Nullable Class<?> rawClass(Type type) {
        if (type instanceof Class<?> clazz) {
            return clazz;
        }
        if (type instanceof ParameterizedType parameterized) {
            return rawClass(parameterized.getRawType());
        }
        if (type instanceof JavaType javaType) {
            return javaType.getRawClass();
        }
        return null;
    }
}
