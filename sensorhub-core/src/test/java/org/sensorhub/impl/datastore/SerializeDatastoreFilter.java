/***************************** BEGIN LICENSE BLOCK ***************************

The contents of this file are subject to the Mozilla Public License, v. 2.0.
If a copy of the MPL was not distributed with this file, You can obtain one
at http://mozilla.org/MPL/2.0/.

Software distributed under the License is distributed on an "AS IS" basis,
WITHOUT WARRANTY OF ANY KIND, either express or implied. See the License
for the specific language governing rights and limitations under the License.
 
Copyright (C) 2019 Sensia Software LLC. All Rights Reserved.
 
******************************* END LICENSE BLOCK ***************************/

package org.sensorhub.impl.datastore;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.sensorhub.api.obs.DataStreamFilter;
import org.sensorhub.api.procedure.ProcedureFilter;
import org.vast.util.Bbox;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.GeometryFactory;


public class SerializeDatastoreFilter
{

    public static void main(String[] args)
    {
        Gson gson = new GsonBuilder()
            .registerTypeAdapterFactory(new DataStoreFiltersTypeAdapterFactory())
            .setFieldNamingStrategy(new DataStoreFiltersTypeAdapterFactory.FieldNamingStrategy())
            .serializeSpecialFloatingPointValues()
            .setPrettyPrinting()
            .create();

        System.out.println("procFilter: " + gson.toJson(new ProcedureFilter.Builder()
            .withValidTimeDuring(
                Instant.now().truncatedTo(ChronoUnit.SECONDS),
                Instant.now().plusSeconds(3600).truncatedTo(ChronoUnit.SECONDS))
            .withLocation()
                .withDistanceToPoint(new GeometryFactory().createPoint(new Coordinate(-98.3, 35.6, 123)), 300)
                .done()
            .build()));
        
        System.out.println();
        System.out.println("procFilter: " + gson.toJson(new ProcedureFilter.Builder()
            .withLatestVersion()
            .withLocation()
                .withBbox(new Bbox(-50,-10,50,10))
                .done()
            .build()));
        
        System.out.println();
        System.out.println("procFilter: " + gson.toJson(new ProcedureFilter.Builder()
            .withLatestVersion()
            .withLocation()
                .withBbox(new Bbox(-50,-10,50,10))
                .done()
            .withFois()
                .withInternalIDs(100L, 200L)
                .withParents(500L, 450L)
                .done()
            .build()));
        
        System.out.println();
        System.out.println("dsFilter: " + gson.toJson(new DataStreamFilter.Builder()
            .withProcedures()
                .withParentGroups("urn:osh:group:1", "urn:osh:group:2")
                .done()
            .withFullText()
                .withKeywords("test", "test1")
                .done()
            .build()));
        
        System.out.println();
        System.out.println("obsFilter: " + gson.toJson(new DataStreamFilter.Builder()
            .withProcedures()
                .withParentGroups("urn:osh:group:1", "urn:osh:group:2")
                .done()
            .withFullText()
                .withKeywords("test", "test1")
                .done()
            .build()));

    }

}
