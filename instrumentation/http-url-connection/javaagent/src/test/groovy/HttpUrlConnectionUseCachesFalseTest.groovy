/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import io.opentelemetry.api.trace.Span
import io.opentelemetry.instrumentation.test.AgentTestTrait
import io.opentelemetry.instrumentation.test.base.HttpClientTest

class HttpUrlConnectionUseCachesFalseTest extends HttpClientTest implements AgentTestTrait {

  @Override
  int doRequest(String method, URI uri, Map<String, String> headers) {
    HttpURLConnection connection = uri.toURL().openConnection()
    try {
      connection.setRequestMethod(method)
      headers.each { connection.setRequestProperty(it.key, it.value) }
      connection.setRequestProperty("Connection", "close")
      connection.useCaches = false
      connection.connectTimeout = CONNECT_TIMEOUT_MS
      def parentSpan = Span.current()
      def stream = connection.inputStream
      assert Span.current() == parentSpan
      stream.readLines()
      stream.close()
      return connection.getResponseCode()
    } finally {
      connection.disconnect()
    }
  }

  @Override
  int maxRedirects() {
    20
  }

  @Override
  Integer statusOnRedirectError() {
    return 302
  }

  @Override
  boolean testCallback() {
    return false
  }
}
