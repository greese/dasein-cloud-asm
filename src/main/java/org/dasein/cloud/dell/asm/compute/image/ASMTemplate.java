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

package org.dasein.cloud.dell.asm.compute.image;

import org.apache.log4j.Logger;
import org.dasein.cloud.CloudException;
import org.dasein.cloud.InternalException;
import org.dasein.cloud.OperationNotSupportedException;
import org.dasein.cloud.compute.AbstractTopologySupport;
import org.dasein.cloud.compute.Topology;
import org.dasein.cloud.compute.TopologyFilterOptions;
import org.dasein.cloud.compute.TopologyProvisionOptions;
import org.dasein.cloud.compute.TopologyState;
import org.dasein.cloud.compute.VirtualMachine;
import org.dasein.cloud.dell.asm.APIHandler;
import org.dasein.cloud.dell.asm.APIResponse;
import org.dasein.cloud.dell.asm.DellASM;
import org.dasein.cloud.dell.asm.NoContextException;
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
import java.util.Locale;

/**
 * Implements support for ASM templates as Dasein Cloud images.
 * <p>Created by George Reese: 5/28/13 6:24 PM</p>
 * @author George Reese
 * @version 2013.07 initial version
 * @since 2013.07
 */
public class ASMTemplate extends AbstractTopologySupport<DellASM> {
    static private final Logger logger = DellASM.getLogger(ASMTemplate.class);

    public ASMTemplate(@Nonnull DellASM provider) { super(provider); }

    @Override
    public Topology getTopology(@Nonnull String providerTopologyId) throws CloudException, InternalException {
        APITrace.begin(getProvider(), "getTopology");
        try {
            if( logger.isTraceEnabled() ) {
                logger.trace("ENTER: " + ASMTemplate.class.getName() + ".getTopology(" + providerTopologyId + ")");
            }
            try {
                // TODO: optimize
                for( Topology t : listTopologies(null) ) {
                    if( t.getProviderTopologyId().equals(providerTopologyId) ) {
                        if( logger.isDebugEnabled() ) {
                            logger.debug("getTopology(" + providerTopologyId + ")=" + t);
                        }
                        return t;
                    }
                }
                if( logger.isDebugEnabled() ) {
                    logger.debug("getTopology(" + providerTopologyId + ")=null");
                }
                return null;
            }
            finally {
                if( logger.isTraceEnabled() ) {
                    logger.trace("EXIT: " + ASMTemplate.class.getName() + ".getTopology()");
                }
            }
        }
        finally {
            APITrace.end();
        }
    }

    @Override
    public @Nonnull String getProviderTermForTopology(@Nonnull Locale locale) {
        return "template";
    }

    @Override
    public boolean isSubscribed() throws CloudException, InternalException {
        return true;
    }

    @Override
    public @Nonnull Iterable<Topology> listTopologies(@Nullable TopologyFilterOptions options) throws CloudException, InternalException {
        APITrace.begin(getProvider(), "listTopologies");
        try {
            if( logger.isTraceEnabled() ) {
                logger.trace("ENTER: " + ASMTemplate.class.getName() + ".listTopologies(" + options + ")");
            }
            try {
                APIHandler handler = new APIHandler(getProvider());
                StringBuilder xml = new StringBuilder();

                xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
                xml.append("<!DOCTYPE drl SYSTEM \"").append(handler.getEndpoint()).append("/labmagic/v1_2/api/archive/enumerateArchiveRequest.dtd\">");

                xml.append("<drl mode=\"normal\" connectionid=\"").append(handler.getConnectionId()).append("\">");
                xml.append("<").append(APIHandler.ENUMERATE_ARCHIVE).append(" type=\"TOPOLOGY\" fetch=\"deep\"").append("/>");
                xml.append("</drl>");

                APIResponse response = handler.post(APIHandler.ENUMERATE_ARCHIVE, xml.toString());

                Document doc = response.getXML();

                if( doc == null ) {
                    Collection<Topology> topologies = Collections.emptyList();

                    if( logger.isDebugEnabled() ) {
                        logger.debug("listTopologies(" + options + ")=" + topologies);
                    }
                    return topologies;
                }
                NodeList archives = doc.getElementsByTagName("archive");
                ArrayList<Topology> topologies = new ArrayList<Topology>();

                for( int i=0; i<archives.getLength(); i++ ) {
                    Node archive = archives.item(i);
                    Topology t = toTopology(archive);

                    if( t != null && (options == null || options.matches(t)) ) {
                        topologies.add(t);
                    }
                }
                if( logger.isDebugEnabled() ) {
                    logger.debug("listTopologies(" + options + ")=" + topologies);
                }
                return topologies;
            }
            finally {
                if( logger.isTraceEnabled() ) {
                    logger.trace("EXIT: " + ASMTemplate.class.getName() + ".listTopologies()");
                }
            }
        }
        finally {
            APITrace.end();
        }
    }

    @Override
    public @Nonnull Iterable<VirtualMachine> provision(@Nonnull TopologyProvisionOptions options) throws CloudException, InternalException {
        throw new OperationNotSupportedException("Not yet supported");
    }

    private @Nullable  Topology toTopology(@Nullable Node archive) throws CloudException, InternalException {
        if( archive == null ) {
            return null;
        }
        HashMap<String,String> tags = new HashMap<String, String>();
        TopologyState state = TopologyState.OFFLINE;
        String regionId = getContext().getRegionId();
        String ownerId = null, topologyId = null;
        String name = null, description = null;
        long created = 0L;

        if( regionId == null ) {
            throw new NoContextException();
        }
        if( archive.hasAttributes() ) {
            NamedNodeMap attrs = archive.getAttributes();
            Node n;

            n = attrs.getNamedItem("namespace");
            if( n != null ) {
                topologyId = n.getNodeValue().trim();
            }
            n = attrs.getNamedItem("name");
            if( n != null ) {
                name = n.getNodeValue().trim();
            }
            n = attrs.getNamedItem("description");
            if( n != null ) {
                description = n.getNodeValue().trim();
            }
            n = attrs.getNamedItem("owner");
            if( n != null ) {
                ownerId = n.getNodeValue().trim();
            }
            else {
                ownerId = "--public--";
            }
            n = attrs.getNamedItem("importedtime");
            if( n != null ) {
                created = DellASM.parseTimestamp(n.getNodeValue().trim());
            }
            n = attrs.getNamedItem("devicemodel");
            if( n != null ) {
                tags.put("devicemodel", n.getNodeValue().trim());
            }
            n = attrs.getNamedItem("devicemanufacturer");
            if( n != null ) {
                tags.put("devicemanufacturer", n.getNodeValue().trim());
            }
            n = attrs.getNamedItem("isrecycled");
            if( n != null ) {
                tags.put("isrecycled", n.getNodeValue().trim());
            }
            n = attrs.getNamedItem("ismaster");
            if( n != null ) {
                tags.put("ismaster", n.getNodeValue().trim());
            }
        }
        if( topologyId == null ) {
            return null;
        }
        if( name == null ) {
            name = topologyId;
        }
        if( description == null ) {
            description = name;
        }
        Topology t = Topology.getInstance(ownerId, regionId, topologyId, state, name, description).createdAt(created);

        t.setTags(tags);
        return t;
    }
}
