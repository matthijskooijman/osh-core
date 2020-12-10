/***************************** BEGIN LICENSE BLOCK ***************************

The contents of this file are subject to the Mozilla Public License, v. 2.0.
If a copy of the MPL was not distributed with this file, You can obtain one
at http://mozilla.org/MPL/2.0/.

Software distributed under the License is distributed on an "AS IS" basis,
WITHOUT WARRANTY OF ANY KIND, either express or implied. See the License
for the specific language governing rights and limitations under the License.
 
Copyright (C) 2019 Sensia Software LLC. All Rights Reserved.
 
******************************* END LICENSE BLOCK ***************************/

package org.sensorhub.impl.database.registry;

import java.math.BigInteger;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentSkipListMap;
import org.sensorhub.api.ISensorHub;
import org.sensorhub.api.database.IDatabase;
import org.sensorhub.api.database.IDatabaseRegistry;
import org.sensorhub.api.database.IFeatureDatabase;
import org.sensorhub.api.database.IProcedureObsDatabase;
import org.sensorhub.api.datastore.IQueryFilter;
import org.sensorhub.api.datastore.feature.FeatureKey;
import org.sensorhub.api.datastore.obs.DataStreamFilter;
import org.sensorhub.api.procedure.IProcedureEventHandlerDatabase;
import org.sensorhub.api.utils.OshAsserts;
import org.sensorhub.utils.MapWithWildcards;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.vast.util.Asserts;


/**
 * <p>
 * Default implementation of the observation registry allowing to register
 * several observation database instances. In this implementation, a given
 * procedure can only be associated to a single database instance.
 * </p>
 *
 * @author Alex Robin
 * @date Sep 18, 2019
 */
public class DefaultDatabaseRegistry implements IDatabaseRegistry
{
    static final Logger log = LoggerFactory.getLogger(DefaultDatabaseRegistry.class);
    static final Integer DEFAULT_DB_ID = Integer.valueOf(0);
    static final int MAX_NUM_DB = 100;
    static final BigInteger MAX_NUM_DB_BIGINT = BigInteger.valueOf(MAX_NUM_DB);
    static final String END_PREFIX_CHAR = "\n";
    
    ISensorHub hub;
    MapWithWildcards<Integer> obsDatabaseIDs;
    Map<Integer, IProcedureObsDatabase> obsDatabases;
    FederatedObsDatabase globalObsDatabase;
    
    
    public static class LocalDatabaseInfo
    {
        IProcedureObsDatabase db;
        int databaseID;
        long entryID;
        BigInteger bigEntryID;
    }
    
    
    public static class LocalFilterInfo
    {
        IProcedureObsDatabase db;
        int databaseID;
        Set<Long> internalIds = new TreeSet<>();
        Set<BigInteger> bigInternalIds = new TreeSet<>();
        IQueryFilter filter;
    }
    
    
    
    public DefaultDatabaseRegistry(ISensorHub hub)
    {
        this.hub = hub;
        this.obsDatabaseIDs = new MapWithWildcards<>();
        this.obsDatabases = new ConcurrentSkipListMap<>();
        this.globalObsDatabase = new FederatedObsDatabase(this);
    }
    
    
    @Override
    public synchronized void register(IDatabase db)
    {
        Asserts.checkNotNull(db, IDatabase.class);
        Asserts.checkArgument(db.getDatabaseNum() != null && db.getDatabaseNum() < MAX_NUM_DB, "Database number must be > 0 and < " + MAX_NUM_DB);
        
        log.info("Registering database {} with ID {}", db.getClass().getSimpleName(), db.getDatabaseNum());
        
        if (db instanceof IProcedureObsDatabase)
            registerObsDatabase((IProcedureObsDatabase)db);
        else if (db instanceof IFeatureDatabase)
            registerFeatureDatabase((IFeatureDatabase)db);
    }


    @Override
    public synchronized void register(String procedureUID, IProcedureObsDatabase db)
    {
        int databaseID = registerObsDatabase(db);
        registerMapping(procedureUID, databaseID);
    }


    @Override
    public synchronized void register(Collection<String> procedureUIDs, IProcedureObsDatabase db)
    {
        int databaseID = registerObsDatabase(db);
        for (String uid: procedureUIDs)
            registerMapping(uid, databaseID);
    }
    
    
    @Override
    public synchronized void unregister(IDatabase db)
    {
        var it = obsDatabaseIDs.values().iterator();
        while (it.hasNext())
        {
            var dbID = it.next();
            if (dbID == db.getDatabaseNum())
                it.remove();
        }
            
        obsDatabases.remove(db.getDatabaseNum());
    }
    
    
    protected int registerObsDatabase(IProcedureObsDatabase db)
    {
        int databaseID = db.getDatabaseNum();
        
        // add to Id->DB instance map only if not already present        
        if (obsDatabases.putIfAbsent(databaseID, db) != null)
            throw new IllegalStateException("A database with ID " + databaseID + " was already registered");
        
        // case of database w/ event handler
        if (db instanceof IProcedureEventHandlerDatabase)
        {
            try
            {
                if (db.isReadOnly())
                    throw new IllegalStateException("Cannot use a read-only database to collect procedure & obs data");
                            
                for (var procUID: ((IProcedureEventHandlerDatabase) db).getHandledProcedures())
                    registerMapping(procUID, databaseID);
            }
            catch (IllegalStateException e)
            {
                // remove database if 2nd registration step failed
                obsDatabases.remove(databaseID);
                throw e;
            }
        }
        
        return databaseID;
    }
    
    
    protected int registerFeatureDatabase(IFeatureDatabase db)
    {
        return 0;
    }
    
    
    protected void registerMapping(String uid, int databaseID)
    {
        OshAsserts.checkValidUID(uid);
        Asserts.checkArgument(databaseID > 0, "Database ID must be > 0");        
        
        // only insert mapping if not already registered by another database
        // or if registered in state database only (ID 0)
        if (obsDatabaseIDs.putIfAbsent(uid, databaseID) != null)
            throw new IllegalStateException("Procedure " + uid + " is already handled by another database");
        
        // remove all entries from default state DB (DB 0) since it's now handled by another DB
        if (databaseID != 0)
        {
            IProcedureObsDatabase defaultDB = obsDatabases.get(0);
            if (defaultDB != null)
            {
                FeatureKey key = defaultDB.getProcedureStore().remove(uid);
                if (key != null)
                {
                    log.info("Database #{} now handles procedure {}. Removing all records from state DB", databaseID, uid);
                    defaultDB.getObservationStore().getDataStreams().removeEntries(new DataStreamFilter.Builder()
                        .withProcedures(key.getInternalID())
                        .build());
                }
            }
        }
    }


    @Override
    public IProcedureObsDatabase getObsDatabase(String procUID)
    {
        Integer dbID = obsDatabaseIDs.get(procUID);
        if (dbID == null)
            dbID = DEFAULT_DB_ID;
        return obsDatabases.get(dbID);
    }
    
    
    @Override
    public Collection<IProcedureObsDatabase> getProcedureObsDatabases()
    {
        return Collections.unmodifiableCollection(obsDatabases.values());
    }


    @Override
    public synchronized void unregister(String uid, IProcedureObsDatabase db)
    {
        Asserts.checkNotNull(uid, "procedureUID");
        
        if (uid.endsWith("*"))
            uid = uid.substring(0, uid.length()-1) + END_PREFIX_CHAR;
        
        obsDatabaseIDs.remove(uid);
    }


    @Override
    public boolean hasDatabase(String procedureUID)
    {
        return obsDatabaseIDs.containsKey(procedureUID);
    }
    
    
    /*
     * Federated ID management methods
     */    
    
    @Override
    public long getLocalID(int databaseID, long publicID)
    {
        return publicID / MAX_NUM_DB;
    }
    
    
    @Override
    public BigInteger getLocalID(int databaseID, BigInteger publicID)
    {
        return publicID.divide(MAX_NUM_DB_BIGINT);
    }
    
    
    @Override
    public long getPublicID(int databaseID, long entryID)
    {
        return entryID * MAX_NUM_DB + (long)databaseID;
    }
    
    
    @Override
    public BigInteger getPublicID(int databaseID, BigInteger entryID)
    {
        return entryID.multiply(MAX_NUM_DB_BIGINT)
            .add(BigInteger.valueOf(databaseID));
    }
    
    
    @Override
    public int getDatabaseID(long publicID)
    {
        return (int)(publicID % MAX_NUM_DB);
    }
    
    
    /*
     * Convert from the public ID to the local database ID
     */
    protected LocalDatabaseInfo getLocalDbInfo(long publicID)
    {
        LocalDatabaseInfo dbInfo = new LocalDatabaseInfo();
        dbInfo.databaseID = (int)(publicID % MAX_NUM_DB);
        dbInfo.db = obsDatabases.get(dbInfo.databaseID);
        dbInfo.entryID = getLocalID(dbInfo.databaseID, publicID);
        
        if (dbInfo.db == null)
            return null;
        
        return dbInfo;
    }
    
    
    /*
     * Convert from the public ID to the local database ID
     */
    protected LocalDatabaseInfo getLocalDbInfo(BigInteger publicID)
    {
        LocalDatabaseInfo dbInfo = new LocalDatabaseInfo();
        dbInfo.databaseID = publicID.mod(MAX_NUM_DB_BIGINT).intValue();
        dbInfo.db = obsDatabases.get(dbInfo.databaseID);
        dbInfo.bigEntryID = getLocalID(dbInfo.databaseID, publicID);
        
        if (dbInfo.db == null)
            return null;
        
        return dbInfo;
    }
    
    
    /*
     * Get map with an entry for each DB ID extracted from public IDs
     */
    protected Map<Integer, LocalFilterInfo> getFilterDispatchMap(Set<Long> publicIDs)
    {
        Map<Integer, LocalFilterInfo> map = new LinkedHashMap<>();
        
        for (long publicID: publicIDs)
        {
            LocalDatabaseInfo dbInfo = getLocalDbInfo(publicID);
            if (dbInfo == null)
                continue;
                
            LocalFilterInfo filterInfo = map.computeIfAbsent(dbInfo.databaseID, k -> new LocalFilterInfo());
            filterInfo.db = dbInfo.db;
            filterInfo.databaseID = dbInfo.databaseID;
            filterInfo.internalIds.add(dbInfo.entryID);
        }
        
        return map;
    }
    
    
    protected Map<Integer, LocalFilterInfo> getFilterDispatchMapBigInt(Set<BigInteger> publicIDs)
    {
        Map<Integer, LocalFilterInfo> map = new LinkedHashMap<>();
        
        for (BigInteger publicID: publicIDs)
        {
            LocalDatabaseInfo dbInfo = getLocalDbInfo(publicID);
            if (dbInfo == null)
                continue;
                
            LocalFilterInfo filterInfo = map.computeIfAbsent(dbInfo.databaseID, k -> new LocalFilterInfo());
            filterInfo.db = dbInfo.db;
            filterInfo.databaseID = dbInfo.databaseID;
            filterInfo.bigInternalIds.add(dbInfo.bigEntryID);
        }
        
        return map;
    }


    @Override
    public IProcedureObsDatabase getFederatedObsDatabase()
    {
        return globalObsDatabase;
    }
}
