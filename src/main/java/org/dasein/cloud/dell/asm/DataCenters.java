/**
 * Copyright (C) 2013 Dell, Inc.
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

package org.dasein.cloud.dell.asm;

import org.apache.log4j.Logger;
import org.dasein.cloud.CloudException;
import org.dasein.cloud.InternalException;
import org.dasein.cloud.ProviderContext;
import org.dasein.cloud.dc.DataCenter;
import org.dasein.cloud.dc.DataCenterServices;
import org.dasein.cloud.dc.Jurisdiction;
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
 * Describes the topology of a converged infrastructure managed by Dell ASM. The current implementation hard-codes
 * a single region with a single data center. The ID of the region and the ID of the data center are identical, with
 * the ID of the region coming from the country for the default locale.
 * @author George Reese
 * @version 2013.04 initial version
 * @since 2013.04
 */
public class DataCenters implements DataCenterServices {
    static private final Logger logger = DellASM.getLogger(DataCenters.class);

    private DellASM provider;

    DataCenters(@Nonnull DellASM provider) { this.provider = provider; }

    @Override
    public @Nullable DataCenter getDataCenter(@Nonnull String dataCenterId) throws InternalException, CloudException {
        if( logger.isTraceEnabled() ) {
            logger.trace("ENTER: " + getClass().getName() + ".getDataCenter(" + dataCenterId + ")");
        }
        try {
            for( Region region : listRegions() ) {
                for( DataCenter dc : listDataCenters(region.getProviderRegionId()) ) {
                    if( dataCenterId.equals(dc.getProviderDataCenterId()) ) {
                        if( logger.isDebugEnabled() ) {
                            logger.debug("getDataCenter(" + dataCenterId + ")=" + dc);
                        }
                        return dc;
                    }
                }
            }
            if( logger.isDebugEnabled() ) {
                logger.debug("getDataCenter(" + dataCenterId + ")=null");
            }
            return null;
        }
        finally {
            if( logger.isTraceEnabled() ) {
                logger.trace("EXIT: " + getClass().getName() + ".getDataCenter()");
            }
        }
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
        if( logger.isTraceEnabled() ) {
            logger.trace("ENTER: " + getClass().getName() + ".getRegion(" + providerRegionId + ")");
        }
        try {
            for( Region r : listRegions() ) {
                if( providerRegionId.equals(r.getProviderRegionId()) ) {
                    if( logger.isDebugEnabled() ) {
                        logger.debug("getRegion(" + providerRegionId + ")=" + r);
                    }
                    return r;
                }
            }
            if( logger.isDebugEnabled() ) {
                logger.debug("getRegion(" + providerRegionId + ")=null");
            }
            return null;
        }
        finally {
            if( logger.isTraceEnabled() ) {
                logger.trace("EXIT: " + getClass().getName() + ".getRegion()");
            }
        }
    }

    @Override
    public @Nonnull Collection<DataCenter> listDataCenters(@Nonnull String providerRegionId) throws InternalException, CloudException {
        APITrace.begin(provider, "listDataCenters");
        try {
            if( logger.isTraceEnabled() ) {
                logger.trace("ENTER: " + getClass().getName() + ".listDataCenters(" + providerRegionId + ")");
            }
            try {
                Region region = getRegion(providerRegionId);

                if( region == null ) {
                    logger.warn("Attempt to fetch data centers for non-existent region: " + providerRegionId);
                    throw new CloudException("No such region: " + providerRegionId);
                }
                ProviderContext ctx = provider.getContext();

                if( ctx == null ) {
                    logger.warn("Attempt to fetch data centers for " + providerRegionId + " with no context");
                    throw new NoContextException();
                }
                Cache<DataCenter> cache = Cache.getInstance(provider, "dataCenters", DataCenter.class, CacheLevel.REGION_ACCOUNT, new TimePeriod<Day>(1, TimePeriod.DAY));
                Collection<DataCenter> dcList = (Collection<DataCenter>)cache.get(ctx);

                if( dcList != null ) {
                    if( logger.isDebugEnabled() ) {
                        logger.debug("listDataCenters(" + providerRegionId + ")=" + dcList);
                    }
                    return dcList;
                }
                ArrayList<DataCenter> dataCenters = new ArrayList<DataCenter>();

                DataCenter dc = new DataCenter();

                dc.setActive(true);
                dc.setAvailable(true);
                dc.setName(region.getName() + " (DC)");
                dc.setProviderDataCenterId(region.getProviderRegionId());
                dc.setRegionId(region.getProviderRegionId());
                dataCenters.add(dc);
                cache.put(ctx, dataCenters);
                if( logger.isDebugEnabled() ) {
                    logger.debug("listDataCenters(" + providerRegionId + ")=" + dataCenters);
                }
                return dataCenters;
            }
            finally {
                if( logger.isTraceEnabled() ) {
                    logger.trace("EXIT: " + getClass().getName() + ".listDataCenters()");
                }
            }
        }
        finally {
            APITrace.end();
        }
    }

    @Override
    public Collection<Region> listRegions() throws InternalException, CloudException {
        APITrace.begin(provider, "listRegions");
        try {
            if( logger.isTraceEnabled() ) {
                logger.trace("ENTER: " + getClass().getName() + ".listRegions()");
            }
            try {
                ProviderContext ctx = provider.getContext();

                if( ctx == null ) {
                    logger.warn("Attempt to fetch regions with no context");
                    throw new NoContextException();
                }
                Cache<Region> cache = Cache.getInstance(provider, "regions", Region.class, CacheLevel.CLOUD_ACCOUNT, new TimePeriod<Hour>(10, TimePeriod.HOUR));
                Collection<Region> regions = (Collection<Region>)cache.get(ctx);

                if( regions != null ) {
                    if( logger.isDebugEnabled() ) {
                        logger.debug("listRegions()=" + regions);
                    }
                    return regions;
                }
                regions = new ArrayList<Region>();

                Region region = new Region();

                region.setActive(true);
                region.setAvailable(true);
                String ctry = Locale.getDefault().getCountry();

                if( ctry == null || ctry.equals("")  ) {
                    ctry = "US";
                }
                try {
                    Jurisdiction.valueOf(ctry);
                }
                catch( Throwable ignore ) {
                    ctry = "US";
                }
                region.setJurisdiction(ctry);
                region.setName(ctry);
                region.setProviderRegionId(ctry);
                regions.add(region);
                cache.put(ctx, regions);
                if( logger.isDebugEnabled() ) {
                    logger.debug("listRegions()=" + regions);
                }
                return regions;
            }
            finally {
                if( logger.isTraceEnabled() ) {
                    logger.trace("EXIT: " + getClass().getName() + ".listRegions()");
                }
            }
        }
        finally {
            APITrace.end();
        }
    }
}
