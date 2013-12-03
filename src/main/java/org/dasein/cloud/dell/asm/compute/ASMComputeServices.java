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

import org.dasein.cloud.compute.AbstractComputeServices;
import org.dasein.cloud.dell.asm.DellASM;

import javax.annotation.Nonnull;

/**
 * Supports interaction with compute cloud services interfaces for the Dell ASM converged infrastructure.
 * <p>Created by George Reese: 5/28/13 6:24 PM</p>
 * @author George Reese
 * @version 2013.07
 * @since 2013.07
 */
public class ASMComputeServices extends AbstractComputeServices {
    private DellASM provider;

    public ASMComputeServices(DellASM provider) { this.provider = provider; }

    public @Nonnull VirtualVM getVirtualMachineSupport() {
        return new VirtualVM(provider);
    }

    public @Nonnull Template getImageSupport() {
        return new Template(provider);
    }
}
