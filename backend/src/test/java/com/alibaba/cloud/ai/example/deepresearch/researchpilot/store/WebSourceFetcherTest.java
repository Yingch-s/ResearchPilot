package com.alibaba.cloud.ai.example.deepresearch.researchpilot.store;

import java.net.InetAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.io.ByteArrayInputStream;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;

class WebSourceFetcherTest {

	@Test
	void redirectToPrivateAddressIsRejectedBeforeSecondHttpRequest() throws Exception {
		var client = org.mockito.Mockito.mock(org.apache.hc.client5.http.impl.classic.CloseableHttpClient.class);
		var builder = org.mockito.Mockito.mock(org.apache.hc.client5.http.impl.classic.HttpClientBuilder.class,
				org.mockito.Mockito.RETURNS_SELF);
		org.mockito.Mockito.when(builder.build()).thenReturn(client);
		org.mockito.Mockito.doAnswer(call -> {
			var response = new org.apache.hc.core5.http.message.BasicClassicHttpResponse(302);
			response.addHeader("Location", "http://127.0.0.1/internal");
			org.apache.hc.core5.http.io.HttpClientResponseHandler<?> handler = call.getArgument(1);
			return handler.handleResponse(response);
		})
			.when(client)
			.execute(org.mockito.ArgumentMatchers.any(org.apache.hc.core5.http.ClassicHttpRequest.class),
					org.mockito.ArgumentMatchers.any(org.apache.hc.core5.http.io.HttpClientResponseHandler.class));
		try (var clients = org.mockito.Mockito.mockStatic(org.apache.hc.client5.http.impl.classic.HttpClients.class)) {
			clients.when(org.apache.hc.client5.http.impl.classic.HttpClients::custom).thenReturn(builder);
			// Mockito static mocks are thread-local; call the synchronous path on this
			// thread.
			var method = WebSourceFetcher.class.getDeclaredMethod("fetchWithRedirects", String.class);
			method.setAccessible(true);
			var thrown = assertThrows(java.lang.reflect.InvocationTargetException.class,
					() -> method.invoke(new WebSourceFetcher(), "http://8.8.8.8/"));
			assertInstanceOf(ResponseStatusException.class, thrown.getCause());
			assertEquals(400, ((ResponseStatusException) thrown.getCause()).getStatusCode().value());
			org.mockito.Mockito.verify(client, org.mockito.Mockito.times(1))
				.execute(org.mockito.ArgumentMatchers.any(org.apache.hc.core5.http.ClassicHttpRequest.class),
						org.mockito.ArgumentMatchers.any(org.apache.hc.core5.http.io.HttpClientResponseHandler.class));
		}
	}

	@Test
	void blocksPrivateSpecialAndTunnelAddressesIncludingIpv6() throws Exception {
		for (String address : java.util.List.of("127.0.0.1", "10.1.1.1", "172.16.0.1", "192.168.1.1", "169.254.169.254",
				"100.64.0.1", "0.0.0.0", "224.0.0.1", "198.18.0.1", "192.0.2.1", "::1", "fc00::1", "fe80::1",
				"2001:db8::1", "2002:7f00:1::")) {
			assertFalse(WebSourceFetcher.isPublicAddress(InetAddress.getByName(address)), address);
		}
		assertTrue(WebSourceFetcher.isPublicAddress(InetAddress.getByName("8.8.8.8")));
		assertTrue(WebSourceFetcher.isPublicAddress(InetAddress.getByName("2606:4700:4700::1111")));
		assertThrows(ResponseStatusException.class, () -> new WebSourceFetcher().fetch("http://127.0.0.1/"));
	}

	@Test
	void rejectsCredentialsProtocolsAndUnsafeRedirectTargets() {
		for (String url : java.util.List.of("file:///etc/passwd", "http://localhost/", "https://example.com:9000/",
				"https://user:pass@example.com/", "http://host.internal/"))
			assertThrows(ResponseStatusException.class, () -> WebSourceFetcher.validateUri(URI.create(url)), url);
		WebSourceFetcher.validateUri(URI.create("https://example.com/research"));
	}

	@Test
	void readsAtMostLimitPlusOneByteAndRejectsOversizedStreamingBody() {
		byte[] large = new byte[WebSourceFetcher.MAX_BYTES * 2];
		var input = new ByteArrayInputStream(large);
		var error = assertThrows(ResponseStatusException.class, () -> WebSourceFetcher.readBounded(input));
		assertEquals(413, error.getStatusCode().value());
		assertEquals(large.length - WebSourceFetcher.MAX_BYTES - 1, input.available());
	}

	@Test
	void extractsArticleAndChineseTitleWithoutNavigationScriptsOrStyles() throws Exception {
		String html = "<html><head><meta charset='UTF-8'><title>中文研究 &amp; Evidence</title><style>bad style</style></head><body><nav>bad navigation</nav><main><h1>研究结论</h1><p>跨语言证据。</p><script>bad script</script></main><footer>bad footer</footer></body></html>";
		var snapshot = WebSourceFetcher.parse("https://example.com/article", "text/html",
				html.getBytes(StandardCharsets.UTF_8));
		assertEquals("中文研究 & Evidence", snapshot.title());
		assertTrue(snapshot.text().contains("研究结论"));
		assertTrue(snapshot.text().contains("跨语言证据"));
		assertFalse(snapshot.text().contains("bad"));
		assertTrue(snapshot.raw().contains("script"));
	}

	@Test
	void respectsDeclaredCharsetAndRejectsEmptyBody() throws Exception {
		var snapshot = WebSourceFetcher.parse("https://example.com/", "text/plain; charset=GB18030",
				"中文来源".getBytes(java.nio.charset.Charset.forName("GB18030")));
		assertEquals("中文来源", snapshot.text());
		assertThrows(ResponseStatusException.class, () -> WebSourceFetcher.parse("https://example.com/", "text/html",
				"<script>nothing</script>".getBytes(StandardCharsets.UTF_8)));
	}

}
