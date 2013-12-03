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

import org.apache.commons.lang.StringEscapeUtils;
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
import org.dasein.cloud.network.IPVersion;
import org.dasein.cloud.network.RawAddress;
import org.dasein.cloud.util.APITrace;
import org.dasein.util.uom.storage.Gigabyte;
import org.dasein.util.uom.storage.Megabyte;
import org.dasein.util.uom.storage.Storage;
import org.w3c.dom.*;

import javax.annotation.Nonnegative;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.ByteArrayInputStream;
import java.io.PrintWriter;
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
    static public final String CONFIRM_RESPONSE = "confirmResponse";
    static public final String JOIN_LAB_SESSION = "joinLabsession";
    static public final String LEAVE_LAB_SESSION = "leaveLabsession";
    static public final String READ_TOPOLOGY = "readTopology";
    static public final String POWER_ON = "powerOn";
    static public final String POWER_OFF = "powerOff";

    static public final String FOUND_RESERVATION_OPTIONS = "res.scheduler.400";

    public VirtualVM(@Nonnull DellASM provider) {
        super(provider);
        this.provider = provider;
    }

    @Override
    public VirtualMachine alterVirtualMachine(@Nonnull String vmId, @Nonnull VMScalingOptions options) throws InternalException, CloudException {
        //TODO: Check modifyReservation call for this
        return null;
    }

    @Nonnull
    @Override
    public VirtualMachine clone(@Nonnull String vmId, @Nonnull String intoDcId, @Nonnull String name, @Nonnull String description, boolean powerOn, @Nullable String... firewallIds) throws InternalException, CloudException {
        //TODO: Can be done in console but maybe not API - need to be sure
        throw new OperationNotSupportedException("Cloning VMs is not supported by ASM");
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
        //TODO: Ditch this - do readtopology call instead then toVM and throw away non matching device IDs.

        VMFilterOptions options = VMFilterOptions.getInstance(vmId);
        ArrayList<VirtualMachine> vms = (ArrayList<VirtualMachine>)listVirtualMachines(options);
        for(VirtualMachine vm : vms){
            if(vm.getProviderVirtualMachineId().equals(vmId)) return vm;
        }
        throw new InternalException("Could not find VM with ID: " + vmId);
    }

    @Nonnull
    @Override
    public VmStatistics getVMStatistics(@Nonnull String vmId, @Nonnegative long from, @Nonnegative long to) throws InternalException, CloudException {
        return null;
    }

    @Nonnull
    @Override
    public Iterable<VmStatistics> getVMStatisticsForPeriod(@Nonnull String vmId, @Nonnegative long from, @Nonnegative long to) throws InternalException, CloudException {
        return null;
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
            String sessionDuration = "<permanent start=\"" + sdf.format(new Date()) + "\" />";//TODO: if start/end times provided add here
            vc.put("sessionDuration", sessionDuration);

            template.merge(vc, sw);
            APIResponse response = handler.post(MAKE_RESERVATION, sw.toString());
            Document doc = response.getXML();
            if(doc == null){
                throw new ASMException(CloudErrorType.COMMUNICATION, response.getCode(), "NoResponse", "No response from make reservation request");
            }

            Node responseSet = doc.getElementsByTagName("responseset").item(0);
            String responseCode = responseSet.getAttributes().getNamedItem("code").getNodeValue().trim();
            if(responseCode.equalsIgnoreCase(FOUND_RESERVATION_OPTIONS)){
                String responseId = null;

                NodeList hosts = responseSet.getChildNodes();
                for(int i=0;i<hosts.getLength();i++){
                    Node host = hosts.item(i);
                    if(host.getNodeType() == Node.TEXT_NODE)continue;
                    if(host.getNodeName().equalsIgnoreCase("response")){
                        responseId = host.getAttributes().getNamedItem("responseid").getNodeValue();
                        //No way to determine host health so just use first one found
                        break;
                    }
                }

                if(responseId != null){
                    sw = new StringWriter();
                    try{
                        template = Velocity.getTemplate("templates/ASM-confirmResponse.vm");
                    }
                    catch(ResourceNotFoundException ex){
                        throw new InternalException("An error occurred while launching a VM: " + ex.getMessage());
                    }

                    vc = new VelocityContext();
                    vc.put("endpoint", handler.getEndpoint());
                    vc.put("connectionId", handler.getConnectionId());
                    vc.put("confirmResponse", CONFIRM_RESPONSE.toLowerCase());
                    vc.put("confirmResponseDtd", CONFIRM_RESPONSE + "Request.dtd");
                    vc.put("responseId", responseId);
                    vc.put("name", name);

                    template.merge(vc, sw);
                    APIResponse confirmResponse = handler.post(CONFIRM_RESPONSE, sw.toString());
                    Document confirmDoc = confirmResponse.getXML();
                    if(confirmDoc == null){
                        throw new ASMException(CloudErrorType.COMMUNICATION, response.getCode(), "NoVM", "An error occurred while creating a session for the VM");
                    }
                    String reservationId = confirmDoc.getElementsByTagName("confirmresponse").item(0).getAttributes().getNamedItem("reservationid").getNodeValue().trim();

                    try{
                        template = Velocity.getTemplate("templates/ASM-readTopology.vm");
                    }
                    catch(ResourceNotFoundException ex){
                        throw new InternalException("An error occurred reading the current topology: " + ex.getMessage());
                    }

                    vc = new VelocityContext();
                    sw = new StringWriter();
                    vc.put("endpoint", handler.getEndpoint());
                    vc.put("connectionId", handler.getConnectionId());
                    vc.put("readTopologyDtd", READ_TOPOLOGY + "Request.dtd");
                    vc.put("reservationId", reservationId);

                    template.merge(vc, sw);
                    APIResponse topologyResponse = handler.post(READ_TOPOLOGY, sw.toString());
                    Document topologyDoc = topologyResponse.getXML();
                    if(doc == null){
                        logger.error("No content in topology");
                        throw new ASMException(CloudErrorType.COMMUNICATION, response.getCode(), "NoContent", "No content in topology");
                    }

                    NodeList xmltext = topologyDoc.getElementsByTagName("xmltext");
                    try{
                        Document topologyContent = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(new ByteArrayInputStream(((CharacterData)xmltext.item(0).getFirstChild()).getData().trim().getBytes()));
                        Collection<VirtualMachine> machines = toVirtualMachine(reservationId, topologyContent);
                        if(machines != null){
                            for(VirtualMachine vm : machines){
                                if(vm != null){
                                    vm.setCreationTimestamp(new Date().getTime());
                                    return vm;//We can safely return on the first VM in the collection
                                }
                            }
                        }
                    }
                    catch(Exception ex){
                        ex.printStackTrace();
                        logger.error(ex.getMessage());
                        throw new InternalException(ex.getMessage());
                    }
                    return null;
                }
                else{
                    throw new InternalException("An error occurred establishing a session for launch");
                }
            }
            else{
                String error = responseSet.getAttributes().getNamedItem("message").getNodeValue().trim();
                throw new ASMException(CloudErrorType.GENERAL, -1, responseCode, error);
            }
        }
        finally{
            APITrace.end();
        }
    }

    @Nonnull
    @Override
    public Iterable<String> listFirewalls(@Nonnull String vmId) throws InternalException, CloudException {
        return null;
    }

    @Override
    public Iterable<VirtualMachineProduct> listProducts(Architecture architecture) throws InternalException, CloudException {
        //TODO: This is a mock up of potential product sizes - there doesn't seem to be a way to extract this data programatically
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
        ArrayList<VirtualMachine> vms = (ArrayList<VirtualMachine>)listVirtualMachines();
        if(vms != null && vms.size() > 0){
            ArrayList<ResourceStatus> statuses = new ArrayList<ResourceStatus>();
            for(VirtualMachine vm : vms){
                statuses.add(new ResourceStatus(vm.getProviderVirtualMachineId(), vm.getCurrentState()));
            }
            return statuses;
        }
        else return Collections.emptyList();
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
        //TODO: Add filters if possible
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
            vc.put("extraFilters", extraFilters);//TODO: Need to filter out anything not in AWAITING_SECURITY_SETTING_UP, SNAPSHOT, RUNNING, SETTING_UP or CONFIRMED - can't get this working

            template.merge(vc, sw);
            APIResponse response = handler.post(ENUMERATE_RESERVATIONS, sw.toString());
            Document doc = response.getXML();
            if(doc == null){
                throw new ASMException(CloudErrorType.COMMUNICATION, response.getCode(), "NoVMs", "No reservations in lab response");
            }

            ArrayList<VirtualMachine> vms = new ArrayList<VirtualMachine>();
            //ImageFilterOptions imgOptions = null;
            //Collection<MachineImage> images = (Collection<MachineImage>)provider.getComputeServices().getImageSupport().listImages(imgOptions);
            NodeList reservations = doc.getElementsByTagName("reservation");
            if(reservations != null){
                for(int i=0;i<reservations.getLength();i++){
                    Node node = reservations.item(i);
                    String reservationId = node.getAttributes().getNamedItem("reservationid").getNodeValue().trim();

                    try{
                        template = Velocity.getTemplate("templates/ASM-readTopology.vm");
                    }
                    catch(ResourceNotFoundException ex){
                        throw new InternalException("An error occurred reading the current topology: " + ex.getMessage());
                    }

                    vc = new VelocityContext();
                    sw = new StringWriter();
                    vc.put("endpoint", handler.getEndpoint());
                    vc.put("connectionId", handler.getConnectionId());
                    vc.put("readTopologyDtd", READ_TOPOLOGY + "Request.dtd");
                    vc.put("reservationId", reservationId);

                    template.merge(vc, sw);
                    APIResponse topologyResponse = handler.post(READ_TOPOLOGY, sw.toString());
                    Document topologyDoc = topologyResponse.getXML();
                    if(doc == null){
                        logger.error("No content in topology");
                        throw new ASMException(CloudErrorType.COMMUNICATION, response.getCode(), "NoContent", "No content in topology");
                    }

                    NodeList xmltext = topologyDoc.getElementsByTagName("xmltext");
                    try{
                        Document topologyContent = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(new ByteArrayInputStream(((CharacterData)xmltext.item(0).getFirstChild()).getData().trim().getBytes()));
                        Collection<VirtualMachine> machines = toVirtualMachine(reservationId, topologyContent);
                        if(machines != null){
                            for(VirtualMachine vm : machines){
                                if(vm != null){
                                    try{
                                        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssz");
                                        vm.setCreationTimestamp(sdf.parse(node.getAttributes().getNamedItem("createdtime").getNodeValue().trim()).getTime());
                                    }
                                    catch(ParseException ex){
                                        logger.error(ex.getMessage());
                                    }
                                    vms.add(vm);
                                }
                            }
                        }
                    }
                    catch(Exception ex){
                        ex.printStackTrace();
                        logger.error(ex.getMessage());
                        throw new InternalException(ex.getMessage());
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
    public void start(@Nonnull String vmId) throws InternalException, CloudException {
        APITrace.begin(provider, "startVM");
        try {
            APIHandler handler = new APIHandler(provider);
            VelocityContext vc;
            org.apache.velocity.Template template;
            StringWriter sw = new StringWriter();

            try{
                template = Velocity.getTemplate("templates/ASM-joinLabSession.vm");
            }
            catch(ResourceNotFoundException ex){
                throw new InternalException("An error occurred joining the session: " + ex.getMessage());
            }

            String reservationId = vmId.split(":")[0];
            String deviceId = vmId.split(":")[1];

            vc = new VelocityContext();
            vc.put("endpoint", handler.getEndpoint());
            vc.put("connectionId", handler.getConnectionId());
            vc.put("joinLabSession", JOIN_LAB_SESSION.toLowerCase());
            vc.put("joinLabSessionDtd", JOIN_LAB_SESSION + "Request.dtd");
            vc.put("reservationId", reservationId);

            template.merge(vc, sw);
            APIResponse response = handler.post(JOIN_LAB_SESSION, sw.toString());
            Document doc = response.getXML();
            if(doc == null){
                throw new ASMException(CloudErrorType.COMMUNICATION, response.getCode(), "NoSession", "No sessions in lab response");
            }


            NodeList ls = doc.getElementsByTagName("labsession");
            if(ls != null){
                String sessionId = ls.item(0).getAttributes().getNamedItem("sessionid").getNodeValue().trim();

                try{
                    template = Velocity.getTemplate("templates/ASM-powerOn.vm");
                }
                catch(ResourceNotFoundException ex){
                    throw new InternalException("An error occurred powering on the VM: " + ex.getMessage());
                }

                vc = new VelocityContext();
                vc.put("endpoint", handler.getEndpoint());
                vc.put("connectionId", handler.getConnectionId());
                vc.put("powerOn", POWER_ON.toLowerCase());
                vc.put("powerOnDtd", POWER_ON + "Request.dtd");
                vc.put("sessionId", sessionId);
                vc.put("deviceId", deviceId);

                sw = new StringWriter();
                template.merge(vc, sw);
                APIResponse powerResponse = handler.post(POWER_ON, sw.toString());
                Document powerDoc = powerResponse.getXML();
                if(powerDoc == null){
                    throw new ASMException(CloudErrorType.COMMUNICATION, powerResponse.getCode(), "NoResponse", "No response from ASM when powering on VM");
                }

                //TODO: Check for errors

                try{
                    template = Velocity.getTemplate("templates/ASM-leaveLabSession.vm");
                }
                catch(ResourceNotFoundException ex){
                    throw new InternalException("An error occurred ending the session: " + ex.getMessage());
                }

                vc = new VelocityContext();
                vc.put("endpoint", handler.getEndpoint());
                vc.put("connectionId", handler.getConnectionId());
                vc.put("leaveLabSession", LEAVE_LAB_SESSION.toLowerCase());
                vc.put("leaveLabSessionDtd", LEAVE_LAB_SESSION + "Request.dtd");
                vc.put("sessionId", sessionId);

                sw = new StringWriter();
                template.merge(vc, sw);
                handler.post(LEAVE_LAB_SESSION, sw.toString());
            }
            else{
                Node error = doc.getElementsByTagName("error").item(0);
                String errorCode = error.getAttributes().getNamedItem("code").getNodeValue().trim();
                String errorMsg = error.getAttributes().getNamedItem("message").getNodeValue().trim();
                throw new ASMException(CloudErrorType.GENERAL, -1, errorCode, errorMsg);
            }
        }
        finally {
            APITrace.end();
        }
    }

    @Override
    public void stop(@Nonnull String vmId, boolean force) throws InternalException, CloudException {
        APITrace.begin(provider, "stopVM");
        try {
            APIHandler handler = new APIHandler(provider);
            VelocityContext vc;
            org.apache.velocity.Template template;
            StringWriter sw = new StringWriter();

            try{
                template = Velocity.getTemplate("templates/ASM-joinLabSession.vm");
            }
            catch(ResourceNotFoundException ex){
                throw new InternalException("An error occurred joining the session: " + ex.getMessage());
            }

            String reservationId = vmId.split(":")[0];
            String deviceId = vmId.split(":")[1];

            vc = new VelocityContext();
            vc.put("endpoint", handler.getEndpoint());
            vc.put("connectionId", handler.getConnectionId());
            vc.put("joinLabSession", JOIN_LAB_SESSION.toLowerCase());
            vc.put("joinLabSessionDtd", JOIN_LAB_SESSION + "Request.dtd");
            vc.put("reservationId", reservationId);

            template.merge(vc, sw);
            APIResponse response = handler.post(JOIN_LAB_SESSION, sw.toString());
            Document doc = response.getXML();
            if(doc == null){
                throw new ASMException(CloudErrorType.COMMUNICATION, response.getCode(), "NoSession", "No sessions in lab response");
            }

            NodeList ls = doc.getElementsByTagName("joinlabsession");
            if(ls != null){
                String sessionId = ls.item(0).getAttributes().getNamedItem("sessionid").getNodeValue().trim();

                try{
                    template = Velocity.getTemplate("templates/ASM-powerOff.vm");
                }
                catch(ResourceNotFoundException ex){
                    throw new InternalException("An error occurred powering off the VM: " + ex.getMessage());
                }

                vc = new VelocityContext();
                vc.put("endpoint", handler.getEndpoint());
                vc.put("connectionId", handler.getConnectionId());
                vc.put("powerOff", POWER_OFF.toLowerCase());
                vc.put("powerOffDtd", POWER_OFF + "Request.dtd");
                vc.put("sessionId", sessionId);
                vc.put("deviceId", deviceId);

                sw = new StringWriter();
                template.merge(vc, sw);
                APIResponse powerResponse = handler.post(POWER_OFF, sw.toString());
                Document powerDoc = powerResponse.getXML();
                if(powerDoc == null){
                    throw new ASMException(CloudErrorType.COMMUNICATION, powerResponse.getCode(), "NoResponse", "No response from ASM when powering off VM");
                }

                //TODO: Check for errors

                try{
                    template = Velocity.getTemplate("templates/ASM-leaveLabSession.vm");
                }
                catch(ResourceNotFoundException ex){
                    throw new InternalException("An error occurred ending the session: " + ex.getMessage());
                }

                vc = new VelocityContext();
                vc.put("endpoint", handler.getEndpoint());
                vc.put("connectionId", handler.getConnectionId());
                vc.put("leaveLabSession", LEAVE_LAB_SESSION.toLowerCase());
                vc.put("leaveLabSessionDtd", LEAVE_LAB_SESSION + "Request.dtd");
                vc.put("sessionId", sessionId);

                sw = new StringWriter();
                template.merge(vc, sw);
                handler.post(LEAVE_LAB_SESSION, sw.toString());
            }
            else{
                Node error = doc.getElementsByTagName("error").item(0);
                String errorCode = error.getAttributes().getNamedItem("code").getNodeValue().trim();
                String errorMsg = error.getAttributes().getNamedItem("message").getNodeValue().trim();
                throw new ASMException(CloudErrorType.GENERAL, -1, errorCode, errorMsg);
            }
        }
        finally {
            APITrace.end();
        }
    }

    @Override
    public void reboot(@Nonnull String vmId) throws CloudException, InternalException {
        //Supported in UI - not in API
        throw new OperationNotSupportedException("Reboot operation is not supported for " + getProvider().getCloudName());
    }

    @Override
    public void resume(@Nonnull String vmId) throws CloudException, InternalException {
        //Supported in UI - not in API
        throw new OperationNotSupportedException("Resume operation is not supported for " + getProvider().getCloudName());
    }

    @Override
    public void suspend(@Nonnull String vmId) throws CloudException, InternalException {
        //Supported in UI - not in API
        throw new OperationNotSupportedException("Suspend operation is not supported for " + getProvider().getCloudName());
    }

    @Override
    public boolean supportsPauseUnpause(@Nonnull VirtualMachine vm) {
        return false;
    }

    @Override
    public boolean supportsStartStop(@Nonnull VirtualMachine vm) {
        return true;
    }

    @Override
    public boolean supportsSuspendResume(@Nonnull VirtualMachine vm) {
        return false;//Supported in UI - not in API
    }

    @Override
    public void terminate(@Nonnull String vmId, @Nullable String explanation) throws InternalException, CloudException {

    }

    private Collection<VirtualMachine> toVirtualMachine(String reservationId, Document topologyContent) throws InternalException, CloudException{
        HashMap<String, VirtualMachine> vmMap = new HashMap<String, VirtualMachine>();
        NodeList topology = topologyContent.getElementsByTagName("topology").item(0).getChildNodes();
        for(int i=0;i<topology.getLength();i++){//Get the list of devices first
            Node current = topology.item(i);
            if(current.getNodeType() == Node.TEXT_NODE)continue;

            if(current.getNodeName().equalsIgnoreCase("device")){
                if(current.getAttributes().getNamedItem("model").getNodeValue().equalsIgnoreCase("VirtualMachine")){
                    String cpuCount = "";
                    String ramInMb = "";

                    VirtualMachine vm = new VirtualMachine();
                    String deviceKey = current.getAttributes().getNamedItem("key").getNodeValue().trim();

                    vm.setName(current.getAttributes().getNamedItem("name").getNodeValue().trim());
                    vm.setDescription(current.getAttributes().getNamedItem("description").getNodeValue().trim());
                    vm.setProviderVirtualMachineId(reservationId + ":" + deviceKey);
                    vm.setProviderDataCenterId(provider.getContext().getRegionId());
                    vm.setProviderRegionId(provider.getContext().getRegionId());
                    vm.setTag("devicekey", deviceKey);

                    for(int j=0;j<current.getChildNodes().getLength();j++){
                        Node vmProperties = current.getChildNodes().item(j);
                        if(vmProperties.getNodeType() == Node.TEXT_NODE)continue;

                        if(vmProperties.getNodeName().equalsIgnoreCase("enforcedproperties")){
                            for(int k=0;k<vmProperties.getChildNodes().getLength();k++){
                                Node property = vmProperties.getChildNodes().item(k);
                                if(property.getNodeType() == Node.TEXT_NODE)continue;

                                if(property.getAttributes().getNamedItem("name").getNodeValue().trim().equalsIgnoreCase("CPU")){
                                    cpuCount = property.getAttributes().getNamedItem("value").getNodeValue().trim();
                                }
                                else if(property.getAttributes().getNamedItem("name").getNodeValue().trim().equalsIgnoreCase("RAM")){
                                    ramInMb = property.getAttributes().getNamedItem("value").getNodeValue().trim();
                                }
                            }
                        }
                    }
                    vm.setProductId(cpuCount + ":" + ramInMb);
                    vmMap.put(deviceKey, vm);
                }
            }
        }

        if(vmMap.size() > 0){//Check to see if there are any devices in the list then get the details
            for(int i=0;i<topology.getLength();i++){
                Node current = topology.item(i);
                if(current.getNodeType() == Node.TEXT_NODE)continue;
                else if(current.getNodeName().equalsIgnoreCase("attribute")){
                    String attributeName = current.getAttributes().getNamedItem("name").getNodeValue().trim();
                    String attributeReference = current.getAttributes().getNamedItem("refs").getNodeValue().trim();

                    if(attributeName.equalsIgnoreCase("power")){
                        for(int j=0;j<current.getChildNodes().getLength();j++){
                            Node attributeNode = current.getChildNodes().item(j);
                            if(attributeNode.getNodeType() == Node.TEXT_NODE)continue;

                            if(attributeNode.getNodeName().equalsIgnoreCase("value") && attributeNode.getFirstChild() != null){
                                String power = attributeNode.getFirstChild().getNodeValue().trim();
                                if(power.equalsIgnoreCase("on")){
                                    vmMap.get(attributeReference).setCurrentState(VmState.RUNNING);
                                }
                                else if(power.equalsIgnoreCase("off")){
                                    vmMap.get(attributeReference).setCurrentState(VmState.STOPPED);
                                }
                            }
                        }
                    }
                    else if(attributeName.equalsIgnoreCase("IPAddress")){
                        for(int j=0;j<current.getChildNodes().getLength();j++){
                            Node attributeNode = current.getChildNodes().item(j);
                            if(attributeNode.getNodeType() == Node.TEXT_NODE)continue;

                            if(attributeNode.getNodeName().equalsIgnoreCase("value") && attributeNode.getFirstChild() != null){

                                String ip = attributeNode.getFirstChild().getNodeValue().trim();
                                RawAddress ipAddress = new RawAddress(ip, IPVersion.IPV4);
                                vmMap.get(attributeReference).setPrivateAddresses(ipAddress);
                            }
                        }
                    }
                    else if(attributeName.equalsIgnoreCase("GuestType")){
                        for(int j=0;j<current.getChildNodes().getLength();j++){
                            Node attributeNode = current.getChildNodes().item(j);
                            if(attributeNode.getNodeType() == Node.TEXT_NODE)continue;

                            if(attributeNode.getNodeName().equalsIgnoreCase("value") && attributeNode.getFirstChild() != null){
                                vmMap.get(attributeReference).setPlatform(Platform.guess(attributeNode.getFirstChild().getNodeValue().trim()));
                            }
                        }
                    }
                }
            }
        }
        return vmMap.values();
    }
}
