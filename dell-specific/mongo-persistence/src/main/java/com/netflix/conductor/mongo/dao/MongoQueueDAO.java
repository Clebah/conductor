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

package com.netflix.conductor.mongo.dao;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.springframework.data.domain.Sort;
import org.springframework.data.domain.Sort.Direction;
import org.springframework.data.domain.Sort.Order;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.google.common.util.concurrent.Uninterruptibles;
import com.netflix.conductor.annotations.Trace;
import com.netflix.conductor.core.events.queue.Message;
import com.netflix.conductor.dao.QueueDAO;
import com.netflix.conductor.mongo.entities.QueueDocument;
import com.netflix.conductor.mongo.entities.QueueMessageDocument;

@Trace
public class MongoQueueDAO  extends MongoBaseDAO implements QueueDAO {
	
	private static final Long UNACK_SCHEDULE_MS = 60_000L;
	
	private final MongoTemplate mongoTemplate;
	
	public MongoQueueDAO(ObjectMapper objectMapper, MongoTemplate mongoTemplate) {
		super(objectMapper);
		this.mongoTemplate = mongoTemplate;
	
		Executors.newSingleThreadScheduledExecutor()
        .scheduleAtFixedRate(this::processAllUnacks,
            UNACK_SCHEDULE_MS, UNACK_SCHEDULE_MS, TimeUnit.MILLISECONDS);
			logger.debug(MongoQueueDAO.class.getName() + " is ready to serve");
	}

	@Override
	public void push(String queueName, String messageId, long offsetTimeInSecond) {
        push(queueName, messageId, 0, offsetTimeInSecond);
    }

	@Override
	public void push(String queueName, String messageId, int priority, long offsetTimeInSecond) {
		 pushMessage(queueName, messageId, null, priority, offsetTimeInSecond);
		
	}

	@Override
	public void push(String queueName, List<Message> messages) {
		messages
        .forEach(message -> pushMessage(queueName, message.getId(), message.getPayload(), message.getPriority(),
            0));
	}

	@Override
	public boolean pushIfNotExists(String queueName, String messageId, long offsetTimeInSecond) {
		return pushIfNotExists(queueName, messageId, 0, offsetTimeInSecond);
	}

	@Override
	public boolean pushIfNotExists(String queueName, String messageId, int priority, long offsetTimeInSecond) {

        if (!existsMessage(queueName, messageId)) {
            pushMessage(queueName, messageId, null, priority, offsetTimeInSecond);
            return true;
        }
        return false;
	}

	@Override
	public List<String> pop(String queueName, int count, int timeout) {
		List<Message> messages = popMessages(queueName, count, timeout);
		if (messages == null) {
            return new ArrayList<>();
        }
		return messages.stream().map(Message::getId).collect(Collectors.toList());
	}

	@Override
	public List<Message> pollMessages(String queueName, int count, int timeout) {
		List<Message> messages = popMessages(queueName, count, timeout);
		if (messages == null) {
            return new ArrayList<>();
        }
        return messages;
	}

	@Override
	public void remove(String queueName, String messageId) {
		removeMessage(queueName, messageId);
		
	}

	@Override
	public int getSize(String queueName) {
		return ((Long)mongoTemplate.count(new Query().addCriteria(Criteria.where("queue_name").is(queueName)), QueueMessageDocument.class)).intValue();
	}

	@Override
	public boolean ack(String queueName, String messageId) {
		return removeMessage(queueName, messageId);
	}

	@Override
	public boolean setUnackTimeout(String queueName, String messageId, long unackTimeout) {
        long updatedOffsetTimeInSecond = unackTimeout / 1000;

        Query query = new Query();
    	query.addCriteria(Criteria.where("queue_name").is(queueName).and("message_id").is(messageId));
    	Update update = new Update().max(DAO_NAME, query);
    	update.set("offset_time_seconds", updatedOffsetTimeInSecond);
    	update.set("deliver_on", getOffsetAddedDate(((Long)updatedOffsetTimeInSecond).intValue()));
    	
        return mongoTemplate.updateMulti(query, update, QueueMessageDocument.class).getModifiedCount() == 1;
    }

	@Override
	public void flush(String queueName) {
		mongoTemplate.remove(new Query().addCriteria(Criteria.where("queue_name").is(queueName)), QueueMessageDocument.class);
	}

	@Override
	public Map<String, Long> queuesDetail() {
        Map<String, Long> detail = Maps.newHashMap();
        Query findQuery = new Query();
        findQuery.addCriteria(Criteria.where("popped").is(false));
    	
        List<String> queueNames = mongoTemplate.findDistinct(findQuery, "queue_name", QueueMessageDocument.class, String.class);
    	
        queueNames.forEach(queueName -> {
        	Query query = new Query();
            query.addCriteria(Criteria.where("popped").is(false).and("queue_name").is(queueName));
            long size = mongoTemplate.count(query, QueueMessageDocument.class);
        	detail.put(queueName, size);
        });
    	
        return detail;
    }

	@Override
	public Map<String, Map<String, Map<String, Long>>> queuesDetailVerbose() {
        Map<String, Map<String, Map<String, Long>>> result = Maps.newHashMap();

        List<String> queueNames = mongoTemplate.findDistinct(new Query(), "queue_name", QueueMessageDocument.class, String.class);
        
        queueNames.forEach(queueName -> {
        	
        	Query sizeQuery = new Query();
        	sizeQuery.addCriteria(Criteria.where("popped").is(false).and("queue_name").is(queueName));
            long size = mongoTemplate.count(sizeQuery, QueueMessageDocument.class);
            
            Query unackedQuery = new Query();
            unackedQuery.addCriteria(Criteria.where("popped").is(true).and("queue_name").is(queueName));
            long queueUnacked = mongoTemplate.count(unackedQuery, QueueMessageDocument.class);
            
            result.put(queueName, ImmutableMap.of("a", ImmutableMap.of( // sharding not implemented, returning only
                    // one shard with all the info
                    "size", size, "uacked", queueUnacked)));
        	
        });
        
       return result;
    }

	@Override
	public boolean resetOffsetTime(String queueName, String messageId) {
		QueueMessageDocument aQueueMessageDocument = mongoTemplate.findOne(new Query().addCriteria(Criteria.where("queue_name").is(queueName).and("message_id").is(messageId)), QueueMessageDocument.class);
		if(null==aQueueMessageDocument)
			return false;
		else
		{
			aQueueMessageDocument.setOffsetTimeSeconds(0);
			aQueueMessageDocument.setDeliverOn(getOffsetAddedDate(0));
			return mongoTemplate.save(aQueueMessageDocument)!=null;
		}
	}
	
	private void pushMessage(String queueName, String messageId, String payload,
	        Integer priority,
	        long offsetTimeInSecond) {

	        createQueueIfNotExists(queueName);
	        
	        QueueMessageDocument aQueueMessageDocument = mongoTemplate.findOne(new Query().addCriteria(Criteria.where("queue_name").is(queueName).and("message_id").is(messageId)), QueueMessageDocument.class);

	        if(null!=aQueueMessageDocument)
	        {
	        	aQueueMessageDocument.setPriority(priority);
	        	aQueueMessageDocument.setOffsetTimeSeconds(offsetTimeInSecond);
	        	aQueueMessageDocument.setDeliverOn(getOffsetAddedDate(((Long)offsetTimeInSecond).intValue()));
	        	aQueueMessageDocument.setPopped(false);
	        	aQueueMessageDocument = mongoTemplate.save(aQueueMessageDocument);
	        }
	        else {
	        	QueueMessageDocument newQueueMessageDocument = new QueueMessageDocument();
	        	newQueueMessageDocument.setQueueName(queueName);
	        	newQueueMessageDocument.setMessageId(messageId);
	        	newQueueMessageDocument.setPriority(priority);
	        	newQueueMessageDocument.setOffsetTimeSeconds(offsetTimeInSecond);
	        	newQueueMessageDocument.setDeliverOn(getOffsetAddedDate(((Long)offsetTimeInSecond).intValue()));
	        	newQueueMessageDocument.setPopped(false);
	        	newQueueMessageDocument.setPayload(payload);
	        	newQueueMessageDocument = mongoTemplate.save(newQueueMessageDocument);
	        }
	    }
	
	 private void createQueueIfNotExists(String queueName) {
	        logger.trace("Creating new queue '{}'", queueName);
	        
	        Query searchQuery = new Query();
	        
	        searchQuery.addCriteria(Criteria.where("name").is(queueName));
	        
	        boolean exists = mongoTemplate.count(searchQuery, QueueDocument.class)>0;
	        if (!exists) {
	        	QueueDocument newQueueDocument = new QueueDocument();
	        	newQueueDocument.setQueueName(queueName);
	        	newQueueDocument = mongoTemplate.save(newQueueDocument);
	        }
	    }
	
	public void processAllUnacks() {

        logger.trace("processAllUnacks started");

        Query query = new Query();
    	query.addCriteria(Criteria.where("popped").is(true).and("deliver_on").lt(getOffsetAddedDate(-60)));
    	Update update = new Update().max(DAO_NAME, query);
    	update.set("popped", false);

        mongoTemplate.updateMulti(query, update, QueueMessageDocument.class);
    }
	
    private boolean existsMessage(String queueName, String messageId) {
        
        return mongoTemplate.exists(new Query().addCriteria(Criteria.where("queue_name").is(queueName).and("message_id").is(messageId)), QueueMessageDocument.class);
    }

    private List<Message> popMessages(String queueName, int count, int timeout) {
        long start = System.currentTimeMillis();
        List<Message> messages = peekMessages(queueName, count);

        while (messages.size() < count && ((System.currentTimeMillis() - start) < timeout)) {
            Uninterruptibles.sleepUninterruptibly(200, TimeUnit.MILLISECONDS);
            messages = peekMessages(queueName, count);
        }

        if (messages.isEmpty()) {
            return messages;
        }

        List<Message> poppedMessages = new ArrayList<>();
        for (Message message : messages) {
            
        	Query query = new Query();
        	query.addCriteria(Criteria.where("popped").is(false).and("queue_name").is(queueName).and("message_id").is(message.getId()));
        	Update update = new Update().max(DAO_NAME, query);
        	update.set("popped", true);
        	
        	if (mongoTemplate.updateMulti(query, update, QueueMessageDocument.class).getModifiedCount() == 1) {
                poppedMessages.add(message);
            }
        }
        return poppedMessages;
    }
    
    private List<Message> peekMessages(String queueName, int count) {
        if (count < 1) {
            return Collections.emptyList();
        }

        List<Order> orderBy = new ArrayList<Order>();
        
        Query query = new Query();
        query.addCriteria(Criteria.where("queue_name").is(queueName).and("popped").is(false).and("deliver_on").lte(getOffsetAddedDate(1000)));
    	orderBy.add(new Order(Direction.DESC, "priority"));
    	orderBy.add(new Order(Direction.ASC, "created_on"));
    	orderBy.add(new Order(Direction.ASC, "deliver_on"));
    	query.with(Sort.by(orderBy));
    	query.limit(count);
    	
    	List<Message> results = new ArrayList<>();
    	
    	List<QueueMessageDocument> aQueueMessageDocumentList = mongoTemplate.find(query, QueueMessageDocument.class);
    	if(!aQueueMessageDocumentList.isEmpty())
    		aQueueMessageDocumentList.forEach(qmd -> {
        		Message m = new Message();
                m.setId(qmd.getMessageId());
                m.setPriority(qmd.getPriority());
                m.setPayload(qmd.getPayload());
                results.add(m);
        	});
    	
        return results;
    }
    
    private boolean removeMessage(String queueName, String messageId) {
    	QueueMessageDocument queueMessageDocument =mongoTemplate.findOne(new Query().addCriteria(Criteria.where("queue_name").is(queueName).and("message_id").is(messageId)), QueueMessageDocument.class);
        
        if(null!=queueMessageDocument)
        {
        	mongoTemplate.remove(queueMessageDocument);
        	return true;
        }
        return false;
    }

	private Date getOffsetAddedDate(int offsetInSeconds) {
		Date oldDate = new Date();
	    Calendar gcal = new GregorianCalendar();
	    gcal.setTime(oldDate);
	    gcal.add(Calendar.SECOND, offsetInSeconds);
	    Date newDate = gcal.getTime();
	    return newDate;
	}
	
	@Override
    public boolean containsMessage(String queueName, String messageId) {
		Query searchQuery = new Query();
		searchQuery.addCriteria(Criteria.where("queue_name").is(queueName).and("message_id").is(messageId));
		
        return mongoTemplate.exists(searchQuery, QueueMessageDocument.class);
    }
}
