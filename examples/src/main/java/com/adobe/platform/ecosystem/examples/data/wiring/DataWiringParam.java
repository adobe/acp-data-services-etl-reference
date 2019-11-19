
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
package com.adobe.platform.ecosystem.examples.data.wiring;

import com.adobe.platform.ecosystem.examples.catalog.model.DataSet;
import com.adobe.platform.ecosystem.examples.util.ConnectorSDKException;
import com.adobe.platform.ecosystem.examples.util.ConnectorSDKUtil;

/**
 * Entity to encapsulate parameters
 * required by underlying reader/
 * writer factories.
 */
public class DataWiringParam {
    private String imsOrg;

    private String sandboxName;

    private DataSet dataSet;

    public DataWiringParam( String imsOrg, DataSet dataSet) {
        this.imsOrg = imsOrg;
        this.dataSet = dataSet;
    }

    public DataWiringParam( String imsOrg, String sandboxName, DataSet dataSet) {
        this.imsOrg = imsOrg;
        this.sandboxName = sandboxName;
        this.dataSet = dataSet;
    }

    public String getImsOrg() {
        return imsOrg;
    }

    public String getSandboxName() {
        return sandboxName;
    }

    public String getAuthToken() throws ConnectorSDKException{
        return ConnectorSDKUtil.getInstance().getAccessToken();
    }

    public DataSet getDataSet() {
        return dataSet;
    }
}