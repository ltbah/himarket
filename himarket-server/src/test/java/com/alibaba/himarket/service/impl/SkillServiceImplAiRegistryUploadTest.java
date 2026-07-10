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

package com.alibaba.himarket.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.alibaba.himarket.core.exception.BusinessException;
import com.alibaba.himarket.core.security.ContextHolder;
import com.alibaba.himarket.entity.Product;
import com.alibaba.himarket.repository.ProductRepository;
import com.alibaba.himarket.service.AiRegistrySkillService;
import com.alibaba.himarket.service.NacosService;
import com.alibaba.himarket.support.enums.ProductType;
import com.alibaba.himarket.support.enums.SkillRegistryType;
import com.alibaba.himarket.support.product.ProductFeature;
import com.alibaba.himarket.support.product.SkillConfig;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;
import org.springframework.web.multipart.MultipartFile;

class SkillServiceImplAiRegistryUploadTest {

    @Test
    void aiRegistryFirstUploadWritesReturnedSkillNameBackToProductConfig() throws Exception {
        ProductRepository productRepository = mock(ProductRepository.class);
        AiRegistrySkillService aiRegistrySkillService = mock(AiRegistrySkillService.class);
        Product product =
                Product.builder()
                        .productId("product-a")
                        .type(ProductType.AGENT_SKILL)
                        .feature(
                                ProductFeature.builder()
                                        .skillConfig(
                                                SkillConfig.builder()
                                                        .registryType(SkillRegistryType.AIREGISTRY)
                                                        .aiRegistryId("airegistry-prod")
                                                        .namespace("ns-prod")
                                                        .build())
                                        .build())
                        .build();
        byte[] zipBytes = skillZip("skill-a");
        MultipartFile file = mock(MultipartFile.class);
        when(file.isEmpty()).thenReturn(false);
        when(file.getSize()).thenReturn((long) zipBytes.length);
        when(file.getOriginalFilename()).thenReturn("skill.zip");
        when(file.getBytes()).thenReturn(zipBytes);
        when(productRepository.findByProductId("product-a")).thenReturn(Optional.of(product));
        when(productRepository.findAllByType(ProductType.AGENT_SKILL)).thenReturn(List.of(product));
        when(aiRegistrySkillService.uploadFromZip(
                        "airegistry-prod", "ns-prod", zipBytes, "skill.zip", true))
                .thenReturn("skill-a");

        SkillServiceImpl service =
                new SkillServiceImpl(
                        mock(NacosService.class),
                        productRepository,
                        mock(ContextHolder.class),
                        aiRegistrySkillService);

        service.uploadPackage("product-a", file);

        assertEquals("skill-a", product.getFeature().getSkillConfig().getSkillName());
    }

    @Test
    void aiRegistryFirstUploadRejectsSkillNameAlreadyBoundByOtherProduct() throws Exception {
        ProductRepository productRepository = mock(ProductRepository.class);
        AiRegistrySkillService aiRegistrySkillService = mock(AiRegistrySkillService.class);
        Product product =
                aiRegistryProduct("product-a", "Search One", "airegistry-prod", "ns-prod", null);
        Product otherProduct =
                aiRegistryProduct(
                        "product-b", "Search Two", "airegistry-prod", "ns-prod", "web-search");
        byte[] zipBytes = skillZip("web-search");
        MultipartFile file = mock(MultipartFile.class);
        when(file.isEmpty()).thenReturn(false);
        when(file.getSize()).thenReturn((long) zipBytes.length);
        when(file.getOriginalFilename()).thenReturn("skill.zip");
        when(file.getBytes()).thenReturn(zipBytes);
        when(productRepository.findByProductId("product-a")).thenReturn(Optional.of(product));
        when(productRepository.findAllByType(ProductType.AGENT_SKILL))
                .thenReturn(List.of(product, otherProduct));

        SkillServiceImpl service =
                new SkillServiceImpl(
                        mock(NacosService.class),
                        productRepository,
                        mock(ContextHolder.class),
                        aiRegistrySkillService);

        BusinessException exception =
                assertThrows(
                        BusinessException.class, () -> service.uploadPackage("product-a", file));

        assertEquals("CONFLICT", exception.getCode());
        verify(aiRegistrySkillService, never())
                .uploadFromZip("airegistry-prod", "ns-prod", zipBytes, "skill.zip", true);
    }

    @Test
    void aiRegistrySubsequentUploadRejectsDifferentSkillNameBeforeRemoteUpload() throws Exception {
        ProductRepository productRepository = mock(ProductRepository.class);
        AiRegistrySkillService aiRegistrySkillService = mock(AiRegistrySkillService.class);
        Product product =
                Product.builder()
                        .productId("product-a")
                        .name("web-search")
                        .feature(
                                ProductFeature.builder()
                                        .skillConfig(
                                                SkillConfig.builder()
                                                        .registryType(SkillRegistryType.AIREGISTRY)
                                                        .aiRegistryId("airegistry-prod")
                                                        .namespace("ns-prod")
                                                        .skillName("web-search")
                                                        .build())
                                        .build())
                        .build();
        byte[] zipBytes = skillZip("aone-authored-code-pr-tracker");
        MultipartFile file = mock(MultipartFile.class);
        when(file.isEmpty()).thenReturn(false);
        when(file.getSize()).thenReturn((long) zipBytes.length);
        when(file.getOriginalFilename()).thenReturn("skill.zip");
        when(file.getBytes()).thenReturn(zipBytes);
        when(productRepository.findByProductId("product-a")).thenReturn(Optional.of(product));

        SkillServiceImpl service =
                new SkillServiceImpl(
                        mock(NacosService.class),
                        productRepository,
                        mock(ContextHolder.class),
                        aiRegistrySkillService);

        BusinessException exception =
                assertThrows(
                        BusinessException.class, () -> service.uploadPackage("product-a", file));

        assertEquals("CONFLICT", exception.getCode());
        assertEquals("web-search", product.getFeature().getSkillConfig().getSkillName());
        verify(aiRegistrySkillService, never())
                .uploadFromZip("airegistry-prod", "ns-prod", zipBytes, "skill.zip", true);
    }

    @Test
    void aiRegistrySharedBindingRejectsSameSkillNameUploadBeforeRemoteUpload() throws Exception {
        ProductRepository productRepository = mock(ProductRepository.class);
        AiRegistrySkillService aiRegistrySkillService = mock(AiRegistrySkillService.class);
        Product product =
                aiRegistryProduct(
                        "product-a", "Search One", "airegistry-prod", "ns-prod", "shared-skill");
        Product otherProduct =
                aiRegistryProduct(
                        "product-b", "Search Two", "airegistry-prod", "ns-prod", "shared-skill");
        byte[] zipBytes = skillZip("shared-skill");
        MultipartFile file = mock(MultipartFile.class);
        when(file.isEmpty()).thenReturn(false);
        when(file.getSize()).thenReturn((long) zipBytes.length);
        when(file.getOriginalFilename()).thenReturn("skill.zip");
        when(file.getBytes()).thenReturn(zipBytes);
        when(productRepository.findByProductId("product-a")).thenReturn(Optional.of(product));
        when(productRepository.findAllByType(ProductType.AGENT_SKILL))
                .thenReturn(List.of(product, otherProduct));

        SkillServiceImpl service =
                new SkillServiceImpl(
                        mock(NacosService.class),
                        productRepository,
                        mock(ContextHolder.class),
                        aiRegistrySkillService);

        BusinessException exception =
                assertThrows(
                        BusinessException.class, () -> service.uploadPackage("product-a", file));

        assertEquals("CONFLICT", exception.getCode());
        verify(aiRegistrySkillService, never())
                .uploadFromZip("airegistry-prod", "ns-prod", zipBytes, "skill.zip", true);
    }

    @Test
    void aiRegistrySharedBindingCanMigrateToUnboundSkillName() throws Exception {
        ProductRepository productRepository = mock(ProductRepository.class);
        AiRegistrySkillService aiRegistrySkillService = mock(AiRegistrySkillService.class);
        Product product =
                aiRegistryProduct(
                        "product-a", "Search One", "airegistry-prod", "ns-prod", "shared-skill");
        Product otherProduct =
                aiRegistryProduct(
                        "product-b", "Search Two", "airegistry-prod", "ns-prod", "shared-skill");
        byte[] zipBytes = skillZip("search-one");
        MultipartFile file = mock(MultipartFile.class);
        when(file.isEmpty()).thenReturn(false);
        when(file.getSize()).thenReturn((long) zipBytes.length);
        when(file.getOriginalFilename()).thenReturn("skill.zip");
        when(file.getBytes()).thenReturn(zipBytes);
        when(productRepository.findByProductId("product-a")).thenReturn(Optional.of(product));
        when(productRepository.findAllByType(ProductType.AGENT_SKILL))
                .thenReturn(List.of(product, otherProduct));
        when(aiRegistrySkillService.uploadFromZip(
                        "airegistry-prod", "ns-prod", zipBytes, "skill.zip", true))
                .thenReturn("search-one");

        SkillServiceImpl service =
                new SkillServiceImpl(
                        mock(NacosService.class),
                        productRepository,
                        mock(ContextHolder.class),
                        aiRegistrySkillService);

        service.uploadPackage("product-a", file);

        assertEquals("search-one", product.getFeature().getSkillConfig().getSkillName());
        assertEquals("shared-skill", otherProduct.getFeature().getSkillConfig().getSkillName());
        verify(aiRegistrySkillService)
                .uploadFromZip("airegistry-prod", "ns-prod", zipBytes, "skill.zip", true);
        verify(productRepository).save(product);
    }

    @Test
    void aiRegistrySubsequentUploadAllowsSameSkillName() throws Exception {
        ProductRepository productRepository = mock(ProductRepository.class);
        AiRegistrySkillService aiRegistrySkillService = mock(AiRegistrySkillService.class);
        Product product =
                aiRegistryProduct(
                        "product-a", "Web Search", "airegistry-prod", "ns-prod", "web-search");
        byte[] zipBytes = skillZip("web-search");
        MultipartFile file = mock(MultipartFile.class);
        when(file.isEmpty()).thenReturn(false);
        when(file.getSize()).thenReturn((long) zipBytes.length);
        when(file.getOriginalFilename()).thenReturn("skill.zip");
        when(file.getBytes()).thenReturn(zipBytes);
        when(productRepository.findByProductId("product-a")).thenReturn(Optional.of(product));
        when(aiRegistrySkillService.uploadFromZip(
                        "airegistry-prod", "ns-prod", zipBytes, "skill.zip", true))
                .thenReturn("web-search");

        SkillServiceImpl service =
                new SkillServiceImpl(
                        mock(NacosService.class),
                        productRepository,
                        mock(ContextHolder.class),
                        aiRegistrySkillService);

        service.uploadPackage("product-a", file);

        assertEquals("web-search", product.getFeature().getSkillConfig().getSkillName());
        verify(aiRegistrySkillService)
                .uploadFromZip("airegistry-prod", "ns-prod", zipBytes, "skill.zip", true);
    }

    @Test
    void aiRegistryDeleteKeepsSharedRemoteSkillWhenOtherProductStillReferencesIt() {
        ProductRepository productRepository = mock(ProductRepository.class);
        AiRegistrySkillService aiRegistrySkillService = mock(AiRegistrySkillService.class);
        Product product =
                aiRegistryProduct(
                        "product-a", "Search One", "airegistry-prod", "ns-prod", "shared-skill");
        Product otherProduct =
                aiRegistryProduct(
                        "product-b", "Search Two", "airegistry-prod", "ns-prod", "shared-skill");
        when(productRepository.findByProductId("product-a")).thenReturn(Optional.of(product));
        when(productRepository.findAllByType(ProductType.AGENT_SKILL))
                .thenReturn(List.of(product, otherProduct));

        SkillServiceImpl service =
                new SkillServiceImpl(
                        mock(NacosService.class),
                        productRepository,
                        mock(ContextHolder.class),
                        aiRegistrySkillService);

        service.deleteSkill("product-a");

        verify(aiRegistrySkillService, never())
                .deleteSkill("airegistry-prod", "ns-prod", "shared-skill");
        assertNull(product.getFeature().getSkillConfig().getSkillName());
        assertEquals("shared-skill", otherProduct.getFeature().getSkillConfig().getSkillName());
        verify(productRepository).save(product);
    }

    @Test
    void aiRegistryDeleteRemovesRemoteSkillWhenNoOtherProductReferencesIt() {
        ProductRepository productRepository = mock(ProductRepository.class);
        AiRegistrySkillService aiRegistrySkillService = mock(AiRegistrySkillService.class);
        Product product =
                aiRegistryProduct(
                        "product-a", "Search One", "airegistry-prod", "ns-prod", "unique-skill");
        Product otherProduct =
                aiRegistryProduct(
                        "product-b", "Search Two", "airegistry-prod", "ns-prod", "other-skill");
        when(productRepository.findByProductId("product-a")).thenReturn(Optional.of(product));
        when(productRepository.findAllByType(ProductType.AGENT_SKILL))
                .thenReturn(List.of(product, otherProduct));

        SkillServiceImpl service =
                new SkillServiceImpl(
                        mock(NacosService.class),
                        productRepository,
                        mock(ContextHolder.class),
                        aiRegistrySkillService);

        service.deleteSkill("product-a");

        verify(aiRegistrySkillService).deleteSkill("airegistry-prod", "ns-prod", "unique-skill");
        assertNull(product.getFeature().getSkillConfig().getSkillName());
        verify(productRepository).save(product);
    }

    @Test
    void uploadFailsWithBusinessExceptionWhenSkillRegistryIsNotConfigured() throws Exception {
        ProductRepository productRepository = mock(ProductRepository.class);
        Product product = Product.builder().productId("product-a").build();
        byte[] zipBytes = new byte[] {1, 2, 3};
        MultipartFile file = mock(MultipartFile.class);
        when(file.isEmpty()).thenReturn(false);
        when(file.getSize()).thenReturn((long) zipBytes.length);
        when(file.getBytes()).thenReturn(zipBytes);
        when(productRepository.findByProductId("product-a")).thenReturn(Optional.of(product));

        SkillServiceImpl service =
                new SkillServiceImpl(
                        mock(NacosService.class),
                        productRepository,
                        mock(ContextHolder.class),
                        mock(AiRegistrySkillService.class));

        BusinessException exception =
                assertThrows(
                        BusinessException.class, () -> service.uploadPackage("product-a", file));

        assertEquals("INVALID_REQUEST", exception.getCode());
    }

    private Product aiRegistryProduct(
            String productId,
            String name,
            String aiRegistryId,
            String namespace,
            String skillName) {
        return Product.builder()
                .productId(productId)
                .name(name)
                .type(ProductType.AGENT_SKILL)
                .feature(
                        ProductFeature.builder()
                                .skillConfig(
                                        SkillConfig.builder()
                                                .registryType(SkillRegistryType.AIREGISTRY)
                                                .aiRegistryId(aiRegistryId)
                                                .namespace(namespace)
                                                .skillName(skillName)
                                                .build())
                                .build())
                .build();
    }

    private byte[] skillZip(String skillName) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            zos.putNextEntry(new ZipEntry(skillName + "/SKILL.md"));
            zos.write(
                    ("---\nname: "
                                    + skillName
                                    + "\ndescription: Test skill\n---\n\nTest instructions\n")
                            .getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
        }
        return baos.toByteArray();
    }
}
