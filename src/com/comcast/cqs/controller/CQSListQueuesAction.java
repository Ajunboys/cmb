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
package com.comcast.plaxo.cqs.controller;

import java.util.List;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.comcast.plaxo.cmb.common.model.CMBPolicy;
import com.comcast.plaxo.cmb.common.model.User;
import com.comcast.plaxo.cmb.common.persistence.PersistenceFactory;
import com.comcast.plaxo.cqs.io.CQSQueuePopulator;
import com.comcast.plaxo.cqs.model.CQSQueue;
/**
 * List queues
 * @author baosen, bwolf, vvenkatraman
 *
 */
public class CQSListQueuesAction extends CQSAction {	
	
	public CQSListQueuesAction() {
		super("ListQueues");
	}		
	
	@Override
	public boolean doAction(User user, HttpServletRequest request, HttpServletResponse response) throws Exception {
		String prefix = request.getParameter("QueueNamePrefix");

        List<CQSQueue> queues = PersistenceFactory.getQueuePersistence().listQueues(user.getUserId(), prefix);

        String out = CQSQueuePopulator.getListQueuesResponse(queues);

        response.getWriter().print(out);
        
        return true;
	}

	@Override
	public boolean isActionAllowed(User user, HttpServletRequest request, String service, CMBPolicy policy) {
	    return true;
	}
}
