/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package com.alibaba.himarket.support.database;

import com.alibaba.himarket.utils.JsonUtil;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import java.nio.charset.StandardCharsets;

/**
 * JSON functions registered as H2 aliases for MySQL-compatible local queries.
 */
public final class H2JsonFunctions {

    private static final String ROOT_PATH_PREFIX = "$.";

    private H2JsonFunctions() {}

    /**
     * Extracts a scalar value from a JSON object using a simple dot-separated object path.
     *
     * @param jsonBytes JSON value supplied by H2
     * @param path path such as {@code $.skillConfig.downloadCount}
     * @return scalar text, or {@code null} when the path does not exist or resolves to JSON null
     */
    public static String jsonExtract(byte[] jsonBytes, String path) {
        if (jsonBytes == null || path == null || !path.startsWith(ROOT_PATH_PREFIX)) {
            return null;
        }

        try {
            JsonNode node =
                    JsonUtil.DEFAULT_JSON_MAPPER.readTree(
                            new String(jsonBytes, StandardCharsets.UTF_8));
            if (node != null && node.isTextual()) {
                node = JsonUtil.DEFAULT_JSON_MAPPER.readTree(node.textValue());
            }

            for (String field : path.substring(ROOT_PATH_PREFIX.length()).split("\\.")) {
                if (node == null || !node.isObject()) {
                    return null;
                }
                node = node.get(field);
            }
            return node == null || node.isNull() ? null : node.asText();
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Invalid JSON value", e);
        }
    }
}
