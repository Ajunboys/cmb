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
package com.comcast.plaxo.cmb.common.controller;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.comcast.plaxo.cmb.common.model.User;
import com.comcast.plaxo.cqs.controller.CQSAction;

/**
 * Provide a gasic health-check URL for load-balancers to hit to monitor weather service is up and version
 * @author aseem
 */
public class HealthCheckShallow extends CQSAction {

    public HealthCheckShallow() {
        super("healthCheckShallow");
    }

    @Override
    public boolean doAction(User user, HttpServletRequest request, HttpServletResponse response) throws Exception {        
        response.setStatus(HttpServletResponse.SC_OK);
        response.getOutputStream().print("<HealthCheckShallowResponse>CNS/CQS Service Version " + CMBControllerServlet.VERSION + " Up</HealthCheckShallowResponse>");
        response.flushBuffer();
        return true;
    }
    
    @Override
    public boolean isAuthRequired() {
        return false;
    }
}
