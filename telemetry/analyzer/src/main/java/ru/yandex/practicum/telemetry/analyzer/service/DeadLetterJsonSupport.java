package ru.yandex.practicum.telemetry.analyzer.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

final class DeadLetterJsonSupport {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private DeadLetterJsonSupport() {
    }

    static String writeValueAsString(Object value) throws JsonProcessingException {
        return OBJECT_MAPPER.writeValueAsString(value);
    }
}
