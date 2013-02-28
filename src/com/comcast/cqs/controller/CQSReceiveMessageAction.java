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

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;

import javax.servlet.AsyncContext;
import javax.servlet.AsyncEvent;
import javax.servlet.AsyncListener;
import javax.servlet.http.HttpServletResponse;

import org.apache.log4j.Logger;

import com.comcast.cmb.common.controller.CMBControllerServlet;
import com.comcast.cmb.common.model.User;
import com.comcast.cmb.common.persistence.PersistenceFactory;
import com.comcast.cmb.common.util.CMBErrorCodes;
import com.comcast.cmb.common.util.CMBException;
import com.comcast.cmb.common.util.CMBProperties;
import com.comcast.cqs.io.CQSMessagePopulator;
import com.comcast.cqs.model.CQSMessage;
import com.comcast.cqs.model.CQSQueue;
import com.comcast.cqs.util.CQSConstants;
/**
 * Receive message
 * @author aseem, baosen, bwolf, vvenkatraman
 *
 */
public class CQSReceiveMessageAction extends CQSAction {
	
    private static Logger logger = Logger.getLogger(CQSReceiveMessageAction.class);

	public CQSReceiveMessageAction() {
		super("ReceiveMessage");
	}
	
	public CQSReceiveMessageAction(String actionName) {
	    super(actionName);
	}
	
	@Override
	public boolean doAction(User user, AsyncContext asyncContext) throws Exception {
		
        CQSHttpServletRequest request = (CQSHttpServletRequest)asyncContext.getRequest();
        HttpServletResponse response = (HttpServletResponse)asyncContext.getResponse();
        
    	CQSQueue queue = CQSControllerServlet.getCachedQueue(user, request);
        
        Map<String, String[]> requestParams = request.getParameterMap();
        List<String> filterAttributes = new ArrayList<String>();
        
        for (String k: requestParams.keySet()) {
        	if (k.contains(CQSConstants.ATTRIBUTE_NAME)) {
        		filterAttributes.add(requestParams.get(k)[0]);
        	}
        }
        
    	asyncContext.addListener(new AsyncListener() {

    		@Override
			public void onComplete(AsyncEvent asyncEvent) throws IOException {
				
    			if (!(asyncEvent.getSuppliedRequest() instanceof CQSHttpServletRequest)) {
					logger.error("event=invalid_request stage=on_complete");
					return;
    			}    			
    			
    			((CQSHttpServletRequest)asyncEvent.getSuppliedRequest()).setActive(false);
			}
			
    		@Override
			public void onError(AsyncEvent asyncEvent) throws IOException {

    			int httpCode = CMBErrorCodes.InternalError.getHttpCode();
                String code = CMBErrorCodes.InternalError.getCMBCode();
                String message = "There is an internal problem with CMB";
                
                if (asyncEvent.getThrowable() instanceof CMBException) {
                    httpCode = ((CMBException)asyncEvent.getThrowable()).getHttpCode();
                    code = ((CMBException)asyncEvent.getThrowable()).getCMBCode();
                    message = asyncEvent.getThrowable().getMessage();
                }

                String errXml = CMBControllerServlet.createErrorResponse(code, message);

                ((HttpServletResponse)asyncEvent.getSuppliedResponse()).setStatus(httpCode);
	            asyncEvent.getSuppliedResponse().getWriter().println(errXml);
	            
    			if (!(asyncEvent.getSuppliedRequest() instanceof CQSHttpServletRequest)) {
					logger.error("event=invalid_request stage=on_error");
					return;
    			}    			

    			CQSQueue queue = ((CQSHttpServletRequest)asyncEvent.getSuppliedRequest()).getQueue();
        		AsyncContext asyncContext = asyncEvent.getAsyncContext(); 
	            
	            if (queue != null) {
	            	
	            	ConcurrentLinkedQueue<AsyncContext> queueContextsList = CQSLongPollReceiver.contextQueues.get(queue.getArn());
	            	
            		if (queueContextsList != null && asyncContext != null) {
	            		queueContextsList.remove(asyncContext);
	            	}
	            }
	            
	            asyncContext.complete();
    		}
			
    		@Override
			public void onTimeout(AsyncEvent asyncEvent) throws IOException {
    			
    			String out = CQSMessagePopulator.getReceiveMessageResponseAfterSerializing(new ArrayList<CQSMessage>(), new ArrayList<String>());
	            asyncEvent.getSuppliedResponse().getWriter().println(out);

	            if (!(asyncEvent.getSuppliedRequest() instanceof CQSHttpServletRequest)) {
					logger.error("event=invalid_request stage=on_timeout");
					return;
    			}    			
	            
    			CQSQueue queue = ((CQSHttpServletRequest)asyncEvent.getSuppliedRequest()).getQueue();
        		AsyncContext asyncContext = asyncEvent.getAsyncContext(); 

	            if (queue != null) {
	            	
	            	ConcurrentLinkedQueue<AsyncContext> queueContextsList = CQSLongPollReceiver.contextQueues.get(queue.getArn());
	            	
            		if (queueContextsList != null && asyncContext != null) {
	            		queueContextsList.remove(asyncContext);
	            	}
	            }
				
	            asyncContext.complete();
			}

			@Override
			public void onStartAsync(AsyncEvent asyncEvent) throws IOException {
			}
    	});
    	
    	request.setFilterAttributes(filterAttributes);
    	request.setQueue(queue);
    	
        HashMap<String, String> msgParam = new HashMap<String, String>();
        
        if (request.getParameter(CQSConstants.MAX_NUMBER_OF_MESSAGES) != null) {
            
        	int maxNumberOfMessages = Integer.parseInt(request.getParameter(CQSConstants.MAX_NUMBER_OF_MESSAGES));
            
        	if (maxNumberOfMessages < 1 || maxNumberOfMessages > CMBProperties.getInstance().getMaxReceiveMessageCount()) {
                throw new CMBException(CMBErrorCodes.InvalidParameterValue, "The value for MaxNumberOfMessages is not valid (must be from 1 to " + CMBProperties.getInstance().getMaxReceiveMessageCount() + ").");
            }
        	
            msgParam.put(CQSConstants.MAX_NUMBER_OF_MESSAGES, "" + maxNumberOfMessages);
        }

        if (request.getParameter(CQSConstants.VISIBILITY_TIMEOUT) != null) {
        	msgParam.put(CQSConstants.VISIBILITY_TIMEOUT, request.getParameter(CQSConstants.VISIBILITY_TIMEOUT));
        }
        
        int waitTimeSeconds = 0;
        
        if (request.getParameter(CQSConstants.WAIT_TIME_SECONDS) != null) {
        	
        	if (!CMBProperties.getInstance().isCqsLongPollEnabled()) {
                throw new CMBException(CMBErrorCodes.InvalidParameterValue, "Long polling not enabled.");
        	}
        	
        	waitTimeSeconds = Integer.parseInt(request.getParameter(CQSConstants.WAIT_TIME_SECONDS));
        	
        	if (waitTimeSeconds < 1 || waitTimeSeconds > 20) {
                throw new CMBException(CMBErrorCodes.InvalidParameterValue, "The value for WaitTimeSeconds must be an integer number between 1 and 20.");
        	}
        	
        	// set timeout appropriately
        	
        	asyncContext.setTimeout(waitTimeSeconds * 1000);
            request.setWaitTime(waitTimeSeconds * 1000);
        }
                
        List<CQSMessage> messageList = PersistenceFactory.getCQSMessagePersistence().receiveMessage(queue, msgParam);
        
        // wait for long poll if desired
        
        if (messageList.size() == 0 && waitTimeSeconds > 0) {
        	
        	// put context on async queue to wait for long poll
        	
        	logger.info("event=queueing_context queue_arn=" + queue.getArn() + " wait_time_sec=" + waitTimeSeconds);
        	
        	CQSLongPollReceiver.contextQueues.putIfAbsent(queue.getArn(), new ConcurrentLinkedQueue<AsyncContext>());
			ConcurrentLinkedQueue<AsyncContext> contextQueue = CQSLongPollReceiver.contextQueues.get(queue.getArn());
			
			if (contextQueue.offer(asyncContext)) {
	            request.setIsQueuedForProcessing(true);
			}
			
        	/*CQSLongPollReceiver.queueMonitors.putIfAbsent(queue.getArn(), new Object());
        	Object monitor = CQSLongPollReceiver.queueMonitors.get(queue.getArn());
        	long referenceTime = System.currentTimeMillis();
        	
        	synchronized (monitor) {
        		
        		while (messageList.size() == 0) {
        		
        			long now = System.currentTimeMillis();
        			long waitPeriodMillis = waitTimeSeconds*1000 - (now-referenceTime); 
        			
        			if (waitPeriodMillis <= 0) {
        				break;
        			}
        			
        			logger.info("event=waiting_for_longpoll millis=" + waitPeriodMillis);
        			
        			monitor.wait(waitPeriodMillis);
        			messageList = PersistenceFactory.getCQSMessagePersistence().receiveMessage(queue, msgParam);
        		}
        		
    			logger.info("event=done_waiting");
        	}*/

        } else {

            CQSMonitor.getInstance().addNumberOfMessagesReturned(queue.getRelativeUrl(), messageList.size());
        	String out = CQSMessagePopulator.getReceiveMessageResponseAfterSerializing(messageList, filterAttributes);
            response.getWriter().println(out);
            
            //asyncContext.complete();
        }
        
        return messageList != null && messageList.size() > 0 ? true : false;
    }
}
