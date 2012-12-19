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
import java.io.PrintWriter;
import java.util.List;
import java.util.Map;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.log4j.Logger;

import com.amazonaws.services.sqs.model.DeleteMessageRequest;
import com.amazonaws.services.sqs.model.SendMessageRequest;
import com.comcast.cmb.common.controller.AdminServlet;
import com.comcast.cmb.common.controller.AdminServletBase;
import com.comcast.cmb.common.controller.CMBControllerServlet;
import com.comcast.cqs.model.CQSMessage;
import com.comcast.cqs.persistence.RedisCachedCassandraPersistence;
import com.comcast.cqs.util.Util;

/**
 * Admin page for showing messages in a queue
 * @author aseem, vvenkatraman, bwolf, baosen
 *
 */
public class CQSQueueMessagesPageServlet extends AdminServletBase {
	
	private static final long serialVersionUID = 1L;
	private static Logger logger = Logger.getLogger(CQSQueueMessagesPageServlet.class);
	
	public void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		
	    CMBControllerServlet.valueAccumulator.initializeAllCounters();
		response.setContentType("text/html");
		PrintWriter out = response.getWriter();
		String userId = request.getParameter("userId");
		String queueName = request.getParameter("queueName");
		String msgStr = request.getParameter("message");
		String prevHandle = request.getParameter("prevHandle");
		String nextHandle = request.getParameter("nextHandle");
		String receiptHandle = request.getParameter("receiptHandle");
		Map<?, ?> parameters = request.getParameterMap();

		String queueUrl = Util.getAbsoluteQueueUrlForName(queueName, userId);
		String relativeQueueUrl = Util.getRelativeQueueUrlForName(queueName, userId);

		connect(userId);
		
		RedisCachedCassandraPersistence messagePersistence = RedisCachedCassandraPersistence.getInstance();
		
		if (parameters.containsKey("Send")) {
			
			try {
			
				SendMessageRequest sendMessageRequest = new SendMessageRequest(queueUrl, msgStr);
				sqs.sendMessage(sendMessageRequest);
			
			} catch (Exception ex) {
				logger.error("event=sendMessage status=failed queue_url= " + queueUrl, ex);
				throw new ServletException(ex);
			}
			
		} else if (parameters.containsKey("Delete")) {
			
			try {
				
				DeleteMessageRequest deleteMessageRequest = new DeleteMessageRequest(queueUrl, receiptHandle);
				sqs.deleteMessage(deleteMessageRequest);
			
			} catch (Exception ex) {
				logger.error("event=deleteMessage status=failed queue_url= " + queueUrl + " receipt_handle=" + receiptHandle, ex);
				throw new ServletException(ex);
			}
		}
		
		out.println("<html>");
		out.println("<head><title>Messages for Queue " + queueName + "</title></head><body>");
		
		header(request, out);
		
		out.println("<h2>Messages for Queue " + queueName + "</h2>");
		
		if (user != null) {
			out.println("<table><tr><td><b>User Name:</b></td><td>"+ user.getUserName()+"</td></tr>");
			out.println("<tr><td><b>User ID:</b></td><td>"+ user.getUserId()+"</td></tr>");
			out.println("<tr><td><b>Access Key:</b></td><td>"+user.getAccessKey()+"</td></tr>");
			out.println("<tr><td><b>Access Secret:</b></td><td>"+user.getAccessSecret()+"</td></tr>");
			out.println("<tr><td><b>Queue Name:</b></td><td>"+queueName+"</td></tr></table>");
		}
		
        out.print("<p><table><tr><td><b>Message</b></td><td></td></tr>");
        out.print("<form action=\"");
        out.print(response.encodeURL("MESSAGE") + "?userId="+userId+"&queueName="+queueName);
        out.print("\" ");
        out.println("method=POST>");
        out.print("<tr><td><textarea rows='3' cols='50' name='message'></textarea><input type='hidden' name='userId' value='"+ userId + "'></td><td valign='bottom'><input type='submit' value='Send' name='Send' /></td></tr></form></table></p>");

        List<CQSMessage> msgs = null;
        
		try {
			
			if (queueUrl != null) {
				
				if (prevHandle != null) {
					msgs = messagePersistence.peekQueue(relativeQueueUrl, prevHandle, null, 10);
				} else if (nextHandle != null) {
					msgs = messagePersistence.peekQueue(relativeQueueUrl, null, nextHandle, 10);
				} else {
					msgs = messagePersistence.peekQueue(relativeQueueUrl, null, null, 10);
				}
			}
		
		} catch (Exception ex) {
			logger.error("event=peekQueue status=failed queue_url=" + queueUrl, ex);
			throw new ServletException(ex);
		}

		String previousHandle = null;
		nextHandle = null;
        
		for (int i = 0; msgs != null && i < msgs.size(); i++) {
        
			CQSMessage cqsMsg = msgs.get(i);
        	String msg = cqsMsg.getBody();
        	Map<String, String> attrs = cqsMsg.getAttributes();
        	String createdDateStr = "";
        	
        	if (attrs.get("CreatedTime") != null) {
        		createdDateStr = attrs.get("CreatedTime");
        	}
        	
        	if (i == 0) {
        		out.println("<p><hr width='100%' align='left' /><p><table border='1' width='100%'>");
        		out.println("<tr><td><b>Message</b></td>");
        		out.println("<td><b>Created Date</b></td>");
        		out.println("<td>&nbsp;</td></tr>");
        		previousHandle = cqsMsg.getReceiptHandle();
        	}
        	
        	out.print("<form action=\"");
            out.print(response.encodeURL("MESSAGE") + "?userId="+user.getUserId()+"&queueName="+queueName+"&receiptHandle="+cqsMsg.getReceiptHandle());
            out.print("\" ");
            out.println("method=POST>");
        	out.println("<td>" + msg + "</td>");
        	out.println("<td>"+ createdDateStr + "</td>");
		    out.println("<td><input type='submit' value='Delete' name='Delete' /><input type='hidden' name='queueUrl' value='"+ queueUrl+ "' /></td></tr></form>");
		    
		    if (i == msgs.size() - 1) {
		    	nextHandle = cqsMsg.getReceiptHandle();
		    }
        }
		
        out.println("</table>");
        
        if (prevHandle != null) {
        	
        	if (previousHandle != null) {
        		out.println("<a style='float:left;' href='" + response.encodeURL("MESSAGE") + "?userId="+user.getUserId()+"&queueName="+queueName+"&nextHandle="+previousHandle+"'>Prev</a>");
        	} else {
        		out.println("<a style='float:left;' href='javascript:history.back()'>Prev</a>");
        	}
        }
        
        if (msgs != null && msgs.size() > 0) {
        	out.println("<a style='float:right;' href='" + response.encodeURL("MESSAGE") + "?userId="+user.getUserId()+"&queueName="+queueName+"&prevHandle="+nextHandle+"'>Next</a>");
        }
        
        out.println("<h5 style='text-align:center;'><a href='"+ response.encodeRedirectURL(AdminServlet.cqsAdminUrl)+ "'>ADMIN HOME</a>");
        out.println("<a href='"+AdminServlet.cqsAdminBaseUrl+"CQSUser?userId="+userId+"'>BACK TO QUEUE</a></h5>");
        
        out.println("</body></html>");

        CMBControllerServlet.valueAccumulator.deleteAllCounters();
	}
	
	protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		doGet(request, response);
	}
}
