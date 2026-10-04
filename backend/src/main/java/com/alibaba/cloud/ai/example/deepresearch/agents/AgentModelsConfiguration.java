/*
 * Copyright 2024-2025 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.alibaba.cloud.ai.example.deepresearch.agents;

import com.alibaba.cloud.ai.example.deepresearch.repository.ModelParamRepository;
import com.alibaba.cloud.ai.example.deepresearch.repository.ModelParamRepositoryImpl;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.util.Assert;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

/**
 * ResearchPilot 改造（2026-10-01）：模型客户端从 DashScope 专用实现切换为 OpenAI
 * 兼容协议（{@link OpenAiChatModel}），base-url 可指向火山方舟 / vLLM / 任何 OpenAI-compatible 端点，亦兼容
 * DashScope 的 compatible-mode。
 *
 * <p>
 * 改造边界：本类是全项目唯一的模型客户端构造点（其余代码消费 ChatClient/ChatModel 抽象）。原 DashScopeChatModel 构造逻辑见 git
 * 历史（fork 基点 7f009ad）。
 *
 * <p>
 * This configuration class will configure the corresponding ChatClientBuilder tool
 * according to the `model-config.json` file to provide multi-agent capabilities for
 * deepsearch. different {@link ChatClient.Builder} will be created according to the name
 * column. for example: `researchChatClientBuilder`
 *
 * @author ViliamSun
 * @since 0.1.0
 */
@Configuration
public class AgentModelsConfiguration implements InitializingBean {

	private static final String BEAN_NAME_SUFFIX = "ChatClientBuilder";

	private final List<ModelParamRepositoryImpl.AgentModel> models;

	private final String apiKey;

	private final String baseUrl;

	private final String modelNameOverride;

	private final ToolCallingManager toolCallingManager;

	private final BiConsumer<String, ChatModel> registerConsumer;

	public AgentModelsConfiguration(ModelParamRepository modelParamRepository, ConfigurableBeanFactory beanFactory,
			@Value("${researchpilot.model.api-key:}") String apiKey,
			@Value("${researchpilot.model.base-url:https://ark.cn-beijing.volces.com/api/v3}") String baseUrl,
			@Value("${researchpilot.model.model-name:}") String modelNameOverride,
			ToolCallingManager toolCallingManager) {
		this.toolCallingManager = toolCallingManager;
		Assert.notNull(modelParamRepository, "ModelParamRepository must not be null");
		this.apiKey = apiKey;
		this.baseUrl = baseUrl;
		this.modelNameOverride = modelNameOverride == null ? "" : modelNameOverride.trim();
		// load models from the repository
		this.models = modelParamRepository.loadModels();
		this.registerConsumer = (key, value) -> beanFactory.registerSingleton(key.concat(BEAN_NAME_SUFFIX),
				ChatClient.create(value).mutate());
	}

	/**
	 * Creates a map of agent model names to their corresponding {@link ChatModel}
	 * instances.
	 * @return a map where the key is the model name and the value is the ChatModel
	 * instance
	 */
	private Map<String, ChatModel> agentModels() {
		return models.stream()
			.filter(Objects::nonNull)
			.collect(Collectors.toMap(ModelParamRepositoryImpl.AgentModel::name,
					model -> OpenAiChatModel.builder()
						.openAiApi(OpenAiApi.builder().baseUrl(baseUrl).apiKey(apiKey).build())
						.toolCallingManager(toolCallingManager)
						.defaultOptions(OpenAiChatOptions.builder()
							.model(modelNameOverride.isBlank() ? model.modelName() : modelNameOverride)
							.temperature(0.7)
							.build())
						.build(),
					(existing, replacement) -> existing) // On duplicate key, keep the
			);
	}

	/**
	 * 注册一个主 ChatModel Bean：给直接注入 ChatModel 的服务 （QuestionClassifierService /
	 * SearchPlatformSelectionService 等）兜底， 使用 coordinator 对应的模型。
	 */
	@Bean
	@Primary
	public ChatModel primaryChatModel() {
		return agentModels().getOrDefault("coordinator", agentModels().values().iterator().next());
	}

	@Override
	public void afterPropertiesSet() throws Exception {
		this.agentModels().forEach(registerConsumer);
	}

}
