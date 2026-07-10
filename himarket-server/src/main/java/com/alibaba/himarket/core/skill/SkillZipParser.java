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

package com.alibaba.himarket.core.skill;

import com.alibaba.himarket.core.exception.BusinessException;
import com.alibaba.himarket.core.exception.ErrorCode;
import com.alibaba.himarket.support.common.Strings;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Objects;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.yaml.snakeyaml.Yaml;

public final class SkillZipParser {

    private static final String SKILL_MD = "SKILL.md";

    private SkillZipParser() {}

    public static String parseSkillName(byte[] zipBytes) {
        if (zipBytes == null || zipBytes.length == 0) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "ZIP file is empty");
        }
        String skillMd = null;
        try (ZipInputStream zis =
                new ZipInputStream(new ByteArrayInputStream(zipBytes), StandardCharsets.UTF_8)) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                String entryName = entry.getName();
                if (shouldSkip(entry, entryName)) {
                    continue;
                }
                String baseName = baseName(entryName);
                if (SKILL_MD.equals(baseName) && skillMd == null) {
                    skillMd = new String(zis.readAllBytes(), StandardCharsets.UTF_8);
                }
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(
                    ErrorCode.INVALID_PARAMETER, "ZIP parse failed: " + e.getMessage());
        }

        try {
            String skillName = parseSkillMdName(skillMd);
            if (Strings.isNotBlank(skillName)) {
                return skillName;
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(
                    ErrorCode.INVALID_PARAMETER, "ZIP parse failed: " + e.getMessage());
        }
        throw new BusinessException(
                ErrorCode.INVALID_PARAMETER, "Invalid Skill package: skill name is required");
    }

    private static boolean shouldSkip(ZipEntry entry, String name) {
        return entry.isDirectory()
                || name.startsWith("__MACOSX/")
                || name.endsWith(".DS_Store")
                || baseName(name).startsWith("._");
    }

    private static String baseName(String path) {
        int slash = path.lastIndexOf('/');
        return slash >= 0 ? path.substring(slash + 1) : path;
    }

    private static String parseSkillMdName(String content) {
        if (Strings.isBlank(content)) {
            return null;
        }
        String normalized = content.replace("\r\n", "\n").replace('\r', '\n');
        String[] lines = normalized.split("\n", -1);
        int start = firstNonBlankLine(lines);
        if (start < 0 || !"---".equals(lines[start].trim())) {
            return null;
        }
        StringBuilder frontMatter = new StringBuilder();
        for (int i = start + 1; i < lines.length; i++) {
            if ("---".equals(lines[i].trim())) {
                return parseYamlName(frontMatter.toString());
            }
            frontMatter.append(lines[i]).append('\n');
        }
        return null;
    }

    private static int firstNonBlankLine(String[] lines) {
        for (int i = 0; i < lines.length; i++) {
            if (Strings.isNotBlank(lines[i])) {
                return i;
            }
        }
        return -1;
    }

    private static String parseYamlName(String content) {
        Object parsed = new Yaml().load(content);
        if (!(parsed instanceof Map<?, ?> map)) {
            return null;
        }
        return Objects.toString(map.get("name"), null);
    }
}
