/*
 * Copyright (c) 2018, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 * WSO2 Inc. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.wso2.extension.siddhi.store.elasticsearch;

import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.nio.reactor.IOReactorConfig;
import org.apache.log4j.Logger;
import org.elasticsearch.ElasticsearchStatusException;
import org.elasticsearch.action.admin.indices.alias.Alias;
import org.elasticsearch.action.admin.indices.create.CreateIndexRequest;
import org.elasticsearch.action.bulk.BackoffPolicy;
import org.elasticsearch.action.bulk.BulkProcessor;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.bulk.BulkResponse;
import org.elasticsearch.action.delete.DeleteRequest;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.update.UpdateRequest;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.unit.ByteSizeUnit;
import org.elasticsearch.common.unit.ByteSizeValue;
import org.elasticsearch.common.unit.TimeValue;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.XContentFactory;
import org.wso2.extension.siddhi.store.elasticsearch.exceptions.ElasticsearchEventTableException;
import org.wso2.extension.siddhi.store.elasticsearch.exceptions.ElasticsearchServiceException;
import org.wso2.extension.siddhi.store.elasticsearch.utils.ElasticsearchTableUtils;
import org.wso2.siddhi.annotation.Example;
import org.wso2.siddhi.annotation.Extension;
import org.wso2.siddhi.annotation.Parameter;
import org.wso2.siddhi.annotation.util.DataType;
import org.wso2.siddhi.core.exception.ConnectionUnavailableException;
import org.wso2.siddhi.core.table.record.AbstractRecordTable;
import org.wso2.siddhi.core.table.record.ExpressionBuilder;
import org.wso2.siddhi.core.table.record.RecordIterator;
import org.wso2.siddhi.core.util.SiddhiConstants;
import org.wso2.siddhi.core.util.collection.operator.CompiledCondition;
import org.wso2.siddhi.core.util.collection.operator.CompiledExpression;
import org.wso2.siddhi.core.util.config.ConfigReader;
import org.wso2.siddhi.query.api.annotation.Annotation;
import org.wso2.siddhi.query.api.annotation.Element;
import org.wso2.siddhi.query.api.definition.Attribute;
import org.wso2.siddhi.query.api.definition.TableDefinition;
import org.wso2.siddhi.query.api.util.AnnotationHelper;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.wso2.extension.siddhi.store.elasticsearch.utils.ElasticsearchTableConstants.
        ANNOTATION_ELEMENT_BACKOFF_POLICY_RETRY_NO;
import static org.wso2.extension.siddhi.store.elasticsearch.utils.ElasticsearchTableConstants.
        ANNOTATION_ELEMENT_BACKOFF_POLICY_WAIT_TIME;
import static org.wso2.extension.siddhi.store.elasticsearch.utils.ElasticsearchTableConstants.
        ANNOTATION_ELEMENT_BULK_ACTIONS;
import static org.wso2.extension.siddhi.store.elasticsearch.utils.ElasticsearchTableConstants.
        ANNOTATION_ELEMENT_BULK_SIZE;
import static org.wso2.extension.siddhi.store.elasticsearch.utils.ElasticsearchTableConstants.
        ANNOTATION_ELEMENT_CLIENT_IO_THREAD_COUNT;
import static org.wso2.extension.siddhi.store.elasticsearch.utils.ElasticsearchTableConstants.
        ANNOTATION_ELEMENT_CONCURRENT_REQUESTS;
import static org.wso2.extension.siddhi.store.elasticsearch.utils.ElasticsearchTableConstants.
        ANNOTATION_ELEMENT_FLUSH_INTERVAL;
import static org.wso2.extension.siddhi.store.elasticsearch.utils.ElasticsearchTableConstants.
        ANNOTATION_ELEMENT_HOSTNAME;
import static org.wso2.extension.siddhi.store.elasticsearch.utils.ElasticsearchTableConstants.
        ANNOTATION_ELEMENT_INDEX_ALIAS;
import static org.wso2.extension.siddhi.store.elasticsearch.utils.ElasticsearchTableConstants.
        ANNOTATION_ELEMENT_INDEX_NAME;
import static org.wso2.extension.siddhi.store.elasticsearch.utils.ElasticsearchTableConstants.
        ANNOTATION_ELEMENT_INDEX_NUMBER_OF_REPLICAS;
import static org.wso2.extension.siddhi.store.elasticsearch.utils.ElasticsearchTableConstants.
        ANNOTATION_ELEMENT_INDEX_NUMBER_OF_SHARDS;
import static org.wso2.extension.siddhi.store.elasticsearch.utils.ElasticsearchTableConstants.
        ANNOTATION_ELEMENT_PASSWORD;
import static org.wso2.extension.siddhi.store.elasticsearch.utils.ElasticsearchTableConstants.
        ANNOTATION_ELEMENT_PORT;
import static org.wso2.extension.siddhi.store.elasticsearch.utils.ElasticsearchTableConstants.
        ANNOTATION_ELEMENT_SCHEME;
import static org.wso2.extension.siddhi.store.elasticsearch.utils.ElasticsearchTableConstants.ANNOTATION_ELEMENT_USER;
import static org.wso2.extension.siddhi.store.elasticsearch.utils.ElasticsearchTableConstants.
        DEFAULT_BACKOFF_POLICY_RETRY_NO;
import static org.wso2.extension.siddhi.store.elasticsearch.utils.ElasticsearchTableConstants.
        DEFAULT_BACKOFF_POLICY_WAIT_TIME;
import static org.wso2.extension.siddhi.store.elasticsearch.utils.ElasticsearchTableConstants.DEFAULT_BULK_ACTIONS;
import static org.wso2.extension.siddhi.store.elasticsearch.utils.ElasticsearchTableConstants.DEFAULT_BULK_SIZE_IN_MB;
import static org.wso2.extension.siddhi.store.elasticsearch.utils.ElasticsearchTableConstants.
        DEFAULT_CONCURRENT_REQUESTS;
import static org.wso2.extension.siddhi.store.elasticsearch.utils.ElasticsearchTableConstants.DEFAULT_FLUSH_INTERVAL;
import static org.wso2.extension.siddhi.store.elasticsearch.utils.ElasticsearchTableConstants.DEFAULT_HOSTNAME;
import static org.wso2.extension.siddhi.store.elasticsearch.utils.ElasticsearchTableConstants.DEFAULT_IO_THREAD_COUNT;
import static org.wso2.extension.siddhi.store.elasticsearch.utils.ElasticsearchTableConstants.
        DEFAULT_NUMBER_OF_REPLICAS;
import static org.wso2.extension.siddhi.store.elasticsearch.utils.ElasticsearchTableConstants.DEFAULT_NUMBER_OF_SHARDS;
import static org.wso2.extension.siddhi.store.elasticsearch.utils.ElasticsearchTableConstants.DEFAULT_PASSWORD;
import static org.wso2.extension.siddhi.store.elasticsearch.utils.ElasticsearchTableConstants.DEFAULT_PORT;
import static org.wso2.extension.siddhi.store.elasticsearch.utils.ElasticsearchTableConstants.DEFAULT_SCHEME;
import static org.wso2.extension.siddhi.store.elasticsearch.utils.ElasticsearchTableConstants.DEFAULT_USER_NAME;
import static org.wso2.extension.siddhi.store.elasticsearch.utils.ElasticsearchTableConstants.ELASTICSEARCH_INDEX_TYPE;
import static org.wso2.extension.siddhi.store.elasticsearch.utils.ElasticsearchTableConstants.
        MAPPING_PROPERTIES_ELEMENT;
import static org.wso2.extension.siddhi.store.elasticsearch.utils.ElasticsearchTableConstants.MAPPING_TYPE_ELEMENT;
import static org.wso2.extension.siddhi.store.elasticsearch.utils.ElasticsearchTableConstants.
        SETTING_INDEX_NUMBER_OF_REPLICAS;
import static org.wso2.extension.siddhi.store.elasticsearch.utils.ElasticsearchTableConstants.
        SETTING_INDEX_NUMBER_OF_SHARDS;

/**
 * This class contains the Event table implementation for Elasticsearch indexing document as underlying data storage.
 */
@Extension(
        name = "elasticsearch",
        namespace = "store",
        description = "Elasticsearch store implementation uses Elasticsearch indexing document for underlying " +
                "data storage. The events are converted to Elasticsearch index documents when the events are " +
                "inserted into the elasticsearch store. Elasticsearch indexing documents are converted to events when" +
                " the documents are read from Elasticsearch indexes. The internal store is connected to the " +
                "Elastisearch server via the Elasticsearch Java High Level REST Client library.",
        parameters = {
                @Parameter(name = "hostname",
                        description = "The hostname of the Elasticsearch server.",
                        type = {DataType.STRING}, optional = true, defaultValue = "localhost"),
                @Parameter(name = "port",
                        description = "The port of the Elasticsearch server.",
                        type = {DataType.INT}, optional = true, defaultValue = "9200"),
                @Parameter(name = "scheme",
                        description = "The scheme type of the Elasticsearch server connection.",
                        type = {DataType.STRING}, optional = true, defaultValue = "http"),
                @Parameter(name = "username",
                        description = "The username for the Elasticsearch server connection.",
                        type = {DataType.STRING}, optional = true, defaultValue = "elastic"),
                @Parameter(name = "password",
                        description = "The password for the Elasticsearch server connection.",
                        type = {DataType.STRING}, optional = true, defaultValue = "changeme"),
                @Parameter(name = "index.name",
                        description = "The name of the Elasticsearch index.",
                        type = {DataType.STRING}, optional = true,
                        defaultValue = "The table name defined in the Siddhi App query."),
                @Parameter(name = "index.alias",
                        description = "The alias of the Elasticsearch index.",
                        type = {DataType.STRING}, optional = true,
                        defaultValue = "null"),
                @Parameter(name = "index.number.of.shards",
                        description = "The number of shards allocated for the index in the Elasticsearch server.",
                        type = {DataType.INT}, optional = true, defaultValue = "3"),
                @Parameter(name = "index.number.of.replicas",
                        description = "The number of replicas for the index in the Elasticsearch server.",
                        type = {DataType.INT}, optional = true, defaultValue = "2"),
                @Parameter(name = "bulk.actions",
                        description = "The number of actions to be added to flush a new bulk request. " +
                                "Use -1 to disable it",
                        type = {DataType.INT}, optional = true, defaultValue = "1"),
                @Parameter(name = "bulk.size",
                        description = "The size of size of actions currently added to the bulk request to flush a " +
                                "new bulk request in MB. Use -1 to disable it",
                        type = {DataType.LONG}, optional = true, defaultValue = "1"),
                @Parameter(name = "concurrent.requests",
                        description = "The number of concurrent requests allowed to be executed. Use 0 to only allow " +
                                "the execution of a single request",
                        type = {DataType.INT}, optional = true, defaultValue = "0"),
                @Parameter(name = "flush.interval",
                        description = "The flush interval flushing any BulkRequest pending if the interval passes.",
                        type = {DataType.LONG}, optional = true, defaultValue = "10"),
                @Parameter(name = "backoff.policy.retry.no",
                        description = "The number of retries until backoff " +
                                "(The backoff policy defines how the bulk processor should handle retries of bulk " +
                                "requests internally in case they have failed due to resource constraints " +
                                "(i.e. a thread pool was full)).",
                        type = {DataType.INT}, optional = true, defaultValue = "3"),
                @Parameter(name = "backoff.policy.wait.time",
                        description = "The constant back off policy that initially waits until the next retry " +
                                "in seconds.",
                        type = {DataType.LONG}, optional = true, defaultValue = "1"),
        },

        examples = {
                @Example(
                        syntax = "@Store(type=\"elasticsearch\", host=\"localhost\", " +
                                "username=\"elastic\", password=\"changeme\", index.name=\"MyStockTable\", " +
                                "field.length=\"symbol:100\", bulk.actions=\"5000\", bulk.size=\"1\", " +
                                "concurrent.requests=\"2\", flush.interval=\"1\", backoff.policy.retry.no=\"3\", " +
                                "backoff.policy.wait.time=\"1\")\n" +
                                "@PrimaryKey(\"symbol\")" +
                                "define table StockTable (symbol string, price float, volume long);",
                        description = "This example creates an index named 'MyStockTable' in the Elasticsearch " +
                                "server if it does not already exist (with three attributes named 'symbol', 'price'," +
                                " and 'volume' of the types 'string', 'float' and 'long' respectively). " +
                                "The connection is made as specified by the parameters configured for the '@Store' " +
                                "annotation. The 'symbol' attribute is considered a unique field and an Elasticsearch" +
                                " index document ID is generated for it."
                )
        }
)

// for more information refer https://wso2.github.io/siddhi/documentation/siddhi-4.0/#event-table-types

public class ElasticsearchEventTable extends AbstractRecordTable {

    private static final Logger logger = Logger.getLogger(ElasticsearchEventTable.class);
    private RestHighLevelClient restHighLevelClient;
    private List<Attribute> attributes;
    private List<String> primaryKeys;
    private String hostname = DEFAULT_HOSTNAME;
    private String indexName;
    private String indexAlias;
    private int port = DEFAULT_PORT;
    private String scheme = DEFAULT_SCHEME;
    private String userName = DEFAULT_USER_NAME;
    private String password = DEFAULT_PASSWORD;
    private int numberOfShards = DEFAULT_NUMBER_OF_SHARDS;
    private int numberOfReplicas = DEFAULT_NUMBER_OF_REPLICAS;
    private BulkProcessor bulkProcessor;
    private int bulkActions = DEFAULT_BULK_ACTIONS;
    private long bulkSize = DEFAULT_BULK_SIZE_IN_MB;
    private int concurrentRequests = DEFAULT_CONCURRENT_REQUESTS;
    private long flushInterval = DEFAULT_FLUSH_INTERVAL;
    private int backoffPolicyRetryNo = DEFAULT_BACKOFF_POLICY_RETRY_NO;
    private long backoffPolicyWaitTime = DEFAULT_BACKOFF_POLICY_WAIT_TIME;
    private int ioThreadCount = DEFAULT_IO_THREAD_COUNT;


    /**
     * Initializing the Record Table
     *
     * @param tableDefinition definintion of the table with annotations if any
     * @param configReader    this hold the {@link AbstractRecordTable} configuration reader.
     */
    @Override
    protected void init(TableDefinition tableDefinition, ConfigReader configReader) {
        this.attributes = tableDefinition.getAttributeList();
        Annotation storeAnnotation = AnnotationHelper.getAnnotation(SiddhiConstants.ANNOTATION_STORE, tableDefinition
                .getAnnotations());
        Annotation primaryKeyAnnotation = AnnotationHelper.getAnnotation(SiddhiConstants.ANNOTATION_PRIMARY_KEY,
                tableDefinition.getAnnotations());
        if (primaryKeyAnnotation != null) {
            this.primaryKeys = new ArrayList<>();
            List<Element> primaryKeyElements = primaryKeyAnnotation.getElements();
            primaryKeyElements.forEach(element -> {
                this.primaryKeys.add(element.getValue().trim());
            });
        }
        if (storeAnnotation != null) {
            indexName = storeAnnotation.getElement(ANNOTATION_ELEMENT_INDEX_NAME);
            indexName = ElasticsearchTableUtils.isEmpty(indexName) ? tableDefinition.getId() : indexName;
            if (!ElasticsearchTableUtils.isEmpty(storeAnnotation.getElement(ANNOTATION_ELEMENT_HOSTNAME))) {
                hostname = storeAnnotation.getElement(ANNOTATION_ELEMENT_HOSTNAME);
            } else {
                hostname = configReader.readConfig(ANNOTATION_ELEMENT_HOSTNAME, hostname);
            }
            if (!ElasticsearchTableUtils.isEmpty(storeAnnotation.getElement(ANNOTATION_ELEMENT_PORT))) {
                port = Integer.parseInt(storeAnnotation.getElement(ANNOTATION_ELEMENT_PORT));
            } else {
                port = Integer.parseInt(configReader.readConfig(ANNOTATION_ELEMENT_HOSTNAME, String.valueOf(port)));
            }
            if (!ElasticsearchTableUtils.isEmpty(storeAnnotation.
                    getElement(ANNOTATION_ELEMENT_INDEX_NUMBER_OF_SHARDS))) {
                numberOfShards = Integer.parseInt(storeAnnotation.getElement(
                        ANNOTATION_ELEMENT_INDEX_NUMBER_OF_SHARDS));
            } else {
                numberOfShards = Integer.parseInt(configReader.readConfig(ANNOTATION_ELEMENT_INDEX_NUMBER_OF_SHARDS,
                        String.valueOf(numberOfShards)));
            }
            if (!ElasticsearchTableUtils.isEmpty(storeAnnotation.
                    getElement(ANNOTATION_ELEMENT_INDEX_NUMBER_OF_REPLICAS))) {
                numberOfReplicas = Integer.parseInt(storeAnnotation.getElement(
                        ANNOTATION_ELEMENT_INDEX_NUMBER_OF_REPLICAS));
            } else {
                numberOfReplicas = Integer.parseInt(configReader.readConfig(ANNOTATION_ELEMENT_INDEX_NUMBER_OF_REPLICAS,
                        String.valueOf(numberOfReplicas)));
            }
            if (!ElasticsearchTableUtils.isEmpty(storeAnnotation.getElement(ANNOTATION_ELEMENT_SCHEME))) {
                scheme = storeAnnotation.getElement(ANNOTATION_ELEMENT_SCHEME);
            } else {
                scheme = configReader.readConfig(ANNOTATION_ELEMENT_SCHEME, scheme);
            }
            if (!ElasticsearchTableUtils.isEmpty(storeAnnotation.getElement(ANNOTATION_ELEMENT_USER))) {
                userName = storeAnnotation.getElement(ANNOTATION_ELEMENT_USER);
            } else {
                userName = configReader.readConfig(ANNOTATION_ELEMENT_USER, userName);
            }
            if (!ElasticsearchTableUtils.isEmpty(storeAnnotation.getElement(ANNOTATION_ELEMENT_PASSWORD))) {
                password = storeAnnotation.getElement(ANNOTATION_ELEMENT_PASSWORD);
            } else {
                password = configReader.readConfig(ANNOTATION_ELEMENT_PASSWORD, password);
            }
            if (!ElasticsearchTableUtils.isEmpty(storeAnnotation.getElement(ANNOTATION_ELEMENT_INDEX_ALIAS))) {
                indexAlias = storeAnnotation.getElement(ANNOTATION_ELEMENT_INDEX_ALIAS);
            }
            if (!ElasticsearchTableUtils.isEmpty(storeAnnotation.getElement(ANNOTATION_ELEMENT_BULK_ACTIONS))) {
                bulkActions = Integer.parseInt(storeAnnotation.getElement(ANNOTATION_ELEMENT_BULK_ACTIONS));
            } else {
                bulkActions = Integer.parseInt(configReader.readConfig(ANNOTATION_ELEMENT_BULK_ACTIONS,
                        String.valueOf(bulkActions)));
            }
            if (!ElasticsearchTableUtils.isEmpty(storeAnnotation.getElement(ANNOTATION_ELEMENT_BULK_SIZE))) {
                bulkSize = Long.parseLong(storeAnnotation.getElement(ANNOTATION_ELEMENT_BULK_SIZE));
            } else {
                bulkSize = Long.parseLong(configReader.readConfig(ANNOTATION_ELEMENT_BULK_SIZE,
                        String.valueOf(bulkSize)));
            }
            if (!ElasticsearchTableUtils.isEmpty(storeAnnotation.getElement(ANNOTATION_ELEMENT_CONCURRENT_REQUESTS))) {
                concurrentRequests = Integer.parseInt(
                        storeAnnotation.getElement(ANNOTATION_ELEMENT_CONCURRENT_REQUESTS));
            } else {
                concurrentRequests = Integer.parseInt(configReader.readConfig(ANNOTATION_ELEMENT_CONCURRENT_REQUESTS,
                        String.valueOf(concurrentRequests)));
            }
            if (!ElasticsearchTableUtils.isEmpty(storeAnnotation.getElement(ANNOTATION_ELEMENT_FLUSH_INTERVAL))) {
                flushInterval = Long.parseLong(
                        storeAnnotation.getElement(ANNOTATION_ELEMENT_FLUSH_INTERVAL));
            } else {
                flushInterval = Long.parseLong(configReader.readConfig(ANNOTATION_ELEMENT_FLUSH_INTERVAL,
                        String.valueOf(flushInterval)));
            }
            if (!ElasticsearchTableUtils.isEmpty(storeAnnotation.getElement(
                    ANNOTATION_ELEMENT_BACKOFF_POLICY_RETRY_NO))) {
                backoffPolicyRetryNo = Integer.parseInt(
                        storeAnnotation.getElement(ANNOTATION_ELEMENT_BACKOFF_POLICY_RETRY_NO));
            } else {
                backoffPolicyRetryNo = Integer.parseInt(
                        configReader.readConfig(ANNOTATION_ELEMENT_BACKOFF_POLICY_RETRY_NO,
                                String.valueOf(backoffPolicyRetryNo)));
            }
            if (!ElasticsearchTableUtils.isEmpty(storeAnnotation.getElement(
                    ANNOTATION_ELEMENT_BACKOFF_POLICY_WAIT_TIME))) {
                backoffPolicyWaitTime = Long.parseLong(
                        storeAnnotation.getElement(ANNOTATION_ELEMENT_BACKOFF_POLICY_WAIT_TIME));
            } else {
                backoffPolicyWaitTime = Long.parseLong(
                        configReader.readConfig(ANNOTATION_ELEMENT_BACKOFF_POLICY_WAIT_TIME,
                                String.valueOf(backoffPolicyWaitTime)));
            }
            if (!ElasticsearchTableUtils.isEmpty(storeAnnotation.getElement(
                    ANNOTATION_ELEMENT_CLIENT_IO_THREAD_COUNT))) {
                ioThreadCount = Integer.parseInt(
                        storeAnnotation.getElement(ANNOTATION_ELEMENT_CLIENT_IO_THREAD_COUNT));
            } else {
                ioThreadCount = Integer.parseInt(
                        configReader.readConfig(ANNOTATION_ELEMENT_CLIENT_IO_THREAD_COUNT,
                                String.valueOf(ioThreadCount)));
            }
        } else {
            throw new ElasticsearchEventTableException("Elasticsearch Store annotation list null for table id : '" +
                    tableDefinition.getId() + "', required properties cannot be resolved.");
        }
        CredentialsProvider credentialsProvider = new BasicCredentialsProvider();
        credentialsProvider.setCredentials(AuthScope.ANY,
                new UsernamePasswordCredentials(userName, password));
        restHighLevelClient = new RestHighLevelClient(RestClient.builder(new HttpHost(hostname, port, scheme)).
                setHttpClientConfigCallback(httpClientBuilder -> {
                    httpClientBuilder.disableAuthCaching();
                    httpClientBuilder.setDefaultIOReactorConfig(IOReactorConfig.custom().
                            setIoThreadCount(ioThreadCount).build());
                    return httpClientBuilder.setDefaultCredentialsProvider(credentialsProvider);
                }));
        BulkProcessor.Builder bulkProcessorBuilder = BulkProcessor.builder(restHighLevelClient::bulkAsync,
                new BulkProcessorListener());
        bulkProcessorBuilder.setBulkActions(bulkActions);
        bulkProcessorBuilder.setBulkSize(new ByteSizeValue(bulkSize, ByteSizeUnit.MB));
        bulkProcessorBuilder.setConcurrentRequests(concurrentRequests);
        bulkProcessorBuilder.setFlushInterval(TimeValue.timeValueSeconds(flushInterval));
        bulkProcessorBuilder.setBackoffPolicy(BackoffPolicy.constantBackoff(
                TimeValue.timeValueSeconds(backoffPolicyWaitTime), backoffPolicyRetryNo));
        bulkProcessor = bulkProcessorBuilder.build();
        CreateIndexRequest request = new CreateIndexRequest(indexName);
        request.settings(Settings.builder()
                .put(SETTING_INDEX_NUMBER_OF_SHARDS, numberOfShards)
                .put(SETTING_INDEX_NUMBER_OF_REPLICAS, numberOfReplicas)
        );
        try {
            XContentBuilder builder = XContentFactory.jsonBuilder();
            builder.startObject();
            {
                builder.startObject(ELASTICSEARCH_INDEX_TYPE);
                {
                    builder.startObject(MAPPING_PROPERTIES_ELEMENT);
                    {
                        for (Attribute attribute : attributes) {
                            builder.startObject(attribute.getName());
                            {
                                if (attribute.getType().equals(Attribute.Type.STRING)) {
                                    builder.field(MAPPING_TYPE_ELEMENT, "text");
                                    builder.startObject("fields");
                                    {
                                        builder.startObject("keyword");
                                        {
                                            builder.field("type", "keyword");
                                            builder.field("ignore_above", 256);
                                        }
                                        builder.endObject();
                                    }
                                    builder.endObject();
                                } else if (attribute.getType().equals(Attribute.Type.INT)) {
                                    builder.field(MAPPING_TYPE_ELEMENT, "integer");
                                } else if (attribute.getType().equals(Attribute.Type.LONG)) {
                                    builder.field(MAPPING_TYPE_ELEMENT, "long");
                                } else if (attribute.getType().equals(Attribute.Type.FLOAT)) {
                                    builder.field(MAPPING_TYPE_ELEMENT, "float");
                                } else if (attribute.getType().equals(Attribute.Type.DOUBLE)) {
                                    builder.field(MAPPING_TYPE_ELEMENT, "double");
                                } else if (attribute.getType().equals(Attribute.Type.BOOL)) {
                                    builder.field(MAPPING_TYPE_ELEMENT, "boolean");
                                } else {
                                    builder.field(MAPPING_TYPE_ELEMENT, "object");
                                }
                            }
                            builder.endObject();
                        }
                    }
                    builder.endObject();
                }
                builder.endObject();
            }
            builder.endObject();
            request.mapping(indexName, builder);
        } catch (IOException e) {
            throw new ElasticsearchEventTableException("Error while generating mapping for table id : '" +
                    tableDefinition.getId(), e);
        }
        if (indexAlias != null) {
            request.alias(new Alias(indexAlias));
        }
        try {
            restHighLevelClient.indices().create(request);
            logger.debug("A table id: " + tableDefinition.getId() + " is created with the provided information.");
        } catch (IOException e) {
            throw new ElasticsearchEventTableException("Error while creating indices for table id : '" +
                    tableDefinition.getId(), e);
        } catch (ElasticsearchStatusException e) {
            logger.debug("Elasticsearch status exception occurs while creating index for table id: " +
                    tableDefinition.getId(), e);
        }
    }

    static class BulkProcessorListener implements BulkProcessor.Listener {
        @Override
        public void beforeBulk(long executionId, BulkRequest request) {
            int numberOfActions = request.numberOfActions();
            logger.debug("Executing bulk [{" + executionId + "}] with {" + numberOfActions + "} requests");
        }

        @Override
        public void afterBulk(long executionId, BulkRequest request, BulkResponse response) {
            if (response.hasFailures()) {
                logger.warn("Bulk [{}] executed with failures for executionId: " + executionId);
            } else {
                logger.debug("Bulk [{" + executionId + "}] completed in {" + response.getTook().getMillis() +
                        "} milliseconds");
            }
        }

        @Override
        public void afterBulk(long executionId, BulkRequest request, Throwable failure) {
            logger.error("Failed to execute bulk", failure);
        }
    }

    /**
     * Add records to the Table
     *
     * @param records records that need to be added to the table, each Object[] represent a record and it will match
     *                the attributes of the Table Definition.
     */
    @Override
    protected void add(List<Object[]> records) throws ConnectionUnavailableException {
        for (Object[] record : records) {
            IndexRequest indexRequest;
            if (primaryKeys != null && !primaryKeys.isEmpty()) {
                String docId = ElasticsearchTableUtils.generateRecordIdFromPrimaryKeyValues(attributes, record,
                        primaryKeys);
                indexRequest = new IndexRequest(indexName, ELASTICSEARCH_INDEX_TYPE, docId);
            } else {
                //record id will be generated by the Elasticsearch
                indexRequest = new IndexRequest(indexName, ELASTICSEARCH_INDEX_TYPE);
            }
            try {
                XContentBuilder builder = XContentFactory.jsonBuilder();
                builder.startObject();
                for (int i = 0; i < record.length; i++) {
                    builder.field(attributes.get(i).getName(), record[i]);
                }
                builder.endObject();
                indexRequest.source(builder);
                bulkProcessor.add(indexRequest);
            } catch (IOException e) {
                throw new ElasticsearchEventTableException("Error while generating content mapping for records : '" +
                        records.toString() + "' in table id: " + tableDefinition.getId(), e);
            }
        }
    }

    /**
     * Find records matching the compiled condition
     *
     * @param findConditionParameterMap map of matching StreamVariable Ids and their values corresponding to the
     *                                  compiled condition
     * @param compiledCondition         the compiledCondition against which records should be matched
     * @return RecordIterator of matching records
     */
    @Override
    protected RecordIterator<Object[]> find(Map<String, Object> findConditionParameterMap,
                                            CompiledCondition compiledCondition) throws ConnectionUnavailableException {
        try {
            return findRecords(findConditionParameterMap, compiledCondition);
        } catch (ElasticsearchServiceException e) {
            throw new ConnectionUnavailableException("Error while performing the find operation " + e.getMessage(),
                    e);
        }
    }

    private ElasticsearchRecordIterator findRecords(Map<String, Object> findConditionParameterMap, CompiledCondition
            compiledCondition) throws ElasticsearchServiceException {
        String condition = ElasticsearchTableUtils.resolveCondition((ElasticsearchCompiledCondition) compiledCondition,
                findConditionParameterMap);
        return new ElasticsearchRecordIterator(indexName, condition, restHighLevelClient, attributes);
    }

    /**
     * Check if matching record exist or not
     *
     * @param containsConditionParameterMap map of matching StreamVariable Ids and their values corresponding to the
     *                                      compiled condition
     * @param compiledCondition             the compiledCondition against which records should be matched
     * @return if matching record found or not
     */
    @Override
    protected boolean contains(Map<String, Object> containsConditionParameterMap,
                               CompiledCondition compiledCondition) throws ConnectionUnavailableException {
        try {
            ElasticsearchRecordIterator elasticsearchRecordIterator = findRecords(containsConditionParameterMap,
                    compiledCondition);
            return elasticsearchRecordIterator.hasNext();
        } catch (ElasticsearchServiceException e) {
            throw new ElasticsearchEventTableException("Error while checking content mapping for '" +
                    "' table id: " + tableDefinition.getId(), e);
        }
    }

    /**
     * Delete all matching records
     *
     * @param deleteConditionParameterMaps map of matching StreamVariable Ids and their values corresponding to the
     *                                     compiled condition
     * @param compiledCondition            the compiledCondition against which records should be matched for deletion
     * @throws ConnectionUnavailableException if end point is unavailable the ConnectionUnavailableException thrown
     *                                        such that the  system will take care retrying for connection
     **/
    @Override
    protected void delete(List<Map<String, Object>> deleteConditionParameterMaps, CompiledCondition compiledCondition)
            throws ConnectionUnavailableException {
        String docId = null;
        try {
            for (Map<String, Object> record : deleteConditionParameterMaps) {
                if (primaryKeys != null && !primaryKeys.isEmpty()) {
                    docId = ElasticsearchTableUtils.generateRecordIdFromPrimaryKeyValues(attributes, record,
                            primaryKeys);
                }
                DeleteRequest deleteRequest = new DeleteRequest(indexName, ELASTICSEARCH_INDEX_TYPE,
                        docId != null ? docId : "1");
                bulkProcessor.add(deleteRequest);
            }
        } catch (Throwable throwable) {
            throw new ElasticsearchEventTableException("Error while deleting content mapping for records id: '" + docId
                    + "' in table id: " + tableDefinition.getId(), throwable);
        }
    }

    /**
     * Update all matching records
     *
     * @param compiledCondition the compiledCondition against which records should be matched for update
     * @param list              map of matching StreamVariable Ids and their values corresponding to the
     *                          compiled condition based on which the records will be updated
     * @param map               the attributes and values that should be updated if the condition matches
     * @param list1             the attributes and values that should be updated for the matching records
     * @throws ConnectionUnavailableException if end point is unavailable the ConnectionUnavailableException thrown
     *                                        such that the  system will take care retrying for connection
     */
    @Override
    protected void update(CompiledCondition compiledCondition, List<Map<String, Object>> list,
                          Map<String, CompiledExpression> map, List<Map<String, Object>> list1)
            throws ConnectionUnavailableException {
        String docId = null;
        try {
            for (Map<String, Object> record : list1) {
                if (primaryKeys != null && !primaryKeys.isEmpty()) {
                    docId = ElasticsearchTableUtils.generateRecordIdFromPrimaryKeyValues(attributes, record,
                            primaryKeys);
                }
                XContentBuilder builder = XContentFactory.jsonBuilder();
                builder.startObject();
                for (int i = 0; i < attributes.size(); i++) {
                    builder.field(attributes.get(i).getName(), record.get(attributes.get(i).getName()));
                }
                builder.endObject();
                UpdateRequest updateRequest = new UpdateRequest(indexName, ELASTICSEARCH_INDEX_TYPE,
                        docId != null ? docId : "1").doc(builder);
                bulkProcessor.add(updateRequest);
            }
        } catch (Throwable throwable) {
            throw new ElasticsearchEventTableException("Error while updating content mapping for records id: '" + docId
                    + "' in table id: " + tableDefinition.getId(), throwable);
        }
    }

    /**
     * Try updating the records if they exist else add the records
     *
     * @param list              map of matching StreamVariable Ids and their values corresponding to the
     *                          compiled condition based on which the records will be updated
     * @param compiledCondition the compiledCondition against which records should be matched for update
     * @param map               the attributes and values that should be updated if the condition matches
     * @param list1             the values for adding new records if the update condition did not match
     * @param list2             the attributes and values that should be updated for the matching records
     * @throws ConnectionUnavailableException if end point is unavailable the ConnectionUnavailableException thrown
     *                                        such that the  system will take care retrying for connection
     */
    @Override
    protected void updateOrAdd(CompiledCondition compiledCondition, List<Map<String, Object>> list,
                               Map<String, CompiledExpression> map, List<Map<String, Object>> list1,
                               List<Object[]> list2) throws ConnectionUnavailableException {
        try {
            for (Object[] record : list2) {
                String docId = null;
                if (primaryKeys != null && !primaryKeys.isEmpty()) {
                    docId = ElasticsearchTableUtils.generateRecordIdFromPrimaryKeyValues(attributes, record,
                            primaryKeys);
                }
                XContentBuilder builder = XContentFactory.jsonBuilder();
                builder.startObject();
                for (int i = 0; i < attributes.size(); i++) {
                    builder.field(attributes.get(i).getName(), record[i]);
                }
                builder.endObject();
                UpdateRequest updateRequest = new UpdateRequest(indexName, ELASTICSEARCH_INDEX_TYPE,
                        docId != null ? docId : "1").doc(builder);
                bulkProcessor.add(updateRequest);
            }
        } catch (Throwable throwable) {
            this.add(list2);
        }
    }

    /**
     * Compile the matching condition
     *
     * @param expressionBuilder that helps visiting the conditions in order to compile the condition
     * @return compiled condition that can be used for matching events in find, contains, delete, update and
     * updateOrAdd
     */
    @Override
    protected CompiledCondition compileCondition(ExpressionBuilder expressionBuilder) {
        ElasticsearchConditionVisitor visitor = new ElasticsearchConditionVisitor();
        expressionBuilder.build(visitor);
        return new ElasticsearchCompiledCondition(visitor.returnCondition());
    }

    /**
     * Compile the matching condition
     *
     * @param expressionBuilder that helps visiting the conditions in order to compile the condition
     * @return compiled condition that can be used for matching events in find, contains, delete, update and
     * updateOrAdd
     */
    @Override
    protected CompiledExpression compileSetAttribute(ExpressionBuilder expressionBuilder) {
        ElasticsearchExpressionVisitor visitor = new ElasticsearchExpressionVisitor();
        expressionBuilder.build(visitor);
        return new ElasticsearchCompiledCondition(visitor.returnExpression());
    }

    /**
     * This method will be called before the processing method.
     * Intention to establish connection to publish event.
     *
     * @throws ConnectionUnavailableException if end point is unavailable the ConnectionUnavailableException thrown
     *                                        such that the  system will take care retrying for connection
     */
    @Override
    protected void connect() throws ConnectionUnavailableException {

    }

    /**
     * Called after all publishing is done, or when {@link ConnectionUnavailableException} is thrown
     * Implementation of this method should contain the steps needed to disconnect.
     */
    @Override
    protected void disconnect() {

    }

    /**
     * The method can be called when removing an event receiver.
     * The cleanups that have to be done after removing the receiver could be done here.
     */
    @Override
    protected void destroy() {

    }
}
