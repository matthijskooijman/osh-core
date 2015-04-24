/***************************** BEGIN LICENSE BLOCK ***************************

The contents of this file are subject to the Mozilla Public License, v. 2.0.
If a copy of the MPL was not distributed with this file, You can obtain one
at http://mozilla.org/MPL/2.0/.

Software distributed under the License is distributed on an "AS IS" basis,
WITHOUT WARRANTY OF ANY KIND, either express or implied. See the License
for the specific language governing rights and limitations under the License.
 
Copyright (C) 2012-2015 Sensia Software LLC. All Rights Reserved.
 
******************************* END LICENSE BLOCK ***************************/

package org.sensorhub.api.sensor;

import org.sensorhub.api.data.DataEvent;
import org.sensorhub.api.data.IStreamingDataInterface;
import net.opengis.swe.v20.DataBlock;


/**
 * <p>
 * Type of event generated when new data is available from sensors.
 * It is immutable and carries sensor data by reference
 * </p>
 *
 * @author Alex Robin <alex.robin@sensiasoftware.com>
 * @since Nov 5, 2010
 */
public class SensorDataEvent extends DataEvent
{    
	
	/**
	 * Constructor from list of records with their descriptor, for an single sensor
	 * @param timeStamp time of event generation (unix time in milliseconds, base 1970)
     * @param dataInterface sensor output interface that produced the associated data
	 * @param records arrays of records that triggered this notification
	 */
	public SensorDataEvent(long timeStamp, ISensorDataInterface dataInterface, DataBlock ... records)
	{
		super(timeStamp, dataInterface, records);
	}
	
	
	/**
     * Constructor from a list of records and their descriptor, for a sensor within a network
     * @param timeStamp time of event generation (unix time in milliseconds, base 1970)
     * @param sensorID ID of sensor within the network
     * @param dataInterface stream interface that generated the associated data
     * @param records arrays of records that triggered this notification
     */
    public SensorDataEvent(long timeStamp, String sensorID, IStreamingDataInterface dataInterface, DataBlock ... records)
    {
        super(timeStamp, sensorID, dataInterface, records);
    }
	
	
	/**
     * For individual sensors, this method will return the same value as
     * {@link #getSourceModuleID()}, but for sensor networks, this can be
     * either the ID of the network as a whole (if the attached data includes
     * records generated by all or several members of the network) or the ID
     * of one of the sensor within the network (if the attached data has been
     * generated only by that sensor).
     * @return the ID of the sensor that this event refers to
     */
    public String getSensorID()
    {
        return relatedObjectID;
    }


    @Override
    public ISensorDataInterface getSource()
    {
        return (ISensorDataInterface)this.source;
    }
}
