/**
 * Copyright (C) 2010-2012 enStratus Networks Inc
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

package org.dasein.cloud.dell.asm.compute;

import org.apache.commons.lang.StringEscapeUtils;
import org.apache.log4j.Logger;
import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.Velocity;
import org.apache.velocity.exception.ResourceNotFoundException;
import org.dasein.cloud.CloudErrorType;
import org.dasein.cloud.CloudException;
import org.dasein.cloud.InternalException;
import org.dasein.cloud.ProviderContext;
import org.dasein.cloud.compute.*;
import org.dasein.cloud.dell.asm.APIHandler;
import org.dasein.cloud.dell.asm.APIResponse;
import org.dasein.cloud.dell.asm.ASMException;
import org.dasein.cloud.dell.asm.DellASM;
import org.dasein.cloud.util.APITrace;
import org.dasein.cloud.util.XMLParser;
import org.dasein.util.CalendarWrapper;
import org.dasein.util.Jiterator;
import org.dasein.util.JiteratorPopulator;
import org.dasein.util.PopulatorThread;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.ByteArrayInputStream;
import java.io.StringWriter;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Locale;
import java.util.TreeSet;

public class Template extends AbstractImageSupport {
    static private final Logger logger = DellASM.getLogger(Template.class);

    private DellASM provider;

    static public final String ENUMERATE_ARCHIVE = "enumerateArchive";
    static public final String DELETE_ARCHIVE = "deleteArchive";

    Template(@Nonnull DellASM cloud) {
        super(cloud);
        provider = cloud;
    }

    @Override
    public @Nullable MachineImage getImage(@Nonnull String providerImageId) throws CloudException, InternalException {
        ArrayList<MachineImage> images = (ArrayList<MachineImage>)searchMachineImages(false, null, providerImageId);
        if(images == null)throw new CloudException("An error occurred retrieving the image");
        //else if(image.size() > 1)throw new CloudException("Multiple images were retrieved for a single ID");//TODO: Put this back if I can get single results from API
        else{
            //return image.get(0);
            for(MachineImage image : images){
                if(image.getProviderMachineImageId().equals(providerImageId)){
                    return image;
                }
            }
        }
        throw new InternalException("Requested imageId could not be found");
    }

    @Override
    public @Nonnull String getProviderTermForImage(@Nonnull Locale locale, @Nonnull ImageClass imageClass) {
        return "Template";
    }

    @Override
    public boolean isSubscribed() throws CloudException, InternalException {
        return true;
    }

    @Override
    public @Nonnull Iterable<MachineImage> listImages(@Nullable ImageFilterOptions options) throws CloudException, InternalException {
        return searchImages(false, options);
    }

    @Override
    public @Nonnull Iterable<MachineImage> searchPublicImages(final @Nonnull ImageFilterOptions options) throws CloudException, InternalException {
        return searchImages(true, options);
    }

    private Iterable<MachineImage> searchImages(final boolean isPublic, final @Nonnull ImageFilterOptions options) throws CloudException, InternalException{
        final ImageFilterOptions opts;

        if( options == null ) {
            opts = ImageFilterOptions.getInstance();
        }
        else {
            opts = options;
        }
        provider.hold();
        PopulatorThread<MachineImage> populator = new PopulatorThread<MachineImage>(new JiteratorPopulator<MachineImage>() {
            @Override
            public void populate(@Nonnull Jiterator<MachineImage> iterator) throws Exception {
                APITrace.begin(getProvider(), "Image.listImages");
                try {
                    try {
                        for( MachineImage img : searchMachineImages(isPublic, opts, null) ) {
                            iterator.push(img);
                        }
                    }
                    finally {
                        provider.release();
                    }
                }
                finally {
                    APITrace.end();
                }
            }
        });

        populator.populate();
        return populator.getResult();
    }

    private Iterable<MachineImage> searchMachineImages(boolean isPublic, @Nullable ImageFilterOptions options, @Nullable String withProviderId) throws CloudException, InternalException{
        APITrace.begin(getProvider(), "Image.executeImageSearch");
        try{
            APIHandler handler = new APIHandler(provider);
            VelocityContext vc;
            org.apache.velocity.Template template;
            StringWriter sw = new StringWriter();

            String filterString = "";
            if(withProviderId != null){
                //Can't get this to work right now but it would be needed for single GETs
                //filterString = "<filtercriteria name=\"name\" operand=\"=\" value1=\"" + withProviderId +"\"/>";
            }
            else{
                //TODO: Add filters if possible including public/private
            }
            try{
                template = Velocity.getTemplate("templates/ASM-enumerateArchive.vm");
            }
            catch(ResourceNotFoundException ex){
                throw new InternalException("An error occurred while authenticating: " + ex.getMessage());
            }
            vc = new VelocityContext();
            vc.put("endpoint", handler.getEndpoint());
            vc.put("connectionId", handler.getConnectionId());
            vc.put("enumerateArchive", ENUMERATE_ARCHIVE.toLowerCase());
            vc.put("enumerateArchiveDtd", ENUMERATE_ARCHIVE + "Request.dtd");
            vc.put("extraFilters", filterString);

            template.merge(vc, sw);
            APIResponse response = handler.post(ENUMERATE_ARCHIVE, sw.toString());
            Document doc = response.getXML();
            if(doc == null){
                throw new ASMException(CloudErrorType.COMMUNICATION, response.getCode(), "NoImages", "No templates in archive response");
            }

            ArrayList<MachineImage> templates = new ArrayList<MachineImage>();
            NodeList archives = doc.getElementsByTagName("archive");
            for(int i=0;i<archives.getLength();i++){
                MachineImage img = toImage(archives.item(i));
                if(img != null){
                    templates.add(img);
                }
            }
            return templates;
        }
        finally {
            APITrace.end();
        }
    }

    @Override
    public void remove(@Nonnull String providerImageId, boolean checkState) throws CloudException, InternalException {
        APITrace.begin(getProvider(), "Image.remove");
        try{
            if (checkState){
                long timeout = System.currentTimeMillis() + (CalendarWrapper.MINUTE * 30L);
                while (timeout > System.currentTimeMillis()){
                    try{
                        MachineImage img = getMachineImage( providerImageId );
                        if (img == null || MachineImageState.DELETED.equals(img.getCurrentState())){
                            return;
                        }
                        if (MachineImageState.ACTIVE.equals( img.getCurrentState())){
                            break;
                        }
                    }
                    catch(Throwable ignore){}
                    try {
                        Thread.sleep( 15000L );
                    }
                    catch ( InterruptedException ignore ) {
                    }
                }
            }

            APIHandler handler = new APIHandler(provider);
            VelocityContext vc;
            org.apache.velocity.Template template;
            StringWriter sw = new StringWriter();

            try{
                template = Velocity.getTemplate("templates/ASM-deleteArchive.vm");
            }
            catch(ResourceNotFoundException ex){
                throw new InternalException("An error occurred while authenticating: " + ex.getMessage());
            }
            vc = new VelocityContext();
            vc.put("endpoint", handler.getEndpoint());
            vc.put("connectionId", handler.getConnectionId());
            vc.put("deleteArchive", DELETE_ARCHIVE.toLowerCase());
            vc.put("deleteArchiveDtd", DELETE_ARCHIVE + "Request.dtd");

            template.merge(vc, sw);
            APIResponse response = handler.post(DELETE_ARCHIVE, sw.toString());
        }
        finally {
            APITrace.end();
        }
    }

    private MachineImage toImage(Node node)throws CloudException, InternalException{
        if( node == null ) {
            return null;
        }
        ProviderContext ctx = provider.getContext();

        if( ctx == null ) {
            throw new CloudException("No context was set for this request");
        }
        String regionId = ctx.getRegionId();

        if( regionId == null ) {
            throw new CloudException("No region was set for this request");
        }
        String ownerId = "";
        String imageId = "";
        String name = "";
        String description = "";
        Architecture architecture = null;
        Platform platform;

        for(int i=0;i<node.getChildNodes().getLength();i++){
            Node item = node.getChildNodes().item(i);
            if(item.getNodeType() != Node.TEXT_NODE && item.getNodeName().equalsIgnoreCase("content")){
                try{
                    String contentString = item.getFirstChild().getNodeValue().trim();
                    Document content = XMLParser.parse(new ByteArrayInputStream(contentString.getBytes()));
                    //if(content.getElementsByTagName("device").getLength() > 1)return null;//Only interested in atomic topology
                    NodeList devices = content.getElementsByTagName("device");
                    for(int j=0;j<devices.getLength();j++){
                        Node device = devices.item(j);
                        if(!device.getAttributes().getNamedItem("model").getNodeValue().trim().equalsIgnoreCase("virtualmachine"))return null;
                    }
                    break;
                }
                catch(Exception ex){
                    throw new InternalException("Error parsing topology device type: " + ex.getMessage());
                }
            }
        }
        ownerId = provider.getContext().getAccountNumber();
        if(node.getAttributes().getNamedItem("namespace") != null){
            imageId = node.getAttributes().getNamedItem("namespace").getNodeValue().trim();
        }
        if(node.getAttributes().getNamedItem("name") != null){
            name = node.getAttributes().getNamedItem("name").getNodeValue().trim();
            if(name.contains("x64") || name.contains("64-bit") || name.contains("64 bit")){
                architecture = Architecture.I64;
            }
            else if(name.contains("x32") ) {
                architecture = Architecture.I32;
            }
        }
        if(node.getAttributes().getNamedItem("description") != null){
            description = node.getAttributes().getNamedItem("description").getNodeValue().trim();
        }

        if(architecture == null)architecture = Architecture.I64;
        platform = Platform.guess(name);
        return MachineImage.getImageInstance(ownerId, regionId, imageId, ImageClass.MACHINE, MachineImageState.ACTIVE, name, description, architecture, platform);
    }
}
