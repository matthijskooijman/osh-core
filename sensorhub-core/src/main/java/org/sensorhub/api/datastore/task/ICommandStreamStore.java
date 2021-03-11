/***************************** BEGIN LICENSE BLOCK ***************************

The contents of this file are subject to the Mozilla Public License, v. 2.0.
If a copy of the MPL was not distributed with this file, You can obtain one
at http://mozilla.org/MPL/2.0/.

Software distributed under the License is distributed on an "AS IS" basis,
WITHOUT WARRANTY OF ANY KIND, either express or implied. See the License
for the specific language governing rights and limitations under the License.
 
Copyright (C) 2021 Sensia Software LLC. All Rights Reserved.
 
******************************* END LICENSE BLOCK ***************************/

package org.sensorhub.api.datastore.task;

import java.util.Optional;
import org.sensorhub.api.datastore.DataStoreException;
import org.sensorhub.api.datastore.ValueField;
import org.sensorhub.api.datastore.procedure.IProcedureStore;
import org.sensorhub.api.datastore.task.ICommandStreamStore.CommandStreamInfoField;
import org.sensorhub.api.resource.IResourceStore;
import org.sensorhub.api.tasking.ICommandStreamInfo;


/**
 * <p>
 * Generic interface for managing command streams within a command store.<br/>
 * Removal operations also remove all commands associated to a command stream. 
 * </p>
 *
 * @author Alex Robin
 * @date Mar 11, 2021
 */
public interface ICommandStreamStore extends IResourceStore<CommandStreamKey, ICommandStreamInfo, CommandStreamInfoField, CommandStreamFilter>
{
    
    public static class CommandStreamInfoField extends ValueField
    {
        public static final CommandStreamInfoField PROCEDURE_ID = new CommandStreamInfoField("procedureID");
        public static final CommandStreamInfoField COMMAND_NAME = new CommandStreamInfoField("commandName");
        public static final CommandStreamInfoField VALID_TIME = new CommandStreamInfoField("validTime");
        public static final CommandStreamInfoField RECORD_DESCRIPTION  = new CommandStreamInfoField("recordDescription");
        public static final CommandStreamInfoField RECORD_ENCODING = new CommandStreamInfoField("recordEncoding");
        
        public CommandStreamInfoField(String name)
        {
            super(name);
        }
    }
    
    
    @Override
    public default CommandStreamFilter.Builder filterBuilder()
    {
        return new CommandStreamFilter.Builder();
    }
    
    
    /**
     * Add a new command stream and generate a new unique key for it.<br/>
     * If the command stream valid time is not set, it will be set to the valid time
     * of the parent procedure.
     * @param csInfo The command stream info object to be stored
     * @return The key associated with the new command stream
     * @throws DataStoreException if a command stream with the same parent procedure,
     * taskable parameter and valid time already exists, or if the parent procedure is unknown.
     */
    public CommandStreamKey add(ICommandStreamInfo csInfo) throws DataStoreException;
    
    
    /**
     * Helper method to retrieve the internal ID of the latest version of the
     * command stream corresponding to the specified procedure and command input.
     * @param procUID Unique ID of procedure producing the data stream
     * @param commandName Name of taskable parameter associated to the command stream
     * @return The command stream key or null if none was found
     */
    public default CommandStreamKey getLatestVersionKey(String procUID, String commandName)
    {
        Entry<CommandStreamKey, ICommandStreamInfo> e = getLatestVersionEntry(procUID, commandName);
        return e != null ? e.getKey() : null;
    }
    
    
    /**
     * Helper method to retrieve the latest version of the command stream
     * corresponding to the specified procedure and command input.
     * @param procUID Unique ID of procedure producing the command stream
     * @param commandName Name of taskable parameter associated to the command stream
     * @return The command stream info or null if none was found
     */
    public default ICommandStreamInfo getLatestVersion(String procUID, String commandName)
    {
        Entry<CommandStreamKey, ICommandStreamInfo> e = getLatestVersionEntry(procUID, commandName);
        return e != null ? e.getValue() : null;
    }
    
    
    /**
     * Helper method to retrieve the entry for the latest version of the
     * command stream corresponding to the specified procedure and command input.
     * @param procUID Unique ID of procedure producing the data stream
     * @param commandName Name of taskable parameter associated to the command stream
     * @return The feature entry or null if none was found with this UID
     */
    public default Entry<CommandStreamKey, ICommandStreamInfo> getLatestVersionEntry(String procUID, String commandName)
    {
        Optional<Entry<CommandStreamKey, ICommandStreamInfo>> entryOpt = selectEntries(new CommandStreamFilter.Builder()
            .withProcedures()
                .withUniqueIDs(procUID)
                .done()
            .withCommandNames(commandName)
            .build())
        .findFirst();
        
        return entryOpt.isPresent() ? entryOpt.get() : null;
    }
    
    
    /**
     * Remove all command streams that are associated to the given procedure command input
     * @param procUID
     * @param commandName
     * @return The number of entries actually removed
     */
    public default long removeAllVersions(String procUID, String commandName)
    {
        return removeEntries(new CommandStreamFilter.Builder()
            .withProcedures()
                .withUniqueIDs(procUID)
                .done()
            .withCommandNames(commandName)
            .build());
    }
    
    
    /**
     * Link this store to an procedure store to enable JOIN queries
     * @param procedureStore
     */
    public void linkTo(IProcedureStore procedureStore);
    
}
