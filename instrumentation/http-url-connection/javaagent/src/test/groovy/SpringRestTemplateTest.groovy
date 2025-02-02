/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import io.opentelemetry.instrumentation.test.AgentTestTrait
import io.opentelemetry.instrumentation.test.base.HttpClientTest
import java.util.function.Consumer
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.ResponseEntity
import org.springframework.http.client.ClientHttpRequestFactory
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.web.client.ResourceAccessException
import org.springframework.web.client.RestTemplate
import spock.lang.Shared

class SpringRestTemplateTest extends HttpClientTest implements AgentTestTrait {

  @Shared
  ClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory()
  @Shared
  RestTemplate restTemplate = new RestTemplate(factory)

  def setupSpec() {
    factory.connectTimeout = CONNECT_TIMEOUT_MS
  }

  @Override
  int doRequest(String method, URI uri, Map<String, String> headers = [:]) {
    try {
      def httpHeaders = new HttpHeaders()
      headers.each { httpHeaders.put(it.key, [it.value]) }
      def request = new HttpEntity<String>(httpHeaders)
      ResponseEntity<String> response = restTemplate.exchange(uri, HttpMethod.valueOf(method), request, String)
      return response.statusCode.value()
    } catch (ResourceAccessException exception) {
      throw exception.getCause()
    }
  }

  @Override
  void doRequestWithCallback(String method, URI uri, Map<String, String> headers = [:], Consumer<Integer> callback) {
    restTemplate.execute(uri, HttpMethod.valueOf(method), { request ->
      headers.forEach(request.getHeaders().&add)
    }, { response ->
      callback.accept(response.statusCode.value())
    })
  }

  @Override
  int maxRedirects() {
    20
  }

  @Override
  Integer statusOnRedirectError() {
    return 302
  }
}
