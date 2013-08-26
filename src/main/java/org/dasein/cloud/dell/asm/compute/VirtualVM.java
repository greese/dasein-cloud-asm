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

import org.dasein.cloud.CloudException;
import org.dasein.cloud.InternalException;
import org.dasein.cloud.Requirement;
import org.dasein.cloud.ResourceStatus;
import org.dasein.cloud.compute.AbstractVMSupport;
import org.dasein.cloud.compute.Architecture;
import org.dasein.cloud.compute.ImageClass;
import org.dasein.cloud.compute.VMFilterOptions;
import org.dasein.cloud.compute.VMLaunchOptions;
import org.dasein.cloud.compute.VMScalingOptions;
import org.dasein.cloud.compute.VirtualMachine;
import org.dasein.cloud.compute.VirtualMachineProduct;
import org.dasein.cloud.compute.VmState;
import org.dasein.cloud.dell.asm.DellASM;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Collections;
import java.util.Locale;

/**
 * Implements the Dasein Cloud interface for interacting with virtual machines for virtual machines and physical servers
 * in a Dell ASM converged infrastructure.
 * <p>Created by George Reese: 6/20/13 4:07 PM</p>
 * @author George Reese
 * @version 2013.07
 * @since 2013.07
 */
public class VirtualVM extends AbstractVMSupport<DellASM> {
    public VirtualVM(@Nonnull DellASM provider) { super(provider); }

    @Override
    public VirtualMachine alterVirtualMachine(@Nonnull String vmId, @Nonnull VMScalingOptions options) throws InternalException, CloudException {
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    public int getCostFactor(@Nonnull VmState state) throws InternalException, CloudException {
        return 100;
    }

    @Override
    public int getMaximumVirtualMachineCount() throws CloudException, InternalException {
        return -2;
    }

    @Override
    public VirtualMachineProduct getProduct(@Nonnull String productId) throws InternalException, CloudException {
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    public @Nonnull String getProviderTermForServer(@Nonnull Locale locale) {
        return "VM";
    }

    @Override
    public VirtualMachine getVirtualMachine(@Nonnull String vmId) throws InternalException, CloudException {
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    public @Nonnull Requirement identifyImageRequirement(@Nonnull ImageClass cls) throws CloudException, InternalException {
        return Requirement.NONE;
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
        return false;  //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    public boolean isUserDataSupported() throws CloudException, InternalException {
        return false;
    }

    @Override
    public @Nonnull VirtualMachine launch(@Nonnull VMLaunchOptions withLaunchOptions) throws CloudException, InternalException {
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    public @Nonnull Iterable<VirtualMachineProduct> listProducts(@Nonnull Architecture architecture) throws InternalException, CloudException {
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    public Iterable<Architecture> listSupportedArchitectures() throws InternalException, CloudException {
        return Collections.singletonList(Architecture.I64);
    }

    @Override
    public @Nonnull Iterable<ResourceStatus> listVirtualMachineStatus() throws InternalException, CloudException {
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    public @Nonnull Iterable<VirtualMachine> listVirtualMachines() throws InternalException, CloudException {
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    public @Nonnull Iterable<VirtualMachine> listVirtualMachines(@Nullable VMFilterOptions options) throws InternalException, CloudException {
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    public void pause(@Nonnull String vmId) throws InternalException, CloudException {
        //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    public void reboot(@Nonnull String vmId) throws CloudException, InternalException {
        //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    public void resume(@Nonnull String vmId) throws CloudException, InternalException {
        //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    public void start(@Nonnull String vmId) throws InternalException, CloudException {
        //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    public void stop(@Nonnull String vmId, boolean force) throws InternalException, CloudException {
        //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    public boolean supportsPauseUnpause(@Nonnull VirtualMachine vm) {
        return false;  //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    public boolean supportsStartStop(@Nonnull VirtualMachine vm) {
        return false;  //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    public boolean supportsSuspendResume(@Nonnull VirtualMachine vm) {
        return false;  //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    public void suspend(@Nonnull String vmId) throws CloudException, InternalException {
        //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    public void terminate(@Nonnull String vmId) throws InternalException, CloudException {
        //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    public void terminate(@Nonnull String vmId, @Nullable String explanation) throws InternalException, CloudException {
        //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    public void unpause(@Nonnull String vmId) throws CloudException, InternalException {
        //To change body of implemented methods use File | Settings | File Templates.
    }
}
