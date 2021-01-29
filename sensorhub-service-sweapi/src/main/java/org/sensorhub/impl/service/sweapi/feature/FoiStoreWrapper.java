/***************************** BEGIN LICENSE BLOCK ***************************

The contents of this file are subject to the Mozilla Public License, v. 2.0.
If a copy of the MPL was not distributed with this file, You can obtain one
at http://mozilla.org/MPL/2.0/.

Software distributed under the License is distributed on an "AS IS" basis,
WITHOUT WARRANTY OF ANY KIND, either express or implied. See the License
for the specific language governing rights and limitations under the License.
 
Copyright (C) 2020 Sensia Software LLC. All Rights Reserved.
 
******************************* END LICENSE BLOCK ***************************/

package org.sensorhub.impl.service.sweapi.feature;

import org.sensorhub.api.datastore.feature.FoiFilter;
import org.sensorhub.api.datastore.feature.IFeatureStore;
import org.sensorhub.api.datastore.feature.IFoiStore;
import org.sensorhub.api.datastore.feature.IFoiStore.FoiField;
import org.sensorhub.api.datastore.obs.IObsStore;
import org.sensorhub.api.datastore.procedure.IProcedureStore;
import org.sensorhub.impl.service.sweapi.IdConverter;
import org.vast.ogc.gml.IGeoFeature;


public class FoiStoreWrapper extends AbstractFeatureStoreWrapper<IGeoFeature, FoiField, FoiFilter, IFoiStore> implements IFoiStore
{

    public FoiStoreWrapper(IFoiStore readStore, IFoiStore writeStore, IdConverter idConverter)
    {
        super(readStore, writeStore, idConverter);
    }
    
    
    @Override
    public FoiFilter.Builder filterBuilder()
    {
        return (FoiFilter.Builder)super.filterBuilder();
    }


    @Override
    public void linkTo(IProcedureStore procStore)
    {
        throw new UnsupportedOperationException();        
    }


    @Override
    public void linkTo(IObsStore obsStore)
    {
        throw new UnsupportedOperationException();
    }


    @Override
    public void linkTo(IFeatureStore featureStore)
    {
        throw new UnsupportedOperationException();
    }

}
