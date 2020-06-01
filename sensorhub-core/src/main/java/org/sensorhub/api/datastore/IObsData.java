/***************************** BEGIN LICENSE BLOCK ***************************

The contents of this file are subject to the Mozilla Public License, v. 2.0.
If a copy of the MPL was not distributed with this file, You can obtain one
at http://mozilla.org/MPL/2.0/.

Software distributed under the License is distributed on an "AS IS" basis,
WITHOUT WARRANTY OF ANY KIND, either express or implied. See the License
for the specific language governing rights and limitations under the License.

Copyright (C) 2019 Sensia Software LLC. All Rights Reserved.

******************************* END LICENSE BLOCK ***************************/

package org.sensorhub.api.datastore;

import java.time.Instant;
import java.util.Map;
import org.sensorhub.api.common.FeatureId;
import com.vividsolutions.jts.geom.Geometry;
import net.opengis.swe.v20.DataBlock;


public interface IObsData
{
    public static final FeatureId NO_FOI = FeatureId.NULL_FEATURE;


    /**
     * @return The internal ID of the data stream that the observation is part of.
     */
    public long getDataStreamID();


    /**
     * @return The ID of the feature of interest that was observed.<br/>
     * This can be 0 if no feature of interest was reported.
     */
    public FeatureId getFoiID();


    public default boolean hasFoi()
    {
        FeatureId id = getFoiID();
        return id == null || id.getInternalID() == NO_FOI.getInternalID();
    }


    /**
     * @return The time of occurrence of the measured phenomenon (e.g. for
     * many automated sensor devices, this is typically the sampling time).<br/>
     * This field cannot be null.
     */
    public Instant getPhenomenonTime();


    /**
     * @return The time at which the observation result was obtained.<br/>
     * This is typically the same as the phenomenon time for many automated
     * in-situ and remote sensors doing the sampling and actual measurement
     * (almost) simultaneously, but different for measurements made in a lab on
     * samples that were collected previously. It is also different for models
     * and simulations outputs (e.g. for a model, this is the run time).<br/>
     * If no result time was explicitly set, this returns the phenomenon time
     */
    public Instant getResultTime();


    /**
     * @return Observation parameters map
     */
    public Map<String, Object> getParameters();


    /**
     * @return Area or volume (2D or 3D) where the observation was made.<br/>
     * If value is null, FoI geometry is used instead when provided. If neither geometry is provided,
     * observation will never be selected when filtering on geometry.<br/>
     * In a given data store, all geometries must be expressed in the same coordinate reference system.
     */
    public Geometry getPhenomenonLocation();


    /**
     * @return Observation result data record
     */
    public DataBlock getResult();
}
