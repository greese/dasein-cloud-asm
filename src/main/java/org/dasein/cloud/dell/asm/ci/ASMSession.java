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
import org.dasein.cloud.dell.asm.DellASM;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

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
        // TODO: implement me
        return null;
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
}
