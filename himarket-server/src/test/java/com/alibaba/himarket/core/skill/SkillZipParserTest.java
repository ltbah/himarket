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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.alibaba.himarket.core.exception.BusinessException;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;

class SkillZipParserTest {

    @Test
    void parseSkillNameFromSkillMdFrontMatter() throws Exception {
        byte[] zipBytes =
                zip("demo-skill/SKILL.md", "---\nname: demo-skill\ndescription: Demo\n---\n\nbody");

        assertEquals("demo-skill", SkillZipParser.parseSkillName(zipBytes));
    }

    @Test
    void rejectPackageWithoutSkillMdName() throws Exception {
        byte[] zipBytes = zip("demo-skill/manifest.json", "{\"name\":\"demo-skill\"}");

        BusinessException exception =
                assertThrows(
                        BusinessException.class, () -> SkillZipParser.parseSkillName(zipBytes));

        assertEquals("INVALID_PARAMETER", exception.getCode());
    }

    private byte[] zip(String entryName, String content) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            zos.putNextEntry(new ZipEntry(entryName));
            zos.write(content.getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
        }
        return baos.toByteArray();
    }
}
