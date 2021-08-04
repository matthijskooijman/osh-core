/***************************** BEGIN LICENSE BLOCK ***************************

The contents of this file are subject to the Mozilla Public License, v. 2.0.
If a copy of the MPL was not distributed with this file, You can obtain one
at http://mozilla.org/MPL/2.0/.

Software distributed under the License is distributed on an "AS IS" basis,
WITHOUT WARRANTY OF ANY KIND, either express or implied. See the License
for the specific language governing rights and limitations under the License.
 
Copyright (C) 2019 Sensia Software LLC. All Rights Reserved.
 
******************************* END LICENSE BLOCK ***************************/

package org.sensorhub.api.datastore.procedure;

import org.sensorhub.api.datastore.EmptyFilterIntersection;
import org.sensorhub.api.datastore.feature.FeatureFilterBase;
import org.sensorhub.api.datastore.feature.FoiFilter;
import org.sensorhub.api.datastore.obs.DataStreamFilter;
import org.sensorhub.api.resource.ResourceFilter;
import org.vast.ogc.om.IProcedure;


/**
 * <p>
 * Immutable filter object for procedures (e.g. sensors, actuators, procedure groups etc.).<br/>
 * There is an implicit AND between all filter parameters
 * </p>
 *
 * @author Alex Robin
 * @date Apr 2, 2018
 */
public class ProcedureFilter extends FeatureFilterBase<IProcedure>
{
    protected ProcedureFilter parentFilter;
    protected DataStreamFilter dataStreamFilter;

    
    /*
     * this class can only be instantiated using builder
     */
    protected ProcedureFilter() {}
    
    
    public ProcedureFilter getParentFilter()
    {
        return parentFilter;
    }
    
    
    public DataStreamFilter getDataStreamFilter()
    {
        return dataStreamFilter;
    }
    
    
    /**
     * Computes the intersection (logical AND) between this filter and another filter of the same kind
     * @param filter The other filter to AND with
     * @return The new composite filter
     * @throws EmptyFilterIntersection if the intersection doesn't exist
     */
    @Override
    public ProcedureFilter intersect(ResourceFilter<IProcedure> filter) throws EmptyFilterIntersection
    {
        if (filter == null)
            return this;
        
        return intersect((ProcedureFilter)filter, new Builder()).build();
    }
    
    
    protected <B extends ProcedureFilterBuilder<B, ProcedureFilter>> B intersect(ProcedureFilter otherFilter, B builder) throws EmptyFilterIntersection
    {
        super.intersect(otherFilter, builder);
        
        var parentFilter = this.parentFilter != null ? this.parentFilter.intersect(otherFilter.parentFilter) : otherFilter.parentFilter;
        if (parentFilter != null)
            builder.withParents(parentFilter);
        
        var dataStreamFilter = this.dataStreamFilter != null ? this.dataStreamFilter.intersect(otherFilter.dataStreamFilter) : otherFilter.dataStreamFilter;
        if (dataStreamFilter != null)
            builder.withDataStreams(dataStreamFilter);
        
        return builder;
    }
    
    
    /**
     * Deep clone this filter
     */
    public ProcedureFilter clone()
    {
        return Builder.from(this).build();
    }
    
    
    /*
     * Builder
     */
    public static class Builder extends ProcedureFilterBuilder<Builder, ProcedureFilter>
    {
        public Builder()
        {
            super(new ProcedureFilter());
        }
        
        /**
         * Builds a new filter using the provided filter as a base
         * @param base Filter used as base
         * @return The new builder
         */
        public static Builder from(ProcedureFilter base)
        {
            return new Builder().copyFrom(base);
        }
    }
    
    
    /*
     * Nested builder for use within another builder
     */
    public static abstract class NestedBuilder<B> extends ProcedureFilterBuilder<NestedBuilder<B>, ProcedureFilter>
    {
        B parent;
        
        public NestedBuilder(B parent)
        {
            super(new ProcedureFilter());
            this.parent = parent;
        }
                
        public abstract B done();
    }
    
    
    @SuppressWarnings("unchecked")
    public static abstract class ProcedureFilterBuilder<
            B extends ProcedureFilterBuilder<B, F>,
            F extends ProcedureFilter>
        extends FeatureFilterBaseBuilder<B, IProcedure, F>
    {        
        
        protected ProcedureFilterBuilder(F instance)
        {
            super(instance);
        }
                
        
        @Override
        public B copyFrom(F base)
        {
            super.copyFrom(base);
            instance.parentFilter = base.parentFilter;
            instance.dataStreamFilter = base.dataStreamFilter;
            return (B)this;
        }
        
        
        /**
         * Select only procedures belonging to the matching groups
         * @param filter Parent procedure filter
         * @return This builder for chaining
         */
        public B withParents(ProcedureFilter filter)
        {
            instance.parentFilter = filter;
            return (B)this;
        }

        
        /**
         * Keep only procedures belonging to the matching groups.<br/>
         * Call done() on the nested builder to go back to main builder.
         * @return The {@link ProcedureFilter} builder for chaining
         */
        public ProcedureFilter.NestedBuilder<B> withParents()
        {
            return new ProcedureFilter.NestedBuilder<B>((B)this) {
                @Override
                public B done()
                {
                    ProcedureFilterBuilder.this.withParents(build());
                    return (B)ProcedureFilterBuilder.this;
                }                
            };
        }
        
        
        /**
         * Select only procedures belonging to the parent groups with
         * specific internal IDs
         * @param ids List of IDs of parent procedure groups
         * @return This builder for chaining
         */
        public B withParents(long... ids)
        {
            return withParents()
                .withInternalIDs(ids)
                .done();
        }
        
        
        /**
         * Select only procedures belonging to the parent groups with
         * specific unique IDs
         * @param uids List of UIDs of parent procedure groups
         * @return This builder for chaining
         */
        public B withParents(String... uids)
        {
            return withParents()
                .withUniqueIDs(uids)
                .done();
        }
        
        
        /**
         * Select only procedures that have no parent
         * @return This builder for chaining
         */
        public B withNoParent()
        {
            return withParents()
                .withInternalIDs(0)
                .done();
        }
        
        
        /**
         * Select only procedures with data streams matching the filter
         * @param filter Data stream filter
         * @return This builder for chaining
         */
        public B withDataStreams(DataStreamFilter filter)
        {
            instance.dataStreamFilter = filter;
            return (B)this;
        }

        
        /**
         * Keep only procedures from data streams matching the filter.<br/>
         * Call done() on the nested builder to go back to main builder.
         * @return The {@link DataStreamFilter} builder for chaining
         */
        public DataStreamFilter.NestedBuilder<B> withDataStreams()
        {
            return new DataStreamFilter.NestedBuilder<B>((B)this) {
                @Override
                public B done()
                {
                    ProcedureFilterBuilder.this.withDataStreams(build());
                    return (B)ProcedureFilterBuilder.this;
                }                
            };
        }
        

        /**
         * Select only procedures that produced observations of features of interest
         * matching the filter
         * @param filter Features of interest filter
         * @return This builder for chaining
         */
        public B withFois(FoiFilter filter)
        {
            return withDataStreams(new DataStreamFilter.Builder()
                .withFois(filter)
                .build());
        }

        
        /**
         * Select only procedures that produced observations of features of interest
         * matching the filter.<br/>
         * Call done() on the nested builder to go back to main builder.
         * @return The {@link FoiFilter} builder for chaining
         */
        public FoiFilter.NestedBuilder<B> withFois()
        {
            return new FoiFilter.NestedBuilder<B>((B)this) {
                @Override
                public B done()
                {
                    ProcedureFilterBuilder.this.withFois(build());
                    return (B)ProcedureFilterBuilder.this;
                }                
            };
        }
    }
}
