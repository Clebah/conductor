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

import java.sql.Connection;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.Sort.Direction;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.netflix.conductor.annotations.Trace;
import com.netflix.conductor.common.metadata.events.EventHandler;
import com.netflix.conductor.common.metadata.tasks.TaskDef;
import com.netflix.conductor.common.metadata.workflow.WorkflowDef;
import com.netflix.conductor.core.exception.ApplicationException;
import com.netflix.conductor.dao.EventHandlerDAO;
import com.netflix.conductor.dao.MetadataDAO;
import com.netflix.conductor.metrics.Monitors;
import com.netflix.conductor.mongo.entities.MetaEventHandlerDocument;
import com.netflix.conductor.mongo.entities.MetaTaskDefDocument;
import com.netflix.conductor.mongo.entities.MetaWorkflowDefDocument;

@Trace
public class MongoMetadataDAO extends MongoBaseDAO implements MetadataDAO, EventHandlerDAO {
	
	private final MongoTemplate mongoTemplate;
		
	 public MongoMetadataDAO(ObjectMapper objectMapper, MongoTemplate mongoTemplate) {
			super(objectMapper);
			this.mongoTemplate = mongoTemplate;
		}
	 
	private static final Logger LOGGER = LoggerFactory.getLogger(MongoMetadataDAO.class);
    private static final String CLASS_NAME = MongoMetadataDAO.class.getSimpleName();
    private static final String INDEX_DELIMITER = "/";
    
    private Map<String, TaskDef> taskDefCache = new HashMap<>();
    
	@Override
	public void createTaskDef(TaskDef taskDef) {
		recordMongoDaoRequests("createTaskDef");
        validate(taskDef);
        insertOrUpdateTaskDef(taskDef);
    }

	@Override
	public String updateTaskDef(TaskDef taskDef) {
		recordMongoDaoRequests("updateTaskDef");
        validate(taskDef);
        return insertOrUpdateTaskDef(taskDef);
    }

	@Override
	public TaskDef getTaskDef(String name) {
		recordMongoDaoRequests("getTaskDef");
		return Optional.ofNullable(taskDefCache.get(name))
                .orElseGet(() -> getTaskDefFromDB(name));
        }

	@Override
	public List<TaskDef> getAllTaskDefs() {
		recordMongoDaoRequests("getAllTaskDefs");
        if (taskDefCache.size() == 0) {
            refreshTaskDefsCache();
        }
        return new ArrayList<>(taskDefCache.values());
    }

	@Override
	public void removeTaskDef(String name) {
        try {
            recordMongoDaoRequests("removeTaskDef");
            long deletedCount = mongoTemplate.remove(new Query().addCriteria(Criteria.where("name").is(name)), MetaTaskDefDocument.class).getDeletedCount();
            if(deletedCount == 0)
            {
            	Monitors.error(CLASS_NAME, "removeTaskDef");
                String errorMsg = String.format("No such task definition: %s", name);
                LOGGER.error(errorMsg);
                throw new ApplicationException(ApplicationException.Code.NOT_FOUND, "No such task definition");
            }
        } catch (Exception e) {
            Monitors.error(CLASS_NAME, "removeTaskDef");
            String errorMsg = String.format("No such task definition: %s", name);
            LOGGER.error(errorMsg, e);
            throw new ApplicationException(ApplicationException.Code.NOT_FOUND, "No such task definition");
        }
        refreshTaskDefsCache();
    }

	@Override
	public void createWorkflowDef(WorkflowDef def) {
        try {
        	recordMongoDaoRequests("createWorkflowDef");
            validate(def);
            
            if (workflowExists(def)) {
                throw new ApplicationException(ApplicationException.Code.CONFLICT,
                    "Workflow with " + def.key() + " already exists!");
            }

            insertOrUpdateWorkflowDef(def);
        
        
        } catch (ApplicationException ae) {
            throw ae;
        } catch (Exception e) {
            Monitors.error(CLASS_NAME, "createWorkflowDef");
            String errorMsg = String.format("Error creating workflow definition: %s/%d", def.getName(),
                def.getVersion());
            LOGGER.error(errorMsg, e);
            throw new ApplicationException(ApplicationException.Code.BACKEND_ERROR, errorMsg, e);
        }
    }

	@Override
	public void updateWorkflowDef(WorkflowDef def) {
	        try {
        	recordMongoDaoRequests("updateWorkflowDef");
            validate(def);
            insertOrUpdateWorkflowDef(def);
        } catch (Exception e) {
            Monitors.error(CLASS_NAME, "updateWorkflowDef");
            String errorMsg = String.format("Error updating workflow definition: %s/%d", def.getName(),
                def.getVersion());
            LOGGER.error(errorMsg, e);
            throw new ApplicationException(ApplicationException.Code.BACKEND_ERROR, errorMsg, e);
        }
    
	}

	@Override
	public Optional<WorkflowDef> getLatestWorkflowDef(String name) {
        try {
        	recordMongoDaoRequests("getLatestWorkflowDef");
            
            Query searchQuery = new Query();
            searchQuery.addCriteria(Criteria.where("name").is(name));
            searchQuery.with(Sort.by(Direction.DESC, "version"));
            
            MetaWorkflowDefDocument metaWorkflowDefDocument = mongoTemplate.findOne(searchQuery, MetaWorkflowDefDocument.class);
            
            
            return Optional.ofNullable(metaWorkflowDefDocument)
                .map(row -> readValue(metaWorkflowDefDocument.getJson_data(), WorkflowDef.class));
        } catch (Exception e) {
            Monitors.error(CLASS_NAME, "getLatestWorkflowDef");
            String errorMsg = String.format("Failed to get latest workflow def: %s", name);
            LOGGER.error(errorMsg, e);
            throw new ApplicationException(ApplicationException.Code.BACKEND_ERROR, errorMsg, e);
        }
    }

	@Override
	public Optional<WorkflowDef> getWorkflowDef(String name, int version) {
        try {
        	Query searchQuery = new Query();
        	searchQuery.addCriteria(Criteria.where("name").is(name).and("version").is(version));
        	
        	recordMongoDaoRequests("getWorkflowDef");
            return Optional.ofNullable(mongoTemplate.findOne(searchQuery, MetaWorkflowDefDocument.class))
                .map(row -> readValue(mongoTemplate.findOne(searchQuery, MetaWorkflowDefDocument.class).getJson_data(), WorkflowDef.class));
        } catch (Exception e) {
            Monitors.error(CLASS_NAME, "getWorkflowDef");
            String errorMsg = String.format("Failed to get workflow def: %s", name);
            LOGGER.error(errorMsg, e);
            throw new ApplicationException(ApplicationException.Code.BACKEND_ERROR, errorMsg, e);
        }
    }

	@Override
	public void removeWorkflowDef(String name, Integer version) {
        try {
            recordMongoDaoRequests("removeWorkflowDef");
            long deletedCount = mongoTemplate.remove(new Query().addCriteria(Criteria.where("name").is(name).and("version").is(version)), MetaWorkflowDefDocument.class).getDeletedCount();
            if(deletedCount == 0)
            {
            	Monitors.error(CLASS_NAME, "removeWorkflowDef");
            	String errorMsg = String.format("No such workflow definition: %s version: %d", name, version);
                LOGGER.error(errorMsg);
                throw new ApplicationException(ApplicationException.Code.NOT_FOUND,
                        String.format("No such workflow definition: %s version: %d", name, version));
            }
            else {
            	Optional<Integer> maxVersion = getLatestVersion(name);
                maxVersion.ifPresent(newVersion -> updateLatestVersion(name, newVersion));
            }
        } catch (Exception e) {
            Monitors.error(CLASS_NAME, "removeWorkflowDef");
            String errorMsg = String.format("No such workflow definition: %s version: %d", name, version);
            LOGGER.error(errorMsg, e);
            throw new ApplicationException(ApplicationException.Code.NOT_FOUND,
                    String.format("No such workflow definition: %s version: %d", name, version));
        }
    }

	@SuppressWarnings("unchecked")
	@Override
	public List<WorkflowDef> getAllWorkflowDefs() {
        try {
            recordMongoDaoRequests("getAllWorkflowDefs");
            List<MetaWorkflowDefDocument> metaWorkflowDefDocuments = mongoTemplate.findAll(MetaWorkflowDefDocument.class);
            if (metaWorkflowDefDocuments.size() == 0) {
                LOGGER.warn("No workflow definitions were found.");
                return Collections.EMPTY_LIST;
            }
            return metaWorkflowDefDocuments.stream()
                .map(metaWorkflowDefDocument -> readValue(metaWorkflowDefDocument.getJson_data(), WorkflowDef.class))
                .collect(Collectors.toList());
        } catch (Exception e) {
            Monitors.error(CLASS_NAME, "getAllWorkflowDefs");
            String errorMsg = String.format("Failed to get all workflow definition");
            LOGGER.error(errorMsg, e);
            throw new ApplicationException(ApplicationException.Code.BACKEND_ERROR, errorMsg, e);
        }
    }

	@Override
	public void addEventHandler(EventHandler eventHandler) {
		recordMongoDaoRequests("addEventHandler");
        Preconditions.checkNotNull(eventHandler.getName(), "EventHandler name cannot be null");
       
        if (getEventHandler(eventHandler.getName()) != null) {
            throw new ApplicationException(ApplicationException.Code.CONFLICT,
                "EventHandler with name " + eventHandler.getName() + " already exists!");
        }

        MetaEventHandlerDocument newMetaEventHandlerDocument = new MetaEventHandlerDocument();
        
        newMetaEventHandlerDocument.setName(eventHandler.getName());
        newMetaEventHandlerDocument.setEvent(eventHandler.getEvent());
        newMetaEventHandlerDocument.setActive(eventHandler.isActive());
        newMetaEventHandlerDocument.setJson_data(toJson(eventHandler));
        
        mongoTemplate.save(newMetaEventHandlerDocument);
    }

	@Override
	public void updateEventHandler(EventHandler eventHandler) {
		recordMongoDaoRequests("updateEventHandler");
        Preconditions.checkNotNull(eventHandler.getName(), "EventHandler name cannot be null");
       
        Query searchQuery = new Query();
        searchQuery.addCriteria(Criteria.where("name").is(eventHandler.getName()));
        
        if (!mongoTemplate.exists(searchQuery, MetaEventHandlerDocument.class)) {
            throw new ApplicationException(ApplicationException.Code.CONFLICT,
                "EventHandler with name " + eventHandler.getName() + " doesn't exists!");
        }
        
        Update update =new Update();
        update.set("event", eventHandler.getEvent());
        update.set("active", eventHandler.isActive());
        update.set("json_data", toJson(eventHandler));

        mongoTemplate.updateFirst(searchQuery, update, MetaEventHandlerDocument.class);
    }

	@Override
	public void removeEventHandler(String name) {
		recordMongoDaoRequests("removeEventHandler");
        Preconditions.checkNotNull(name, "EventHandler name cannot be null");
       
        if (getEventHandler(name) != null) {
            throw new ApplicationException(ApplicationException.Code.CONFLICT,
                "EventHandler with name " + name + " doesn't exists!");
        }

        long deletedCount = mongoTemplate.remove(new Query().addCriteria(Criteria.where("name").is(name)), MetaEventHandlerDocument.class).getDeletedCount();
        
        if(deletedCount == 0)
        {
        	Monitors.error(CLASS_NAME, "removeWorkflowDef");
        	String errorMsg = String.format("No such EventHandler: %s", name);
            LOGGER.error(errorMsg);
            throw new ApplicationException(ApplicationException.Code.NOT_FOUND,
                    "EventHandler with name " + name + " not found!");
        }
    }

	@SuppressWarnings("unchecked")
	@Override
	public List<EventHandler> getAllEventHandlers() {
		recordMongoDaoRequests("getAllEventHandlers");
        try {
            List<MetaEventHandlerDocument> metaEventHandlerDocuments = mongoTemplate.findAll(MetaEventHandlerDocument.class);
        	if (metaEventHandlerDocuments.size() == 0) {
                LOGGER.debug("No Event Handlers were found.");
                return Collections.EMPTY_LIST;
            }
            return metaEventHandlerDocuments.stream()
                .map(metaEventHandlerDocument -> readValue(metaEventHandlerDocument.getJson_data(), EventHandler.class))
                .collect(Collectors.toList());
        } catch (Exception e) {
            Monitors.error(CLASS_NAME, "getAllEventHandlers");
            String errorMsg = "Failed to get all event handlers";
            LOGGER.error(errorMsg, e);
            throw new ApplicationException(ApplicationException.Code.BACKEND_ERROR, errorMsg, e);
        }
    }

	@SuppressWarnings("unchecked")
	@Override
	public List<EventHandler> getEventHandlersForEvent(String event, boolean activeOnly) {
		recordMongoDaoRequests("getEventHandlersForEvent");
        try {
        	List<MetaEventHandlerDocument> metaEventHandlerDocuments = !activeOnly ? mongoTemplate.find(new Query().addCriteria(Criteria.where("event").is(event)), MetaEventHandlerDocument.class) : mongoTemplate.find(new Query().addCriteria(Criteria.where("event").is(event).and("active").is(activeOnly)), MetaEventHandlerDocument.class);
        	if (metaEventHandlerDocuments.size() == 0) {
                LOGGER.info("No Event Handlers were found.");
                return Collections.EMPTY_LIST;
            }
            return metaEventHandlerDocuments.stream()
                .map(metaEventHandlerDocument -> readValue(metaEventHandlerDocument.getJson_data(), EventHandler.class))
                .collect(Collectors.toList());
        } catch (Exception e) {
            Monitors.error(CLASS_NAME, "getEventHandlersForEvent");
            String errorMsg = String.format("Failed to get all event handlers for event: %s", event);
            LOGGER.error(errorMsg, e);
            throw new ApplicationException(ApplicationException.Code.BACKEND_ERROR, errorMsg, e);
        }
    }
	
	private void validate(TaskDef taskDef) {
        Preconditions.checkNotNull(taskDef, "TaskDef object cannot be null");
        Preconditions.checkNotNull(taskDef.getName(), "TaskDef name cannot be null");
    }
    
    private String insertOrUpdateTaskDef(TaskDef taskDef) {
        try {
            String taskDefinition = toJson(taskDef);
            
            Query searchQuery = new Query();
            searchQuery.addCriteria(Criteria.where("name").is(taskDef.getName()));
            
            if(mongoTemplate.exists(searchQuery, MetaTaskDefDocument.class)) {
            	Update update = new Update();
            	update.set("json_data", taskDefinition);
            	mongoTemplate.updateFirst(searchQuery, update, MetaTaskDefDocument.class);
            }
            else
            {
            	MetaTaskDefDocument taskDefDocument = new MetaTaskDefDocument();
            	taskDefDocument.setName(taskDef.getName());
                taskDefDocument.setJson_data(taskDefinition);
                taskDefDocument = mongoTemplate.save(taskDefDocument);
            }
            
            taskDefCache.put(taskDef.getName(), taskDef);
        } catch (Exception e) {
            Monitors.error(CLASS_NAME, "insertOrUpdateTaskDef");
            String errorMsg = String.format("Error creating/updating task definition: %s", taskDef.getName());
            LOGGER.error(errorMsg, e);
            throw new ApplicationException(ApplicationException.Code.BACKEND_ERROR, errorMsg, e);
        }
        
        return taskDef.getName();
    }

    private void refreshTaskDefsCache() {       
        try {
            Map<String, TaskDef> map = new HashMap<>();
            getAllTaskDefsFromDB().forEach(taskDef -> map.put(taskDef.getName(), taskDef));
            this.taskDefCache = map;
            LOGGER.debug("Refreshed task defs, total num: " + this.taskDefCache.size());
        } catch (Exception e) {
            Monitors.error(CLASS_NAME, "refreshTaskDefs");
            LOGGER.error("refresh TaskDefs failed ", e);
        }
    }

    private TaskDef getTaskDefFromDB(String name) {
        try {
        	Query searchQuery = new Query();
            searchQuery.addCriteria(Criteria.where("name").is(name));
        	
            recordMongoDaoRequests("getTaskDef");
            
            return !mongoTemplate.exists(searchQuery, MetaTaskDefDocument.class) ? null : readValue(mongoTemplate.findOne(searchQuery, MetaTaskDefDocument.class).getJson_data(), TaskDef.class);
        } catch (Exception e) {
            Monitors.error(CLASS_NAME, "getTaskDef");
            String errorMsg = String.format("Failed to get task def: %s", name);
            LOGGER.error(errorMsg, e);
            throw new ApplicationException(ApplicationException.Code.BACKEND_ERROR, errorMsg, e);
        }
    }
    
    @SuppressWarnings("unchecked")
	private List<TaskDef> getAllTaskDefsFromDB() {
    	recordMongoDaoRequests("getAllTaskDefsFromDB");
        try {
            List<MetaTaskDefDocument> metaTaskDefDocuments = mongoTemplate.findAll(MetaTaskDefDocument.class);
        	if (metaTaskDefDocuments.isEmpty()) {
                LOGGER.info("No task definitions were found.");
                return Collections.EMPTY_LIST;
            }
            return metaTaskDefDocuments.stream()
                .map(metaTaskDefDocument -> readValue(metaTaskDefDocument.getJson_data(), TaskDef.class))
                .collect(Collectors.toList());
        } catch (Exception e) {
            Monitors.error(CLASS_NAME, "getAllTaskDefs");
            String errorMsg = "Failed to get all task defs";
            LOGGER.error(errorMsg, e);
            throw new ApplicationException(ApplicationException.Code.BACKEND_ERROR, errorMsg, e);
        }
    }

    /**
     * Use {@link Preconditions} to check for required {@link WorkflowDef} fields, throwing a Runtime exception if
     * validations fail.
     *
     * @param def The {@code WorkflowDef} to check.
     */
    private void validate(WorkflowDef def) {
        Preconditions.checkNotNull(def, "WorkflowDef object cannot be null");
        Preconditions.checkNotNull(def.getName(), "WorkflowDef name cannot be null");
    }

    /**
     * Retrieve a {@link EventHandler} by {@literal name}.
     *
     * @param connection The {@link Connection} to use for queries.
     * @param name       The {@code EventHandler} name to look for.
     * @return {@literal null} if nothing is found, otherwise the {@code EventHandler}.
     */
    private EventHandler getEventHandler(String name) {
    	recordMongoDaoRequests("getEventHandler");
    	
    	Query searchQuery = new Query();
        searchQuery.addCriteria(Criteria.where("name").is(name));
        return mongoTemplate.exists(searchQuery, MetaEventHandlerDocument.class) ? readValue(mongoTemplate.findOne(searchQuery,MetaEventHandlerDocument.class).getJson_data(), EventHandler.class) : null;
    }

    /**
     * Check if a {@link WorkflowDef} with the same {@literal name} and {@literal version} already exist.
     *
     * @param connection The {@link Connection} to use for queries.
     * @param def        The {@code WorkflowDef} to check for.
     * @return {@literal true} if a {@code WorkflowDef} already exists with the same values.
     */
    private Boolean workflowExists(WorkflowDef def) {
    	recordMongoDaoRequests("workflowExists");
    	MetaWorkflowDefDocument aMetaWorkflowDefDocument = mongoTemplate.findOne(new Query().addCriteria(Criteria.where("name").is(def.getName()).and("version").is(def.getVersion())), MetaWorkflowDefDocument.class);
    	
    	return null!=aMetaWorkflowDefDocument;
    }

    /**
     * Return the latest version that exists for the provided {@code name}.
     *
     * @param tx   The {@link Connection} to use for queries.
     * @param name The {@code name} to check for.
     * @return {@code Optional.empty()} if no versions exist, otherwise the max {@link WorkflowDef#getVersion} found.
     */
    private Optional<Integer> getLatestVersion(String name) {
    	recordMongoDaoRequests("getLatestVersion");
    	MetaWorkflowDefDocument aMetaWorkflowDefDocument = mongoTemplate.findOne(new Query().addCriteria(Criteria.where("name").is(name)).with(Sort.by(Direction.DESC, "version")), MetaWorkflowDefDocument.class);
        return Optional.ofNullable(null!=aMetaWorkflowDefDocument ? aMetaWorkflowDefDocument.getVersion() : null);
    }

    /**
     * Update the latest version for the workflow with name {@code WorkflowDef} to the version provided in {@literal
     * version}.
     *
     * @param tx      The {@link Connection} to use for queries.
     * @param name    Workflow def name to update
     * @param version The new latest {@code version} value.
     */
    private void updateLatestVersion(String name, int version) {
    	recordMongoDaoRequests("updateLatestVersion");
    	Query query = new Query();
    	query.addCriteria(Criteria.where("name").is(name));
    	Update update = new Update();
    	update.set("latest_version", version);

        mongoTemplate.updateMulti(query, update, MetaWorkflowDefDocument.class);
    }

    private void insertOrUpdateWorkflowDef(WorkflowDef def) {
    	
    	recordMongoDaoRequests("insertOrUpdateWorkflowDef");
        
        Optional<Integer> version = getLatestVersion(def.getName());
        String workflowDefinition = toJson(def);
        
        if (!workflowExists(def)) {
        	MetaWorkflowDefDocument metaWorkFlowDefDocument = new MetaWorkflowDefDocument();
            
            metaWorkFlowDefDocument.setJson_data(workflowDefinition);
            metaWorkFlowDefDocument.setName(def.getName());
            metaWorkFlowDefDocument.setVersion(def.getVersion());
            
            mongoTemplate.save(metaWorkFlowDefDocument);
            
        } else {
        	
        	Query updateQuery = new Query();
        	updateQuery.addCriteria(Criteria.where("name").is(def.getName()));
        	updateQuery.addCriteria(Criteria.where("version").is(def.getVersion()));
        	Update update = new Update();
        	update.set("json_data", workflowDefinition);
        	
        	mongoTemplate.updateFirst(updateQuery, update, MetaWorkflowDefDocument.class);
            
        }
        int maxVersion = def.getVersion();
        if (version.isPresent() && version.get() > def.getVersion()) {
            maxVersion = version.get();
        }

        updateLatestVersion(def.getName(), maxVersion);
    }
    
    public List<String> findAll() {
    	return mongoTemplate.findDistinct("name", MetaWorkflowDefDocument.class, String.class);
    }
    
    public List<WorkflowDef> getAllLatest() {
    	List<WorkflowDef> result = new ArrayList<WorkflowDef>();
    	
        Query query = new Query();
//        query.withHint("{ version : { $eq: $latest_version } }");
        query.with(Sort.by(Direction.DESC, "version")).limit(1);
        
        mongoTemplate.find(query, MetaWorkflowDefDocument.class).forEach(wdd -> {
        	result.add(readValue(wdd.getJson_data(), WorkflowDef.class));
        });;

        return result;
    }

    public List<WorkflowDef> getAllVersions(String name) {
    	List<WorkflowDef> result = new ArrayList<WorkflowDef>();
    	
        Query query = new Query();
        query.addCriteria(Criteria.where("name").is(name));
    	query.with(Sort.by(Direction.ASC, "version"));
    	
        mongoTemplate.find(query, MetaWorkflowDefDocument.class).forEach(wdd -> {
        	result.add(readValue(wdd.getJson_data(), WorkflowDef.class));
        });;

        return result;
    }
    
    @VisibleForTesting
    String getWorkflowDefIndexValue(String name, int version) {
        return name + INDEX_DELIMITER + version;
    }
}
