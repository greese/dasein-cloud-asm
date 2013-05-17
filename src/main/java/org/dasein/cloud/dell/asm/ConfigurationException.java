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

package org.dasein.cloud.dell.asm;

import org.dasein.cloud.CloudException;

import javax.annotation.Nonnull;

/**
 * An error in configuring Dell ASM's context in some manner.
 * <p>Created by George Reese: 05/17/2013 9:44 AM</p>
 * @author George Reese
 * @version 2013.4 initial version
 * @since 2013.4
 */
public class ConfigurationException extends CloudException {
    /**
     * Constructs a configuration error with the specified message.
     * @param message the message describing the nature of the configuration problem
     */
    public ConfigurationException(@Nonnull String message) {
        super(message);
    }

    /**
     * Constructs a configuration error based on a prior exception.
     * @param cause the prior exception resulting in the configuration problem
     */
    public ConfigurationException(@Nonnull Throwable cause) {
        super(cause);
    }
}
