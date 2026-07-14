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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.alibaba.himarket.core.security.ContextHolder;
import com.alibaba.himarket.dto.params.product.ProductSortBy;
import com.alibaba.himarket.dto.params.product.QueryProductParam;
import com.alibaba.himarket.dto.result.common.PageResult;
import com.alibaba.himarket.dto.result.product.ProductResult;
import com.alibaba.himarket.entity.Product;
import com.alibaba.himarket.repository.ApiDefinitionRepository;
import com.alibaba.himarket.repository.ConsumerRepository;
import com.alibaba.himarket.repository.ProductPublicationRepository;
import com.alibaba.himarket.repository.ProductRefRepository;
import com.alibaba.himarket.repository.ProductRepository;
import com.alibaba.himarket.repository.SubscriptionRepository;
import com.alibaba.himarket.service.AiRegistryService;
import com.alibaba.himarket.service.GatewayService;
import com.alibaba.himarket.service.McpToolService;
import com.alibaba.himarket.service.NacosService;
import com.alibaba.himarket.service.PortalService;
import com.alibaba.himarket.service.ProductCategoryService;
import com.alibaba.himarket.service.SkillService;
import com.alibaba.himarket.service.WorkerService;
import com.alibaba.himarket.support.enums.ProductType;
import com.alibaba.himarket.support.product.ProductFeature;
import com.alibaba.himarket.support.product.SkillConfig;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

class ProductServiceImplListTest {

    @Test
    void downloadSortedSkillListUsesStoredMetadataWithoutRemoteVersionQueries() {
        ContextHolder contextHolder = mock(ContextHolder.class);
        ProductRepository productRepository = mock(ProductRepository.class);
        ProductRefRepository productRefRepository = mock(ProductRefRepository.class);
        ProductCategoryService productCategoryService = mock(ProductCategoryService.class);
        SkillService skillService = mock(SkillService.class);
        Pageable pageable = PageRequest.of(0, 12);

        Product first = skillProduct(1L, "product-1", "1.0.0", 5L);
        Product second = skillProduct(2L, "product-2", "2.0.0", 10L);

        when(contextHolder.isAdministrator()).thenReturn(true);
        when(productRepository.findAll(
                        org.mockito.ArgumentMatchers
                                .<org.springframework.data.jpa.domain.Specification<Product>>any(),
                        eq(pageable)))
                .thenReturn(new PageImpl<>(List.of(second, first), pageable, 2));
        when(productRefRepository.findByProductIdIn(anyList())).thenReturn(Collections.emptyList());
        when(productCategoryService.listCategoriesForProducts(anyList()))
                .thenReturn(Collections.emptyMap());

        ProductServiceImpl service =
                newService(
                        contextHolder,
                        productRepository,
                        productRefRepository,
                        productCategoryService,
                        skillService);
        QueryProductParam param = new QueryProductParam();
        param.setType(ProductType.AGENT_SKILL);
        param.setSortBy(ProductSortBy.DOWNLOAD_COUNT);

        PageResult<ProductResult> result = service.listProducts(param, pageable);

        assertEquals(List.of("product-2", "product-1"), productIds(result));
        assertEquals("2.0.0", result.getContent().get(0).getSkillConfig().getLatestVersion());
        assertEquals(10L, result.getContent().get(0).getSkillConfig().getDownloadCount());
        verify(skillService, never()).listVersions(any());
    }

    private Product skillProduct(
            Long id, String productId, String latestVersion, Long downloadCount) {
        SkillConfig config =
                SkillConfig.builder()
                        .latestVersion(latestVersion)
                        .downloadCount(downloadCount)
                        .build();
        return Product.builder()
                .id(id)
                .productId(productId)
                .name(productId)
                .type(ProductType.AGENT_SKILL)
                .feature(ProductFeature.builder().skillConfig(config).build())
                .build();
    }

    private List<String> productIds(PageResult<ProductResult> result) {
        return result.getContent().stream().map(ProductResult::getProductId).toList();
    }

    private ProductServiceImpl newService(
            ContextHolder contextHolder,
            ProductRepository productRepository,
            ProductRefRepository productRefRepository,
            ProductCategoryService productCategoryService,
            SkillService skillService) {
        return new ProductServiceImpl(
                contextHolder,
                mock(PortalService.class),
                mock(GatewayService.class),
                productRepository,
                productRefRepository,
                mock(ApiDefinitionRepository.class),
                mock(ProductPublicationRepository.class),
                mock(SubscriptionRepository.class),
                mock(ConsumerRepository.class),
                mock(NacosService.class),
                productCategoryService,
                mock(McpToolService.class),
                mock(WorkerService.class),
                skillService,
                mock(AiRegistryService.class),
                mock(ApplicationEventPublisher.class));
    }
}
