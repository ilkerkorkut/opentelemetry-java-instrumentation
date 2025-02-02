/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.test.server.http

import static io.opentelemetry.api.trace.SpanKind.SERVER
import static io.opentelemetry.instrumentation.test.server.http.HttpServletRequestExtractAdapter.GETTER

import io.opentelemetry.api.GlobalOpenTelemetry
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.SpanBuilder
import io.opentelemetry.api.trace.Tracer
import io.opentelemetry.context.Context
import io.opentelemetry.instrumentation.test.asserts.InMemoryExporterAssert
import io.opentelemetry.instrumentation.test.asserts.TraceAssert
import io.opentelemetry.instrumentation.test.utils.PortUtils
import io.opentelemetry.sdk.trace.data.SpanData
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import javax.servlet.ServletException
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse
import org.eclipse.jetty.http.HttpMethods
import org.eclipse.jetty.server.Handler
import org.eclipse.jetty.server.Request
import org.eclipse.jetty.server.Server
import org.eclipse.jetty.server.handler.AbstractHandler
import org.eclipse.jetty.server.handler.HandlerList

class TestHttpServer implements AutoCloseable {

  private static final Tracer tracer = GlobalOpenTelemetry.getTracer("test")

  static TestHttpServer httpServer(@DelegatesTo(value = TestHttpServer, strategy = Closure.DELEGATE_FIRST) Closure spec) {

    def server = new TestHttpServer()
    def clone = (Closure) spec.clone()
    clone.delegate = server
    clone.resolveStrategy = Closure.DELEGATE_FIRST
    clone(server)
    server.start()
    return server
  }

  private final Server internalServer
  private HandlersSpec handlers


  private URI address
  private final AtomicReference<HandlerApi.RequestApi> last = new AtomicReference<>()

  private TestHttpServer() {
    internalServer = new Server(0)
    internalServer.connectors.each {
      it.setHost('localhost')
    }
  }

  def start() {
    if (internalServer.isStarted()) {
      return
    }

    assert handlers != null: "handlers must be defined"
    def handlerList = new HandlerList()
    handlerList.handlers = handlers.configured
    internalServer.handler = handlerList
    internalServer.start()
    // set after starting, otherwise two callbacks get added.
    internalServer.stopAtShutdown = true

    def port = internalServer.connectors[0].localPort
    address = new URI("http://localhost:${port}")

    PortUtils.waitForPortToOpen(port, 20, TimeUnit.SECONDS)
    System.out.println("Started server $this on port ${address.getPort()}")
    return this
  }

  def stop() {
    System.out.println("Stopping server $this on port $address.port")
    internalServer.stop()
    return this
  }

  void close() {
    stop()
  }

  URI getAddress() {
    return address
  }

  def getLastRequest() {
    return last.get()
  }

  void handlers(@DelegatesTo(value = HandlersSpec, strategy = Closure.DELEGATE_FIRST) Closure spec) {
    assert handlers == null: "handlers already defined"
    handlers = new HandlersSpec()

    def clone = (Closure) spec.clone()
    clone.delegate = handlers
    clone.resolveStrategy = Closure.DELEGATE_FIRST
    clone(handlers)
  }

  static distributedRequestTrace(InMemoryExporterAssert traces, int index, SpanData parentSpan = null) {
    traces.trace(index, 1) {
      distributedRequestSpan(it, 0, parentSpan)
    }
  }

  static distributedRequestSpan(TraceAssert trace, int index, SpanData parentSpan = null) {
    trace.span(index) {
      name "test-http-server"
      kind SERVER
      errored false
      if (parentSpan == null) {
        hasNoParent()
      } else {
        childOf(parentSpan)
      }
      attributes {
      }
    }
  }

  private class HandlersSpec {

    List<Handler> configured = []

    void get(String path, @DelegatesTo(value = HandlerApi, strategy = Closure.DELEGATE_FIRST) Closure<Void> spec) {
      assert path != null
      configured << new HandlerSpec(HttpMethods.GET, path, spec)
    }

    void post(String path, @DelegatesTo(value = HandlerApi, strategy = Closure.DELEGATE_FIRST) Closure<Void> spec) {
      assert path != null
      configured << new HandlerSpec(HttpMethods.POST, path, spec)
    }

    void put(String path, @DelegatesTo(value = HandlerApi, strategy = Closure.DELEGATE_FIRST) Closure<Void> spec) {
      assert path != null
      configured << new HandlerSpec(HttpMethods.PUT, path, spec)
    }

    void prefix(String path, @DelegatesTo(value = HandlerApi, strategy = Closure.DELEGATE_FIRST) Closure<Void> spec) {
      configured << new PrefixHandlerSpec(path, spec)
    }

    void all(@DelegatesTo(value = HandlerApi, strategy = Closure.DELEGATE_FIRST) Closure<Void> spec) {
      configured << new AllHandlerSpec(spec)
    }
  }

  private class HandlerSpec extends AllHandlerSpec {

    private final String method
    private final String path

    private HandlerSpec(String method, String path, Closure<Void> spec) {
      super(spec)
      this.method = method
      this.path = path.startsWith("/") ? path : "/" + path
    }

    @Override
    void handle(String target, Request baseRequest, HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
      if (request.method == method && target == path) {
        send(baseRequest, response)
      }
    }
  }

  private class PrefixHandlerSpec extends AllHandlerSpec {

    private final String prefix

    private PrefixHandlerSpec(String prefix, Closure<Void> spec) {
      super(spec)
      this.prefix = prefix.startsWith("/") ? prefix : "/" + prefix
    }

    @Override
    void handle(String target, Request baseRequest, HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
      if (target.startsWith(prefix)) {
        send(baseRequest, response)
      }
    }
  }

  private class AllHandlerSpec extends AbstractHandler {
    protected final Closure<Void> spec

    protected AllHandlerSpec(Closure<Void> spec) {
      this.spec = spec
    }

    @Override
    void handle(String target, Request baseRequest, HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
      send(baseRequest, response)
    }

    protected void send(Request baseRequest, HttpServletResponse response) {
      def api = new HandlerApi(baseRequest, response)
      last.set(api.request)

      def clone = (Closure) spec.clone()
      clone.delegate = api
      clone.resolveStrategy = Closure.DELEGATE_FIRST

      try {
        clone(api)
      } catch (Exception e) {
        api.response.status(500).send(e.getMessage())
      }
    }
  }

  static class HandlerApi {
    private final Request req
    private final HttpServletResponse resp

    private HandlerApi(Request request, HttpServletResponse response) {
      this.req = request
      this.resp = response
    }

    def getRequest() {
      return new RequestApi()
    }


    def getResponse() {
      return new ResponseApi()
    }

    void redirect(String uri) {
      resp.sendRedirect(uri)
      req.handled = true
    }

    void handleDistributedRequest() {
      boolean isTestServer = true
      if (request.getHeader("is-test-server") != null) {
        isTestServer = Boolean.parseBoolean(request.getHeader("is-test-server"))
      }
      if (isTestServer) {
        final SpanBuilder spanBuilder = tracer.spanBuilder("test-http-server").setSpanKind(SERVER)
        // using Context.root() to avoid inheriting any potentially leaked context here
        spanBuilder.setParent(GlobalOpenTelemetry.getPropagators().getTextMapPropagator().extract(Context.root(), req, GETTER))

        def traceRequestId = request.getHeader("test-request-id")
        if (traceRequestId != null) {
          spanBuilder.setAttribute("test.request.id", Integer.parseInt(traceRequestId))
        }

        final Span span = spanBuilder.startSpan()
        span.end()
      }
    }

    class RequestApi {
      def path = req.pathInfo
      def headers = new Headers(req)
      def contentLength = req.contentLength
      def contentType = req.contentType?.split(";")

      def body = req.inputStream.bytes

      def getPath() {
        return path
      }

      def getContentLength() {
        return contentLength
      }

      def getContentType() {
        return contentType ? contentType[0] : null
      }

      def getHeaders() {
        return headers
      }

      String getHeader(String header) {
        return headers[header]
      }

      def getBody() {
        return body
      }

      def getText() {
        return new String(body)
      }
    }

    class ResponseApi {
      private int status = 200
      private String id

      ResponseApi status(int status) {
        this.status = status
        return this
      }

      ResponseApi id(String id) {
        this.id = id
        return this
      }

      void send() {
        assert !req.handled
        req.contentType = "text/plain;charset=utf-8"
        resp.status = status
        resp.setHeader("test-request-id", id)
        req.handled = true
      }

      void send(String body) {
        assert body != null

        send()
        resp.setContentLength(body.bytes.length)
        resp.writer.print(body)
      }
    }

    static class Headers {
      private final Map<String, String> headers

      private Headers(Request request) {
        this.headers = [:]
        request.getHeaderNames().each {
          headers.put(it, request.getHeader(it))
        }
      }

      def get(String header) {
        return headers[header]
      }
    }
  }
}
