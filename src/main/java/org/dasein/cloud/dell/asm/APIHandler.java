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

import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpHost;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.HttpVersion;
import org.apache.http.StatusLine;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.conn.params.ConnRoutePNames;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.DefaultHttpClient;
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
import javax.xml.parsers.ParserConfigurationException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
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
public class APIHandler {
    static private final Logger logger = DellASM.getLogger(APIHandler.class);
    static private final Logger wire   = DellASM.getWireLogger(APIHandler.class);

    static public final String ENUMERATE_ARCHIVE = "enumeratearchive";
    static public final String OPEN_CONNECTION   = "openconnection";

    private DellASM provider;

    public APIHandler(@Nonnull DellASM provider) { this.provider = provider; }

    /**
     * Performs authentication against Dell ASM.
     * @param ctx the context for authenticating this request
     * @return the connection ID resulting from the Dell ASM authentication
     * @throws CloudException an error occurred authentication with Dell ASM
     * @throws InternalException an internal error occurred generating the request to Dell ASM
     */
    public @Nonnull String authenticate(@Nonnull ProviderContext ctx) throws CloudException, InternalException {
        try {
            String user = new String(ctx.getAccessPublic(), "utf-8");
            String password = new String(ctx.getAccessPrivate(), "utf-8");
            StringBuilder xml = new StringBuilder();

            xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
            xml.append("<!DOCTYPE drl SYSTEM \"").append(getEndpoint()).append("/labmagic/v1_2/api/connection/openConnectionRequest.dtd\">");
            xml.append("<drl mode=\"normal\">");
            xml.append("<").append(OPEN_CONNECTION).append(" username=\"").append(user).append("\" password=\"").append(password).append("\"/>");
            xml.append("</drl>");

            APIResponse response = post(OPEN_CONNECTION, xml.toString());
            Document doc = response.getXML();

            if( doc == null ) {
                throw new ASMException(CloudErrorType.AUTHENTICATION, response.getCode(), "NoAuth", "No authentication in response");
            }
            NodeList roots = doc.getElementsByTagName("drl");

            if( roots.getLength() == 1 ) {
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
        catch( UnsupportedEncodingException e ) {
            throw new InternalException(e);
        }
    }

    /**
     * Provides the currently authenticated connection ID, if one exists. If not, it will authenticate and cache that connection ID.
     * @return a valid connection ID for executing API operations against Dell ASM
     * @throws CloudException an error occurred authentication with Dell ASM
     * @throws InternalException an internal error occurred generating the request to Dell ASM
     */
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
            connectionId = authenticate(ctx);
            cache.put(ctx, Collections.singletonList(connectionId));
        }
        return connectionId;
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

    /**
     * Provides access to the context endpoint that will be used for this connection.
     * @return the context endpoint for API calls
     * @throws ConfigurationException the environment was not properly configured to make calls
     * @throws InternalException an error occurred within Dasein Cloud while processing the request
     */
    public @Nonnull String getEndpoint() throws ConfigurationException, InternalException {
        ProviderContext ctx = provider.getContext();

        if( ctx == null ) {
            throw new NoContextException();
        }
        String endpoint = ctx.getEndpoint();

        if( endpoint == null ) {
            logger.error("Null endpoint for the Dell ASM cloud");
            throw new ConfigurationException("Null endpoint for DellASM cloud");
        }
        return endpoint;
    }


    private void parseError(int httpCode, @Nonnull String defaultReason, NodeList errors) throws ASMException, InternalException {
        String reason = defaultReason;
        String body = "";

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
        throw new ASMException(CloudErrorType.GENERAL, httpCode, reason, body);
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

    /**
     * Posts to the specified resource with the specified XML payload.
     * @param operation the API operation being triggered
     * @param xml an XML document to post
     * @return the API response from Dell ASM
     * @throws InternalException an error occurred internally while processing the request
     * @throws CloudException an error occurred in Dell ASM executing the request
     */
    public @Nonnull APIResponse post(@Nonnull String operation, @Nonnull String xml) throws InternalException, CloudException {
        if( logger.isTraceEnabled() ) {
            logger.trace("ENTER: " + APIHandler.class.getName() + ".post(" + xml + ")");
        }
        try {
            if( logger.isDebugEnabled() ) {
                try {
                    XMLParser.parse(new ByteArrayInputStream(xml.getBytes("utf-8")));
                    logger.debug("XML body is valid");
                }
                catch( Throwable t ) {
                    logger.warn("Invalid XML being submitted to cloud: " + t.getMessage());
                }
            }
            String target = getEndpoint() + "/xmlApiServlet";

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
                        APITrace.trace(provider, operation);
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
                    if( status.getStatusCode() == HttpStatus.SC_NOT_FOUND ) {
                        throw new CloudException("No such endpoint: " + target);
                    }
                    HttpEntity entity = response.getEntity();

                    if( entity == null ) {
                        throw new ASMException(CloudErrorType.GENERAL, status.getStatusCode(), status.getReasonPhrase(), status.getReasonPhrase());
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

                    Document doc = parseResponse(xml);

                    NodeList errors = doc.getElementsByTagName("error");

                    if( errors.getLength() > 0 ) {
                        parseError(status.getStatusCode(), status.getReasonPhrase(), errors);
                        throw new ASMException(CloudErrorType.GENERAL, status.getStatusCode(), status.getReasonPhrase(), status.getReasonPhrase());
                    }
                    else {
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
                logger.trace("EXIT: " + APIHandler.class.getName() + ".post()");
            }
        }
    }
}
