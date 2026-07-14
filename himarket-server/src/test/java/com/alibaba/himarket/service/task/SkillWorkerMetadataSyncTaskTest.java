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

package com.alibaba.himarket.service.task;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.alibaba.himarket.dto.result.airegistry.AiRegistrySkillResult;
import com.alibaba.himarket.entity.Product;
import com.alibaba.himarket.repository.ProductRepository;
import com.alibaba.himarket.service.AiRegistrySkillService;
import com.alibaba.himarket.service.NacosService;
import com.alibaba.himarket.support.enums.ProductStatus;
import com.alibaba.himarket.support.enums.ProductType;
import com.alibaba.himarket.support.enums.SkillRegistryType;
import com.alibaba.himarket.support.product.ProductFeature;
import com.alibaba.himarket.support.product.SkillConfig;
import com.alibaba.himarket.support.product.WorkerConfig;
import com.alibaba.nacos.api.ai.model.agentspecs.AgentSpecSummary;
import com.alibaba.nacos.api.ai.model.skills.SkillSummary;
import com.alibaba.nacos.api.model.Page;
import com.alibaba.nacos.maintainer.client.ai.AgentSpecMaintainerService;
import com.alibaba.nacos.maintainer.client.ai.AiMaintainerService;
import com.alibaba.nacos.maintainer.client.ai.SkillMaintainerService;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SkillWorkerMetadataSyncTaskTest {

    @Test
    void aiRegistrySkillMetadataIsSyncedTogether() {
        ProductRepository productRepository = mock(ProductRepository.class);
        NacosService nacosService = mock(NacosService.class);
        AiRegistrySkillService aiRegistrySkillService = mock(AiRegistrySkillService.class);
        SkillConfig skillConfig = skillConfig(SkillRegistryType.AIREGISTRY, "1.0.0", 1L);
        skillConfig.setAiRegistryId("airegistry-1");
        Product product = skillProduct(skillConfig);
        AiRegistrySkillResult metadata =
                AiRegistrySkillResult.builder()
                        .name("weather-skill")
                        .downloadCount(12L)
                        .latestVersion("2.0.0")
                        .build();

        when(productRepository.findAllByType(ProductType.AGENT_SKILL)).thenReturn(List.of(product));
        when(productRepository.findAllByType(ProductType.WORKER)).thenReturn(List.of());
        when(aiRegistrySkillService.listSkillMetadata("airegistry-1", "ns-prod"))
                .thenReturn(Map.of("weather-skill", metadata));

        sync(productRepository, nacosService, aiRegistrySkillService);

        assertEquals(12L, skillConfig.getDownloadCount());
        assertEquals("2.0.0", skillConfig.getLatestVersion());
        assertEquals(ProductStatus.PENDING, product.getStatus());
        verify(productRepository).saveAll(List.of(product));
        verify(nacosService, never()).getAiMaintainerService("airegistry-1");
    }

    @Test
    void nacosSkillMetadataIsSyncedFromOneSummaryRequest() throws Exception {
        ProductRepository productRepository = mock(ProductRepository.class);
        NacosService nacosService = mock(NacosService.class);
        AiRegistrySkillService aiRegistrySkillService = mock(AiRegistrySkillService.class);
        AiMaintainerService aiMaintainerService = mock(AiMaintainerService.class);
        SkillMaintainerService skillMaintainerService = mock(SkillMaintainerService.class);
        SkillConfig skillConfig = skillConfig(SkillRegistryType.NACOS, "1.0.0", 1L);
        skillConfig.setNacosId("nacos-1");
        Product product = skillProduct(skillConfig);
        SkillConfig calendarConfig = skillConfig(SkillRegistryType.NACOS, "1.0.0", 2L);
        calendarConfig.setNacosId("nacos-1");
        calendarConfig.setSkillName("calendar-skill");
        Product calendarProduct = skillProduct(calendarConfig);
        calendarProduct.setProductId("product-2");
        SkillSummary summary = new SkillSummary();
        summary.setName("weather-skill");
        summary.setDownloadCount(8L);
        summary.setLabels(Map.of("latest", "1.1.0"));
        SkillSummary calendarSummary = new SkillSummary();
        calendarSummary.setName("calendar-skill");
        calendarSummary.setDownloadCount(9L);
        calendarSummary.setLabels(Map.of("latest", "1.2.0"));
        Page<SkillSummary> page = new Page<>();
        page.setPageItems(List.of(summary, calendarSummary));

        when(productRepository.findAllByType(ProductType.AGENT_SKILL))
                .thenReturn(List.of(product, calendarProduct));
        when(productRepository.findAllByType(ProductType.WORKER)).thenReturn(List.of());
        when(nacosService.getAiMaintainerService("nacos-1")).thenReturn(aiMaintainerService);
        when(aiMaintainerService.skill()).thenReturn(skillMaintainerService);
        when(skillMaintainerService.listSkills("ns-prod", null, null, 1, Integer.MAX_VALUE))
                .thenReturn(page);

        sync(productRepository, nacosService, aiRegistrySkillService);

        assertEquals(8L, skillConfig.getDownloadCount());
        assertEquals("1.1.0", skillConfig.getLatestVersion());
        assertEquals(9L, calendarConfig.getDownloadCount());
        assertEquals("1.2.0", calendarConfig.getLatestVersion());
        assertEquals(ProductStatus.PENDING, product.getStatus());
        verify(skillMaintainerService, times(1))
                .listSkills("ns-prod", null, null, 1, Integer.MAX_VALUE);
        verify(productRepository).saveAll(List.of(product, calendarProduct));
    }

    @Test
    void unavailableRegistryDoesNotBlockAnotherRegistry() {
        ProductRepository productRepository = mock(ProductRepository.class);
        NacosService nacosService = mock(NacosService.class);
        AiRegistrySkillService aiRegistrySkillService = mock(AiRegistrySkillService.class);

        SkillConfig unavailableConfig = skillConfig(SkillRegistryType.NACOS, "1.0.0", 1L);
        unavailableConfig.setNacosId("nacos-unavailable");
        unavailableConfig.setSkillName("unavailable-skill");
        Product unavailableProduct = skillProduct(unavailableConfig);

        SkillConfig availableConfig = skillConfig(SkillRegistryType.AIREGISTRY, "1.0.0", 2L);
        availableConfig.setAiRegistryId("airegistry-available");
        availableConfig.setSkillName("available-skill");
        Product availableProduct = skillProduct(availableConfig);
        availableProduct.setProductId("product-2");
        AiRegistrySkillResult availableMetadata =
                AiRegistrySkillResult.builder()
                        .name("available-skill")
                        .downloadCount(20L)
                        .latestVersion("2.0.0")
                        .build();

        when(productRepository.findAllByType(ProductType.AGENT_SKILL))
                .thenReturn(List.of(unavailableProduct, availableProduct));
        when(productRepository.findAllByType(ProductType.WORKER)).thenReturn(List.of());
        when(nacosService.getAiMaintainerService("nacos-unavailable"))
                .thenThrow(new RuntimeException("connection failed"));
        when(aiRegistrySkillService.listSkillMetadata("airegistry-available", "ns-prod"))
                .thenReturn(Map.of("available-skill", availableMetadata));

        sync(productRepository, nacosService, aiRegistrySkillService);

        assertEquals(1L, unavailableConfig.getDownloadCount());
        assertEquals("1.0.0", unavailableConfig.getLatestVersion());
        assertEquals(20L, availableConfig.getDownloadCount());
        assertEquals("2.0.0", availableConfig.getLatestVersion());
        verify(aiRegistrySkillService).listSkillMetadata("airegistry-available", "ns-prod");
        verify(productRepository).saveAll(List.of(availableProduct));
    }

    @Test
    void unchangedMetadataDoesNotWriteProduct() {
        ProductRepository productRepository = mock(ProductRepository.class);
        NacosService nacosService = mock(NacosService.class);
        AiRegistrySkillService aiRegistrySkillService = mock(AiRegistrySkillService.class);
        SkillConfig skillConfig = skillConfig(SkillRegistryType.AIREGISTRY, "2.0.0", 12L);
        skillConfig.setAiRegistryId("airegistry-1");
        Product product = skillProduct(skillConfig);
        product.setStatus(ProductStatus.PUBLISHED);
        AiRegistrySkillResult metadata =
                AiRegistrySkillResult.builder()
                        .name("weather-skill")
                        .downloadCount(12L)
                        .latestVersion("2.0.0")
                        .build();

        when(productRepository.findAllByType(ProductType.AGENT_SKILL)).thenReturn(List.of(product));
        when(productRepository.findAllByType(ProductType.WORKER)).thenReturn(List.of());
        when(aiRegistrySkillService.listSkillMetadata("airegistry-1", "ns-prod"))
                .thenReturn(Map.of("weather-skill", metadata));

        sync(productRepository, nacosService, aiRegistrySkillService);

        verify(productRepository, never()).saveAll(anyList());
    }

    @Test
    void nacosWorkerMetadataIsSyncedFromOneSummaryRequest() throws Exception {
        ProductRepository productRepository = mock(ProductRepository.class);
        NacosService nacosService = mock(NacosService.class);
        AiRegistrySkillService aiRegistrySkillService = mock(AiRegistrySkillService.class);
        AiMaintainerService aiMaintainerService = mock(AiMaintainerService.class);
        AgentSpecMaintainerService agentSpecMaintainerService =
                mock(AgentSpecMaintainerService.class);
        WorkerConfig workerConfig =
                WorkerConfig.builder()
                        .nacosId("nacos-1")
                        .namespace("ns-prod")
                        .agentSpecName("weather-worker")
                        .latestVersion("1.0.0")
                        .downloadCount(1L)
                        .build();
        Product product =
                Product.builder()
                        .productId("product-1")
                        .type(ProductType.WORKER)
                        .status(ProductStatus.PENDING)
                        .feature(ProductFeature.builder().workerConfig(workerConfig).build())
                        .build();
        AgentSpecSummary summary = new AgentSpecSummary();
        summary.setName("weather-worker");
        summary.setDownloadCount(15L);
        summary.setLabels(Map.of("latest", "1.2.0"));
        Page<AgentSpecSummary> page = new Page<>();
        page.setPageItems(List.of(summary));

        when(productRepository.findAllByType(ProductType.AGENT_SKILL)).thenReturn(List.of());
        when(productRepository.findAllByType(ProductType.WORKER)).thenReturn(List.of(product));
        when(nacosService.getAiMaintainerService("nacos-1")).thenReturn(aiMaintainerService);
        when(aiMaintainerService.agentSpec()).thenReturn(agentSpecMaintainerService);
        when(agentSpecMaintainerService.listAgentSpecAdminItems(
                        "ns-prod", null, null, 1, Integer.MAX_VALUE))
                .thenReturn(page);

        sync(productRepository, nacosService, aiRegistrySkillService);

        assertEquals(15L, workerConfig.getDownloadCount());
        assertEquals("1.2.0", workerConfig.getLatestVersion());
        assertEquals(ProductStatus.PENDING, product.getStatus());
        verify(productRepository).saveAll(List.of(product));
    }

    private SkillConfig skillConfig(
            SkillRegistryType registryType, String latestVersion, Long downloadCount) {
        return SkillConfig.builder()
                .registryType(registryType)
                .namespace("ns-prod")
                .skillName("weather-skill")
                .latestVersion(latestVersion)
                .downloadCount(downloadCount)
                .build();
    }

    private Product skillProduct(SkillConfig skillConfig) {
        return Product.builder()
                .productId("product-1")
                .type(ProductType.AGENT_SKILL)
                .status(ProductStatus.PENDING)
                .feature(ProductFeature.builder().skillConfig(skillConfig).build())
                .build();
    }

    private void sync(
            ProductRepository productRepository,
            NacosService nacosService,
            AiRegistrySkillService aiRegistrySkillService) {
        new SkillWorkerMetadataSyncTask(productRepository, nacosService, aiRegistrySkillService)
                .syncMetadata();
    }
}
