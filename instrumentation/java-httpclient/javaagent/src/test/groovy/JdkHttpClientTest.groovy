/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import static io.opentelemetry.api.trace.SpanKind.CLIENT

import io.opentelemetry.instrumentation.test.AgentTestTrait
import io.opentelemetry.instrumentation.test.base.HttpClientTest
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.time.temporal.ChronoUnit
import java.util.function.Consumer
import spock.lang.Requires
import spock.lang.Shared

class JdkHttpClientTest extends HttpClientTest implements AgentTestTrait {

  @Shared
  def client = HttpClient.newBuilder().connectTimeout(Duration.of(CONNECT_TIMEOUT_MS,
    ChronoUnit.MILLIS)).followRedirects(HttpClient.Redirect.NORMAL).build()

  @Override
  int doRequest(String method, URI uri, Map<String, String> headers = [:]) {
    return client.send(buildRequest(method, uri, headers), HttpResponse.BodyHandlers.ofString())
      .statusCode()
  }

  @Override
  void doRequestWithCallback(String method, URI uri, Map<String, String> headers = [:], Consumer<Integer> callback) {
    client.sendAsync(buildRequest(method, uri, headers), HttpResponse.BodyHandlers.ofString())
      .thenAccept {
        callback.accept(it.statusCode())
      }
  }

  private static HttpRequest buildRequest(String method, URI uri, Map<String, String> headers) {
    def builder = HttpRequest.newBuilder().uri(uri).method(method, HttpRequest.BodyPublishers.noBody())

    headers.entrySet().each {
      builder.header(it.key, it.value)
    }
    return builder.build()
  }

  @Override
  boolean testCircularRedirects() {
    return false
  }

  //We override this test below because it produces somewhat different attributes
  @Override
  boolean testRemoteConnection() {
    return false
  }

  // TODO nested client span is not created, but context is still injected
  //  which is not what the test expects
  @Override
  boolean testWithClientParent() {
    false
  }

  @Requires({ !System.getProperty("java.vm.name").contains("IBM J9 VM") })
  def "test https request"() {
    given:
    def uri = new URI("https://www.google.com/")

    when:
    def status = doRequest(method, uri)

    then:
    status == 200
    assertTraces(1) {
      trace(0, 1 + extraClientSpans()) {
        span(0) {
          hasNoParent()
          name expectedOperationName(method)
          kind CLIENT
          errored false
          attributes {
            "${SemanticAttributes.NET_TRANSPORT.key}" "IP.TCP"
            "${SemanticAttributes.NET_PEER_NAME.key}" uri.host
            "${SemanticAttributes.NET_PEER_IP.key}" { it == null || it == "127.0.0.1" }
            // Optional
            "${SemanticAttributes.NET_PEER_PORT.key}" uri.port > 0 ? uri.port : { it == null || it == 443 }
            "${SemanticAttributes.HTTP_URL.key}" { it == "${uri}" || it == "${removeFragment(uri)}" }
            "${SemanticAttributes.HTTP_METHOD.key}" method
            "${SemanticAttributes.HTTP_FLAVOR.key}" "2.0"
            "${SemanticAttributes.HTTP_STATUS_CODE.key}" status
          }
        }
      }
    }

    where:
    method = "HEAD"
  }

}
