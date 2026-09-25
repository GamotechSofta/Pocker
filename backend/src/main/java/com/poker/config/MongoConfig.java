package com.poker.config;

import com.poker.entity.BaseDocument;
import org.bson.UuidRepresentation;
import org.springframework.boot.mongodb.autoconfigure.MongoClientSettingsBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.config.EnableMongoAuditing;
import org.springframework.data.mongodb.core.mapping.event.BeforeConvertCallback;

@Configuration
@EnableMongoAuditing
public class MongoConfig {

    @Bean
    MongoClientSettingsBuilderCustomizer uuidRepresentation() {
        return builder -> builder.uuidRepresentation(UuidRepresentation.STANDARD);
    }

    @Bean
    BeforeConvertCallback<BaseDocument> assignIds() {
        return (entity, collection) -> {
            entity.ensureId();
            return entity;
        };
    }
}
