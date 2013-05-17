/**
 * Copyright (C) 2012-2013 Dell, Inc.
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

package org.dasein.cloud.skeleton;

import org.dasein.cloud.CloudException;
import org.dasein.util.CalendarWrapper;
import org.json.JSONObject;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.InputStream;

/**
 * A response from a RESTful API.
 * @author George Reese
 * @version 2013.01 initial version
 * @since 2013.01
 */
public class APIResponse {
    private int code;
    private JSONObject json;
    private InputStream data;
    private Boolean complete;

    private CloudException error;
    private APIResponse next;

    public APIResponse() { }

    public int getCode() throws CloudException {
        synchronized( this ) {
            while( complete == null && error == null ) {
                try { wait(CalendarWrapper.MINUTE); }
                catch( InterruptedException ignore ) { }
            }
            if( error != null ) {
                throw error;
            }
            return code;
        }
    }

    public InputStream getData() throws CloudException {
        synchronized( this ) {
            while( complete == null && error == null ) {
                try { wait(CalendarWrapper.MINUTE); }
                catch( InterruptedException ignore ) { }
            }
            if( error != null ) {
                throw error;
            }
            return data;
        }
    }

    public JSONObject getJSON() throws CloudException {
        synchronized( this ) {
            while( complete == null && error ==null ) {
                try { wait(CalendarWrapper.MINUTE); }
                catch( InterruptedException ignore ) { }
            }
            if( error != null ) {
                throw error;
            }
            return json;
        }
    }

    public boolean isComplete() throws CloudException {
        synchronized( this ) {
            while( complete == null && error == null ) {
                try { wait(CalendarWrapper.MINUTE); }
                catch( InterruptedException ignore ) { }
            }
            if( error != null ) {
                throw error;
            }
            return complete;
        }
    }

    public @Nullable APIResponse next() throws CloudException {
        synchronized( this ) {
            while( complete == null && error == null ) {
                try { wait(CalendarWrapper.MINUTE); }
                catch( InterruptedException ignore ) { }
            }
            if( error != null ) {
                throw error;
            }
            if( complete ) {
                return null;
            }
            while( next == null && error == null ) {
                try { wait(CalendarWrapper.MINUTE); }
                catch( InterruptedException ignore ) { }
            }
            if( error != null ) {
                throw error;
            }
            return next;
        }
    }

    void receive() {
        synchronized( this ) {
            this.code = RESTMethod.NOT_FOUND;
            this.complete = true;
            notifyAll();
        }
    }

    void receive(CloudException error) {
        synchronized( this ) {
            this.code = error.getHttpCode();
            this.error = error;
            this.complete = true;
            notifyAll();
        }
    }

    void receive(int statusCode, @Nonnull InputStream data) {
        synchronized( this ) {
            this.code = statusCode;
            this.data = data;
            this.complete = true;
            notifyAll();
        }
    }

    void receive(int statusCode, @Nonnull JSONObject json, boolean complete) {
        synchronized( this ) {
            this.code = statusCode;
            this.json = json;
            this.complete = complete;
            notifyAll();
        }
    }

    void setNext(APIResponse next) {
        synchronized( this ) {
            this.next = next;
            notifyAll();
        }
    }
}
