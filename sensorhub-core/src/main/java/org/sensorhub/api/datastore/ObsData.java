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

import java.util.HashMap;
import java.util.Map;
import org.sensorhub.utils.ObjectUtils;
import org.vast.util.Asserts;
import org.vast.util.BaseBuilder;
import com.vividsolutions.jts.geom.Geometry;
import net.opengis.swe.v20.DataBlock;


/**
 * <p>
 * Immutable object representing observation data (result, foi ID, sampling
 * geometry, validity period, observation parameters) stored in an observation
 * store.
 * </p>
 *
 * @author Alex Robin
 * @date Apr 3, 2018
 */
public class ObsData
{
    protected Map<String, Object> parameters = null;
    protected Geometry phenomenonLocation = null;
    //protected Range<Instant> validTime = null;
    protected DataBlock result;
    
    
    protected ObsData()
    {        
    }
    
    
    public ObsData(DataBlock result)
    {
        this.result = result;
    }
    
    
    /**
     * @return Observation parameters map
     */
    public Map<String, Object> getParameters()
    {
        return parameters;
    }


    /**
     * @return Area or volume (2D or 3D) where the observation was made.<br/>
     * If value is null, FoI geometry is used instead when provided. If neither geometry is provided,
     * observation will never be selected when filtering on geometry.<br/>
     * In a given data store, all geometries must be expressed in the same coordinate reference system.
     */    
    public Geometry getPhenomenonLocation()
    {
        return phenomenonLocation;
    }


    /**
     * @return Observation result data record
     */
    public DataBlock getResult()
    {
        return result;
    }


    @Override
    public String toString()
    {
        return ObjectUtils.toString(this, true);
    }
    
    
    /*
     * Builder
     */
    public static class Builder extends ObsDataBuilder<Builder, ObsData>
    {
        public Builder()
        {
            this.instance = new ObsData();
        }
        
        public static Builder from(ObsData base)
        {
            return new Builder().copyFrom(base);
        }
    }
    
    
    @SuppressWarnings("unchecked")
    public static abstract class ObsDataBuilder<
            B extends ObsDataBuilder<B, T>,
            T extends ObsData>
        extends BaseBuilder<T>
    {       
        protected ObsDataBuilder()
        {
        }
        
        
        protected B copyFrom(ObsData base)
        {
            instance.parameters = base.parameters;
            instance.phenomenonLocation = base.phenomenonLocation;
            instance.result = base.result;
            return (B)this;
        }


        public B withParameter(String key, Object value)
        {
            if (instance.parameters == null)
                instance.parameters = new HashMap<>();
            instance.parameters.put(key, value);
            return (B)this;
        }


        public B withPhenomenonLocation(Geometry phenomenonLocation)
        {
            instance.phenomenonLocation = phenomenonLocation;
            return (B)this;
        }


        public B withResult(DataBlock result)
        {
            instance.result = result;
            return (B)this;
        }
        
        
        public T build()
        {
            Asserts.checkNotNull(instance.result, "result");
            return super.build();
        }
    }
}
