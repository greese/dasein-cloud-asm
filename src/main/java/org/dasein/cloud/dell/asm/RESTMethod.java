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

import org.apache.commons.codec.binary.Base64;
import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpHost;
import org.apache.http.HttpResponse;
import org.apache.http.HttpVersion;
import org.apache.http.NameValuePair;
import org.apache.http.StatusLine;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.utils.URLEncodedUtils;
import org.apache.http.conn.params.ConnRoutePNames;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.CoreConnectionPNames;
import org.apache.http.params.HttpParams;
import org.apache.http.params.HttpProtocolParams;
import org.apache.http.protocol.HTTP;
import org.apache.http.util.EntityUtils;
import org.apache.log4j.Logger;
import org.dasein.cloud.CloudErrorType;
import org.dasein.cloud.CloudException;
import org.dasein.cloud.InternalException;
import org.dasein.cloud.ProviderContext;
import org.dasein.cloud.util.APITrace;
import org.dasein.cloud.util.Cache;
import org.dasein.cloud.util.CacheLevel;
import org.dasein.cloud.util.XMLParser;
import org.dasein.util.uom.time.Minute;
import org.dasein.util.uom.time.TimePeriod;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.xml.parsers.ParserConfigurationException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.Iterator;
import java.util.Properties;

/**
 * Implements the wire protocol for communicating with Dell ASM.
 * @author George Reese
 * @version 2013.04 initial version
 * @since 2013.04
 */
public class RESTMethod {
    static private final Logger logger = DellASM.getLogger(RESTMethod.class);
    static private final Logger wire   = DellASM.getWireLogger(RESTMethod.class);

    static public final int OK             = 200;
    static public final int CREATED        = 201;
    static public final int ACCEPTED       = 202;
    static public final int NO_CONTENT     = 204;
    static public final int NOT_FOUND      = 404;

    private DellASM provider;

    public RESTMethod(@Nonnull DellASM provider) { this.provider = provider; }

    private @Nonnull String authenticate() throws CloudException, InternalException {
        StringBuilder xml = new StringBuilder();

        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
        xml.append("<drl mode=\"\">");
        xml.append("<openconnection username=\"\" password=\"\"/>");
        xml.append("</drl>");

        APIResponse response = post("/connection/openConnectionRequest", xml.toString());
        Document doc = response.getXML();

        if( doc == null ) {
            throw new ASMException(CloudErrorType.AUTHENTICATION, response.getCode(), "NoAuth", "No authentication in response");
        }
        NodeList roots = doc.getElementsByTagName("drl");

        if( roots.getLength() != 1 ) {
            Node drl = roots.item(0);

            if( drl.hasAttributes() ) {
                Node c = drl.getAttributes().getNamedItem("connectionid");

                if( c != null ) {
                    return c.getNodeValue().trim();
                }
            }
        }
        throw new ASMException(CloudErrorType.AUTHENTICATION, response.getCode(), String.valueOf(response.getCode()), doc.toString());
    }

    public @Nonnull String getConnectionId() throws CloudException, InternalException {
        ProviderContext ctx = provider.getContext();

        if( ctx == null ) {
            throw new NoContextException();
        }
        String connectionId = null;

        Cache<String> cache = Cache.getInstance(provider, "connectionId", String.class, CacheLevel.CLOUD_ACCOUNT, new TimePeriod<Minute>(50, TimePeriod.MINUTE));
        Iterable<String> it = cache.get(ctx);

        if( it != null ) {
            Iterator<String> iterator = it.iterator();

            if( iterator.hasNext() ) {
                connectionId = iterator.next();
            }
        }
        if( connectionId == null ) {
            connectionId = authenticate();
        }
        return connectionId;
    }

    /*
    public void delete(@Nonnull String resource, @Nonnull String id, @Nullable NameValuePair ... parameters) throws InternalException, CloudException {
        if( logger.isTraceEnabled() ) {
            logger.trace("ENTER - " + RESTMethod.class.getName() + ".delete(" + resource + "," + id + "," + Arrays.toString(parameters) + ")");
        }
        try {
            String target = getEndpoint(resource, id, parameters);

            if( wire.isDebugEnabled() ) {
                wire.debug("");
                wire.debug(">>> [DELETE (" + (new Date()) + ")] -> " + target + " >--------------------------------------------------------------------------------------");
            }
            try {
                URI uri;

                try {
                    uri = new URI(target);
                }
                catch( URISyntaxException e ) {
                    throw new ConfigurationException(e);
                }
                HttpClient client = getClient(uri);

                try {
                    ProviderContext ctx = provider.getContext();

                    if( ctx == null ) {
                        throw new NoContextException();
                    }
                    HttpDelete delete = new HttpDelete(target);

                    long timestamp = System.currentTimeMillis();

                    String signature = getSignature(ctx.getAccessPublic(), ctx.getAccessPrivate(), "DELETE", resource, id, timestamp);

                    try {
                        delete.addHeader(ACCESS_KEY_HEADER, new String(ctx.getAccessPublic(), "utf-8"));
                    }
                    catch( UnsupportedEncodingException e ) {
                        throw new InternalException(e);
                    }
                    delete.addHeader("Accept", "application/json");
                    delete.addHeader(SIGNATURE_HEADER, signature);
                    delete.addHeader(VERSION_HEADER, VERSION);

                    if( wire.isDebugEnabled() ) {
                        wire.debug(delete.getRequestLine().toString());
                        for( Header header : delete.getAllHeaders() ) {
                            wire.debug(header.getName() + ": " + header.getValue());
                        }
                        wire.debug("");
                    }
                    HttpResponse response;
                    StatusLine status;

                    try {
                        APITrace.trace(provider, "DELETE " + resource);
                        response = client.execute(delete);
                        status = response.getStatusLine();
                    }
                    catch( IOException e ) {
                        logger.error("Failed to execute HTTP request due to a cloud I/O error: " + e.getMessage());
                        throw new CloudException(e);
                    }
                    if( logger.isDebugEnabled() ) {
                        logger.debug("HTTP Status " + status);
                    }
                    Header[] headers = response.getAllHeaders();

                    if( wire.isDebugEnabled() ) {
                        wire.debug(status.toString());
                        for( Header h : headers ) {
                            if( h.getValue() != null ) {
                                wire.debug(h.getName() + ": " + h.getValue().trim());
                            }
                            else {
                                wire.debug(h.getName() + ":");
                            }
                        }
                        wire.debug("");
                    }
                    if( status.getStatusCode() == NOT_FOUND ) {
                        throw new CloudException("No such endpoint: " + target);
                    }
                    if( status.getStatusCode() != NO_CONTENT ) {
                        logger.error("Expected NO CONTENT for DELETE request, got " + status.getStatusCode());
                        HttpEntity entity = response.getEntity();

                        if( entity == null ) {
                            throw new ASMException(CloudErrorType.GENERAL, status.getStatusCode(), status.getReasonPhrase(), status.getReasonPhrase());
                        }
                        String body;

                        try {
                            body = EntityUtils.toString(entity);
                        }
                        catch( IOException e ) {
                            throw new ASMException(e);
                        }
                        if( wire.isDebugEnabled() ) {
                            wire.debug(body);
                        }
                        wire.debug("");
                        throw new ASMException(CloudErrorType.GENERAL, status.getStatusCode(), status.getReasonPhrase(), body);
                    }
                }
                finally {
                    try { client.getConnectionManager().shutdown(); }
                    catch( Throwable ignore ) { }
                }
            }
            finally {
                if( wire.isDebugEnabled() ) {
                    wire.debug("<<< [DELETE (" + (new Date()) + ")] -> " + target + " <--------------------------------------------------------------------------------------");
                    wire.debug("");
                }
            }
        }
        finally {
            if( logger.isTraceEnabled() ) {
                logger.trace("EXIT - " + RESTMethod.class.getName() + ".delete()");
            }
        }
    }
    */

    public @Nonnull APIResponse get(final @Nonnull String operation, final @Nonnull String resource, final @Nullable String id, final @Nullable NameValuePair ... parameters) {
        final APIResponse response = new APIResponse();

        Thread t = new Thread() {
            public void run() {
                try {
                    APITrace.begin(provider, operation);
                    try {
                        try {
                            get(response, null, 1, resource, id, parameters);
                        }
                        catch( Throwable t ) {
                            response.receive(new CloudException(t));
                        }
                    }
                    finally {
                        APITrace.end();
                    }
                }
                finally {
                    provider.release();
                }
            }
        };

        t.setName(operation);
        t.setDaemon(true);

        provider.hold();
        t.start();
        return response;
    }

    private void get(@Nonnull APIResponse apiResponse, @Nullable String paginationId, final int page, final @Nonnull String resource, final @Nullable String id, final @Nullable NameValuePair ... parameters) throws InternalException, CloudException {
        if( logger.isTraceEnabled() ) {
            logger.trace("ENTER - " + RESTMethod.class.getName() + ".get(" + paginationId + "," + page + "," + resource + "," + id + "," + Arrays.toString(parameters) + ")");
        }
        try {
            NameValuePair[] params;

            if( parameters != null && paginationId != null ) {
                if( parameters == null || parameters.length < 1 ) {
                    params = new NameValuePair[] { new BasicNameValuePair("requestPaginationId", paginationId), new BasicNameValuePair("requestPage", String.valueOf(page)) };
                }
                else {
                    params = new NameValuePair[parameters.length + 2];

                    int i = 0;

                    for( ; i<parameters.length; i++ ) {
                        params[i] = parameters[i];
                    }
                    params[i++] = new BasicNameValuePair("requestPaginationId", paginationId);
                    params[i] = new BasicNameValuePair("requestPage", String.valueOf(page));
                }
            }
            else {
                params = parameters;
            }
            String target = getEndpoint(resource, id, params);

            if( wire.isDebugEnabled() ) {
                wire.debug("");
                wire.debug(">>> [GET (" + (new Date()) + ")] -> " + target + " >--------------------------------------------------------------------------------------");
            }
            try {
                URI uri;

                try {
                    uri = new URI(target);
                }
                catch( URISyntaxException e ) {
                    throw new ConfigurationException(e);
                }
                HttpClient client = getClient(uri);

                try {
                    ProviderContext ctx = provider.getContext();

                    if( ctx == null ) {
                        throw new NoContextException();
                    }
                    HttpGet get = new HttpGet(target);

                    if( wire.isDebugEnabled() ) {
                        wire.debug(get.getRequestLine().toString());
                        for( Header header : get.getAllHeaders() ) {
                            wire.debug(header.getName() + ": " + header.getValue());
                        }
                        wire.debug("");
                    }
                    HttpResponse response;
                    StatusLine status;

                    try {
                        APITrace.trace(provider, "GET " + resource);
                        response = client.execute(get);
                        status = response.getStatusLine();
                    }
                    catch( IOException e ) {
                        logger.error("Failed to execute HTTP request due to a cloud I/O error: " + e.getMessage());
                        throw new CloudException(e);
                    }
                    if( logger.isDebugEnabled() ) {
                        logger.debug("HTTP Status " + status);
                    }
                    Header[] headers = response.getAllHeaders();

                    if( wire.isDebugEnabled() ) {
                        wire.debug(status.toString());
                        for( Header h : headers ) {
                            if( h.getValue() != null ) {
                                wire.debug(h.getName() + ": " + h.getValue().trim());
                            }
                            else {
                                wire.debug(h.getName() + ":");
                            }
                        }
                        wire.debug("");
                    }
                    if( status.getStatusCode() == NOT_FOUND ) {
                        apiResponse.receive();
                        return;
                    }
                    if( status.getStatusCode() != OK ) {
                        logger.error("Expected OK for GET request, got " + status.getStatusCode());
                        HttpEntity entity = response.getEntity();

                        if( entity == null ) {
                            throw new ASMException(CloudErrorType.GENERAL, status.getStatusCode(), status.getReasonPhrase(), status.getReasonPhrase());
                        }
                        parseError(apiResponse, entity, status.getStatusCode(), status.getReasonPhrase());
                    }
                    else {
                        HttpEntity entity = response.getEntity();

                        if( entity == null ) {
                            throw new CloudException("No entity was returned from an HTTP GET");
                        }
                        boolean complete;

                        Header h = response.getFirstHeader("x-es-pagination");
                        final String pid;

                        if( h != null ) {
                            pid = h.getValue();

                            if( pid != null ) {
                                Header last = response.getFirstHeader("x-es-last-page");

                                complete = last != null && last.getValue().equalsIgnoreCase("true");
                            }
                            else {
                                complete = true;
                            }
                        }
                        else {
                            pid = null;
                            complete = true;
                        }
                        if( entity.getContentType() == null || entity.getContentType().getValue().contains("json") ) {
                            String body;

                            try {
                                body = EntityUtils.toString(entity);
                            }
                            catch( IOException e ) {
                                throw new ASMException(e);
                            }
                            if( wire.isDebugEnabled() ) {
                                wire.debug(body);
                            }
                            wire.debug("");

                            apiResponse.receive(status.getStatusCode(), parseResponse(body), complete);
                        }
                        else {
                            try {
                                apiResponse.receive(status.getStatusCode(), entity.getContent());
                            }
                            catch( IOException e ) {
                                throw new CloudException(e);
                            }
                        }
                        if( !complete ) {
                            APIResponse r = new APIResponse();

                            apiResponse.setNext(r);
                            get(r, pid, page+1, resource, id, parameters);
                        }
                    }
                }
                finally {
                    try { client.getConnectionManager().shutdown(); }
                    catch( Throwable ignore ) { }
                }
            }
            finally {
                if( wire.isDebugEnabled() ) {
                    wire.debug("<<< [GET (" + (new Date()) + ")] -> " + target + " <--------------------------------------------------------------------------------------");
                    wire.debug("");
                }
            }
        }
        finally {
            if( logger.isTraceEnabled() ) {
                logger.trace("EXIT - " + RESTMethod.class.getName() + ".get()");
            }
        }
    }

    private void parseError(@Nullable APIResponse apiResponse, @Nonnull HttpEntity entity, int httpCode, @Nonnull String defaultReason) throws ASMException, InternalException {
        String reason = defaultReason;
        String body;

        try {
            body = EntityUtils.toString(entity);

            Document doc = parseResponse(body);

            if( wire.isDebugEnabled() ) {
                wire.debug(body);
            }
            wire.debug("");
            NodeList errors = doc.getElementsByTagName("error");

            if( errors != null && errors.getLength() > 0 ) {
                Node error = errors.item(0);

                if( error.hasAttributes() ) {
                    Node code = error.getAttributes().getNamedItem("code");
                    Node message = error.getAttributes().getNamedItem("message");

                    if( code != null ) {
                        reason = code.getNodeValue().trim();
                    }
                    if( message != null ) {
                        body = message.getNodeValue().trim();
                    }
                }
            }
        }
        catch( IOException e ) {
            throw new ASMException(e);
        }
        if( apiResponse != null ) {
            apiResponse.receive(new ASMException(CloudErrorType.GENERAL, httpCode, reason, body));
        }
        else {
            throw new ASMException(CloudErrorType.GENERAL, httpCode, reason, body);
        }
    }

    private @Nonnull Document parseResponse(@Nonnull String responseBody) throws ASMException, InternalException {
        try {
            if( wire.isDebugEnabled() ) {
                String[] lines = responseBody.split("\n");

                if( lines.length < 1 ) {
                    lines = new String[] { responseBody };
                }
                for( String l : lines ) {
                    wire.debug(l);
                }
            }
            return XMLParser.parse(new ByteArrayInputStream(responseBody.getBytes()));
        }
        catch( IOException e ) {
            throw new ASMException(e);
        }
        catch( ParserConfigurationException e ) {
            throw new InternalException(e);
        }
        catch( SAXException e ) {
            throw new ASMException(e);
        }
    }

    private @Nonnull HttpClient getClient(URI uri) throws InternalException, CloudException {
        ProviderContext ctx = provider.getContext();

        if( ctx == null ) {
            throw new NoContextException();
        }

        boolean ssl = uri.getScheme().startsWith("https");

        HttpParams params = new BasicHttpParams();


        HttpProtocolParams.setVersion(params, HttpVersion.HTTP_1_1);
        //noinspection deprecation
        HttpProtocolParams.setContentCharset(params, HTTP.UTF_8);
        HttpProtocolParams.setUserAgent(params, "Dasein Cloud");
        params.setParameter(CoreConnectionPNames.CONNECTION_TIMEOUT, 10000);
        params.setParameter(CoreConnectionPNames.SO_TIMEOUT, 300000);

        Properties p = ctx.getCustomProperties();

        if( p != null ) {
            String proxyHost = p.getProperty("proxyHost");
            String proxyPort = p.getProperty("proxyPort");

            if( proxyHost != null ) {
                int port = 0;

                if( proxyPort != null && proxyPort.length() > 0 ) {
                    port = Integer.parseInt(proxyPort);
                }
                params.setParameter(ConnRoutePNames.DEFAULT_PROXY, new HttpHost(proxyHost, port, ssl ? "https" : "http"));
            }
        }
        return new DefaultHttpClient(params);
    }

    private @Nonnull String getEndpoint(@Nonnull String resource, @Nullable String id, @Nullable NameValuePair... parameters) throws ConfigurationException, InternalException {
        // TODO: implement this to provide a canonical URI based on the resource and ID being references
        ProviderContext ctx = provider.getContext();

        if( ctx == null ) {
            throw new NoContextException();
        }
        String endpoint = ctx.getEndpoint();

        if( endpoint == null ) {
            logger.error("Null endpoint for the DellASM cloud");
            throw new ConfigurationException("Null endpoint for DellASM cloud");
        }
       while( endpoint.endsWith("/") && !endpoint.equals("/") ) {
           endpoint = endpoint.substring(0, endpoint.length()-1);
        }
        if( resource.startsWith("/") ) {
            endpoint =  endpoint + resource;
        }
        else {
            endpoint = endpoint + "/" + resource;
        }
        if( id != null ) {
            if( endpoint.endsWith("/") ) {
                endpoint = endpoint + id;
            }
            else {
                endpoint = endpoint + "/" + id;
            }
        }
        if( parameters != null && parameters.length > 0 ) {
            while( endpoint.endsWith("/") ) {
                endpoint = endpoint.substring(0, endpoint.length()-1);
            }
            endpoint = endpoint + "?";
            ArrayList<NameValuePair> params = new ArrayList<NameValuePair>();

            Collections.addAll(params, parameters);
            endpoint = endpoint + URLEncodedUtils.format(params, "utf-8");
        }
        return endpoint;
    }

    public @Nonnull APIResponse post(@Nonnull String resource, @Nonnull String xml) throws InternalException, CloudException {
        if( logger.isTraceEnabled() ) {
            logger.trace("ENTER - " + RESTMethod.class.getName() + ".post(" + resource + "," + xml + ")");
        }
        try {
            String target = getEndpoint(resource, null);

            if( wire.isDebugEnabled() ) {
                wire.debug("");
                wire.debug(">>> [POST (" + (new Date()) + ")] -> " + target + " >--------------------------------------------------------------------------------------");
            }
            try {
                URI uri;

                try {
                    uri = new URI(target);
                }
                catch( URISyntaxException e ) {
                    throw new ConfigurationException(e);
                }
                HttpClient client = getClient(uri);

                try {
                    ProviderContext ctx = provider.getContext();

                    if( ctx == null ) {
                        throw new NoContextException();
                    }
                    HttpPost post = new HttpPost(target);

                    try {
                        post.setEntity(new StringEntity(xml, "utf-8"));
                    }
                    catch( UnsupportedEncodingException e ) {
                        logger.error("Unsupported encoding UTF-8: " + e.getMessage());
                        throw new InternalException(e);
                    }

                    if( wire.isDebugEnabled() ) {
                        wire.debug(post.getRequestLine().toString());
                        for( Header header : post.getAllHeaders() ) {
                            wire.debug(header.getName() + ": " + header.getValue());
                        }
                        wire.debug("");
                        wire.debug(xml);
                        wire.debug("");
                    }
                    HttpResponse response;
                    StatusLine status;

                    try {
                        APITrace.trace(provider, "POST " + resource);
                        response = client.execute(post);
                        status = response.getStatusLine();
                    }
                    catch( IOException e ) {
                        logger.error("Failed to execute HTTP request due to a cloud I/O error: " + e.getMessage());
                        throw new CloudException(e);
                    }
                    if( logger.isDebugEnabled() ) {
                        logger.debug("HTTP Status " + status);
                    }
                    Header[] headers = response.getAllHeaders();

                    if( wire.isDebugEnabled() ) {
                        wire.debug(status.toString());
                        for( Header h : headers ) {
                            if( h.getValue() != null ) {
                                wire.debug(h.getName() + ": " + h.getValue().trim());
                            }
                            else {
                                wire.debug(h.getName() + ":");
                            }
                        }
                        wire.debug("");
                    }
                    if( status.getStatusCode() == NOT_FOUND ) {
                        throw new CloudException("No such endpoint: " + target);
                    }
                    if( status.getStatusCode() != ACCEPTED && status.getStatusCode() != CREATED ) {
                        logger.error("Expected ACCEPTED or CREATED for POST request, got " + status.getStatusCode());
                        HttpEntity entity = response.getEntity();

                        if( entity == null ) {
                            throw new ASMException(CloudErrorType.GENERAL, status.getStatusCode(), status.getReasonPhrase(), status.getReasonPhrase());
                        }
                        parseError(null, entity, status.getStatusCode(), status.getReasonPhrase());
                        throw new ASMException(CloudErrorType.GENERAL, status.getStatusCode(), status.getReasonPhrase(), status.getReasonPhrase());
                    }
                    else {
                        HttpEntity entity = response.getEntity();

                        if( entity == null ) {
                            throw new CloudException("No response to the POST");
                        }
                        try {
                            xml = EntityUtils.toString(entity);
                        }
                        catch( IOException e ) {
                            throw new ASMException(e);
                        }
                        if( wire.isDebugEnabled() ) {
                            wire.debug(xml);
                        }
                        wire.debug("");
                        APIResponse r = new APIResponse();

                        r.receive(status.getStatusCode(), parseResponse(xml), true);
                        return r;
                    }
                }
                finally {
                    try { client.getConnectionManager().shutdown(); }
                    catch( Throwable ignore ) { }
                }
            }
            finally {
                if( wire.isDebugEnabled() ) {
                    wire.debug("<<< [POST (" + (new Date()) + ")] -> " + target + " <--------------------------------------------------------------------------------------");
                    wire.debug("");
                }
            }
        }
        finally {
            if( logger.isTraceEnabled() ) {
                logger.trace("EXIT - " + RESTMethod.class.getName() + ".post()");
            }
        }
    }

    /*
    public @Nonnull APIResponse put(@Nonnull String resource, @Nonnull String id, @Nonnull String json) throws InternalException, CloudException {
        if( logger.isTraceEnabled() ) {
            logger.trace("ENTER - " + RESTMethod.class.getName() + ".put(" + resource + "," + id + "," + json + ")");
        }
        try {
            String target = getEndpoint(resource, id, null);

            if( wire.isDebugEnabled() ) {
                wire.debug("");
                wire.debug(">>> [PUT (" + (new Date()) + ")] -> " + target + " >--------------------------------------------------------------------------------------");
            }
            try {
                URI uri;

                try {
                    uri = new URI(target);
                }
                catch( URISyntaxException e ) {
                    throw new ConfigurationException(e);
                }
                HttpClient client = getClient(uri);

                try {
                    ProviderContext ctx = provider.getContext();

                    if( ctx == null ) {
                        throw new NoContextException();
                    }
                    HttpPut put = new HttpPut(target);

                    long timestamp = System.currentTimeMillis();

                    String signature = getSignature(ctx.getAccessPublic(), ctx.getAccessPrivate(), "PUT", resource, id, timestamp);

                    try {
                        put.addHeader(ACCESS_KEY_HEADER, new String(ctx.getAccessPublic(), "utf-8"));
                    }
                    catch( UnsupportedEncodingException e ) {
                        throw new InternalException(e);
                    }
                    put.addHeader("Accept", "application/json");
                    put.addHeader(SIGNATURE_HEADER, signature);
                    put.addHeader(VERSION_HEADER, VERSION);

                    put.addHeader("Content-type", "application/json;charset=utf-8");
                    try {
                        put.setEntity(new StringEntity(json, "utf-8"));
                    }
                    catch( UnsupportedEncodingException e ) {
                        logger.error("Unsupported encoding UTF-8: " + e.getMessage());
                        throw new InternalException(e);
                    }

                    if( wire.isDebugEnabled() ) {
                        wire.debug(put.getRequestLine().toString());
                        for( Header header : put.getAllHeaders() ) {
                            wire.debug(header.getName() + ": " + header.getValue());
                        }
                        wire.debug("");
                        wire.debug(json);
                        wire.debug("");
                    }
                    HttpResponse response;
                    StatusLine status;

                    try {
                        APITrace.trace(provider, "PUT " + resource);
                        response = client.execute(put);
                        status = response.getStatusLine();
                    }
                    catch( IOException e ) {
                        logger.error("Failed to execute HTTP request due to a cloud I/O error: " + e.getMessage());
                        throw new CloudException(e);
                    }
                    if( logger.isDebugEnabled() ) {
                        logger.debug("HTTP Status " + status);
                    }
                    Header[] headers = response.getAllHeaders();

                    if( wire.isDebugEnabled() ) {
                        wire.debug(status.toString());
                        for( Header h : headers ) {
                            if( h.getValue() != null ) {
                                wire.debug(h.getName() + ": " + h.getValue().trim());
                            }
                            else {
                                wire.debug(h.getName() + ":");
                            }
                        }
                        wire.debug("");
                    }
                    if( status.getStatusCode() == NOT_FOUND || status.getStatusCode() == NO_CONTENT ) {
                        APIResponse r = new APIResponse();

                        r.receive();
                        return r;
                    }
                    if( status.getStatusCode() != ACCEPTED ) {
                        logger.error("Expected ACCEPTED or CREATED for POST request, got " + status.getStatusCode());
                        HttpEntity entity = response.getEntity();

                        if( entity == null ) {
                            throw new ASMException(CloudErrorType.GENERAL, status.getStatusCode(), status.getReasonPhrase(), status.getReasonPhrase());
                        }
                        try {
                            json = EntityUtils.toString(entity);
                        }
                        catch( IOException e ) {
                            throw new ASMException(e);
                        }
                        if( wire.isDebugEnabled() ) {
                            wire.debug(json);
                        }
                        wire.debug("");
                        throw new ASMException(CloudErrorType.GENERAL, status.getStatusCode(), status.getReasonPhrase(), json);
                    }
                    else {
                        HttpEntity entity = response.getEntity();

                        if( entity == null ) {
                            throw new CloudException("No response to the PUT");
                        }
                        try {
                            json = EntityUtils.toString(entity);
                        }
                        catch( IOException e ) {
                            throw new ASMException(e);
                        }
                        if( wire.isDebugEnabled() ) {
                            wire.debug(json);
                        }
                        wire.debug("");
                        APIResponse r = new APIResponse();

                        try {
                            r.receive(status.getStatusCode(), new JSONObject(json), true);
                        }
                        catch( JSONException e ) {
                            throw new CloudException(e);
                        }
                        return r;
                    }
                }
                finally {
                    try { client.getConnectionManager().shutdown(); }
                    catch( Throwable ignore ) { }
                }
            }
            finally {
                if( wire.isDebugEnabled() ) {
                    wire.debug("<<< [PUT (" + (new Date()) + ")] -> " + target + " <--------------------------------------------------------------------------------------");
                    wire.debug("");
                }
            }
        }
        finally {
            if( logger.isTraceEnabled() ) {
                logger.trace("EXIT - " + RESTMethod.class.getName() + ".put()");
            }
        }
    }
    */

}
