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

package com.netflix.conductor.mongo.entities;

import java.util.Date;

import javax.persistence.PrePersist;
import javax.persistence.Temporal;
import javax.persistence.TemporalType;

import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import lombok.Data;

@Document(collection = "queue")
@Data
public class QueueDocument {

	@Field("name")
	@Indexed(name = "queue_queue_name_idx", unique=true)
	private String name;
 	@Field("created_on")
	@Temporal(TemporalType.TIMESTAMP)
	private Date created_on;
 	
	public QueueDocument() {
		super();
		// TODO Auto-generated constructor stub
	}

	public QueueDocument(String queueName, Date created_on) {
		super();
		this.name = queueName;
		this.created_on = created_on;
	}
	
	@PrePersist
	protected void onCreate() {
		created_on = new Date();
	}

	public String getQueueName() {
		return name;
	}

	public void setQueueName(String queueName) {
		this.name = queueName;
	}

	public Date getCreated_on() {
		return created_on;
	}

	public void setCreated_on(Date created_on) {
		this.created_on = created_on;
	}
 	
 	
}
