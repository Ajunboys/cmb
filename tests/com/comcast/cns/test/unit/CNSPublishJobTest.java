/**
 * Copyright 2012 Comcast Corporation
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *   http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.comcast.cns.test.unit;

import static org.junit.Assert.*;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedList;

import org.apache.log4j.Logger;
import org.junit.Before;
import org.junit.Test;

import com.comcast.plaxo.cmb.common.controller.CMBControllerServlet;
import com.comcast.plaxo.cmb.common.persistence.PersistenceFactory;
import com.comcast.plaxo.cmb.common.util.CMBException;
import com.comcast.plaxo.cmb.common.util.CMBProperties;
import com.comcast.plaxo.cmb.common.util.Util;
import com.comcast.plaxo.cns.model.CNSMessage;
import com.comcast.plaxo.cns.model.CNSMessage.CNSMessageStructure;
import com.comcast.plaxo.cns.model.CNSSubscription.CnsSubscriptionProtocol;
import com.comcast.plaxo.cns.model.CNSEndpointPublishJob;
import com.comcast.plaxo.cns.persistence.CachedCNSEndpointPublishJob;
import com.comcast.plaxo.cns.persistence.CachedCNSEndpointPublishJob.CachedSubInfo;

public class CNSPublishJobTest {
    static Logger logger = Logger.getLogger(CNSPublishJobTest.class);
    
    @Before
    public void setup() throws Exception {
        Util.initLog4jTest();
        CMBControllerServlet.valueAccumulator.initializeAllCounters();
        PersistenceFactory.reset();
        CMBProperties.getInstance().setUseSubInfoCache(false);
    }

    @Test
    public void testEqualsEPJob() {
        CNSMessage p1 = CNSMessageTest.getMessage("test", null, "test", "test-arn", "test-pub-userId");
        CNSEndpointPublishJob.SubInfo subInfo = new CNSEndpointPublishJob.SubInfo(CnsSubscriptionProtocol.cqs, "test-endpoint1", "test-sub-arn");
        CNSEndpointPublishJob j1 = new CNSEndpointPublishJob(p1, Arrays.asList(subInfo));
        if (!j1.equals(j1)) {
            fail("CNSEndpointPublishJob not equal to itself");
        }
    }
    
    @Test
    public void serializeDeserializeEPJob() {
        
    	try {
	    	CNSMessage p1 = CNSMessageTest.getMessage("test", null, "test", "test-arn", "test-pub-userId");
	        CNSEndpointPublishJob.SubInfo subInfo = new CNSEndpointPublishJob.SubInfo(CnsSubscriptionProtocol.cqs, "test-endpoint1", "test-sub-arn");
	        CNSEndpointPublishJob j1 = new CNSEndpointPublishJob(p1, Arrays.asList(subInfo));
	
	        String str = j1.serialize();
	        CNSEndpointPublishJob rec = CNSEndpointPublishJob.parseInstance(str);
	        if (!j1.equals(rec)) {
	            fail("orig != rec. orig=" + j1 + " rec=" + rec);
	        }
	        
	        //test with no subs
	        j1 = new CNSEndpointPublishJob(p1, new LinkedList<CNSEndpointPublishJob.SubInfo>());
	        str = j1.serialize();
	        rec = CNSEndpointPublishJob.parseInstance(str);
	        if (!j1.equals(rec)) {
	            fail("orig != rec. orig=" + j1 + " rec=" + rec);
	        }
	        
	        //test with no subject
	        p1 = CNSMessageTest.getMessage("test", null, null, "test-arn", "test-pub-userId");
	        j1 = new CNSEndpointPublishJob(p1, Arrays.asList(subInfo));
	        str = j1.serialize();
	        rec = CNSEndpointPublishJob.parseInstance(str);
	        if (!j1.equals(rec)) {
	            fail("orig != rec. orig=" + j1 + " rec=" + rec);
	        }
	        
    	} catch (Exception ex) {
    		fail(ex.toString());
    	}
    }
    
    @Test
    public void serializeSize() {
        CNSMessage p1 = CNSMessageTest.getMessage("test", null, "test", "test-arn", "test-pub-userId");
        LinkedList<CNSEndpointPublishJob.SubInfo> subInfos = new LinkedList<CNSEndpointPublishJob.SubInfo>();
        for (int i = 0; i < 500; i++) {
            subInfos.add(new CNSEndpointPublishJob.SubInfo(CnsSubscriptionProtocol.cqs, "http://nis.test3.plaxo.com:8080/CMB/Endpoint/info/1234" + i, "27daac76-34dd-47df-bd01-1f6e873584a0" + i));    
        }
        
        CNSEndpointPublishJob j1 = new CNSEndpointPublishJob(p1, subInfos);
        String str = j1.serialize();
        logger.info("size of serialized=" + str.length());
    }
        
    @Test
    public void serializeDeserializeNoSubUsingCache() throws CMBException {
        CNSMessage p1 = CNSMessageTest.getMessage("test", null, "test", "test-arn", "test-pub-userId");
        CachedCNSEndpointPublishJob job = new CachedCNSEndpointPublishJob(p1, Collections.EMPTY_LIST);
        
        String str = job.serialize();
        logger.debug("serializedFOrm=" + str);
        
        CNSEndpointPublishJob rec = CachedCNSEndpointPublishJob.parseInstance(str);
        if (!job.equals(rec)) {
            fail("orig!=rec. orig=" + job + " rec=" + rec);
        }
    }
}
