package com.alels.gateway.service;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import com.alels.gateway.model.DictionaryAvlInfo;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public class DictionaryParser {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private DictionaryParser() {
    }

    public static List<DictionaryAvlInfo> parse(String dictionaryFile) {

        List<DictionaryAvlInfo> result = new ArrayList<>();

        try (
                InputStream is = DictionaryParser.class
                        .getClassLoader()
                        .getResourceAsStream("device-dictionary/" + dictionaryFile)
        ) {
            if (is == null) {
                System.err.println("[DICT PARSER] file not found " + dictionaryFile);
                return result;
            }

            JsonNode root = MAPPER.readTree(is);
            JsonNode avlNode = root.get("avl");

            if (avlNode == null || !avlNode.isObject()) {
                System.err.println("[DICT PARSER] avl node not found " + dictionaryFile);
                return result;
            }

            Iterator<String> fieldNames = avlNode.fieldNames();

            while (fieldNames.hasNext()) {

                String avlId = fieldNames.next();
                JsonNode node = avlNode.get(avlId);

                DictionaryAvlInfo info = new DictionaryAvlInfo();

                info.setAvlId(avlId);
                info.setName(node.path("name").asText(null));
                info.setUnit(node.path("unit").asText(null));
                info.setValueType(node.path("type").asText(null));
                info.setMultiplier(node.path("multiplier").isNumber()
                        ? node.path("multiplier").asDouble()
                        : 1.0);
                info.setCategory(node.path("category").asText(null));

                result.add(info);
            }

            System.out.println("[DICT PARSER] file=" + dictionaryFile + " avl=" + result.size());

        } catch (Exception e) {
            System.err.println("[DICT PARSER ERROR] " + dictionaryFile + " " + e.getMessage());
        }

        return result;
    }
}