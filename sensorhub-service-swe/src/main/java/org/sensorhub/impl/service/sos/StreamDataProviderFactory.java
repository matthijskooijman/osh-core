/***************************** BEGIN LICENSE BLOCK ***************************

The contents of this file are subject to the Mozilla Public License, v. 2.0.
If a copy of the MPL was not distributed with this file, You can obtain one
at http://mozilla.org/MPL/2.0/.

Software distributed under the License is distributed on an "AS IS" basis,
WITHOUT WARRANTY OF ANY KIND, either express or implied. See the License
for the specific language governing rights and limitations under the License.
 
Copyright (C) 2012-2015 Sensia Software LLC. All Rights Reserved.
 
******************************* END LICENSE BLOCK ***************************/

package org.sensorhub.impl.service.sos;

import java.util.Iterator;
import net.opengis.gml.v32.AbstractFeature;
import net.opengis.sensorml.v20.AbstractProcess;
import net.opengis.swe.v20.DataArray;
import net.opengis.swe.v20.DataComponent;
import net.opengis.swe.v20.DataRecord;
import org.sensorhub.api.event.Event;
import org.sensorhub.api.common.SensorHubException;
import org.sensorhub.api.data.IDataProducer;
import org.sensorhub.api.data.IMultiSourceDataProducer;
import org.sensorhub.api.data.IStreamingDataInterface;
import org.sensorhub.api.event.IEventListener;
import org.sensorhub.api.module.ModuleEvent;
import org.sensorhub.api.module.ModuleEvent.ModuleState;
import org.sensorhub.api.persistence.IFoiFilter;
import org.sensorhub.api.service.ServiceException;
import org.sensorhub.utils.MsgUtils;
import org.vast.data.DataIterator;
import org.vast.ogc.om.IObservation;
import org.vast.ows.OWSException;
import org.vast.ows.sos.SOSOfferingCapabilities;
import org.vast.swe.SWEConstants;
import org.vast.util.TimeExtent;


/**
 * <p>
 * Base factory for streaming data providers.
 * </p>
 *
 * @author Alex Robin
 * @since Feb 28, 2015
 */
public class StreamDataProviderFactory implements ISOSDataProviderFactory, IEventListener
{
    final SOSServlet servlet;
    final StreamDataProviderConfig config;
    final IDataProducer producer;
    long liveDataTimeOut;
    long refTimeOut;
    SOSOfferingCapabilities caps;
    boolean disableEvents;
    
    
    protected StreamDataProviderFactory(SOSServlet servlet, StreamDataProviderConfig config) throws ServiceException
    {
        this.servlet = servlet;
        this.config = config;
        this.liveDataTimeOut = (long)(config.liveDataTimeout * 1000);
        this.refTimeOut = System.currentTimeMillis(); // initial ref for timeout is SOS startup time
        
        // get handle to producer object
        try
        {
            this.producer = (IDataProducer)servlet.getParentHub().getModuleRegistry().getModuleById(config.getProducerID());
        }
        catch (Exception e)
        {
            throw new ServiceException("Data source " + config.getProducerID() + " is not available", e);
        }
        
        // listen to producer lifecycle events
        disableEvents = true; // disable events on startup
        producer.registerListener(this);
        disableEvents = false;
    }
    
    
    /*
     * Constructor for use as alt provider
     * In this mode, we purposely don't handle events
     */
    protected StreamDataProviderFactory(StreamDataProviderConfig config, IDataProducer producer)
    {
        this.servlet = null;
        this.config = config;
        this.producer = producer;
    }
    
    
    @Override
    public SOSOfferingCapabilities generateCapabilities() throws SensorHubException
    {
        checkEnabled();
        
        try
        {
            caps = new SOSOfferingCapabilities();
            
            // identifier
            if (config.offeringID != null)
                caps.setIdentifier(config.offeringID);
            else
                caps.setIdentifier(producer.getUniqueIdentifier());
            
            // name + description
            updateNameAndDescription();
            
            // phenomenon time
            // enable real-time requests only if streaming data source is enabled
            TimeExtent timeExtent = new TimeExtent();
            if (producer.isEnabled())
            {
                timeExtent.setBeginNow(true);
                timeExtent.setEndNow(true);
            }
            caps.setPhenomenonTime(timeExtent);
        
            // use producer uniqueID as procedure ID
            caps.getProcedures().add(producer.getCurrentDescription().getUniqueIdentifier());
            
            // obs properties & obs types
            getObsPropertiesAndTypesFromProducer();
            
            // FOI IDs and BBOX
            SOSProviderUtils.updateFois(caps, producer, config.maxFois);
            
            return caps;
        }
        catch (Exception e)
        {
            throw new ServiceException("Cannot generate capabilities for stream provider " + producer, e);
        }
    }
    
    
    protected void updateNameAndDescription()
    {
        // name
        if (config.name != null)
            caps.setTitle(config.name);
        else
            caps.setTitle(producer.getName());
        
        // description
        if (config.description != null)
            caps.setDescription(config.description);
        else
            caps.setDescription("Live data from " + producer.getName());
    }
    
    
    @Override
    public synchronized void updateCapabilities() throws SensorHubException
    {
        checkEnabled();
        if (caps == null)
            return;
            
        updateNameAndDescription();
        SOSProviderUtils.updateFois(caps, producer, config.maxFois);
        
        // enable real-time requests if streaming data source is enabled
        if (producer.isEnabled())
        {
            // if latest record is not too old, enable real-time
            if (hasNewRecords(liveDataTimeOut))
            {
                caps.getPhenomenonTime().setBeginNow(true);
                caps.getPhenomenonTime().setEndNow(true);
            }
            else
                caps.getPhenomenonTime().nullify();
        }
    }
    
    
    protected boolean hasNewRecords(long maxAge)
    {
        return hasNewRecords(producer, maxAge);
    }
    
    
    protected boolean hasNewRecords(IDataProducer producer, long maxAge)
    {
        long now =  System.currentTimeMillis();
        
        // check if at least one output has recent data
        for (IStreamingDataInterface output: producer.getOutputs().values())
        {
            // skip excluded outputs
            if (config.excludedOutputs != null && config.excludedOutputs.contains(output.getName()))
                continue;
            
            long lastRecordTime = output.getLatestRecordTime();
            if (lastRecordTime == Long.MIN_VALUE)
                lastRecordTime = refTimeOut;
        
            if (now - lastRecordTime < liveDataTimeOut)
                return true;
        }

        // if multi-source, call recursively on child producers
        if (producer instanceof IMultiSourceDataProducer)
        {
            for (IDataProducer childProducer: ((IMultiSourceDataProducer) producer).getMembers().values())
            {
                if (hasNewRecords(childProducer, maxAge))
                    return true;
            }
        }
        
        return false;
    }
    
    
    protected void getObsPropertiesAndTypesFromProducer()
    {
        caps.getObservationTypes().add(IObservation.OBS_TYPE_GENERIC);
        caps.getObservationTypes().add(IObservation.OBS_TYPE_SCALAR);        
        getObsPropertiesAndTypesFromProducer(producer);
    }
    
        
    protected void getObsPropertiesAndTypesFromProducer(IDataProducer producer)
    {
        // scan outputs descriptions
        for (IStreamingDataInterface output: producer.getOutputs().values())
        {
            // skip excluded outputs
            if (config.excludedOutputs != null && config.excludedOutputs.contains(output.getName()))
                continue;
            
            // obs type only depends on top-level component            
            DataComponent dataStruct = output.getRecordDescription();
            if (dataStruct instanceof DataRecord)
                caps.getObservationTypes().add(IObservation.OBS_TYPE_RECORD);
            else if (dataStruct instanceof DataArray)
                caps.getObservationTypes().add(IObservation.OBS_TYPE_ARRAY);
    
            // iterate through all SWE components and add all definition URIs as observables
            // this way only composites with URI will get added
            DataIterator it = new DataIterator(output.getRecordDescription());
            while (it.hasNext())
            {
                String defUri = it.next().getDefinition();
                if (defUri != null && !defUri.equals(SWEConstants.DEF_SAMPLING_TIME))
                    caps.getObservableProperties().add(defUri);
            }
        }
        
        // if multisource, call recursively on child producers
        if (producer instanceof IMultiSourceDataProducer)
        {
            for (IDataProducer childProducer: ((IMultiSourceDataProducer) producer).getMembers().values())
                getObsPropertiesAndTypesFromProducer(childProducer);
        }
    }
    
    
    @Override
    public AbstractProcess generateSensorMLDescription(double time) throws SensorHubException
    {
        checkEnabled();
        return producer.getCurrentDescription();
    }
    
    
    @Override
    public Iterator<AbstractFeature> getFoiIterator(final IFoiFilter filter) throws SensorHubException
    {
        checkEnabled();
        return SOSProviderUtils.getFilteredFoiIterator(producer, filter);
    }
    
    
    /*
     * Checks if provider and underlying sensor are enabled
     */
    protected void checkEnabled() throws SensorHubException
    {
        if (!config.enabled)
            throw new ServiceException("Offering " + config.offeringID + " is disabled");
                
        if (!producer.isEnabled())
            throw new ServiceException("Data source '" + MsgUtils.entityString(producer) + "' is disabled");    }


    @Override
    public void handleEvent(Event e)
    {
        if (disableEvents)
            return;
        
        // producer events
        if (e instanceof ModuleEvent && e.getSource() == producer)
        {
            switch (((ModuleEvent)e).getType())
            {
                // show/hide offering when enabled/disabled
                case STATE_CHANGED:
                    ModuleState state = ((ModuleEvent)e).getNewState();
                    if (state == ModuleState.STARTED || state == ModuleState.STOPPING)
                    {
                        if (isEnabled())
                            servlet.showProviderCaps(this);
                        else
                            servlet.hideProviderCaps(this);
                    }
                    break;
                
                // cleanly remove provider when producer is deleted
                case DELETED:
                    servlet.removeProvider(config.offeringID);
                    break;
                    
                default:
                    return;
            }
        }      
    }


    @Override
    public ISOSDataProvider getNewDataProvider(SOSDataFilter filter) throws OWSException, SensorHubException
    {
        checkEnabled();
        return new StreamDataProvider(producer, config, filter);
    }


    @Override
    public void cleanup()
    {
        producer.unregisterListener(this);
    }


    @Override
    public boolean isEnabled()
    {
        return (config.enabled && producer.isEnabled());
    }
    
    
    @Override
    public StreamDataProviderConfig getConfig()
    {
        return this.config;
    }
}
