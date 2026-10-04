package com.alibaba.cloud.ai.example.deepresearch.researchpilot.store;

import java.io.IOException;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.*;
import org.apache.hc.client5.http.DnsResolver;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.core5.util.Timeout;
import org.jsoup.Jsoup;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

@Component
public class WebSourceFetcher {

	static final int MAX_BYTES = 2 * 1024 * 1024;

	private static final ExecutorService FETCHES = new ThreadPoolExecutor(0, 4, 30, TimeUnit.SECONDS,
			new SynchronousQueue<>(), runnable -> {
				Thread thread = new Thread(runnable, "web-source-fetch");
				thread.setDaemon(true);
				return thread;
			});

	public record Snapshot(String finalUrl, String title, String contentType, String raw, String text) {
	}

	public Snapshot fetch(String url) {
		Future<Snapshot> future;
		try {
			future = FETCHES.submit(() -> fetchWithRedirects(url));
		}
		catch (RejectedExecutionException e) {
			throw failure(HttpStatus.SERVICE_UNAVAILABLE, "网页抓取繁忙，请稍后重试");
		}
		try {
			return future.get(30, TimeUnit.SECONDS);
		}
		catch (TimeoutException e) {
			future.cancel(true);
			throw failure(HttpStatus.GATEWAY_TIMEOUT, "网页抓取超时");
		}
		catch (InterruptedException e) {
			future.cancel(true);
			Thread.currentThread().interrupt();
			throw failure(HttpStatus.SERVICE_UNAVAILABLE, "网页抓取已中断");
		}
		catch (ExecutionException e) {
			if (e.getCause() instanceof ResponseStatusException status)
				throw status;
			throw failure(HttpStatus.BAD_GATEWAY, "网页抓取失败");
		}
	}

	private Snapshot fetchWithRedirects(String url) throws IOException {
		URI uri;
		try {
			uri = URI.create(url.trim());
		}
		catch (Exception e) {
			throw failure(HttpStatus.BAD_REQUEST, "URL 格式无效");
		}
		for (int hop = 0; hop <= 5; hop++) {
			validateUri(uri);
			// Bind validated DNS answers to the actual connection, avoiding a second DNS
			// lookup.
			String host = uri.getHost().replace("[", "").replace("]", "");
			InetAddress[] addresses = InetAddress.getAllByName(host);
			for (var address : addresses) {
				if (!isPublicAddress(address))
					throw failure(HttpStatus.BAD_REQUEST, "只允许公网 HTTP 或 HTTPS 网页来源");
			}
			DnsResolver resolver = new DnsResolver() {
				@Override
				public InetAddress[] resolve(String target) throws UnknownHostException {
					if (!host.equalsIgnoreCase(target))
						throw new UnknownHostException(target);
					return addresses.clone();
				}

				@Override
				public String resolveCanonicalHostname(String target) {
					return target;
				}
			};
			var manager = PoolingHttpClientConnectionManagerBuilder.create().setDnsResolver(resolver).build();
			var config = RequestConfig.custom()
				.setConnectTimeout(Timeout.ofSeconds(8))
				.setResponseTimeout(Timeout.ofSeconds(8))
				.setConnectionRequestTimeout(Timeout.ofSeconds(2))
				.build();
			try (var client = HttpClients.custom()
				.setConnectionManager(manager)
				.setDefaultRequestConfig(config)
				.disableRedirectHandling()
				.disableAutomaticRetries()
				.disableContentCompression()
				.build()) {
				var request = new HttpGet(uri);
				request.setHeader("Accept", "text/html,text/plain;q=0.9");
				Hop result = client.execute(request, response -> {
					int status = response.getCode();
					if (List.of(301, 302, 303, 307, 308).contains(status)) {
						var location = response.getFirstHeader("Location");
						if (location == null)
							throw failure(HttpStatus.BAD_GATEWAY, "网页重定向缺少目标地址");
						response.close();
						return new Hop(location.getValue(), null, null);
					}
					if (status < 200 || status >= 300)
						throw failure(HttpStatus.BAD_GATEWAY, "网页返回状态 " + status);
					var entity = response.getEntity();
					if (entity == null)
						throw failure(HttpStatus.BAD_GATEWAY, "网页没有正文");
					String type = entity.getContentType() == null ? "text/plain" : entity.getContentType();
					if (!type.toLowerCase(Locale.ROOT).startsWith("text/html")
							&& !type.toLowerCase(Locale.ROOT).startsWith("text/plain"))
						throw failure(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "只支持 HTML 或纯文本网页，请下载其他格式后上传");
					if (entity.getContentLength() > MAX_BYTES)
						throw failure(HttpStatus.PAYLOAD_TOO_LARGE, "网页快照超过 2MB");
					return new Hop(null, type, readBounded(entity.getContent()));
				});
				if (result.redirect() != null) {
					if (hop == 5)
						throw failure(HttpStatus.BAD_GATEWAY, "网页重定向次数过多");
					uri = uri.resolve(result.redirect());
					continue;
				}
				return parse(uri.toString(), result.type(), result.bytes());
			}
		}
		throw failure(HttpStatus.BAD_GATEWAY, "网页抓取失败");
	}

	private record Hop(String redirect, String type, byte[] bytes) {
	}

	static byte[] readBounded(java.io.InputStream input) throws IOException {
		try (var out = new java.io.ByteArrayOutputStream()) {
			byte[] buffer = new byte[8192];
			int read;
			while ((read = input.read(buffer, 0, Math.min(buffer.length, MAX_BYTES + 1 - out.size()))) != -1) {
				if (Thread.currentThread().isInterrupted())
					throw new java.io.InterruptedIOException("Fetch cancelled");
				out.write(buffer, 0, read);
				if (out.size() > MAX_BYTES)
					throw failure(HttpStatus.PAYLOAD_TOO_LARGE, "网页快照超过 2MB");
			}
			return out.toByteArray();
		}
	}

	static void validateUri(URI uri) {
		String host = uri.getHost();
		if (!List.of("http", "https").contains(uri.getScheme()) || host == null || uri.getUserInfo() != null
				|| uri.toString().length() > 4000
				|| (uri.getPort() != -1 && uri.getPort() != 80 && uri.getPort() != 443)
				|| host.equalsIgnoreCase("localhost") || host.toLowerCase(Locale.ROOT).endsWith(".local")
				|| host.toLowerCase(Locale.ROOT).endsWith(".internal"))
			throw failure(HttpStatus.BAD_REQUEST, "只允许公网 HTTP 或 HTTPS 网页来源");
	}

	static boolean isPublicAddress(InetAddress address) {
		if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress()
				|| address.isSiteLocalAddress() || address.isMulticastAddress())
			return false;
		byte[] bytes = address.getAddress();
		int first = bytes[0] & 255;
		if (bytes.length == 4) {
			int second = bytes[1] & 255;
			return first != 0 && first < 224 && !(first == 100 && second >= 64 && second <= 127)
					&& !(first == 192 && (second == 0 || second == 2))
					&& !(first == 198 && (second == 18 || second == 19 || second == 51))
					&& !(first == 203 && second == 0);
		}
		// Only global-unicast IPv6, excluding documentation and translation/tunnel
		// prefixes.
		return (first & 0xe0) == 0x20 && !(first == 0x20 && (bytes[1] & 255) == 0x02)
				&& !(first == 0x20 && (bytes[1] & 255) == 0x01
						&& ((bytes[2] & 255) < 2 || ((bytes[2] & 255) == 0x0d && (bytes[3] & 255) == 0xb8)));
	}

	static Snapshot parse(String url, String type, byte[] bytes) throws IOException {
		if (type.toLowerCase(Locale.ROOT).startsWith("text/plain")) {
			String text = new String(bytes,
					org.apache.hc.core5.http.ContentType.parse(type).getCharset(StandardCharsets.UTF_8));
			if (text.isBlank())
				throw failure(HttpStatus.UNPROCESSABLE_ENTITY, "网页没有可用正文");
			return new Snapshot(url, URI.create(url).getHost(), type, text, text);
		}
		var contentType = org.apache.hc.core5.http.ContentType.parse(type);
		var document = Jsoup.parse(new java.io.ByteArrayInputStream(bytes),
				contentType.getCharset() == null ? null : contentType.getCharset().name(), url);
		String raw = document.outerHtml();
		String title = document.title().isBlank() ? URI.create(url).getHost() : document.title();
		document.select("script,style,nav,header,footer,aside,form,noscript,iframe").remove();
		var main = document.selectFirst("article,main,[role=main]");
		String text = (main == null ? document.body() : main).wholeText().trim();
		if (text.isBlank())
			throw failure(HttpStatus.UNPROCESSABLE_ENTITY, "网页没有可用正文，可能需要登录或运行脚本");
		return new Snapshot(url, title, type, raw, text);
	}

	private static ResponseStatusException failure(HttpStatus status, String message) {
		return new ResponseStatusException(status, message);
	}

}
