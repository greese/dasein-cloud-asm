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

package org.dasein.cloud.dell.asm.compute;

import org.apache.log4j.Logger;
import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.Velocity;
import org.apache.velocity.exception.ResourceNotFoundException;
import org.dasein.cloud.*;
import org.dasein.cloud.compute.*;
import org.dasein.cloud.dell.asm.APIHandler;
import org.dasein.cloud.dell.asm.APIResponse;
import org.dasein.cloud.dell.asm.ASMException;
import org.dasein.cloud.dell.asm.DellASM;
import org.dasein.cloud.util.APITrace;
import org.dasein.util.uom.storage.Gigabyte;
import org.dasein.util.uom.storage.Megabyte;
import org.dasein.util.uom.storage.Storage;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.annotation.Nonnegative;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.StringWriter;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * Implements the Dasein Cloud interface for interacting with virtual machines for virtual machines and physical servers
 * in a Dell ASM converged infrastructure.
 * <p>Created by George Reese: 6/20/13 4:07 PM</p>
 * @author George Reese
 * @version 2013.07
 * @since 2013.07
 */
public class VirtualVM extends AbstractVMSupport<DellASM> {
    static private final Logger logger = DellASM.getLogger(VirtualVM.class);
    DellASM provider = null;

    static public final String ENUMERATE_LAB_SESSIONS = "enumerateLabSessions";
    static public final String ENUMERATE_RESERVATIONS = "enumerateReservations";
    static public final String MAKE_RESERVATION = "makeReservation";

    public VirtualVM(@Nonnull DellASM provider) {
        super(provider);
        this.provider = provider;
    }

    @Override
    public VirtualMachine alterVirtualMachine(@Nonnull String vmId, @Nonnull VMScalingOptions options) throws InternalException, CloudException {
        throw new OperationNotSupportedException("Cannot alter a running VM in ASM");
    }

    @Nonnull
    @Override
    public VirtualMachine clone(@Nonnull String vmId, @Nonnull String intoDcId, @Nonnull String name, @Nonnull String description, boolean powerOn, @Nullable String... firewallIds) throws InternalException, CloudException {
        return null;  //To change body of implemented methods use File | Settings | File Template.
    }

    @Override
    public VMScalingCapabilities describeVerticalScalingCapabilities() throws CloudException, InternalException {
        return VMScalingCapabilities.getInstance(false, false, Requirement.NONE, Requirement.NONE);
    }

    @Override
    public @Nullable VirtualMachineProduct getProduct(@Nonnull String productId) throws InternalException, CloudException {
        for( VirtualMachineProduct product : listProducts(Architecture.I64) ) {
            if( product.getProviderProductId().equals(productId) ) {
                return product;
            }
        }
        for( VirtualMachineProduct product : listProducts(Architecture.I32) ) {
            if( product.getProviderProductId().equals(productId) ) {
                return product;
            }
        }
        return null;
    }

    @Nonnull
    @Override
    public String getProviderTermForServer(@Nonnull Locale locale) {
        return "Virtual Machine";
    }

    @Override
    public VirtualMachine getVirtualMachine(@Nonnull String vmId) throws InternalException, CloudException {
        return null;  //To change body of implemented methods use File | Settings | File Template.
    }

    @Nonnull
    @Override
    public VmStatistics getVMStatistics(@Nonnull String vmId, @Nonnegative long from, @Nonnegative long to) throws InternalException, CloudException {
        return null;  //To change body of implemented methods use File | Settings | File Template.
    }

    @Nonnull
    @Override
    public Iterable<VmStatistics> getVMStatisticsForPeriod(@Nonnull String vmId, @Nonnegative long from, @Nonnegative long to) throws InternalException, CloudException {
        return null;  //To change body of implemented methods use File | Settings | File Template.
    }

    @Nonnull
    @Override
    public Requirement identifyImageRequirement(@Nonnull ImageClass cls) throws CloudException, InternalException {
        return Requirement.REQUIRED;
    }

    @Nonnull
    @Override
    public Requirement identifyPasswordRequirement(Platform platform) throws CloudException, InternalException {
        return Requirement.OPTIONAL;
    }

    @Nonnull
    @Override
    public Requirement identifyRootVolumeRequirement() throws CloudException, InternalException {
        return Requirement.NONE;
    }

    @Nonnull
    @Override
    public Requirement identifyShellKeyRequirement(Platform platform) throws CloudException, InternalException {
        return Requirement.OPTIONAL;
    }

    @Nonnull
    @Override
    public Requirement identifyStaticIPRequirement() throws CloudException, InternalException {
        return Requirement.OPTIONAL;
    }

    @Nonnull
    @Override
    public Requirement identifyVlanRequirement() throws CloudException, InternalException {
        return Requirement.OPTIONAL;
    }

    @Override
    public boolean isAPITerminationPreventable() throws CloudException, InternalException {
        return false;
    }

    @Override
    public boolean isBasicAnalyticsSupported() throws CloudException, InternalException {
        return false;
    }

    @Override
    public boolean isExtendedAnalyticsSupported() throws CloudException, InternalException {
        return false;
    }

    @Override
    public boolean isSubscribed() throws CloudException, InternalException {
        return true;
    }

    @Override
    public boolean isUserDataSupported() throws CloudException, InternalException {
        return true;
    }

    @Nonnull
    @Override
    public VirtualMachine launch(@Nonnull VMLaunchOptions withLaunchOptions) throws CloudException, InternalException {
        APITrace.begin(provider, "launchVM");
        try{
            String imageId = withLaunchOptions.getMachineImageId();
            String dataCenterId = withLaunchOptions.getDataCenterId();
            String name = withLaunchOptions.getHostName();
            String description = withLaunchOptions.getDescription();

            APIHandler handler = new APIHandler(provider);
            VelocityContext vc;
            org.apache.velocity.Template template;
            StringWriter sw = new StringWriter();

            try{
                template = Velocity.getTemplate("templates/ASM-makeReservation.vm");
            }
            catch(ResourceNotFoundException ex){
                throw new InternalException("An error occurred while launching a VM: " + ex.getMessage());
            }

            vc = new VelocityContext();
            vc.put("endpoint", handler.getEndpoint());
            vc.put("connectionId", handler.getConnectionId());
            vc.put("makeReservation", MAKE_RESERVATION.toLowerCase());
            vc.put("makeReservationDtd", MAKE_RESERVATION + "Request.dtd");
            vc.put("machineImageId", imageId);
            vc.put("hostName", name);
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssz");
            vc.put("startTime", sdf.format(new Date()));

            template.merge(vc, sw);
            APIResponse response = handler.post(MAKE_RESERVATION, sw.toString());
            Document doc = response.getXML();
            if(doc == null){
                throw new ASMException(CloudErrorType.COMMUNICATION, response.getCode(), "NoResponse", "No response from make reservation request");
            }
            try{
                TransformerFactory transfac = TransformerFactory.newInstance();
                Transformer trans = transfac.newTransformer();
                trans.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
                trans.setOutputProperty(OutputKeys.INDENT, "yes");

                StringWriter sw2 = new StringWriter();
                StreamResult result = new StreamResult(sw2);
                DOMSource source = new DOMSource(doc);
                trans.transform(source, result);
                String xmlString = sw2.toString();
                System.out.println(xmlString);
            }
            catch(Exception ex){
                ex.printStackTrace();
            }
        }
        finally{
            APITrace.end();
        }
        return null;
    }

    @Nonnull
    @Override
    public Iterable<String> listFirewalls(@Nonnull String vmId) throws InternalException, CloudException {
        return null;  //To change body of implemented methods use File | Settings | File Template.
    }

    @Override
    public Iterable<VirtualMachineProduct> listProducts(Architecture architecture) throws InternalException, CloudException {
        //TODO: This is a mock up of potential product sizes - there's no way to extract this data programatically
        ArrayList<VirtualMachineProduct> sizes = new ArrayList<VirtualMachineProduct>();

        for( Architecture a : listSupportedArchitectures() ) {
            if( a.equals(architecture) ) {
                if( a.equals(Architecture.I32) ) {
                    for( int cpu : new int[] { 1, 2 } ) {
                        for( int ram : new int[] { 512, 1024, 2048 } ) {
                            VirtualMachineProduct product = new VirtualMachineProduct();

                            product.setCpuCount(cpu);
                            product.setDescription("Custom product " + architecture + " - " + cpu + " CPU, " + ram + "GB RAM");
                            product.setName(cpu + " CPU/" + ram + " GB RAM");
                            product.setRootVolumeSize(new Storage<Gigabyte>(1, Storage.GIGABYTE));
                            product.setProviderProductId(cpu + ":" + ram);
                            product.setRamSize(new Storage<Megabyte>(ram, Storage.MEGABYTE));
                            sizes.add(product);
                        }
                    }
                }
                else {
                    for( int cpu : new int[] { 1, 2, 4, 8 } ) {
                        for( int ram : new int[] { 1024, 2048, 4096, 10240, 20480 } ) {
                            VirtualMachineProduct product = new VirtualMachineProduct();

                            product.setCpuCount(cpu);
                            product.setDescription("Custom product " + architecture + " - " + cpu + " CPU, " + ram + "GB RAM");
                            product.setName(cpu + " CPU/" + ram + " GB RAM");
                            product.setRootVolumeSize(new Storage<Gigabyte>(1, Storage.GIGABYTE));
                            product.setProviderProductId(cpu + ":" + ram);
                            product.setRamSize(new Storage<Megabyte>(ram, Storage.MEGABYTE));
                            sizes.add(product);
                        }
                    }
                }
                return sizes;
            }
        }
        return Collections.emptyList();
    }

    @Override
    public Iterable<Architecture> listSupportedArchitectures() throws InternalException, CloudException {
        Collection<Architecture> supported = new ArrayList<Architecture>();
        supported.add(Architecture.I32);
        supported.add(Architecture.I64);
        return supported;
    }

    @Nonnull
    @Override
    public Iterable<ResourceStatus> listVirtualMachineStatus() throws InternalException, CloudException {
        return null;  //To change body of implemented methods use File | Settings | File Template.
    }

    @Nonnull
    @Override
    public Iterable<VirtualMachine> listVirtualMachines() throws InternalException, CloudException {
        VMFilterOptions options = VMFilterOptions.getInstance();
        return listVirtualMachines(options);
    }

    @Nonnull
    @Override
    public Iterable<VirtualMachine> listVirtualMachines(@Nullable VMFilterOptions options) throws InternalException, CloudException {
        APITrace.begin(provider, "listVirtualMachines");
        try{
            ProviderContext ctx = provider.getContext();
            if( ctx == null ) {
                throw new CloudException("No context was established for this request");
            }

            APIHandler handler = new APIHandler(provider);
            VelocityContext vc;
            org.apache.velocity.Template template;
            StringWriter sw = new StringWriter();

            try{
                template = Velocity.getTemplate("templates/ASM-enumerateReservations.vm");
            }
            catch(ResourceNotFoundException ex){
                throw new InternalException("An error occurred while listing VMs: " + ex.getMessage());
            }
            String extraFilters = "";

            vc = new VelocityContext();
            vc.put("endpoint", handler.getEndpoint());
            vc.put("connectionId", handler.getConnectionId());
            vc.put("enumerateReservations", ENUMERATE_RESERVATIONS.toLowerCase());
            vc.put("enumerateReservationsDtd", ENUMERATE_RESERVATIONS + "Request.dtd");
            vc.put("extraFilters", extraFilters);

            template.merge(vc, sw);
            APIResponse response = handler.post(ENUMERATE_RESERVATIONS, sw.toString());
            Document doc = response.getXML();
            if(doc == null){
                throw new ASMException(CloudErrorType.COMMUNICATION, response.getCode(), "NoVMs", "No sessions in lab response");
            }

            //There doesn't appear to be any way to differentiate a session running a VLAN or a VM (ASM doesn't treat them differently)
            //So to pull out an individual VM I need to compare to the possible Machine Images
            ArrayList<VirtualMachine> vms = new ArrayList<VirtualMachine>();
            ImageFilterOptions imgOptions = null;
            Collection<MachineImage> images = (Collection<MachineImage>)provider.getComputeServices().getImageSupport().listImages(imgOptions);
            NodeList reservations = doc.getElementsByTagName("reservation");
            if(reservations != null){
                for(int i=0;i<reservations.getLength();i++){
                    Node node = reservations.item(i);
                    String machineImageId = node.getAttributes().getNamedItem("topologyid").getNodeValue();
                    if(machineImageId != null && !machineImageId.equals("")){
                        for(MachineImage image : images){//This makes me cry
                            if(image.getProviderMachineImageId().equals(machineImageId)){
                                VirtualMachine vm = toVirtualMachine(node, image);
                                if(vm != null){
                                    vms.add(vm);
                                }
                                break;
                            }
                        }
                    }
                }
            }
            return vms;
        }
        finally{
            APITrace.end();
        }
    }

    @Override
    public void pause(@Nonnull String vmId) throws InternalException, CloudException {
        //To change body of implemented methods use File | Settings | File Template.
    }

    @Override
    public void reboot(@Nonnull String vmId) throws CloudException, InternalException {
        //To change body of implemented methods use File | Settings | File Template.
    }

    @Override
    public void resume(@Nonnull String vmId) throws CloudException, InternalException {
        //To change body of implemented methods use File | Settings | File Template.
    }

    @Override
    public void start(@Nonnull String vmId) throws InternalException, CloudException {
        //To change body of implemented methods use File | Settings | File Template.
    }

    @Override
    public void stop(@Nonnull String vmId, boolean force) throws InternalException, CloudException {
        //To change body of implemented methods use File | Settings | File Template.
    }

    @Override
    public boolean supportsPauseUnpause(@Nonnull VirtualMachine vm) {
        return false;  //To change body of implemented methods use File | Settings | File Template.
    }

    @Override
    public boolean supportsStartStop(@Nonnull VirtualMachine vm) {
        return false;  //To change body of implemented methods use File | Settings | File Template.
    }

    @Override
    public boolean supportsSuspendResume(@Nonnull VirtualMachine vm) {
        return false;  //To change body of implemented methods use File | Settings | File Template.
    }

    @Override
    public void suspend(@Nonnull String vmId) throws CloudException, InternalException {
        //To change body of implemented methods use File | Settings | File Template.
    }

    @Override
    public void terminate(@Nonnull String vmId) throws InternalException, CloudException {
        //To change body of implemented methods use File | Settings | File Template.
    }

    @Override
    public void terminate(@Nonnull String vmId, @Nullable String explanation) throws InternalException, CloudException {
        //To change body of implemented methods use File | Settings | File Template.
    }

    @Override
    public void unpause(@Nonnull String vmId) throws CloudException, InternalException {
        //To change body of implemented methods use File | Settings | File Template.
    }

    private VirtualMachine toVirtualMachine(Node node, MachineImage image){
        String reservationStatus = node.getAttributes().getNamedItem("status").getNodeValue().trim();

        if(!reservationStatus.equalsIgnoreCase("running")){//TODO: Confirmed/setting_up maps well to starting
            VirtualMachine vm = new VirtualMachine();

            String providerId = node.getAttributes().getNamedItem("reservationid").getNodeValue().trim();
            vm.setProviderVirtualMachineId(providerId);

            String name = node.getAttributes().getNamedItem("reservationname").getNodeValue().trim();
            vm.setName(name);
            vm.setDescription(name);

            vm.setProviderDataCenterId(provider.getContext().getRegionId());
            vm.setProviderRegionId(provider.getContext().getRegionId());

            vm.setProviderMachineImageId(image.getProviderMachineImageId());
            vm.setCurrentState(VmState.RUNNING);

            vm.setArchitecture(image.getArchitecture());//TODO: These are currently guessed from the image name - should be a more accurate way
            vm.setPlatform(image.getPlatform());

            try{
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssz");
                vm.setCreationTimestamp(sdf.parse(node.getAttributes().getNamedItem("createdtime").getNodeValue().trim()).getTime());
            }
            catch(ParseException ex){
                logger.error(ex.getMessage());
            }

            //TODO: Should be able to get currentState, productId from API
            //TODO: Can get product values from image - but image can change after VM launch

            return vm;
        }
        else return null;
    }
}
