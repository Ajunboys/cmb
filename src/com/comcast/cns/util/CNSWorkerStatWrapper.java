package com.comcast.cns.util;

import java.util.ArrayList;
import java.util.List;

import javax.management.JMX;
import javax.management.MBeanServerConnection;
import javax.management.ObjectName;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXServiceURL;

import org.apache.log4j.Logger;

import me.prettyprint.cassandra.serializers.StringSerializer;
import me.prettyprint.hector.api.beans.Row;

import com.comcast.cmb.common.persistence.CassandraPersistence;
import com.comcast.cmb.common.util.CMBErrorCodes;
import com.comcast.cmb.common.util.CMBException;
import com.comcast.cmb.common.util.CMBProperties;
import com.comcast.cns.model.CNSWorkerStats;
import com.comcast.cns.tools.CNSWorkerMonitorMBean;

public class CNSWorkerStatWrapper {
	private static Logger logger = Logger.getLogger(CNSWorkerStatWrapper.class);
	
	public static List<CNSWorkerStats> getCassandraWorkerStats(){
		CassandraPersistence cassandraHandler = new CassandraPersistence(CMBProperties.getInstance().getCNSKeyspace());
		
		List<Row<String, String, String>> rows = cassandraHandler.readNextNNonEmptyRows("CNSWorkers", null, 1000, 10, new StringSerializer(), new StringSerializer(), new StringSerializer(), CMBProperties.getInstance().getReadConsistencyLevel());
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

				if (row.getColumnSlice().getColumnByName("dataCenter") != null) {
					stats.setDataCenter(row.getColumnSlice().getColumnByName("dataCenter").getValue());
				}
				statsList.add(stats);
			}
		}
		
		return statsList;
	}
	
	public static List<CNSWorkerStats> getCassandraWorkerStatsByDataCenter(String dataCenter){
		List<CNSWorkerStats> cnsWorkerStatsList = getCassandraWorkerStats();
		List<CNSWorkerStats> cnsWorkerStatsByDataCenterList = new ArrayList<CNSWorkerStats>();
		for (CNSWorkerStats currentWorkerStats: cnsWorkerStatsList){
			if(currentWorkerStats.getDataCenter().equals(dataCenter)){
				cnsWorkerStatsByDataCenterList.add(currentWorkerStats);
			}
		}
		return cnsWorkerStatsByDataCenterList;
	}
	
	private static void callOperation(String operation, List<CNSWorkerStats> cnsWorkerStats) throws Exception{
		if((operation!=null)&&(operation.equals("startWorker")||operation.equals("stopWorker"))){
			JMXConnector jmxConnector = null;
			String url = null;
			String host = null;
			long port = 0;
			for(CNSWorkerStats stats:cnsWorkerStats){
			try {

				host = stats.getIpAddress(); 
				port = stats.getJmxPort();
				url = "service:jmx:rmi:///jndi/rmi://" + host + ":" + port + "/jmxrmi";

				JMXServiceURL serviceUrl = new JMXServiceURL(url);
				jmxConnector = JMXConnectorFactory.connect(serviceUrl, null);

				MBeanServerConnection mbeanConn = jmxConnector.getMBeanServerConnection();
				ObjectName cnsWorkerMonitor = new ObjectName("com.comcast.cns.tools:type=CNSWorkerMonitorMBean");
				CNSWorkerMonitorMBean mbeanProxy = JMX.newMBeanProxy(mbeanConn, cnsWorkerMonitor,	CNSWorkerMonitorMBean.class, false);
				if(operation.equals("startWorker")){
					mbeanProxy.startCNSWorkers();
				} else {
					mbeanProxy.stopCNSWorkers();
				}
			} catch(Exception e){
				logger.error("event=error_in_"+operation+" Hose:"+host+" port:"+port+"Exception: "+e);
				throw new CMBException(CMBErrorCodes.InternalError, "Cannot " + operation + "CNS workers");
			}
			finally {

				if (jmxConnector != null) {
					jmxConnector.close();
				}
			}
			}
		}
		
	}
	
	public static void stopWorkers(List<CNSWorkerStats> cnsWorkersList) throws Exception{
		callOperation("stopWorker", cnsWorkersList);
	}

	public static void startWorkers(List<CNSWorkerStats> cnsWorkersList)throws Exception{
		callOperation("startWorker", cnsWorkersList);
	}
	
	public static void startWorkers(String dataCenter) throws Exception{
		List<CNSWorkerStats> cnsWorkerStarts = getCassandraWorkerStatsByDataCenter(dataCenter);
		callOperation("startWorker", cnsWorkerStarts);
	}

	public static void stopWorkers(String dataCenter) throws Exception{
		List<CNSWorkerStats> cnsWorkerStarts = getCassandraWorkerStatsByDataCenter(dataCenter);
		callOperation("stopWorker", cnsWorkerStarts);
	}

}
