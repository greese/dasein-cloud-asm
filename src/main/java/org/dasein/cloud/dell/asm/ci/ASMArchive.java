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
import org.dasein.cloud.ci.AbstractTopologySupport;
import org.dasein.cloud.ci.Topology;
import org.dasein.cloud.ci.TopologyFilterOptions;
import org.dasein.cloud.ci.TopologyState;
import org.dasein.cloud.compute.Architecture;
import org.dasein.cloud.compute.Platform;
import org.dasein.cloud.dell.asm.APIHandler;
import org.dasein.cloud.dell.asm.APIResponse;
import org.dasein.cloud.dell.asm.DellASM;
import org.dasein.cloud.dell.asm.NoContextException;
import org.dasein.cloud.util.APITrace;
import org.dasein.cloud.util.XMLParser;
import org.dasein.util.uom.storage.Megabyte;
import org.dasein.util.uom.storage.Storage;
import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.xml.parsers.ParserConfigurationException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;

/**
 * [Class Documentation]
 * <p>Created by George Reese: 6/3/13 3:56 PM</p>
 *
 * @author George Reese
 */
public class ASMArchive extends AbstractTopologySupport<DellASM> {
    static private final Logger logger = DellASM.getLogger(ASMArchive.class);

    public ASMArchive(@Nonnull DellASM provider) { super(provider); }

    @Override
    public Topology getTopology(@Nonnull String providerTopologyId) throws CloudException, InternalException {
        APITrace.begin(getProvider(), "getTopology");
        try {
            if( logger.isTraceEnabled() ) {
                logger.trace("ENTER: " + ASMArchive.class.getName() + ".getTopology(" + providerTopologyId + ")");
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
                    logger.trace("EXIT: " + ASMArchive.class.getName() + ".getTopology()");
                }
            }
        }
        finally {
            APITrace.end();
        }
    }

    @Override
    public @Nonnull String getProviderTermForTopology(@Nonnull Locale locale) {
        return "archive";
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
                logger.trace("ENTER: " + ASMArchive.class.getName() + ".listTopologies(" + options + ")");
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
                    logger.trace("EXIT: " + ASMArchive.class.getName() + ".listTopologies()");
                }
            }
        }
        finally {
            APITrace.end();
        }
    }


    private @Nullable  Topology toTopology(@Nullable Node archive) throws CloudException, InternalException {
        if( archive == null ) {
            return null;
        }
        HashMap<String,String> tags = new HashMap<String, String>();
        ArrayList<Topology.VMDevice> vms = new ArrayList<Topology.VMDevice>();
        ArrayList<Topology.VLANDevice> vlans = new ArrayList<Topology.VLANDevice>();
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
        if( archive.hasChildNodes() ) {
            NodeList items = archive.getChildNodes();

            for( int i=0; i<items.getLength(); i++ ) {
                Node n = items.item(i);

                if( n.getNodeName().equalsIgnoreCase("content") && n.hasChildNodes() ) {
                    String xml = n.getFirstChild().getNodeValue();

                    try {
                        Document doc = XMLParser.parse(new ByteArrayInputStream(xml.getBytes()));
                        NodeList topologies = doc.getElementsByTagName("topology");

                        for( int j=0; j<topologies.getLength(); j++ ) {
                            Node t = topologies.item(j);

                            if( t.hasChildNodes() ) {
                                NodeList children = t.getChildNodes();

                                // have to parse all devices first
                                // yes, the DTD says they should all come first, but I never trust that
                                for( int k=0; k<children.getLength(); k++ ) {
                                    Node child = children.item(k);

                                    if( child.getNodeName().equalsIgnoreCase("device") ) {
                                        parseDevice(child, vms, vlans);
                                    }
                                }
                                // then attributes
                                for( int k=0; k<children.getLength(); k++ ) {
                                    Node child = children.item(k);
                                    if( child.getNodeName().equalsIgnoreCase("attribute") ) {
                                        parseAttribute(child, vms, vlans);
                                    }
                                }
                            }
                        }
                    }
                    catch( IOException e ) {
                        throw new CloudException(e);
                    }
                    catch( ParserConfigurationException e ) {
                        throw new InternalException(e);
                    }
                    catch( SAXException e ) {
                        throw new CloudException(e);
                    }
                }
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
        if( !vms.isEmpty() ) {
            t.withVirtualMachines(vms.toArray(new Topology.VMDevice[vms.size()]));
        }
        if( !vlans.isEmpty() ) {
            t.withVLANs(vlans.toArray(new Topology.VLANDevice[vlans.size()]));
        }
        return t;
    }

    private void parseAttribute(@Nonnull Node node, @Nonnull List<Topology.VMDevice> vms, @Nonnull List<Topology.VLANDevice> vlans) throws CloudException, InternalException {
        if( !node.hasAttributes() || !node.hasChildNodes() ) {
            return;
        }
        NamedNodeMap attrs = node.getAttributes();
        String name = null, refs = null;

        Node a = attrs.getNamedItem("name");

        if( a != null ) {
            name = a.getNodeValue().trim().toLowerCase();
        }
        a = attrs.getNamedItem("refs");
        if( a != null ) {
            refs = a.getNodeValue().trim().toLowerCase();
        }
        if( name == null || refs == null ) {
            return;
        }


        Topology.VLANDevice vlan = null;
        Topology.VMDevice vm = null;

        for( Topology.VMDevice d : vms ) {
            if( d.getDeviceId().equals(refs) ) {
                vm = d;
                break;
            }
        }
        if( vm == null ) {
            for( Topology.VLANDevice d : vlans ) {
                if( d.getDeviceId().equals(refs) ) {
                    vlan = d;
                    break;
                }
            }
            if( vlan == null ) {
                return;
            }
            // TODO: parse VLAN stuff
        }
        else {
            if( name.equalsIgnoreCase("al_osimages") ) {
                NodeList values = node.getChildNodes();

                for( int i=0; i<values.getLength(); i++ ) {
                    Node n = values.item(i);

                    if( n.getNodeName().equalsIgnoreCase("value") && n.hasChildNodes() ) {
                        NodeList osimages = n.getChildNodes();

                        for( int j=0; j<osimages.getLength(); j++ ) {
                            Node item = osimages.item(j);

                            if( item.getNodeName().equalsIgnoreCase("osimages") && item.hasChildNodes() ) {
                                NodeList elements = item.getChildNodes();

                                for( int k=0; k<elements.getLength(); k++ ) {
                                    Node element = elements.item(k);

                                    if( element.getNodeName().equalsIgnoreCase("element") && element.hasAttributes() ) {
                                        NamedNodeMap os = element.getAttributes();
                                        StringBuilder osname = new StringBuilder();
                                        Node o;

                                        o = os.getNamedItem("name");
                                        if( o != null ) {
                                            osname.append(o.getNodeValue().trim());
                                        }
                                        o = os.getNamedItem("path");
                                        if( o != null ) {
                                            osname.append(" ").append(o.getNodeValue().trim());
                                        }
                                        Platform platform = Platform.guess(osname.toString());

                                        if( !platform.equals(Platform.UNKNOWN) ) {
                                            vm.withPlatform(platform);
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

    }

    private void parseDevice(@Nonnull Node node, @Nonnull List<Topology.VMDevice> vms, @Nonnull List<Topology.VLANDevice> vlans) throws CloudException, InternalException {
        if( !node.hasAttributes() ) {
            return;
        }
        NamedNodeMap attrs = node.getAttributes();
        String type = null;

        Node a = attrs.getNamedItem("model");

        if( a != null ) {
            type = a.getNodeValue().trim().toLowerCase();
        }
        if( type == null ) {
            return;
        }
        if( type.equals("virtualmachine") || type.equals("servers") ) {
            Storage<Megabyte> memory = new Storage<Megabyte>(1, Storage.MEGABYTE);
            Architecture architecture = Architecture.I64;
            Platform platform = Platform.UNKNOWN;
            int capacity = 1, cpuCount = 1;
            String deviceId, name;
            String[] interfaces = new String[0];

            a = attrs.getNamedItem("key");
            if( a == null ) {
                return;
            }
            deviceId = a.getNodeValue().trim();
            if( deviceId.equals("") ) {
                return;
            }
            a = attrs.getNamedItem("name");
            if( a == null ) {
                name = deviceId;
            }
            else {
                name = a.getNodeValue().trim();
            }
            if( node.hasChildNodes() ) {
                NodeList children = node.getChildNodes();

                for( int i=0; i<children.getLength(); i++ ) {
                    Node child = children.item(i);

                    if( child.getNodeName().equalsIgnoreCase("enforcedproperties") && child.hasChildNodes() ) {
                        NodeList properties = child.getChildNodes();

                        for( int j=0; j<properties.getLength(); j++ ) {
                            Node property = properties.item(j);

                            if( property.getNodeName().equalsIgnoreCase("property") && property.hasAttributes() ) {
                                NamedNodeMap pa = property.getAttributes();
                                Node n = pa.getNamedItem("name");
                                Node v = pa.getNamedItem("value");

                                if( n != null && v != null ) {
                                    if( n.getNodeValue().equalsIgnoreCase("cpu") ) {
                                        try {
                                            cpuCount = Integer.parseInt(v.getNodeValue());
                                        }
                                        catch( NumberFormatException e ) {
                                            logger.warn("Invalid CPU count value: " + v.getNodeValue());
                                            return;
                                        }
                                    }
                                    else if( n.getNodeValue().equalsIgnoreCase("ram") ) {
                                        try {
                                            memory = new Storage<Megabyte>(Integer.parseInt(v.getNodeValue()), Storage.MEGABYTE);
                                        }
                                        catch( NumberFormatException e ) {
                                            logger.warn("Invalid RAM value: " + v.getNodeValue());
                                            return;
                                        }
                                    }
                                    else if( n.getNodeValue().equalsIgnoreCase("servercount") ) {
                                        try {
                                            capacity = Integer.parseInt(v.getNodeValue());
                                        }
                                        catch( NumberFormatException e ) {
                                            logger.warn("Invalid server count value: " + v.getNodeValue());
                                            return;
                                        }
                                    }
                                }
                            }
                        }
                    }
                    else if( child.getNodeName().equalsIgnoreCase("interface") && child.hasChildNodes() ) {
                        // TODO: interface parsing
                    }
                }
            }
            vms.add(Topology.VMDevice.getInstance(deviceId, capacity, name, cpuCount, memory, architecture, platform, interfaces));
        }
        else if( type.equals("vlan") ) {
            // TODO: parse VLAN
        }
    }
}
