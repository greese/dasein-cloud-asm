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
import org.dasein.cloud.AbstractCloud;
import org.dasein.cloud.ProviderContext;

import javax.annotation.Nonnegative;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Bootstrap class for interacting with Dell ASM per the Dasein Cloud API.
 * @author George Reese
 * @version 2013.04 initial version
 * @since 2013.04
 */
public class DellASM extends AbstractCloud {
    static private final Logger logger = getLogger(DellASM.class);

    static private @Nonnull String getLastItem(@Nonnull String name) {
        int idx = name.lastIndexOf('.');

        if( idx < 0 ) {
            return name;
        }
        else if( idx == (name.length()-1) ) {
            return "";
        }
        return name.substring(idx + 1);
    }

    /**
     * Provides access to a log4j logger aligned with the naming conventions for standard Dasein Cloud logging.
     * @param cls the class in which logging is being done
     * @return a log4j logger that handles standard message logging
     */
    static public @Nonnull Logger getLogger(@Nonnull Class<?> cls) {
        String pkg = getLastItem(cls.getPackage().getName());

        if( pkg.equals("asm") ) {
            pkg = "";
        }
        else {
            pkg = pkg + ".";
        }
        return Logger.getLogger("dasein.cloud.dell.asm.std." + pkg + getLastItem(cls.getName()));
    }

    /**
     * Provides access to a log4j logger aligned with the naming conventions for Dasein Cloud wire logging. The wire logging
     * is solely for logging data going over the network and not for internal messaging.
     * @param cls the class in which the wire logging is being done
     * @return the log4j logger that handles wire message logging
     */
    static public @Nonnull Logger getWireLogger(@Nonnull Class<?> cls) {
        return Logger.getLogger("dasein.cloud.dell.asm.wire." + getLastItem(cls.getPackage().getName()) + "." + getLastItem(cls.getName()));
    }

    static public @Nonnegative long parseTimestamp(@Nonnull String ts) {
        //
        return 0L;
    }

    /**
     * Empty constructor to create an instance of the {@link DellASM} cloud provider class.
     */
    public DellASM() { }

    @Override
    public @Nonnull String getCloudName() {
        ProviderContext ctx = getContext();
        String name = (ctx == null ? null : ctx.getCloudName());

        return (name == null ? "Dell ASM CI" : name);
    }

    @Override
    public @Nonnull DataCenters getDataCenterServices() {
        return new DataCenters(this);
    }

    @Override
    public @Nonnull String getProviderName() {
        return "Dell ASM";
    }

    @Override
    public @Nullable String testContext() {
        if( logger.isTraceEnabled() ) {
            logger.trace("ENTER - " + DellASM.class.getName() + ".testContext()");
        }
        try {
            ProviderContext ctx = getContext();

            if( ctx == null ) {
                logger.warn("No context was provided for testing");
                return null;
            }
            try {
                // TODO: Go to DellASM and verify that the specified credentials in the context are correct
                // return null if they are not
                // return an account number if they are
                return null;
            }
            catch( Throwable t ) {
                logger.error("Error querying API key: " + t.getMessage());
                t.printStackTrace();
                return null;
            }
        }
        finally {
            if( logger.isTraceEnabled() ) {
                logger.trace("EXIT - " + DellASM.class.getName() + ".textContext()");
            }
        }
    }
}