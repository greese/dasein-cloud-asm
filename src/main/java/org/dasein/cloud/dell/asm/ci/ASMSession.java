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

package org.dasein.cloud.dell.asm.ci;

import org.apache.log4j.Logger;
import org.dasein.cloud.CloudException;
import org.dasein.cloud.InternalException;
import org.dasein.cloud.OperationNotSupportedException;
import org.dasein.cloud.ci.AbstractConveredInfrastructureSupport;
import org.dasein.cloud.ci.CIFilterOptions;
import org.dasein.cloud.ci.CIProvisionOptions;
import org.dasein.cloud.ci.ConvergedInfrastructure;
import org.dasein.cloud.ci.ConvergedInfrastructureState;
import org.dasein.cloud.dell.asm.APIHandler;
import org.dasein.cloud.dell.asm.APIResponse;
import org.dasein.cloud.dell.asm.DellASM;
import org.dasein.cloud.util.APITrace;
import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;

/**
 * [Class Documentation]
 * <p>Created by George Reese: 6/3/13 3:55 PM</p>
 *
 * @author George Reese
 */
public class ASMSession extends AbstractConveredInfrastructureSupport<DellASM> {
    static private final Logger logger = DellASM.getLogger(ASMSession.class);

    public ASMSession(@Nonnull DellASM provider) { super(provider); }

    @Override
    public boolean isSubscribed() throws CloudException, InternalException {
        return true; // TODO: implement me
    }

    @Override
    public @Nonnull Iterable<ConvergedInfrastructure> listConvergedInfrastructures(@Nullable CIFilterOptions options) throws CloudException, InternalException {
        APITrace.begin(getProvider(), "listConvergedInfrastructures");
        try {
            if( logger.isTraceEnabled() ) {
                logger.trace("ENTER: " + ASMSession.class.getName() + ".listConvergedInfrastructures(" + options + ")");
            }
            try {
                APIHandler handler = new APIHandler(getProvider());
                StringBuilder xml = new StringBuilder();

                xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
                xml.append("<!DOCTYPE drl SYSTEM \"").append(handler.getEndpoint()).append("/labmagic/v1_2/api/archive/enumerateReservationRequest.dtd\">");

                xml.append("<drl mode=\"normal\" connectionid=\"").append(handler.getConnectionId()).append("\">");
                xml.append("<").append(APIHandler.ENUMERATE_RESERVATION).append(" reservationndlrequired=\"true\"").append("/>");
                xml.append("</drl>");

                APIResponse response = handler.post(APIHandler.ENUMERATE_RESERVATION, xml.toString());

                Document doc = response.getXML();

                if( doc == null ) {
                    Collection<ConvergedInfrastructure> infras = Collections.emptyList();

                    if( logger.isDebugEnabled() ) {
                        logger.debug("listConvergedInfrastructures(" + options + ")=" + infras);
                    }
                    return infras;
                }
                NodeList archives = doc.getElementsByTagName("archive");
                ArrayList<ConvergedInfrastructure> infras = new ArrayList<ConvergedInfrastructure>();

                for( int i=0; i<archives.getLength(); i++ ) {
                    Node archive = archives.item(i);
                    ConvergedInfrastructure ci = toCI(archive);

                    if( ci != null && (options == null || options.matches(ci)) ) {
                        infras.add(ci);
                    }
                }
                if( logger.isDebugEnabled() ) {
                    logger.debug("listConvergedInfrastructures(" + options + ")=" + infras);
                }
                return infras;
            }
            finally {
                if( logger.isTraceEnabled() ) {
                    logger.trace("EXIT: " + ASMSession.class.getName() + ".listConvergedInfrastructures()");
                }
            }
        }
        finally {
            APITrace.end();
        }
    }

    @Override
    public @Nonnull Iterable<String> listVirtualMachines(@Nonnull String ciId) throws CloudException, InternalException {
        return null; // TODO: implement me
    }

    @Override
    public @Nonnull Iterable<String> listVLANs(@Nonnull String ciId) throws CloudException, InternalException {
        return null; // TODO: implement me
    }

    @Override
    public @Nonnull ConvergedInfrastructure provision(@Nonnull CIProvisionOptions options) throws CloudException, InternalException {
        throw new OperationNotSupportedException("Not yet supported");
    }

    @Override
    public void terminate(@Nonnull String ciId, @Nullable String explanation) throws CloudException, InternalException {
        // TODO: implement me
    }

    private @Nullable  ConvergedInfrastructure toCI(@Nullable Node archive) throws CloudException, InternalException {
        if( archive == null ) {
            return null;
        }
        HashMap<String,String> tags = new HashMap<String, String>();
        String ciId = null, name = null, description = null, ownerId = null, statusString = null;
        ConvergedInfrastructureState state = ConvergedInfrastructureState.PENDING;
        String regionId = getContext().getRegionId();
        long created = 0L;
        String type = "";

        if( regionId == null ) {
            return null;
        }
        if( archive.hasAttributes() ) {
            NamedNodeMap attrs = archive.getAttributes();
            Node n;

            n = attrs.getNamedItem("reservationid");
            if( n != null ) {
                ciId = n.getNodeValue().trim();
            }
            n = attrs.getNamedItem("name");
            if( n != null ) {
                name = n.getNodeValue().trim();
            }
            n = attrs.getNamedItem("description");
            if( n != null ) {
                description = n.getNodeValue().trim();
            }
            n = attrs.getNamedItem("type");
            if( n != null ) {
                type = n.getNodeValue().trim();
            }
            n = attrs.getNamedItem("status");
            if( n != null ) {
                statusString = n.getNodeValue().trim();
            }
            n = attrs.getNamedItem("owner");
            if( n != null ) {
                ownerId = n.getNodeValue().trim();
            }
            else {
                ownerId = "--public--";
            }
            n = attrs.getNamedItem("createdtime");
            if( n != null ) {
                created = DellASM.parseTimestamp(n.getNodeValue().trim());
            }
        }
        if( statusString != null ) {
            if( statusString.equalsIgnoreCase("running") ) {
                state = ConvergedInfrastructureState.RUNNING;
            }
            else if( statusString.equalsIgnoreCase("deleted") ) {
                state = ConvergedInfrastructureState.DELETED;
            }
        }
        if( ciId == null || !"session".equalsIgnoreCase(type) ) {
            return null;
        }
        if( name == null ) {
            name = ciId;
        }
        if( description == null ) {
            description = name;
        }
        ConvergedInfrastructure ci = ConvergedInfrastructure.getInstance(ownerId, regionId, ciId, state, name, description).provisionedAt(created);

        ci.setTags(tags);
        return ci;
    }
}
