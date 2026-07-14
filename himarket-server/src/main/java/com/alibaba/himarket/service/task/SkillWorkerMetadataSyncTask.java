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

import com.alibaba.himarket.dto.result.airegistry.AiRegistrySkillResult;
import com.alibaba.himarket.entity.Product;
import com.alibaba.himarket.repository.ProductRepository;
import com.alibaba.himarket.service.AiRegistrySkillService;
import com.alibaba.himarket.service.NacosService;
import com.alibaba.himarket.support.enums.ProductType;
import com.alibaba.himarket.support.enums.SkillRegistryType;
import com.alibaba.himarket.support.product.SkillConfig;
import com.alibaba.himarket.support.product.WorkerConfig;
import com.alibaba.nacos.api.ai.model.agentspecs.AgentSpecSummary;
import com.alibaba.nacos.api.ai.model.skills.SkillSummary;
import com.alibaba.nacos.api.model.Page;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
public class SkillWorkerMetadataSyncTask {

    private final ProductRepository productRepository;
    private final NacosService nacosService;
    private final AiRegistrySkillService aiRegistrySkillService;

    @Scheduled(fixedDelay = 300_000)
    public void syncMetadata() {
        try {
            List<Product> changedProducts = new ArrayList<>();
            syncSkills(changedProducts);
            syncWorkers(changedProducts);
            if (!changedProducts.isEmpty()) {
                productRepository.saveAll(changedProducts);
            }
        } catch (Exception e) {
            log.error(
                    "Unexpected error during Skill and Worker metadata sync, errorMessage={}",
                    e.getMessage(),
                    e);
        }
    }

    private void syncSkills(List<Product> changedProducts) {
        Map<String, Map<String, SkillSummary>> nacosCache = new HashMap<>();
        Map<String, Map<String, AiRegistrySkillResult>> aiRegistryCache = new HashMap<>();
        for (Product product : productRepository.findAllByType(ProductType.AGENT_SKILL)) {
            SkillConfig config =
                    product.getFeature() == null ? null : product.getFeature().getSkillConfig();
            if (config == null) {
                continue;
            }

            boolean aiRegistry = config.getRegistryType() == SkillRegistryType.AIREGISTRY;
            String instanceId = aiRegistry ? config.getAiRegistryId() : config.getNacosId();
            if (instanceId == null) {
                continue;
            }
            String registryKey = instanceId + ":" + config.getNamespace();

            try {
                Long downloadCount;
                String latestVersion;
                if (aiRegistry) {
                    Map<String, AiRegistrySkillResult> skills = aiRegistryCache.get(registryKey);
                    if (skills == null) {
                        skills =
                                aiRegistrySkillService.listSkillMetadata(
                                        instanceId, config.getNamespace());
                        aiRegistryCache.put(registryKey, skills);
                    }
                    AiRegistrySkillResult skill = skills.get(config.getSkillName());
                    if (skill == null) {
                        continue;
                    }
                    downloadCount = skill.getDownloadCount();
                    latestVersion = skill.getLatestVersion();
                } else {
                    Map<String, SkillSummary> skills = nacosCache.get(registryKey);
                    if (skills == null) {
                        skills = new HashMap<>();
                        Page<SkillSummary> page =
                                nacosService
                                        .getAiMaintainerService(instanceId)
                                        .skill()
                                        .listSkills(
                                                config.getNamespace(),
                                                null,
                                                null,
                                                1,
                                                Integer.MAX_VALUE);
                        if (page != null && page.getPageItems() != null) {
                            for (SkillSummary skill : page.getPageItems()) {
                                skills.putIfAbsent(skill.getName(), skill);
                            }
                        }
                        nacosCache.put(registryKey, skills);
                    }
                    SkillSummary skill = skills.get(config.getSkillName());
                    if (skill == null) {
                        continue;
                    }
                    downloadCount = skill.getDownloadCount();
                    latestVersion =
                            skill.getLabels() == null ? null : skill.getLabels().get("latest");
                }

                boolean changed = false;
                if (downloadCount != null
                        && !Objects.equals(config.getDownloadCount(), downloadCount)) {
                    config.setDownloadCount(downloadCount);
                    changed = true;
                }
                if (!Objects.equals(config.getLatestVersion(), latestVersion)) {
                    config.setLatestVersion(latestVersion);
                    changed = true;
                }
                if (changed) {
                    changedProducts.add(product);
                }
            } catch (Exception e) {
                if (aiRegistry) {
                    aiRegistryCache.put(registryKey, Map.of());
                } else {
                    nacosCache.put(registryKey, Map.of());
                }
                log.warn(
                        "Failed to sync {} metadata, instanceId={}, namespace={},"
                                + " errorMessage={}",
                        aiRegistry ? "AIRegistry Skill" : "Nacos Skill",
                        instanceId,
                        config.getNamespace(),
                        e.getMessage(),
                        e);
            }
        }
    }

    private void syncWorkers(List<Product> changedProducts) {
        Map<String, Map<String, AgentSpecSummary>> metadataCache = new HashMap<>();
        for (Product product : productRepository.findAllByType(ProductType.WORKER)) {
            WorkerConfig config =
                    product.getFeature() == null ? null : product.getFeature().getWorkerConfig();
            if (config == null || config.getNacosId() == null) {
                continue;
            }
            String registryKey = config.getNacosId() + ":" + config.getNamespace();

            try {
                Map<String, AgentSpecSummary> workers = metadataCache.get(registryKey);
                if (workers == null) {
                    workers = new HashMap<>();
                    Page<AgentSpecSummary> page =
                            nacosService
                                    .getAiMaintainerService(config.getNacosId())
                                    .agentSpec()
                                    .listAgentSpecAdminItems(
                                            config.getNamespace(),
                                            null,
                                            null,
                                            1,
                                            Integer.MAX_VALUE);
                    if (page != null && page.getPageItems() != null) {
                        for (AgentSpecSummary worker : page.getPageItems()) {
                            workers.putIfAbsent(worker.getName(), worker);
                        }
                    }
                    metadataCache.put(registryKey, workers);
                }
                AgentSpecSummary worker = workers.get(config.getAgentSpecName());
                if (worker == null) {
                    continue;
                }

                boolean changed = false;
                if (worker.getDownloadCount() != null
                        && config.getDownloadCount() != worker.getDownloadCount()) {
                    config.setDownloadCount(worker.getDownloadCount());
                    changed = true;
                }
                String latestVersion =
                        worker.getLabels() == null ? null : worker.getLabels().get("latest");
                if (!Objects.equals(config.getLatestVersion(), latestVersion)) {
                    config.setLatestVersion(latestVersion);
                    changed = true;
                }
                if (changed) {
                    changedProducts.add(product);
                }
            } catch (Exception e) {
                metadataCache.put(registryKey, Map.of());
                log.warn(
                        "Failed to sync Nacos Worker metadata, instanceId={}, namespace={},"
                                + " errorMessage={}",
                        config.getNacosId(),
                        config.getNamespace(),
                        e.getMessage(),
                        e);
            }
        }
    }
}
