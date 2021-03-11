/***************************** BEGIN LICENSE BLOCK ***************************

The contents of this file are subject to the Mozilla Public License, v. 2.0.
If a copy of the MPL was not distributed with this file, You can obtain one
at http://mozilla.org/MPL/2.0/.

Software distributed under the License is distributed on an "AS IS" basis,
WITHOUT WARRANTY OF ANY KIND, either express or implied. See the License
for the specific language governing rights and limitations under the License.
 
Copyright (C) 2019 Sensia Software LLC. All Rights Reserved.
 
******************************* END LICENSE BLOCK ***************************/

package org.sensorhub.api.tasking;

import java.time.Instant;


/**
 * <p>
 * Status of an individual command, used as ACK
 * </p>
 *
 * @author Alex Robin
 * @date Mar 10, 2021
 */
public class CommandAck implements ICommandAck
{
    public static final int SUCCESS = 0;
    public static final int FAILED = 1;
    
    //protected Instant receiveTime;
    protected long commandRefID;
    protected Instant actuationTime;
    protected int statusCode;
    
    
    protected CommandAck(long commandRefID, int statusCode)
    {
        this(commandRefID, statusCode, Instant.now());
    }
    
    
    protected CommandAck(long commandRefID, int statusCode, Instant actuationTime)
    {
        this.commandRefID = commandRefID;
        this.statusCode = statusCode;
        this.actuationTime = actuationTime;        
    }
    
    
    public static ICommandAck success(long commandRefID)
    {
        return new CommandAck(commandRefID, SUCCESS);
    }
    
    
    public static ICommandAck success(long commandRefID, Instant actuationTime)
    {
        return new CommandAck(commandRefID, SUCCESS, actuationTime);
    }
    
    
    public static ICommandAck fail(long commandRefID)
    {
        return new CommandAck(commandRefID, FAILED);
    }
    
    
    public static ICommandAck fail(long commandRefID, int errorCode)
    {
        return new CommandAck(commandRefID, errorCode);
    }


    @Override
    public long getCommandRefID()
    {
        return commandRefID;
    }


    @Override
    public Instant getActuationTime()
    {
        return actuationTime;
    }


    @Override
    public int getStatusCode()
    {
        return statusCode;
    }
}
