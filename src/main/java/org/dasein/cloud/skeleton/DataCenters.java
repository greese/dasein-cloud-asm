/**
 * Copyright (C) 2012-2013 Dell, Inc.
 * See annotations for authorship information
 *
 * ====================================================================
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * ====================================================================
 */

package org.dasein.cloud.skeleton;

import org.apache.log4j.Logger;
import org.dasein.cloud.CloudException;
import org.dasein.cloud.InternalException;
import org.dasein.cloud.ProviderContext;
import org.dasein.cloud.dc.DataCenter;
import org.dasein.cloud.dc.DataCenterServices;
import org.dasein.cloud.dc.Region;
import org.dasein.cloud.util.APITrace;
import org.dasein.cloud.util.Cache;
import org.dasein.cloud.util.CacheLevel;
import org.dasein.util.uom.time.Day;
import org.dasein.util.uom.time.Hour;
import org.dasein.util.uom.time.TimePeriod;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Locale;

/**
 * Unimplemented skeleton class
 * @author INSERT NAME HERE
 * @version 2013.01 initial version
 * @since 2013.01
 */
public class DataCenters implements DataCenterServices {
    static private final Logger logger = MyCloud.getLogger(DataCenters.class);

    private MyCloud provider;

    DataCenters(@Nonnull MyCloud provider) { this.provider = provider; }

    @Override
    public @Nullable DataCenter getDataCenter(@Nonnull String dataCenterId) throws InternalException, CloudException {
        for( Region region : listRegions() ) {
            for( DataCenter dc : listDataCenters(region.getProviderRegionId()) ) {
                if( dataCenterId.equals(dc.getProviderDataCenterId()) ) {
                    return dc;
                }
            }
        }
        return null;
    }

    @Override
    public @Nonnull String getProviderTermForDataCenter(@Nonnull Locale locale) {
        return "data center";
    }

    @Override
    public @Nonnull String getProviderTermForRegion(@Nonnull Locale locale) {
        return "region";
    }

    @Override
    public @Nullable Region getRegion(@Nonnull String providerRegionId) throws InternalException, CloudException {
        for( Region r : listRegions() ) {
            if( providerRegionId.equals(r.getProviderRegionId()) ) {
                return r;
            }
        }
        return null;
    }

    @Override
    public @Nonnull Collection<DataCenter> listDataCenters(@Nonnull String providerRegionId) throws InternalException, CloudException {
        APITrace.begin(provider, "listDataCenters");
        try {
            Region region = getRegion(providerRegionId);

            if( region == null ) {
                throw new CloudException("No such region: " + providerRegionId);
            }
            ProviderContext ctx = provider.getContext();

            if( ctx == null ) {
                throw new NoContextException();
            }
            Cache<DataCenter> cache = Cache.getInstance(provider, "dataCenters", DataCenter.class, CacheLevel.REGION_ACCOUNT, new TimePeriod<Day>(1, TimePeriod.DAY));
            Collection<DataCenter> dcList = (Collection<DataCenter>)cache.get(ctx);

            if( dcList != null ) {
                return dcList;
            }
            ArrayList<DataCenter> dataCenters = new ArrayList<DataCenter>();
            // TODO: query the API for the data center list
            cache.put(ctx, dataCenters);
            return dataCenters;
        }
        finally {
            APITrace.end();
        }
    }

    @Override
    public Collection<Region> listRegions() throws InternalException, CloudException {
        APITrace.begin(provider, "listRegions");
        try {
            ProviderContext ctx = provider.getContext();

            if( ctx == null ) {
                throw new NoContextException();
            }
            Cache<Region> cache = Cache.getInstance(provider, "regions", Region.class, CacheLevel.CLOUD_ACCOUNT, new TimePeriod<Hour>(10, TimePeriod.HOUR));
            Collection<Region> regions = (Collection<Region>)cache.get(ctx);

            if( regions != null ) {
                return regions;
            }
            regions = new ArrayList<Region>();
            // TODO: query the API for the regions
            cache.put(ctx, regions);
            return regions;

        }
        finally {
            APITrace.end();
        }
    }
}
