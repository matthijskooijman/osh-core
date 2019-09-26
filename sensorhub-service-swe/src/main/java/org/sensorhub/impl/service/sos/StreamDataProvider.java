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

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import net.opengis.gml.v32.AbstractFeature;
import net.opengis.swe.v20.DataBlock;
import net.opengis.swe.v20.DataComponent;
import net.opengis.swe.v20.DataEncoding;
import org.sensorhub.api.event.Event;
import org.sensorhub.api.data.DataEvent;
import org.sensorhub.api.data.FoiEvent;
import org.sensorhub.api.data.IDataProducer;
import org.sensorhub.api.data.IMultiSourceDataProducer;
import org.sensorhub.api.data.IStreamingDataInterface;
import org.sensorhub.api.event.IEventListener;
import org.sensorhub.api.module.IModule;
import org.sensorhub.utils.DataStructureHash;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.vast.data.DataIterator;
import org.vast.ogc.om.IObservation;
import org.vast.ows.OWSException;
import org.vast.ows.sos.SOSException;
import org.vast.swe.SWEConstants;
import org.vast.util.Asserts;
import org.vast.util.TimeExtent;


/**
 * <p>
 * Implementation of SOS data provider connecting to a streaming data source
 * </p>
 *
 * @author Alex Robin
 * @since Sep 7, 2013
 */
public class StreamDataProvider implements ISOSDataProvider, IEventListener
{
    private static final Logger log = LoggerFactory.getLogger(StreamDataProvider.class);
    private static final int DEFAULT_QUEUE_SIZE = 200;

    final IDataProducer dataSource;
    final String selectedOutput;
    final BlockingQueue<DataEvent> eventQueue;
    final long timeOut;
    final long stopTime;
    final boolean latestRecordOnly;
        
    boolean isMultiSource = false;
    DataComponent resultStruct;
    DataEncoding resultEncoding;  
    DataStructureHash resultStructureHash;
    long lastQueueErrorTime = Long.MIN_VALUE;

    DataEvent lastDataEvent;
    int nextEventRecordIndex = 0;
    Set<String> requestedFois;
    Map<String, String> currentFoiMap = new LinkedHashMap<>(); // producerID ID -> current FOI ID
    

    public StreamDataProvider(IDataProducer dataSource, StreamDataProviderConfig config, SOSDataFilter filter) throws OWSException
    {
        this.dataSource = dataSource;
        
        // figure out number of potential producers
        int numProducers = 1;
        if (dataSource instanceof IMultiSourceDataProducer)
        {
            if (!currentFoiMap.isEmpty())
                numProducers = currentFoiMap.size();
            else
                numProducers = ((IMultiSourceDataProducer)dataSource).getMembers().size();
        }

        // create queue with proper size
        eventQueue = new LinkedBlockingQueue<>(Math.max(DEFAULT_QUEUE_SIZE, numProducers));
        
        // find selected output
        selectedOutput = findOutput(dataSource, config.excludedOutputs, filter.getObservables());
        Asserts.checkNotNull(selectedOutput, "selectedOutput");

        // scan FOIs
        if (!filter.getFoiIds().isEmpty())
        {
            requestedFois = filter.getFoiIds();
            String badFoi = null;
            
            // fill up initial FOI map
            if (dataSource instanceof IMultiSourceDataProducer)
            {
                for (String foiID : filter.getFoiIds())
                {
                    Collection<String> producerIDs = ((IMultiSourceDataProducer) dataSource).getProceduresWithFoi(foiID);
                    if (producerIDs.isEmpty())
                        badFoi = foiID;

                    for (String producerID: producerIDs)
                        currentFoiMap.put(producerID, foiID);
                }
            }
            else
            {
                // error if no FOI is currently being observed
                AbstractFeature foi = dataSource.getCurrentFeatureOfInterest();
                if (foi == null || !requestedFois.contains(foi.getUniqueIdentifier()))
                    badFoi = requestedFois.iterator().next();
                
                currentFoiMap.put(null, dataSource.getCurrentFeatureOfInterest().getUniqueIdentifier());
            }
            
            // send error if foi is not currently observed
            if (badFoi != null)
                throw new SOSException(SOSException.invalid_param_code, "featureOfInterest", badFoi, "No real-time data available for FOI " + badFoi);
        }

        // detect if only latest records are requested
        latestRecordOnly = isNowTimeInstant(filter.getTimeRange());
        if (latestRecordOnly)
        {
            stopTime = Long.MAX_VALUE; // make sure stoptime does not cause us to return null
            timeOut = 0L;
        }
        else
        {
            stopTime = ((long) filter.getTimeRange().getStopTime()) * 1000L;
            timeOut = (long) (config.liveDataTimeout * 1000);
        }
        
        // connect to data source
        connectDataSource(dataSource);
    }
    
    
    protected String findOutput(IDataProducer producer, List<String> excludedOutputs, Set<String> defUris)
    {
        for (IStreamingDataInterface output : producer.getOutputs().values())
        {
            // skip hidden outputs
            if (excludedOutputs != null && excludedOutputs.contains(output.getName()))
                continue;

            // keep it if we can find one of the observables
            DataIterator it = new DataIterator(output.getRecordDescription());
            while (it.hasNext())
            {
                String defUri = it.next().getDefinition();
                if (defUris.contains(defUri))
                {                    
                    // use the first output found since we only support requesting data from one output at a time
                    // TODO support case of multiple outputs since it is technically possible with GetObservation
                    
                    resultStruct = output.getRecordDescription();
                    resultStructureHash = new DataStructureHash(resultStruct);
                    resultEncoding = output.getRecommendedEncoding();
                    return output.getName();
                }
            }
        }
                
        // if multi producer, try to find output in any of the nested producers
        if (producer instanceof IMultiSourceDataProducer)
        {
            IMultiSourceDataProducer multiSource = (IMultiSourceDataProducer)producer;
            for (IDataProducer member: multiSource.getMembers().values())
            {
                // return the first one we find
                String outputName = findOutput(member, excludedOutputs, defUris);
                if (outputName != null)
                    return outputName;
            }
        }
        
        return null;
    }

    
    protected void connectDataSource(IDataProducer producer)
    {
        // if multisource, call recursively to connect nested producers
        if (producer instanceof IMultiSourceDataProducer)
        {
            IMultiSourceDataProducer multiSource = (IMultiSourceDataProducer)producer;            
            for (IDataProducer member: multiSource.getMembers().values())
                connectDataSource(member);
        }
        
        // skip if foi was not requested
        if (requestedFois != null && !requestedFois.contains(producer.getCurrentFeatureOfInterest().getUniqueIdentifier()))
            return;

        // get selected output
        IStreamingDataInterface output = producer.getOutputs().get(selectedOutput);
        if (output == null)
            return;
        
        // only use output if structure is compatible with selected output
        // needed in case there is an output with the same name but different structure
        if (!resultStructureHash.equals(new DataStructureHash(output.getRecordDescription())))
            return;
        
        // always send latest record if available                 
        DataBlock data = output.getLatestRecord();
        if (data != null)
            eventQueue.offer(new DataEvent(System.currentTimeMillis(), output, data));

            // otherwise register listener to stream next records
        if (!latestRecordOnly)
            output.registerListener(this);
    }
    
    
    protected void disconnectDataSource(IDataProducer producer)
    {
        // get selected output
        IStreamingDataInterface output = producer.getOutputs().get(selectedOutput);
        if (output != null)
            output.unregisterListener(this);
        
        // if multisource, call recursively to disconnect nested producers
        if (producer instanceof IMultiSourceDataProducer)
        {
            IMultiSourceDataProducer multiSource = (IMultiSourceDataProducer)producer;            
            for (IDataProducer member: multiSource.getMembers().values())
                disconnectDataSource(member);
        }
    }


    protected boolean isNowTimeInstant(TimeExtent timeFilter)
    {
        if (timeFilter.isTimeInstant() && timeFilter.isBaseAtNow())
            return true;

        return false;
    }


    @Override
    public IObservation getNextObservation()
    {
        DataBlock rec = getNextResultRecord();
        if (rec == null)
            return null;
        
        return buildObservation(rec);
    }
    
    
    protected IObservation buildObservation(DataBlock rec)
    {
        resultStruct.setData(rec);
        
        // FOI
        AbstractFeature foi = dataSource.getCurrentFeatureOfInterest();
        if (dataSource instanceof IMultiSourceDataProducer)
        {
            String producerID = lastDataEvent.getProcedureUID();
            IDataProducer producer = ((IMultiSourceDataProducer)dataSource).getMembers().get(producerID);
            Asserts.checkNotNull(producer, IDataProducer.class);
            foi = producer.getCurrentFeatureOfInterest();
        }

        String foiID;
        if (foi != null)
            foiID = foi.getUniqueIdentifier();
        else
            foiID = SWEConstants.NIL_UNKNOWN;

        return SOSProviderUtils.buildObservation(resultStruct, foiID, dataSource.getCurrentDescription().getUniqueIdentifier());
    }


    @Override
    public DataBlock getNextResultRecord()
    {
        if (eventQueue.isEmpty() && !hasMoreData())
            return null;

        try
        {
            // only poll next event from queue once we have returned all records associated to last event
            if (lastDataEvent == null || nextEventRecordIndex >= lastDataEvent.getRecords().length)
            {
                lastDataEvent = eventQueue.poll(timeOut, TimeUnit.MILLISECONDS);
                if (lastDataEvent == null)
                    return null;

                // we stop if record is passed the given stop date
                if (lastDataEvent.getTimeStamp() > stopTime)
                    return null;

                nextEventRecordIndex = 0;
            }

            //System.out.println("->" + new DateTimeFormat().formatIso(lastDataEvent.getTimeStamp()/1000., 0));
            DataBlock dataBlk = lastDataEvent.getRecords()[nextEventRecordIndex++];
            return dataBlk;

            // TODO add choice token value if request includes several outputs
        }
        catch (InterruptedException e)
        {
            Thread.currentThread().interrupt();
            return null;
        }
    }


    /*
     * For real-time streams, more data is always available unless
     * sensor is disabled or all sensor outputs are disabled
     */
    private boolean hasMoreData()
    {
        if (dataSource instanceof IModule<?> && !((IModule<?>)dataSource).isStarted())
            return false;

        return hasMoreData(dataSource);
    }
    
    
    private boolean hasMoreData(IDataProducer producer)
    {
        IStreamingDataInterface output = producer.getOutputs().get(selectedOutput);
        if (output != null && output.isEnabled())
            return true;
        
        // if multi producer, also check if outputs of nested producers have more data
        if (producer instanceof IMultiSourceDataProducer)
        {
            IMultiSourceDataProducer multiSource = (IMultiSourceDataProducer)producer;            
            for (IDataProducer member: multiSource.getMembers().values())
            {
                if (hasMoreData(member))
                    return true;
            }
        }

        return false;
    }


    @Override
    public DataComponent getResultStructure()
    {
        return resultStruct;
    }


    @Override
    public DataEncoding getDefaultResultEncoding()
    {
        return resultEncoding;
    }


    @Override
    public void handleEvent(Event e)
    {
        if (e instanceof DataEvent)
        {
            // check foi if filtering on it
            if (requestedFois != null)
            {
                // skip if procedure/foi was not selected
                String producerID = ((DataEvent) e).getProcedureUID();
                String foiID = currentFoiMap.get(producerID);
                if (!requestedFois.contains(foiID))
                    return;
            }

            // try to add to queue
            if (!eventQueue.offer((DataEvent) e))
            {
                long now = System.currentTimeMillis();
                if (now - lastQueueErrorTime > 10000)
                {
                    log.warn("Maximum queue size reached while streaming data from {}. "
                           + "Some records will be discarded. This is often due to insufficient bandwidth", dataSource);
                    lastQueueErrorTime = now;
                }
            }
        }

        else if (e instanceof FoiEvent && requestedFois != null)
        {
            // remember current FOI of each producer
            FoiEvent foiEvent = (FoiEvent) e;
            String producerID = ((FoiEvent) e).getProcedureUID();
            currentFoiMap.put(producerID, foiEvent.getFoiUID());
        }
    }


    @Override
    public boolean hasMultipleProducers()
    {
        return dataSource instanceof IMultiSourceDataProducer;
    }


    @Override
    public String getProducerIDPrefix()
    {
        if (dataSource instanceof IMultiSourceDataProducer)
        {
            IMultiSourceDataProducer multiProducer = (IMultiSourceDataProducer)dataSource;
            StringBuilder prefix = new StringBuilder();
            boolean first = true;
            
            // try to detect common prefix            
            for (String uid: multiProducer.getMembers().keySet())
            {
                if (first)
                {
                    prefix = new StringBuilder(uid);
                    first = false;                    
                }
                else
                {
                    // prefix cannot be longer than ID
                    if (prefix.length() > uid.length())
                        prefix.setLength(uid.length());
                    
                    // keep only common chars
                    for (int i = 0; i < prefix.length(); i++)
                    {
                        if (uid.charAt(i) != prefix.charAt(i))
                        {
                            prefix.setLength(i);
                            break;
                        }
                    }
                }                    
            }
            
            return prefix.toString();
        }
        
        return null;
    }


    @Override
    public String getNextProducerID()
    {
        return lastDataEvent.getProcedureUID();
    }


    @Override
    public void close()
    {
        if (!latestRecordOnly)
            disconnectDataSource(dataSource);
        
        eventQueue.clear();
    }
}
