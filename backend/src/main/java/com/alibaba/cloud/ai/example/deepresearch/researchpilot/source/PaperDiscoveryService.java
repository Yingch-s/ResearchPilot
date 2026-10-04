package com.alibaba.cloud.ai.example.deepresearch.researchpilot.source;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Read-only Crossref discovery. Results are candidates until the user explicitly imports
 * one.
 */
@Service
public class PaperDiscoveryService {

	private static final Logger logger = LoggerFactory.getLogger(PaperDiscoveryService.class);

	private final RestClient client;

	private final ObjectMapper mapper = new ObjectMapper();

	public PaperDiscoveryService(RestClient client) {
		this.client = client;
	}

	public record Candidate(String title, String doi, String url, String authors, Integer year, String source) {
	}

	public List<Candidate> discover(String input, int limit) {
		if (input == null || input.isBlank()) {
			return List.of();
		}
		int rows = Math.max(1, Math.min(10, limit));
		try {
			String doi = normalizeDoi(input);
			String endpoint = doi == null
					? "https://api.crossref.org/works?query.bibliographic="
							+ URLEncoder.encode(input.trim(), StandardCharsets.UTF_8) + "&rows=" + rows
					: "https://api.crossref.org/works/" + URLEncoder.encode(doi, StandardCharsets.UTF_8);
			JsonNode message = mapper.readTree(client.get().uri(endpoint).retrieve().body(String.class))
				.path("message");
			if (doi != null) {
				return message.isMissingNode() ? List.of() : List.of(toCandidate(message));
			}
			List<Candidate> candidates = new ArrayList<>();
			message.path("items").forEach(item -> candidates.add(toCandidate(item)));
			return candidates.stream().filter(candidate -> !candidate.title().isBlank()).limit(rows).toList();
		}
		catch (Exception error) {
			logger.warn("Crossref 论文发现失败: {}", error.getMessage());
			return List.of();
		}
	}

	static String normalizeDoi(String input) {
		String value = input.trim().replaceFirst("(?i)^https?://(dx\\.)?doi\\.org/", "");
		return value.matches("10\\.\\d{4,9}/\\S+") ? value : null;
	}

	private Candidate toCandidate(JsonNode item) {
		StringBuilder authors = new StringBuilder();
		item.path("author").forEach(author -> {
			if (authors.length() > 0) {
				authors.append("; ");
			}
			authors.append(author.path("given").asText("")).append(' ').append(author.path("family").asText(""));
		});
		int year = item.path("published-print")
			.path("date-parts")
			.path(0)
			.path(0)
			.asInt(item.path("published-online").path("date-parts").path(0).path(0).asInt(0));
		return new Candidate(item.path("title").path(0).asText(""), item.path("DOI").asText(""),
				item.path("URL").asText(""), authors.toString().trim(), year == 0 ? null : year, "CROSSREF");
	}

}
