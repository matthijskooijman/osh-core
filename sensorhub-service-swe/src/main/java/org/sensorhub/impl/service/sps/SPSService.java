/***************************** BEGIN LICENSE BLOCK ***************************

The contents of this file are subject to the Mozilla Public License, v. 2.0.
If a copy of the MPL was not distributed with this file, You can obtain one
at http://mozilla.org/MPL/2.0/.

Software distributed under the License is distributed on an "AS IS" basis,
WITHOUT WARRANTY OF ANY KIND, either express or implied. See the License
for the specific language governing rights and limitations under the License.
 
Copyright (C) 2012-2015 Sensia Software LLC. All Rights Reserved.
 
******************************* END LICENSE BLOCK ***************************/

package org.sensorhub.impl.service.sps;

import org.sensorhub.api.event.Event;
import org.sensorhub.api.common.SensorHubException;
import org.sensorhub.api.event.IEventListener;
import org.sensorhub.api.module.ModuleEvent;
import org.sensorhub.api.module.ModuleEvent.ModuleState;
import org.sensorhub.api.service.IServiceModule;
import org.sensorhub.impl.module.AbstractModule;
import org.sensorhub.impl.service.HttpServer;


/**
 * <p>
 * Implementation of SensorHub generic SPS service.
 * The service can manage any of the sensors installed on the SensorHub instance
 * and is configured automatically from the information generated by the sensors
 * interfaces.
 * </p>
 *
 * @author Alex Robin
 * @since Jan 15, 2015
 */
public class SPSService extends AbstractModule<SPSServiceConfig> implements IServiceModule<SPSServiceConfig>, IEventListener
{
    SPSServlet servlet;
    
    
    @Override
    public void requestStart() throws SensorHubException
    {
        if (canStart())
        {
            HttpServer httpServer = HttpServer.getInstance();
            if (httpServer == null)
                throw new SensorHubException("HTTP server module is not loaded");
            
            // subscribe to server lifecycle events
            httpServer.registerListener(this);
            
            // we actually start in the handleEvent() method when
            // a STARTED event is received from HTTP server
        }
    }    
    
    
    @Override
    public void setConfiguration(SPSServiceConfig config)
    {
        super.setConfiguration(config);
        this.securityHandler = new SPSSecurity(this, config.security.enableAccessControl);
    }
    
    
    @Override
    public void start() throws SensorHubException
    {
        // deploy servlet
        servlet = new SPSServlet(this, (SPSSecurity)this.securityHandler, getLogger());
        deploy();
        
        setState(ModuleState.STARTED);        
    }
    
    
    @Override
    public void stop()
    {
        // undeploy servlet
        undeploy();        
        if (servlet != null)
            servlet.stop();
        servlet = null;
        
        setState(ModuleState.STOPPED);
    }
    
    
    protected void deploy() throws SensorHubException
    {
        HttpServer httpServer = HttpServer.getInstance();
        if (httpServer == null || !httpServer.isStarted())
            throw new SensorHubException("An HTTP server instance must be started");
        
        // deploy ourself to HTTP server
        httpServer.deployServlet(servlet, config.endPoint);
        httpServer.addServletSecurity(config.endPoint, config.security.requireAuth);
    }
    
    
    protected void undeploy()
    {
        HttpServer httpServer = HttpServer.getInstance();
        
        // return silently if HTTP server missing on stop
        if (httpServer == null || !httpServer.isStarted())
            return;
        
        httpServer.undeployServlet(servlet);
    }
    
    
    @Override
    public void cleanup() throws SensorHubException
    {
        // stop listening to http server events
        HttpServer httpServer = HttpServer.getInstance();
        if (httpServer != null)
            httpServer.unregisterListener(this);
        
        // unregister security handler
        if (securityHandler != null)
            securityHandler.unregister();
    }
    
    
    @Override
    public void handleEvent(Event e)
    {
        // catch HTTP server lifecycle events
        if (e instanceof ModuleEvent && e.getSource() == HttpServer.getInstance())
        {
            ModuleState newState = ((ModuleEvent) e).getNewState();
            
            // start when HTTP server is enabled
            if (newState == ModuleState.STARTED)
            {
                try
                {
                    start();
                }
                catch (Exception ex)
                {
                    reportError("SPS Service could not start", ex);
                }
            }
            
            // stop when HTTP server is disabled
            else if (newState == ModuleState.STOPPED)
                stop();
        }
    }
}
