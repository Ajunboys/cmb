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
package com.comcast.cns.controller;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.management.MBeanServerConnection;
import javax.management.ObjectName;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXServiceURL;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import me.prettyprint.cassandra.serializers.StringSerializer;
import me.prettyprint.hector.api.HConsistencyLevel;
import me.prettyprint.hector.api.beans.Row;

import org.apache.log4j.Logger;

import com.comcast.cmb.common.model.User;
import com.comcast.cmb.common.persistence.CassandraPersistence;
import com.comcast.cmb.common.util.CMBProperties;
import com.comcast.cns.io.CNSWorkerStatsPopulator;
import com.comcast.cns.model.CNSWorkerStats;
/**
 * Subscribe action
 * @author bwolf, jorge
 *
 */
public class CNSGetWorkerStatsAction extends CNSAction {

	private static Logger logger = Logger.getLogger(CNSGetWorkerStatsAction.class);
	
	public CNSGetWorkerStatsAction() {
		super("GetWorkerStats");
	}

    /**
     * Get various stats about active cns workers
     * @param user the user for whom we are subscribing.
     * @param request the servlet request including all the parameters for the doUnsubscribe call
     * @param response the response servlet we write to.
     */
	@Override
	public boolean doAction(User user, HttpServletRequest request, HttpServletResponse response) throws Exception {
		
		CassandraPersistence cassandraHandler = new CassandraPersistence(CMBProperties.getInstance().getCMBCNSKeyspace());
		
		List<Row<String, String, String>> rows = cassandraHandler.readNextNNonEmptyRows("CNSWorkers", null, 1000, 10, new StringSerializer(), new StringSerializer(), new StringSerializer(), HConsistencyLevel.QUORUM);
		List<CNSWorkerStats> statsList = new ArrayList<CNSWorkerStats>();
		
		if (rows != null) {
			
			for (Row<String, String, String> row : rows) {
				
				CNSWorkerStats stats = new CNSWorkerStats();
				stats.setIpAddress(row.getKey());
				
				if (row.getColumnSlice().getColumnByName("producerTimestamp") != null) {
					stats.setProducerTimestamp(Long.parseLong(row.getColumnSlice().getColumnByName("producerTimestamp").getValue()));
				}
				
				if (row.getColumnSlice().getColumnByName("consumerTimestamp") != null) {
					stats.setConsumerTimestamp(Long.parseLong(row.getColumnSlice().getColumnByName("consumerTimestamp").getValue()));
				}

				if (row.getColumnSlice().getColumnByName("jmxport") != null) {
					stats.setJmxPort(Long.parseLong(row.getColumnSlice().getColumnByName("jmxport").getValue()));
				}

				if (row.getColumnSlice().getColumnByName("mode") != null) {
					stats.setMode(row.getColumnSlice().getColumnByName("mode").getValue());
				}

				statsList.add(stats);
			}
		}
		
		for (CNSWorkerStats stats : statsList) {
			
			if (stats.getJmxPort() > 0) {
			
				JMXConnector jmxConnector = null;
				String url = null;

				try {

					String host = stats.getIpAddress();  
					long port = stats.getJmxPort();
					url = "service:jmx:rmi:///jndi/rmi://" + host + ":" + port + "/jmxrmi";

					JMXServiceURL serviceUrl = new JMXServiceURL(url);
					jmxConnector = JMXConnectorFactory.connect(serviceUrl, null);

					MBeanServerConnection mbeanConn = jmxConnector.getMBeanServerConnection();

					//Set<ObjectName> beanSet = mbeanConn.queryNames(null, null);

					ObjectName cnsWorkerMonitor = new ObjectName("com.comcast.cns.controller:type=CNSMonitorMBean");

					Integer deliveryQueueSize = (Integer)mbeanConn.getAttribute(cnsWorkerMonitor, "DeliveryQueueSize");
					stats.setDeliveryQueueSize(deliveryQueueSize);
					
					Integer redeliveryQueueSize = (Integer)mbeanConn.getAttribute(cnsWorkerMonitor, "RedeliveryQueueSize");
					stats.setRedeliveryQueueSize(redeliveryQueueSize);
					
					Boolean consumerOverloaded = (Boolean)mbeanConn.getAttribute(cnsWorkerMonitor, "ConsumerOverloaded");
					stats.setConsumerOverloaded(consumerOverloaded);
					
					Integer numPublishedMessages = (Integer)mbeanConn.getAttribute(cnsWorkerMonitor, "NumPublishedMessages");
					stats.setNumPublishedMessages(numPublishedMessages);
					
					Map<String, String> errorRateForEndpoints = (Map<String, String>)mbeanConn.getAttribute(cnsWorkerMonitor, "ErrorRateForEndpoints");
					stats.setErrorRateForEndpoints(errorRateForEndpoints);

					Boolean cqsServiceAvailable = (Boolean)mbeanConn.getAttribute(cnsWorkerMonitor, "CQSServiceAvailable");
					stats.setCqsServiceAvailable(cqsServiceAvailable);

				} catch (Exception ex) {

					logger.warn("event=failed_to_connect_to_jmx_server url=" + url);

				} finally {

					if (jmxConnector != null) {
						jmxConnector.close();
					}
				}
			}
		}

    	String res = CNSWorkerStatsPopulator.getGetWorkerStatsResponse(statsList);	
		response.getWriter().println(res);

    	return true;
	}
}
