
/*
 *  Copyright 2017-2018 Adobe.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *          http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package com.adobe.platform.ecosystem.examples.data.write.writer;

/**
 * Created by vardgupt on 10/23/2017.
 */

import com.adobe.platform.ecosystem.examples.catalog.api.CatalogService;
import com.adobe.platform.ecosystem.examples.catalog.model.Batch;
import com.adobe.platform.ecosystem.examples.catalog.model.SDKField;
import com.adobe.platform.ecosystem.examples.constants.SDKConstants;
import com.adobe.platform.ecosystem.examples.data.FileFormat;
import com.adobe.platform.ecosystem.examples.data.ingestion.api.DataIngestionService;
import com.adobe.platform.ecosystem.examples.data.write.converter.PipelineToJSONConverter;
import com.adobe.platform.ecosystem.examples.data.wiring.DataWiringParam;
import com.adobe.platform.ecosystem.examples.data.write.FlushHandler;
import com.adobe.platform.ecosystem.examples.data.write.Formatter;
import com.adobe.platform.ecosystem.examples.data.write.WriteAttributes;
import com.adobe.platform.ecosystem.examples.data.write.Writer;
import com.adobe.platform.ecosystem.examples.util.ConnectorSDKException;

import org.json.simple.JSONObject;

import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Reader which queries Data Access APIs
 * for reading data for any underlying
 * data source.
 */
public class DataIngestionAPIWriter implements Writer {
    private final DataWiringParam param;
    private final DataIngestionService dis;
    private final CatalogService catalogService;
    private FileFormat outputFileFormat;
    private String batchId;
    private Formatter formatter;
    private WriteAttributes writeAttributes;

    private static final Logger logger = Logger.getLogger(DataIngestionAPIWriter.class.getName());

    private List<String> headerFields;

    public DataIngestionAPIWriter(DataIngestionService dis,
                                  DataWiringParam param,
                                  FileFormat outputFileFormat,
                                  Formatter formatter,
                                  WriteAttributes writeAttributes,
                                  CatalogService catalogService) throws ConnectorSDKException {
        this.param = param;
        this.dis = dis;
        this.catalogService = catalogService;
        this.outputFileFormat = outputFileFormat;
        this.formatter = formatter;
        this.writeAttributes = writeAttributes;
        initWriter();
    }

    private void initWriter() throws ConnectorSDKException {
        if (batchId == null) {
            initBatch();
        }
    }

    private void initBatch() throws ConnectorSDKException {
        batchId =
                dis.getBatchId(
                        this.param.getImsOrg(),
                        this.param.getSandboxName(),
                        this.param.getAuthToken(),
                        getPayLoadForBatchCreation()
                );
    }

    @SuppressWarnings("unchecked")
    @Override
    /**
     * Write function invoked incase of flat connector
     * where hierarchy building needs to be done by sdk itself
     * return types are 0 for success & -1 for failure.
     */
    public int write(List<SDKField> sdkFields, List<List<Object>> dataTable) throws ConnectorSDKException {
        if(dataTable!=null){
            if(writeAttributes.isFlushStrategyRequired()){
                FlushHandler flushHandler = writeAttributes.getFlushHandler();
                flushHandler.incrementProcessedRows((long) dataTable.size());
                flushHandler.addRecords(dataTable);
                if(isBufferEligibleForFlush(flushHandler)){
                    dataTable = (List<List<Object>>) flushHandler.getDataTable();
                    return flushRecords(sdkFields, dataTable, flushHandler);
                }
                else
                    return 0;
            }
            else{
                return flushRecords(sdkFields, dataTable, null);
            }
        }
        else{
            if(writeAttributes.isFlushStrategyRequired()){
                FlushHandler flushHandler = writeAttributes.getFlushHandler();
                if(isBufferEligibleForFlush(flushHandler)){
                    dataTable = (List<List<Object>>) flushHandler.getDataTable();
                    return flushRecords(sdkFields, dataTable, flushHandler);
                }
                else
                    return 0;
            }
            else{// Not a valid case, ideally write is called in two cases 1) with not-null dataTable 2) when flush strategy is opted.
                return -1;
            }
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    /**
     * Write function invoked incase of procedural connector
     * where hierarchy is already constructed by vendor
     * return types are 0 for success & -1 for failure.
     */
    public int write(List<Object> dataTable) throws ConnectorSDKException {
        List<JSONObject> dataRecords;
        if(dataTable!=null){
            if(writeAttributes.isFlushStrategyRequired()){
                FlushHandler flushHandler = writeAttributes.getFlushHandler();
                flushHandler.incrementProcessedRows((long) dataTable.size());
                flushHandler.addRecords(dataTable);
                if(isBufferEligibleForFlush(flushHandler)){
                    dataTable = (List<Object>) flushHandler.getDataTable();
                    dataRecords = PipelineToJSONConverter.getFields(dataTable, param.getDataSet().getId());
                    return flushRecords(dataRecords, flushHandler);
                }
                else
                    return 0;
            }
            else{
                dataRecords = PipelineToJSONConverter.getFields(dataTable, param.getDataSet().getId());
                return flushRecords(dataRecords, null);
            }
        }
        else{
            if(writeAttributes.isFlushStrategyRequired()){
                FlushHandler flushHandler = writeAttributes.getFlushHandler();
                if(isBufferEligibleForFlush(flushHandler)){
                    dataTable = (List<Object>) flushHandler.getDataTable();
                    dataRecords = PipelineToJSONConverter.getFields(dataTable, param.getDataSet().getId());
                    return flushRecords(dataRecords, flushHandler);
                }
                else
                    return 0;
            }
            else{// Not a valid case, ideally write is called in two cases 1) with not-null dataTable 2) when flush strategy is opted.
                return -1;
            }
        }
    }

    private boolean isBufferEligibleForFlush(FlushHandler flushHandler) {
        return flushHandler.getRowsProcessed() >= flushHandler.getBatchSize() || (writeAttributes.isEOF() && flushHandler.getDataTable() != null && !flushHandler.getDataTable().isEmpty());
    }

    private int flushRecords(List<SDKField> sdkFields,List<List<Object>> dataTable, FlushHandler flushHandler) throws ConnectorSDKException {
        try{
            if(dataTable.size() == 0) {
                return 0;
            }
            byte[] buffer = formatter.getBuffer(sdkFields, dataTable);
            logger.log(Level.INFO,"Buffer received for " + outputFileFormat + " file, total records flushed: "+dataTable.size());
            if(dis.writeToBatch(batchId, this.param.getDataSet().getId(), this.param.getImsOrg(), this.param.getSandboxName(), this.param.getAuthToken(), outputFileFormat, buffer) == 0){
                if(flushHandler!=null)
                    flushHandler.reset();
                return 0;
            }
            else
                return -1;
        }
        catch(Exception e){
            throw new ConnectorSDKException("Error while executing flushRecords :" + e.getMessage(), e.getCause());
        }
    }

    private int flushRecords(List<JSONObject> dataRecords, FlushHandler flushHandler) throws ConnectorSDKException{
        byte[] buffer = formatter.getBuffer(dataRecords);
        logger.log(Level.INFO,"Buffer received for " + outputFileFormat + " file, total records flushed: "+dataRecords.size());
        if(dis.writeToBatch(batchId, this.param.getDataSet().getId(), this.param.getImsOrg(), this.param.getSandboxName(), this.param.getAuthToken(), outputFileFormat, buffer) == 0){
            if(flushHandler!=null)
                flushHandler.reset();
            return 0;
        }
        else
            return -1;

    }

	@Override
	public int markBatchCompletion(Boolean isSuccess) throws ConnectorSDKException {
		return markBatchCompletion(isSuccess, false);
	}

	@Override
	public int markBatchCompletion(Boolean isSuccess, Boolean shouldPollForBatchStatus) throws ConnectorSDKException {
		logger.log(Level.FINER, "Inside markBatchCompletion with status " + isSuccess);
		int outputResponse = -1;
		if (isSuccess) {
			outputResponse = dis.signalBatchCompletion(batchId, this.param.getImsOrg(), this.param.getSandboxName(), this.param.getAuthToken());
			logger.log(Level.FINER, "signalBatchCompletion triggered");
		}

		if (shouldPollForBatchStatus) {
			try {
				final Batch batch = catalogService.pollForBatchProcessingCompletion(
						param.getImsOrg(),
                        this.param.getSandboxName(),
						param.getAuthToken(),
						batchId
				);

				if (batch != null
							&& (SDKConstants.CATALOG_BATCH_STATUS_SUCCESS.equals(batch.getStatus()) || SDKConstants.CATALOG_BATCH_STATUS_ACTIVE.equals(batch.getStatus()))) {
					outputResponse = 0;
				} else {
					outputResponse = -1;
				}

			} catch (ConnectorSDKException exception) { // Exception can arise from Catalog API or Timeout expired. Check 'pollForBatchProcessingCompletion'.
				logger.log(Level.SEVERE, "Error while invoking operation[pollForBatchProcessingCompletion]: ", exception);
				outputResponse = -1;
			}
		}
		return outputResponse;
	}

    private JSONObject getPayLoadForBatchCreation() {
        JSONObject payload = new JSONObject();
        payload.put(SDKConstants.DATA_INGESTION_DATASET_ID, param.getDataSet().getId());
        return payload;
    }
}
