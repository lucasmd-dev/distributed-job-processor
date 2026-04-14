package com.lucas.jobprocessor.domain.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.lucas.jobprocessor.api.exception.InvalidJobRequestException;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

@Service
public class JsonPayloadService {

    private final ObjectMapper objectMapper;

    public JsonPayloadService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String canonicalize(String payload) {
        try {
            JsonNode tree = objectMapper.readTree(payload);
            JsonNode normalized = normalize(tree);
            return objectMapper.writeValueAsString(normalized);
        } catch (JsonProcessingException ex) {
            throw new InvalidJobRequestException("Payload must be valid JSON");
        }
    }

    private JsonNode normalize(JsonNode node) {
        if (node == null || node.isNull() || node.isValueNode()) {
            return node;
        }

        if (node.isArray()) {
            ArrayNode normalized = objectMapper.createArrayNode();
            for (JsonNode item : node) {
                normalized.add(normalize(item));
            }
            return normalized;
        }

        ObjectNode normalized = objectMapper.createObjectNode();
        List<String> fieldNames = new ArrayList<>();
        Iterator<String> iterator = node.fieldNames();
        while (iterator.hasNext()) {
            fieldNames.add(iterator.next());
        }
        Collections.sort(fieldNames);

        for (String fieldName : fieldNames) {
            normalized.set(fieldName, normalize(node.get(fieldName)));
        }

        return normalized;
    }
}
