package com.alibaba.cloud.ai.example.deepresearch.researchpilot.ask;

/**
 * Counts the budget units used by a context adapter. The default implementation uses
 * UTF-8 bytes; a model-specific tokenizer can be supplied without changing the compaction
 * policy.
 */
@FunctionalInterface
public interface ContextTokenCounter {

	int count(String text);

	static ContextTokenCounter utf8Bytes() {
		return text -> ConversationContextService.budgetSize(text == null ? "" : text);
	}

	/**
	 * Uses the bundled tiktoken-compatible registry when the configured model is
	 * recognized. A tokenizer failure deliberately falls back to UTF-8 bytes so a context
	 * request never fails merely because a model name is vendor-specific.
	 */
	static ContextTokenCounter jtokkit(String model) {
		final var fallback = utf8Bytes();
		final var encoding = java.util.Optional.ofNullable(model).flatMap(name -> {
			try {
				return com.knuddels.jtokkit.Encodings.newLazyEncodingRegistry().getEncodingForModel(name);
			}
			catch (RuntimeException ignored) {
				return java.util.Optional.empty();
			}
		}).orElse(null);
		return text -> {
			if (encoding == null)
				return fallback.count(text);
			try {
				return encoding.countTokens(text == null ? "" : text);
			}
			catch (RuntimeException ignored) {
				return fallback.count(text);
			}
		};
	}

}
