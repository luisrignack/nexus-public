/*
 * Sonatype Nexus (TM) Open Source Version
 * Copyright (c) 2008-present Sonatype, Inc.
 * All rights reserved. Includes the third-party code listed at http://links.sonatype.com/products/nexus/oss/attributions.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse Public License Version 1.0,
 * which accompanies this distribution and is available at http://www.eclipse.org/legal/epl-v10.html.
 *
 * Sonatype Nexus (TM) Professional Version is available from Sonatype, Inc. "Sonatype" and "Sonatype Nexus" are trademarks
 * of Sonatype, Inc. Apache Maven is a trademark of the Apache Software Foundation. M2eclipse is a trademark of the
 * Eclipse Foundation. All other trademarks are the property of their respective owners.
 */
package org.sonatype.nexus.internal.httpclient;

import java.net.URI;

import org.sonatype.goodies.testsupport.Test5Support;
import org.sonatype.nexus.common.event.EventManager;
import org.sonatype.nexus.httpclient.HttpClientPlan;
import org.sonatype.nexus.httpclient.config.HttpClientConfiguration;
import org.sonatype.nexus.httpclient.config.HttpClientConfigurationChangedEvent;

import org.apache.http.HttpHost;
import org.apache.http.HttpRequest;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.config.ConnectionConfig;
import org.apache.http.config.SocketConfig;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.message.BasicHttpRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link HttpClientManagerImpl}.
 */
class HttpClientManagerImplTest
    extends Test5Support
{

  @Mock
  private EventManager eventManager;

  @Mock
  private HttpClientConfigurationStore configStore;

  @Mock
  private SharedHttpClientConnectionManager connectionManager;

  @Mock
  private DefaultsCustomizer defaultsCustomizer;

  @Mock
  private HttpClientConfigurationEvent configEvent;

  private HttpClientManagerImpl underTest;

  @BeforeEach
  void setUp() {
    underTest = new HttpClientManagerImpl(eventManager, configStore, TestHttpClientConfiguration::new,
        connectionManager,
        defaultsCustomizer);
  }

  @Test
  void testPrepareUserAgentHeaderSetOnBuilder() throws Exception {
    // Setup
    String expectedUserAgentHeader = "Nexus/Agent my user agent";
    HttpClientPlan plan = mock(HttpClientPlan.class);
    doReturn(expectedUserAgentHeader).when(plan).getUserAgent();
    HttpClientBuilder builder = mock(HttpClientBuilder.class);
    doReturn(builder).when(plan).getClient();

    ConnectionConfig.Builder conn = mock(ConnectionConfig.Builder.class);
    SocketConfig.Builder sock = mock(SocketConfig.Builder.class);
    RequestConfig.Builder req = mock(RequestConfig.Builder.class);
    doReturn(null).when(conn).build();
    doReturn(null).when(sock).build();
    doReturn(null).when(req).build();
    doReturn(conn).when(plan).getConnection();
    doReturn(sock).when(plan).getSocket();
    doReturn(req).when(plan).getRequest();

    underTest.start();
    HttpClientManagerImpl spy = spy(underTest);

    doReturn(plan).when(spy).httpClientPlan();

    // Execute
    HttpClientBuilder returned = spy.prepare(null);

    // Verify
    assertNotNull(returned, "Returned builder must not be null.");
    assertEquals(builder, returned, "Returned builder must be expected builder.");
    verify(spy).setUserAgent(builder, expectedUserAgentHeader);
  }

  @Test
  void testOnStoreChanged_LocalEvent() {
    when(configEvent.isLocal()).thenReturn(true);
    underTest.onStoreChanged(configEvent);
    verifyNoInteractions(eventManager, configStore);
  }

  @Test
  void testOnStoreChanged_RemoteEvent() {
    HttpClientConfiguration config = new TestHttpClientConfiguration();
    when(configStore.load()).thenReturn(config);
    when(configEvent.isLocal()).thenReturn(false);
    when(configEvent.getRemoteNodeId()).thenReturn("remote-node-id");
    underTest.onStoreChanged(configEvent);
    ArgumentCaptor<HttpClientConfigurationChangedEvent> eventCaptor = ArgumentCaptor
        .forClass(HttpClientConfigurationChangedEvent.class);
    verify(eventManager).post(eventCaptor.capture());
    assertThat(eventCaptor.getValue().getConfiguration(), is(config));
  }

  @Test
  void testGetRequestURI_PreservesEncodingWithRelativeURI() {
    HttpClientContext context = HttpClientContext.create();
    HttpHost target = new HttpHost("example.com", 443, "https");
    context.setTargetHost(target);

    // Create request with encoded path (spaces, special chars)
    String encodedPath = "/path/with%20spaces/and%2Bplus/file%40name.txt";
    HttpRequest request = new BasicHttpRequest("GET", encodedPath);
    context.setAttribute(HttpClientContext.HTTP_REQUEST, request);

    URI result = underTest.getRequestURI(context);

    assertThat(result.toString(), containsString("%20"));
    assertThat(result.toString(), containsString("%2B"));
    assertThat(result.toString(), containsString("%40"));
    assertThat(result.getScheme(), is("https"));
    assertThat(result.getHost(), is("example.com"));
    assertThat(result.getPort(), is(443));
    assertThat(result.getRawPath(), is(encodedPath));
  }

  @Test
  void testGetRequestURI_PreservesEncodingWithQueryString() {
    HttpClientContext context = HttpClientContext.create();
    HttpHost target = new HttpHost("example.com", 80, "http");
    context.setTargetHost(target);

    // Create request with encoded query parameters
    String encodedUri = "/search?q=hello%20world&filter=type%3Djar";
    HttpRequest request = new BasicHttpRequest("GET", encodedUri);
    context.setAttribute(HttpClientContext.HTTP_REQUEST, request);

    URI result = underTest.getRequestURI(context);

    assertThat(result.toString(), containsString("q=hello%20world"));
    assertThat(result.toString(), containsString("filter=type%3Djar"));
    assertThat(result.getRawQuery(), containsString("%20"));
    assertThat(result.getRawQuery(), containsString("%3D"));
  }

  @Test
  void testGetRequestURI_HandlesAbsoluteURI() {
    HttpClientContext context = HttpClientContext.create();
    HttpHost target = new HttpHost("example.com", 443, "https");
    context.setTargetHost(target);

    String absoluteUri = "https://example.com/path/with%20spaces";
    HttpGet request = new HttpGet(absoluteUri);
    context.setAttribute(HttpClientContext.HTTP_REQUEST, request);

    URI result = underTest.getRequestURI(context);

    assertThat(result.toString(), is(absoluteUri));
    assertThat(result.getRawPath(), containsString("%20"));
  }

  @Test
  void testGetRequestURI_HandlesPortInURI() {
    HttpClientContext context = HttpClientContext.create();
    HttpHost target = new HttpHost("example.com", 8443, "https");
    context.setTargetHost(target);

    // Create request with encoded path
    String encodedPath = "/api/v1/resource%20name";
    HttpRequest request = new BasicHttpRequest("GET", encodedPath);
    context.setAttribute(HttpClientContext.HTTP_REQUEST, request);

    URI result = underTest.getRequestURI(context);

    assertThat(result.toString(), containsString(":8443"));
    assertThat(result.getPort(), is(8443));
    assertThat(result.getRawPath(), containsString("%20"));
  }
}
