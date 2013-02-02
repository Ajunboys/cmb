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
package com.comcast.cqs.controller;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

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

import com.comcast.cmb.common.model.CMBPolicy;
import com.comcast.cmb.common.model.User;
import com.comcast.cmb.common.persistence.CassandraPersistence;
import com.comcast.cmb.common.util.CMBProperties;
import com.comcast.cqs.io.CQSAPIStatsPopulator;
import com.comcast.cqs.model.CQSAPIStats;
/**
 * Subscribe action
 * @author bwolf, jorge
 *
 */
public class CQSGetAPIStatsAction extends CQSAction {

	private static Logger logger = Logger.getLogger(CQSGetAPIStatsAction.class);
	
	public CQSGetAPIStatsAction() {
		super("GetAPIStats");
	}
	
	@Override
    public boolean isActionAllowed(User user, HttpServletRequest request, String service, CMBPolicy policy) throws Exception {
    	return true;
    }

    /**
     * Get various stats about active cns workers
     * @param user the user for whom we are subscribing.
     * @param request the servlet request including all the parameters for the doUnsubscribe call
     * @param response the response servlet we write to.
     */
	@Override
	public boolean doAction(User user, HttpServletRequest request, HttpServletResponse response) throws Exception {
		
		CassandraPersistence cassandraHandler = new CassandraPersistence(CMBProperties.getInstance().getCMBCQSKeyspace());
		
		List<Row<String, String, String>> rows = cassandraHandler.readNextNNonEmptyRows("CQSAPIServers", null, 1000, 10, new StringSerializer(), new StringSerializer(), new StringSerializer(), HConsistencyLevel.QUORUM);
		List<CQSAPIStats> statsList = new ArrayList<CQSAPIStats>();
		
		if (rows != null) {
			
			for (Row<String, String, String> row : rows) {
				
				CQSAPIStats stats = new CQSAPIStats();
				stats.setIpAddress(row.getKey());
				
				if (row.getColumnSlice().getColumnByName("timestamp") != null) {
					stats.setTimestamp(Long.parseLong(row.getColumnSlice().getColumnByName("timestamp").getValue()));
				}
				
				if (row.getColumnSlice().getColumnByName("jmxport") != null) {
					stats.setJmxPort(Long.parseLong(row.getColumnSlice().getColumnByName("jmxport").getValue()));
				}

				if (row.getColumnSlice().getColumnByName("port") != null) {
					stats.setLongPollPort(Long.parseLong(row.getColumnSlice().getColumnByName("port").getValue()));
				}

				if (row.getColumnSlice().getColumnByName("dataCenter") != null) {
					stats.setDataCenter(row.getColumnSlice().getColumnByName("dataCenter").getValue());
				}
				
				if (row.getColumnSlice().getColumnByName("serviceUrl") != null) {
					stats.setServiceUrl(row.getColumnSlice().getColumnByName("serviceUrl").getValue());
				}
				
				if (row.getColumnSlice().getColumnByName("redisServerList") != null) {
					stats.setRedisServerList(row.getColumnSlice().getColumnByName("redisServerList").getValue());
				}

				if (stats.getIpAddress().contains(":")) {
					statsList.add(stats);
				}
			}
		}
		
		for (CQSAPIStats stats : statsList) {
			
			if (stats.getJmxPort() > 0 && System.currentTimeMillis() - stats.getTimestamp() < 5*60*1000) {
			
				JMXConnector jmxConnector = null;
				String url = null;

				try {

					String host = stats.getIpAddress();  
					
					if (host.contains(":")) {
						host = host.substring(0, host.indexOf(":"));
					}
					
					long port = stats.getJmxPort();
					url = "service:jmx:rmi:///jndi/rmi://" + host + ":" + port + "/jmxrmi";

					JMXServiceURL serviceUrl = new JMXServiceURL(url);
					jmxConnector = JMXConnectorFactory.connect(serviceUrl, null);

					MBeanServerConnection mbeanConn = jmxConnector.getMBeanServerConnection();

					ObjectName cqsAPIMonitor = new ObjectName("com.comcast.cqs.controller:type=CQSMonitorMBean");

					Long numberOfLongPollReceives = (Long)mbeanConn.getAttribute(cqsAPIMonitor, "NumberOfLongPollReceives");
					stats.setNumberOfLongPollReceives(numberOfLongPollReceives);
					
					Integer numberOfRedisShards = (Integer)mbeanConn.getAttribute(cqsAPIMonitor, "NumberOfRedisShards");
					stats.setNumberOfRedisShards(numberOfRedisShards);

					List<Map<String, String>> redisShardInfos = (List<Map<String, String>>)mbeanConn.getAttribute(cqsAPIMonitor, "RedisShardInfos");
					
					for (Map<String, String> shardInfo : redisShardInfos) {
						if (shardInfo.containsKey("db0")) {
							long numKeys = Long.parseLong(shardInfo.get("db0").split(",")[0].split("=")[1]);
							stats.setNumberOfRedisKeys(numKeys);
						}
					}
					
					Map<String, AtomicLong> callStats = (Map<String, AtomicLong>)mbeanConn.getAttribute(cqsAPIMonitor, "CallStats");
					
					stats.setCallStats(callStats);
					
					Map<String, AtomicLong> callFailureStats = (Map<String, AtomicLong>)mbeanConn.getAttribute(cqsAPIMonitor, "CallFailureStats");

					stats.setCallFailureStats(callFailureStats);

				} catch (Exception ex) {

					logger.warn("event=failed_to_connect_to_jmx_server url=" + url, ex);

				} finally {

					if (jmxConnector != null) {
						jmxConnector.close();
					}
				}
			}
		}

    	String res = CQSAPIStatsPopulator.getGetAPIStatsResponse(statsList);	
		response.getWriter().println(res);

    	return true;
	}
}
