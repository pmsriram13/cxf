/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.cxf.tracing.brave;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.github.kristofa.brave.Brave;
import com.github.kristofa.brave.http.SpanNameProvider;

import org.apache.cxf.helpers.CastUtils;
import org.apache.cxf.message.Message;
import org.apache.cxf.message.MessageUtils;
import org.apache.cxf.phase.PhaseInterceptor;

public abstract class AbstractBraveInterceptor extends AbstractBraveProvider implements PhaseInterceptor<Message> {
    private final String phase;

    protected static class ParsedMessage {
        private Message message;

        ParsedMessage(Message message) {
            this.message = message;
        }

        String safeGet(String key) {
            if (!message.containsKey(key)) {
                return null;
            }
            Object value = message.get(key);
            return (value instanceof String) ? value.toString() : null;
        }

        private String getUriSt() {
            String uri = safeGet(Message.REQUEST_URL);
            if (uri == null) {
                String address = safeGet(Message.ENDPOINT_ADDRESS);
                uri = safeGet(Message.REQUEST_URI);
                if (uri != null && uri.startsWith("/")) {
                    if (address != null && !address.startsWith(uri)) {
                        if (address.endsWith("/") && address.length() > 1) {
                            address = address.substring(0, address.length());
                        }
                        uri = address + uri;
                    }
                } else {
                    uri = address;
                }
            }
            String query = safeGet(Message.QUERY_STRING);
            if (query != null) {
                return uri + "?" + query;
            } else {
                return uri;
            }
        }

        URI getUri() {
            try {
                String uriSt = getUriSt();
                return uriSt != null ? new URI(uriSt) : new URI("");
            } catch (URISyntaxException e) {
                throw new RuntimeException(e.getMessage(), e);
            }
        }

        Message getEffectiveMessage() {
            boolean isRequestor = MessageUtils.isRequestor(message);
            boolean isOutbound = MessageUtils.isOutbound(message);
            if (isRequestor) {
                return isOutbound ? message : message.getExchange().getOutMessage();
            } else {
                return isOutbound ? message.getExchange().getInMessage() : message;
            }
        }

        Map<String, List<String>> getHeaders() {
            Map<String, List<String>> headers = CastUtils.cast((Map<?, ?>)message.get(Message.PROTOCOL_HEADERS));

            if (headers == null) {
                return Collections.emptyMap();
            }

            return headers;
        }

        void addHeader(String key, String value) {
            Map<String, List<String>> headers = CastUtils.cast((Map<?, ?>)message.get(Message.PROTOCOL_HEADERS));
            if (headers == null) {
                headers = new HashMap<String, List<String>>();
                message.put(Message.PROTOCOL_HEADERS, headers);
            }
            headers.put(key, Arrays.asList(value));
        }

        String getHttpMethod() {
            ParsedMessage eMessage = new ParsedMessage(getEffectiveMessage());
            return eMessage.safeGet(Message.HTTP_REQUEST_METHOD);
        }
    }

    protected AbstractBraveInterceptor(String phase, Brave brave, SpanNameProvider spanNameProvider) {
        super(brave, spanNameProvider);
        this.phase = phase;
    }

    @Override
    public Set<String> getAfter() {
        return Collections.emptySet();
    }

    @Override
    public Set<String> getBefore() {
        return Collections.emptySet();
    }

    @Override
    public String getId() {
        return getClass().getName();
    }

    @Override
    public String getPhase() {
        return phase;
    }

    @Override
    public Collection<PhaseInterceptor<? extends Message>> getAdditionalInterceptors() {
        return null;
    }

    @Override
    public void handleFault(Message message) {
    }
}
