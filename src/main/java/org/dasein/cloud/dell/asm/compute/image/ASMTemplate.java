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
import org.dasein.cloud.compute.AbstractImageSupport;
import org.dasein.cloud.compute.Architecture;
import org.dasein.cloud.compute.ImageClass;
import org.dasein.cloud.compute.ImageFilterOptions;
import org.dasein.cloud.compute.MachineImage;
import org.dasein.cloud.compute.MachineImageFormat;
import org.dasein.cloud.compute.MachineImageState;
import org.dasein.cloud.compute.Platform;
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
public class ASMTemplate extends AbstractImageSupport {
    static private final Logger logger = DellASM.getLogger(ASMTemplate.class);

    public ASMTemplate(@Nonnull DellASM provider) { super(provider); }

    @Override
    public MachineImage getImage(@Nonnull String providerImageId) throws CloudException, InternalException {
        APITrace.trace(getProvider(), "getImage");
        try {
            if( logger.isTraceEnabled() ) {
                logger.trace("ENTER: " + ASMTemplate.class.getName() + ".getImage(" + providerImageId + ")");
            }
            try {
                // TODO: optimize
                for( MachineImage img : listImages((ImageFilterOptions)null) ) {
                    if( img.getProviderMachineImageId().equals(providerImageId) ) {
                        if( logger.isDebugEnabled() ) {
                            logger.debug("getImage(" + providerImageId + ")=" + img);
                        }
                        return img;
                    }
                }
                if( logger.isDebugEnabled() ) {
                    logger.debug("getImage(" + providerImageId + ")=null");
                }
                return null;
            }
            finally {
                if( logger.isTraceEnabled() ) {
                    logger.trace("EXIT: " + ASMTemplate.class.getName() + ".getImage()");
                }
            }
        }
        finally {
            APITrace.end();
        }
    }

    @Override
    public @Nonnull String getProviderTermForImage(@Nonnull Locale locale, @Nonnull ImageClass cls) {
        return "template";
    }

    @Override
    public boolean isSubscribed() throws CloudException, InternalException {
        return true;
    }

    @Override
    public @Nonnull Iterable<MachineImage> listImages(@Nullable ImageFilterOptions options) throws CloudException, InternalException {
        APITrace.trace(getProvider(), "listImages");
        try {
            if( logger.isTraceEnabled() ) {
                logger.trace("ENTER: " + ASMTemplate.class.getName() + ".listImages(" + options + ")");
            }
            try {
                APIHandler handler = new APIHandler((DellASM)getProvider());
                StringBuilder xml = new StringBuilder();

                xml.append("<drl mode=\"normal\" connectionid=\"").append(handler.getConnectionId()).append("\">");
                xml.append("<").append(APIHandler.ENUMERATE_ARCHIVE).append(" />");
                xml.append("</drl>");

                APIResponse response = handler.post(APIHandler.ENUMERATE_ARCHIVE, xml.toString());

                Document doc = response.getXML();

                if( doc == null ) {
                    Collection<MachineImage> images = Collections.emptyList();

                    if( logger.isDebugEnabled() ) {
                        logger.debug("listImages(" + options + ")=" + images);
                    }
                    return images;
                }
                NodeList archives = doc.getElementsByTagName("archive");
                ArrayList<MachineImage> images = new ArrayList<MachineImage>();

                for( int i=0; i<archives.getLength(); i++ ) {
                    Node archive = archives.item(i);
                    MachineImage img = toImage(archive);

                    if( img != null && (options == null || options.matches(img)) ) {
                        images.add(img);
                    }
                }
                if( logger.isDebugEnabled() ) {
                    logger.debug("listImages(" + options + ")=" + images);
                }
                return images;
            }
            finally {
                if( logger.isTraceEnabled() ) {
                    logger.trace("EXIT: " + ASMTemplate.class.getName() + ".listImages()");
                }
            }
        }
        finally {
            APITrace.end();
        }
    }

    @Override
    public void remove(@Nonnull String providerImageId, boolean checkState) throws CloudException, InternalException {
        //To change body of implemented methods use File | Settings | File Templates.
    }

    private @Nullable MachineImage toImage(@Nullable Node archive) throws CloudException, InternalException {
        if( archive == null ) {
            return null;
        }
        HashMap<String,String> tags = new HashMap<String, String>();
        MachineImageState state = MachineImageState.PENDING;
        Architecture architecture = Architecture.I64;
        String regionId = getContext().getRegionId();
        String ownerId = null, imageId = null;
        String name = null, description = null;
        Platform platform = Platform.UNKNOWN;
        long created = 0L;

        if( regionId == null ) {
            throw new NoContextException();
        }
        if( archive.hasAttributes() ) {
            NamedNodeMap attrs = archive.getAttributes();
            Node n;

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
        if( imageId == null ) {
            return null;
        }
        if( name == null ) {
            name = imageId;
        }
        if( description == null ) {
            description = name;
        }
        MachineImage img = MachineImage.getMachineImageInstance(ownerId, regionId, imageId, state, name, description, architecture, platform, MachineImageFormat.RAW).createdAt(created);

        img.setTags(tags);
        return img;
    }
}
