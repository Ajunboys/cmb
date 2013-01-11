package com.comcast.cqs.test.unit;

import static org.junit.Assert.*;

import java.util.HashMap;
import java.util.Random;

import org.apache.log4j.Logger;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.services.sns.AmazonSNS;
import com.amazonaws.services.sns.AmazonSNSClient;
import com.amazonaws.services.sqs.AmazonSQS;
import com.amazonaws.services.sqs.AmazonSQSClient;
import com.amazonaws.services.sqs.model.CreateQueueRequest;
import com.amazonaws.services.sqs.model.DeleteQueueRequest;
import com.amazonaws.services.sqs.model.ReceiveMessageRequest;
import com.amazonaws.services.sqs.model.ReceiveMessageResult;
import com.amazonaws.services.sqs.model.SendMessageRequest;
import com.comcast.cmb.common.controller.CMBControllerServlet;
import com.comcast.cmb.common.model.User;
import com.comcast.cmb.common.persistence.IUserPersistence;
import com.comcast.cmb.common.persistence.PersistenceFactory;
import com.comcast.cmb.common.persistence.UserCassandraPersistence;
import com.comcast.cmb.common.util.CMBProperties;
import com.comcast.cmb.common.util.Util;
import com.comcast.cqs.persistence.ICQSMessagePersistence;
import com.comcast.cqs.persistence.RedisCachedCassandraPersistence;

public class CQSLongPollTest {
	
    private static Logger logger = Logger.getLogger(CQSIntegrationTest.class);

    private AmazonSQS sqs = null;
    private AmazonSNS sns = null;
    
    private HashMap<String, String> attributeParams = new HashMap<String, String>();
    private User user = null;
    private User user1 = null;
    private User user2 = null;
    private Random randomGenerator = new Random();
    private final static String QUEUE_PREFIX = "TSTQ_"; 
    
    private static String queueUrl;
    
    @Before
    public void setup() throws Exception {
    	
        Util.initLog4jTest();
        CMBControllerServlet.valueAccumulator.initializeAllCounters();
        PersistenceFactory.reset();
        
        try {
        	
            IUserPersistence userPersistence = new UserCassandraPersistence();
 
            user = userPersistence.getUserByName("cqs_unit_test");

            if (user == null) {
                user = userPersistence.createUser("cqs_unit_test", "cqs_unit_test");
            }

            BasicAWSCredentials credentialsUser = new BasicAWSCredentials(user.getAccessKey(), user.getAccessSecret());

            user1 = userPersistence.getUserByName("cqs_unit_test_1");

            if (user1 == null) {
                user1 = userPersistence.createUser("cqs_unit_test_1", "cqs_unit_test_1");
            }

            user2 = userPersistence.getUserByName("cqs_unit_test_2");

            if (user2 == null) {
                user2 = userPersistence.createUser("cqs_unit_test_2", "cqs_unit_test_2");
            }

            BasicAWSCredentials credentialsUser1 = new BasicAWSCredentials(user1.getAccessKey(), user1.getAccessSecret());

            sqs = new AmazonSQSClient(credentialsUser);
            sqs.setEndpoint(CMBProperties.getInstance().getCQSServerUrl());

            sns = new AmazonSNSClient(credentialsUser1);
            sns.setEndpoint(CMBProperties.getInstance().getCNSServerUrl());
            
        } catch (Exception ex) {
            logger.error("setup failed", ex);
            fail("setup failed: "+ex);
            return;
        }
        
        attributeParams.put("MessageRetentionPeriod", "600");
        attributeParams.put("VisibilityTimeout", "30");
    }
    
    private class MessageSender extends Thread {
    	public void run() {
    		try {
				logger.info("sender sleeping for 5 sec");
    			sleep(5000);
	            sqs.sendMessage(new SendMessageRequest(queueUrl, "test message"));
	            logger.info("test message sent");
			} catch (Exception ex) {
				ex.printStackTrace();
			}
    	}
    }
    
    @Test
    public void testLongPoll() {

    	try {

    		String queueName = QUEUE_PREFIX + randomGenerator.nextLong();
	        CreateQueueRequest createQueueRequest = new CreateQueueRequest(queueName);
	        createQueueRequest.setAttributes(attributeParams);
	        queueUrl = sqs.createQueue(createQueueRequest).getQueueUrl();
	        
	        ICQSMessagePersistence messagePersistence = RedisCachedCassandraPersistence.getInstance();
			messagePersistence.clearQueue(queueUrl);
			
			logger.info("queue " + queueUrl + "created");
	        
	        Thread.sleep(1000);

			ReceiveMessageRequest receiveMessageRequest = new ReceiveMessageRequest();
			receiveMessageRequest.setQueueUrl(queueUrl);
			receiveMessageRequest.setMaxNumberOfMessages(1);
			receiveMessageRequest.setWaitTimeSeconds(20);
			
			long start = System.currentTimeMillis();
			
			(new MessageSender()).start();
			
			logger.info("calling receive message");
			
			ReceiveMessageResult receiveMessageResult = sqs.receiveMessage(receiveMessageRequest);
			
			logger.info("receive message returns");
			
			long end = System.currentTimeMillis();
			
			assertTrue("No message received", receiveMessageResult.getMessages().size() == 1);
			
			assertTrue("Wrong message content", receiveMessageResult.getMessages().get(0).getBody().equals("test message"));
			
			assertTrue("Message came back too fast: " + (end-start) + " ms", end-start >= 4000);
			
			assertTrue("Message came back too slow: " + (end-start) + " ms", end-start <= 6000);
	        
		} catch (Exception ex) {
			ex.printStackTrace();
		} finally {
	        if (queueUrl != null) {
	        	sqs.deleteQueue(new DeleteQueueRequest(queueUrl));
	        }
		}
    }

    @Test
    public void testLongPollTimeout() {

    	try {

    		String queueName = QUEUE_PREFIX + randomGenerator.nextLong();
	        CreateQueueRequest createQueueRequest = new CreateQueueRequest(queueName);
	        createQueueRequest.setAttributes(attributeParams);
	        queueUrl = sqs.createQueue(createQueueRequest).getQueueUrl();
	        
	        ICQSMessagePersistence messagePersistence = RedisCachedCassandraPersistence.getInstance();
			messagePersistence.clearQueue(queueUrl);
			
			logger.info("queue " + queueUrl + "created");
	        
	        Thread.sleep(1000);

			ReceiveMessageRequest receiveMessageRequest = new ReceiveMessageRequest();
			receiveMessageRequest.setQueueUrl(queueUrl);
			receiveMessageRequest.setMaxNumberOfMessages(1);
			receiveMessageRequest.setWaitTimeSeconds(5);
			
			long start = System.currentTimeMillis();
			
			logger.info("calling receive message");
			
			ReceiveMessageResult receiveMessageResult = sqs.receiveMessage(receiveMessageRequest);
			
			logger.info("receive message returns");
			
			long end = System.currentTimeMillis();
			
			assertTrue("Message received: " + receiveMessageResult.getMessages().get(0).getBody(), receiveMessageResult.getMessages().size() == 0);
			
			assertTrue("Receive came back too fast: " + (end-start) + " ms", end-start >= 4000);
			
			assertTrue("Receive came back too slow: " + (end-start) + " ms", end-start <= 6000);
	        
		} catch (Exception ex) {
			ex.printStackTrace();
		} finally {
	        if (queueUrl != null) {
	        	sqs.deleteQueue(new DeleteQueueRequest(queueUrl));
	        }
		}
    }

    @After    
    public void tearDown() {
        CMBControllerServlet.valueAccumulator.deleteAllCounters();
    }    
}
