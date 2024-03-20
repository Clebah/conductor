/*
 * Copyright 2020 Netflix, Inc.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */

package com.netflix.conductor.mongo.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.data.mongo.MongoDataAutoConfiguration;
import org.springframework.boot.autoconfigure.mongo.MongoAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.mongodb.core.MongoTemplate;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.conductor.dao.ExecutionDAO;
import com.netflix.conductor.dao.MetadataDAO;
import com.netflix.conductor.dao.QueueDAO;
import com.netflix.conductor.mongo.dao.MongoExecutionDAO;
import com.netflix.conductor.mongo.dao.MongoMetadataDAO;
import com.netflix.conductor.mongo.dao.MongoQueueDAO;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "conductor.db.type", havingValue = "mongo")
@Import({MongoAutoConfiguration.class, MongoDataAutoConfiguration.class})
public class MongoConfiguration {
	
	@Autowired 
	MongoTemplate mongoTemplate;
	
	@Bean
    public MetadataDAO mongoMetadataDAO(ObjectMapper objectMapper,MongoTemplate mongoTemplate) {
        return new MongoMetadataDAO(objectMapper, mongoTemplate);
    }

    @Bean
    public ExecutionDAO mongoExecutionDAO(ObjectMapper objectMapper,MongoTemplate mongoTemplate) {
        return new MongoExecutionDAO(objectMapper, mongoTemplate);
    }

    @Bean
    public QueueDAO mongoQueueDAO(ObjectMapper objectMapper,MongoTemplate mongoTemplate) {
        return new MongoQueueDAO(objectMapper, mongoTemplate);
    }
}