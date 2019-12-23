package com.preapm.agent.plugin.interceptor.filter;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.preapm.sdk.zipkin.ZipkinClientContext;
import com.preapm.sdk.zipkin.util.InetAddressUtils;
import com.preapm.sdk.zipkin.util.TraceKeys;

import okhttp3.HttpUrl;
import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Request.Builder;
import okhttp3.Response;
import zipkin.Endpoint;
import zipkin.Span;

public class OkHttpFilter implements Interceptor {

	private static final Logger logger = LoggerFactory.getLogger(OkHttpFilter.class);

	@Override
	public Response intercept(Interceptor.Chain chain) throws IOException {
		Request originalRequest = chain.request();
		Builder newBuilder = originalRequest.newBuilder();
		int ipv4 = InetAddressUtils.localIpv4();
		Endpoint endpoint = Endpoint.builder().serviceName(ZipkinClientContext.serverName).ipv4(ipv4).build();
		HttpUrl url = originalRequest.url();
		ZipkinClientContext.getClient().startSpan(url.url().toString());
		ZipkinClientContext.getClient().sendAnnotation(TraceKeys.CLIENT_SEND,endpoint);
		try {
			Span span = ZipkinClientContext.getClient().getSpan();
			if (span != null) {
				logger.debug("放入traceId到http请求头：" + Long.toHexString(span.traceId));
				logger.debug("放入SPAN_ID到http请求头：" + Long.toHexString(span.id));
				newBuilder.header(com.preapm.sdk.zipkin.util.TraceKeys.TRACE_ID, Long.toHexString(span.traceId));
				newBuilder.header(com.preapm.sdk.zipkin.util.TraceKeys.SPAN_ID, Long.toHexString(span.id));
			} else {
				logger.debug("ZipkinClientContext.getClient().getSpan() is null");
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		Request request = newBuilder.build();
		long t1 = System.nanoTime();
		logger.info(
				String.format("Sending request %s on %s%n%s", request.url(), chain.connection(), request.headers()));
		Response response = chain.proceed(request);
		ZipkinClientContext.getClient().sendAnnotation(TraceKeys.CLIENT_RECV);
		ZipkinClientContext.getClient().sendBinaryAnnotation(com.preapm.sdk.zipkin.util.TraceKeys.PRE_NAME, "okhttps", endpoint);
		long t2 = System.nanoTime();
		logger.info(String.format("Received response for %s in %.1fms%n%s", response.request().url(), (t2 - t1) / 1e6d,
				response.headers()));

		return response;
	}
}