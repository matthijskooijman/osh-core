/***************************** BEGIN LICENSE BLOCK ***************************

The contents of this file are subject to the Mozilla Public License, v. 2.0.
If a copy of the MPL was not distributed with this file, You can obtain one
at http://mozilla.org/MPL/2.0/.

Software distributed under the License is distributed on an "AS IS" basis,
WITHOUT WARRANTY OF ANY KIND, either express or implied. See the License
for the specific language governing rights and limitations under the License.
 
Copyright (C) 2020 Sensia Software LLC. All Rights Reserved.
 
******************************* END LICENSE BLOCK ***************************/

package org.sensorhub.impl.service.sweapi.obs;

import java.io.IOException;
import java.math.BigInteger;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Flow.Subscriber;
import java.util.concurrent.Flow.Subscription;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import org.sensorhub.api.data.IDataStreamInfo;
import org.sensorhub.api.data.IObsData;
import org.sensorhub.api.data.ObsEvent;
import org.sensorhub.api.datastore.DataStoreException;
import org.sensorhub.api.datastore.SpatialFilter;
import org.sensorhub.api.datastore.obs.DataStreamKey;
import org.sensorhub.api.datastore.obs.IObsStore;
import org.sensorhub.api.datastore.obs.ObsFilter;
import org.sensorhub.api.event.EventUtils;
import org.sensorhub.api.event.IEventBus;
import org.sensorhub.impl.service.sweapi.IdConverter;
import org.sensorhub.impl.service.sweapi.IdEncoder;
import org.sensorhub.impl.service.sweapi.InvalidRequestException;
import org.sensorhub.impl.service.sweapi.ObsSystemDbWrapper;
import org.sensorhub.impl.service.sweapi.ServiceErrors;
import org.sensorhub.impl.service.sweapi.SWEApiSecurity.ResourcePermissions;
import org.sensorhub.impl.service.sweapi.feature.FoiHandler;
import org.sensorhub.impl.service.sweapi.resource.BaseResourceHandler;
import org.sensorhub.impl.service.sweapi.resource.RequestContext;
import org.sensorhub.impl.service.sweapi.resource.ResourceFormat;
import org.sensorhub.impl.service.sweapi.stream.StreamHandler;
import org.sensorhub.impl.system.DataStreamTransactionHandler;
import org.sensorhub.impl.system.SystemDatabaseTransactionHandler;
import org.sensorhub.impl.service.sweapi.resource.ResourceBinding;
import org.sensorhub.impl.service.sweapi.resource.RequestContext.ResourceRef;
import org.sensorhub.utils.CallbackException;
import org.vast.util.Asserts;


public class ObsHandler extends BaseResourceHandler<BigInteger, IObsData, ObsFilter, IObsStore>
{
    public static final int EXTERNAL_ID_SEED = 71145893;
    public static final String[] NAMES = { "observations" };
    
    final IEventBus eventBus;
    final ObsSystemDbWrapper db;
    final SystemDatabaseTransactionHandler transactionHandler;
    final ScheduledExecutorService threadPool;
    final IdConverter idConverter;
    final IdEncoder dsIdEncoder = new IdEncoder(DataStreamHandler.EXTERNAL_ID_SEED);
    final IdEncoder foiIdEncoder = new IdEncoder(FoiHandler.EXTERNAL_ID_SEED);
    final Map<String, CustomObsFormat> customFormats;
    
    
    public static class ObsHandlerContextData
    {
        public long dsID;
        public IDataStreamInfo dsInfo;
        public long foiId;
        public DataStreamTransactionHandler dsHandler;
    }
    
    
    public ObsHandler(IEventBus eventBus, ObsSystemDbWrapper db, ScheduledExecutorService threadPool, ResourcePermissions permissions, Map<String, CustomObsFormat> customFormats)
    {
        super(db.getObservationStore(), new IdEncoder(EXTERNAL_ID_SEED), permissions);
        
        this.eventBus = eventBus;
        this.db = db;
        this.transactionHandler = new SystemDatabaseTransactionHandler(eventBus, db.getWriteDb(), db.getDatabaseRegistry());
        this.threadPool = threadPool;
        this.idConverter = db.getIdConverter();
        this.customFormats = Asserts.checkNotNull(customFormats);
    }
    
    
    @Override
    protected ResourceBinding<BigInteger, IObsData> getBinding(RequestContext ctx, boolean forReading) throws IOException
    {
        var contextData = new ObsHandlerContextData();
        ctx.setData(contextData);
        
        // try to fetch datastream since it's needed to configure binding
        var publicDsID = ctx.getParentID();
        if (publicDsID > 0)
            contextData.dsInfo = db.getDataStreamStore().get(new DataStreamKey(publicDsID));
                
        if (forReading)
        {
            // when ingesting obs, datastream should be known at this stage
            Asserts.checkNotNull(contextData.dsInfo, IDataStreamInfo.class);
            
            // create transaction handler here so it can be reused multiple times
            contextData.dsID = idConverter.toInternalID(publicDsID);
            contextData.dsHandler = transactionHandler.getDataStreamHandler(publicDsID);
            if (contextData.dsHandler == null)
                throw ServiceErrors.notWritable();
            
            // try to parse featureOfInterest argument
            String foiArg = ctx.getParameter("foi");
            if (foiArg != null)
            {
                long publicFoiID = decodeID(ctx, foiArg);
                if (!db.getFoiStore().contains(publicFoiID))
                    throw ServiceErrors.badRequest("Invalid FOI ID");
                contextData.foiId = idConverter.toInternalID(publicFoiID);
            }
        }
        
        // select binding depending on format
        // use a custom format if auto-detected or available for selected mime type
        var binding = getCustomFormatBinding(ctx, contextData.dsInfo);
        if (binding != null)
            return binding;
        
        // otherwise use standard formats
        // default to OM JSON
        var format = ctx.getFormat();
        if (format.equals(ResourceFormat.AUTO))
            format = ResourceFormat.OM_JSON;
        
        if (format.isOneOf(ResourceFormat.JSON, ResourceFormat.OM_JSON))
            return new ObsBindingOmJson(ctx, idEncoder, forReading, dataStore);
        else
            return new ObsBindingSweCommon(ctx, idEncoder, forReading, dataStore);
    }
    
    
    protected ResourceBinding<BigInteger, IObsData> getCustomFormatBinding(RequestContext ctx, IDataStreamInfo dsInfo) throws IOException
    {
        var format = ctx.getFormat();
        CustomObsFormat obsFormat = null;
        
        // try to auto select format when requesting from browser
        if (format.equals(ResourceFormat.AUTO) && isHttpRequestFromBrowser(ctx))
        {
            for (var entry: customFormats.entrySet())
            {
                var mimeType = entry.getKey();
                var formatImpl = entry.getValue();
                
                if (formatImpl.isCompatible(dsInfo))
                {
                    obsFormat = formatImpl;
                    ctx.getLogger().info("Auto-selecting format {}", mimeType);
                    break;
                }
            }
        }
        
        // otherwise just use format implementation for selected mime type
        if (obsFormat == null)
            obsFormat = customFormats.get(format.getMimeType());
        
        return obsFormat != null ? obsFormat.getObsBinding(ctx, idEncoder, dsInfo) : null;
    }
    
    
    /*
     * Check if request comes from a compatible browser
     */
    protected boolean isHttpRequestFromBrowser(RequestContext ctx)
    {
        // don't do multipart with websockets or MQTT!
        if (ctx.isStreamRequest())
            return false;
        
        String userAgent = ctx.getRequestHeader("User-Agent");
        if (userAgent == null)
            return false;

        if (userAgent.contains("Firefox") ||
            userAgent.contains("Chrome") ||
            userAgent.contains("Safari"))
            return true;

        return false;
    }
    
    
    @Override
    public void doPost(RequestContext ctx) throws IOException
    {
        if (ctx.isEndOfPath() &&
            !(ctx.getParentRef().type instanceof DataStreamHandler))
            throw ServiceErrors.unsupportedOperation("Observations can only be created within a Datastream");
        
        super.doPost(ctx);
    }
    
    
    protected void subscribe(final RequestContext ctx) throws InvalidRequestException, IOException
    {
        ctx.getSecurityHandler().checkPermission(permissions.stream);
        Asserts.checkNotNull(ctx.getStreamHandler(), StreamHandler.class);
        
        var dsID = ctx.getParentID();
        if (dsID <= 0)
            throw ServiceErrors.badRequest("Streaming is only supported on a specific datastream");
        
        var queryParams = ctx.getParameterMap();
        var filter = getFilter(ctx.getParentRef(), queryParams, 0, Long.MAX_VALUE);
        var responseFormat = parseFormat(queryParams);
        ctx.setFormatOptions(responseFormat, parseSelectArg(queryParams));
        
        // detect if real-time request
        boolean isRealTime = (filter.getPhenomenonTime() == null && filter.getResultTime() == null) ||
                             (filter.getResultTime() != null && filter.getResultTime().beginsNow()) ||
                             (filter.getPhenomenonTime() != null && filter.getPhenomenonTime().beginsNow());
        
        // parse replaySpeed param
        var replaySpeedOrNull = parseDoubleArg("replaySpeed", ctx.getParameterMap());
        var replaySpeed = replaySpeedOrNull != null ? replaySpeedOrNull.doubleValue() : 1.0;
        
        // continue when streaming actually starts
        ctx.getStreamHandler().setStartCallback(() -> {
            
            try
            {
                // init binding and get datastream info
                var binding = getBinding(ctx, false);
                
                if (isRealTime)
                    startRealTimeStream(ctx, dsID, filter, binding);
                else
                    startReplayStream(ctx, dsID, filter, replaySpeed, binding);
            }
            catch (IOException e)
            {
                throw new IllegalStateException("Error initializing binding", e);
            }
        });
    }
    
    
    protected void startRealTimeStream(final RequestContext ctx, final long dsID, final ObsFilter filter, final ResourceBinding<BigInteger, IObsData> binding)
    {
        // init event to obs converter
        var dsInfo = ((ObsHandlerContextData)ctx.getData()).dsInfo;
        var streamHandler = ctx.getStreamHandler();
        
        // create subscriber
        var subscriber = new Subscriber<ObsEvent>() {
            Subscription subscription;
            
            @Override
            public void onSubscribe(Subscription subscription)
            {
                this.subscription = subscription;
                subscription.request(Long.MAX_VALUE);
                ctx.getLogger().debug("Starting real-time obs subscription #{}", System.identityHashCode(subscription));
                
                // cancel subscription if streaming is stopped by client
                ctx.getStreamHandler().setCloseCallback(() -> {
                    subscription.cancel();
                    ctx.getLogger().debug("Cancelling real-time obs subscription #{}", System.identityHashCode(subscription));
                });
            }

            @Override
            public void onNext(ObsEvent event)
            {
                try
                {
                    for (var obs: event.getObservations())
                    {
                        binding.serialize(null, obs, false);
                        streamHandler.sendPacket();
                    }
                }
                catch (IOException e)
                {
                    subscription.cancel();
                    throw new CallbackException(e);
                }
            }

            @Override
            public void onError(Throwable e)
            {
                ctx.getLogger().error("Error while publishing real-time obs data", e);
            }

            @Override
            public void onComplete()
            {
                ctx.getLogger().debug("Ending real-time obs subscription #{}", System.identityHashCode(subscription));
                streamHandler.close();
            }
        };
        
        var topic = EventUtils.getDataStreamDataTopicID(dsInfo);
        eventBus.newSubscription(ObsEvent.class)
            .withTopicID(topic)
            .withEventType(ObsEvent.class)
            .subscribe(subscriber);
    }
    
    
    protected void startReplayStream(final RequestContext ctx, final long dsID, final ObsFilter filter, final double replaySpeed, final ResourceBinding<BigInteger, IObsData> binding)
    {
        var streamHandler = ctx.getStreamHandler();
        var dsInfo = ((ObsHandlerContextData)ctx.getData()).dsInfo;
        
        int batchSize = 100;
        var itemQueue = new ConcurrentLinkedQueue<IObsData>();
        var obsIterator = dataStore.select(filter).iterator();
        if (!obsIterator.hasNext())
        {
            streamHandler.close();
            return;
        }
        
        // init time params
        var startTime = filter.getPhenomenonTime() != null ?
            filter.getPhenomenonTime().getMin() : 
            filter.getResultTime().getMin();
        if (startTime == Instant.MIN)
            startTime = dsInfo.getPhenomenonTimeRange().begin();
        var requestStartTime = startTime.toEpochMilli();
        var requestSystemTime = System.currentTimeMillis();
        
        // cancel timer task if streaming is stopped by client
        var future = new AtomicReference<ScheduledFuture<?>>();
        ctx.getStreamHandler().setCloseCallback(() -> {
            var f = future.get();
            if (f != null)
            {
                future.get().cancel(true);
                ctx.getLogger().debug("Cancelling obs replay stream #{}", System.identityHashCode(streamHandler));
            }
        });
        
        ctx.getLogger().debug("Starting obs replay stream #{}", System.identityHashCode(streamHandler));
        future.set(threadPool.scheduleWithFixedDelay(() -> {
            
            try
            {
                //ctx.getLogger().debug("Replay loop called");
                
                if (itemQueue.size() <= batchSize)
                {
                    int i;
                    for (i = 0; i < batchSize && obsIterator.hasNext(); i++)
                        itemQueue.add(obsIterator.next());
                    //if (i > 0)
                    //    ctx.getLogger().debug("fetched batch of {} items", i);
                }
                
                // send all obs that are due
                while (!itemQueue.isEmpty())
                {
                    var nextItem = itemQueue.peek();
                    
                    // slow down item dispatch at required replay speed
                    var deltaClockTime = (System.currentTimeMillis() - requestSystemTime) * replaySpeed;
                    var deltaObsTime = nextItem.getPhenomenonTime().toEpochMilli() - requestStartTime;
                    //ctx.getLogger().debug("delta clock time = {}ms", deltaClockTime);
                    //ctx.getLogger().debug("delta obs time = {}ms", deltaObsTime);
                    
                    // skip if it's not time to send this record yet
                    if (deltaObsTime > deltaClockTime)
                        break;
                
                    //ctx.getLogger().debug("sending obs at {}", nextItem.getPhenomenonTime());
                    binding.serialize(null, itemQueue.poll(), false);
                    streamHandler.sendPacket();
                }
                
                // stop streaming if done
                if (itemQueue.isEmpty() && !obsIterator.hasNext())
                {
                    future.get().cancel(false);
                    streamHandler.close();
                    ctx.getLogger().debug("Ending obs replay stream #{}", System.identityHashCode(streamHandler));
            }
            }
            catch (IOException e)
            {
                throw new CompletionException(e);
            }
            
        }, 0, 10, TimeUnit.MILLISECONDS));
    }


    @Override
    protected BigInteger getKey(RequestContext ctx, String id) throws InvalidRequestException
    {
        try
        {
            var internalID = new BigInteger(id, ResourceBinding.ID_RADIX);
            if (internalID.signum() <= 0)
                return null;
            
            return internalID;
        }
        catch (NumberFormatException e)
        {
            throw ServiceErrors.notFound();
        }
    }
    
    
    @Override
    protected String encodeKey(final RequestContext ctx, BigInteger key)
    {
        var externalID = key;
        return externalID.toString(ResourceBinding.ID_RADIX);
    }


    @Override
    protected ObsFilter getFilter(ResourceRef parent, Map<String, String[]> queryParams, long offset, long limit) throws InvalidRequestException
    {
        var builder = new ObsFilter.Builder();
        
        // filter on parent if needed
        if (parent.internalID > 0)
            builder.withDataStreams(parent.internalID);
        
        // phenomenonTime param
        var phenomenonTime = parseTimeStampArg("phenomenonTime", queryParams);
        if (phenomenonTime != null)
            builder.withPhenomenonTime(phenomenonTime);
        
        // resultTime param
        var resultTime = parseTimeStampArg("resultTime", queryParams);
        if (resultTime != null)
            builder.withResultTime(resultTime);
        
        // foi param
        var foiIDs = parseResourceIds("foi", queryParams, foiIdEncoder);
        if (foiIDs != null && !foiIDs.isEmpty())
            builder.withFois(foiIDs);
        
        // datastream param
        var dsIDs = parseResourceIds("datastream", queryParams, dsIdEncoder);
        if (dsIDs != null && !dsIDs.isEmpty())
            builder.withDataStreams(dsIDs);
        
        // use opensearch bbox param to filter spatially
        var bbox = parseBboxArg("bbox", queryParams);
        if (bbox != null)
        {
            builder.withPhenomenonLocation(new SpatialFilter.Builder()
                .withBbox(bbox)
                .build());
        }
        
        // geom param
        var geom = parseGeomArg("location", queryParams);
        if (geom != null)
        {
            builder.withPhenomenonLocation(new SpatialFilter.Builder()
                .withRoi(geom)
                .build());
        }
        
        // limit
        // need to limit to offset+limit+1 since we rescan from the beginning for now
        if (limit != Long.MAX_VALUE)
            builder.withLimit(offset+limit+1);
        
        return builder.build();
    }
    
    
    @Override
    protected ResourceFormat parseFormat(final Map<String, String[]> queryParams) throws InvalidRequestException
    {
        return super.parseFormat(queryParams, ResourceFormat.AUTO);
    }


    @Override
    protected BigInteger addEntry(RequestContext ctx, IObsData res) throws DataStoreException
    {
        var dsHandler = ((ObsHandlerContextData)ctx.getData()).dsHandler;
        return idConverter.toPublicID(dsHandler.addObs(res));
    }
    
    
    @Override
    protected boolean isValidID(long internalID)
    {
        return false;
    }


    @Override
    protected void validate(IObsData resource)
    {
        // TODO Auto-generated method stub
        
    }
    
    
    @Override
    public String[] getNames()
    {
        return NAMES;
    }
}
