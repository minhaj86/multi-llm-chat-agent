package com.example.aichat.infrastructure.llm;

import com.example.aichat.common.Secrets;
import com.example.aichat.domain.llm.ProviderKind;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Configuration of one provider instance. Nothing here is hard-coded in adapters: URLs, models, credentials,
 * timeouts, token limits and vendor specific request parameters all come from configuration (and therefore from
 * environment variables).
 */
public class ProviderProperties {

    /** Default for the runtime "enabled" flag (administrators can override it). */
    private boolean enabled = true;
    /** Adapter type, e.g. {@code openai} (Responses API) or {@code openai-compatible} (Chat Completions). */
    private String type;
    /** Optional model family selecting a specialised adapter, e.g. {@code qwen} or {@code llama}. */
    private String family;
    /**
     * Inference engine behind an OpenAI-compatible endpoint ({@code generic}, {@code ollama}, {@code vllm},
     * {@code llama-cpp}, {@code lm-studio}, {@code localai}, {@code sglang}). Only used to pick sensible
     * defaults for engine specific request parameters.
     */
    private String engine = "generic";
    private String displayName;
    /** {@code cloud} or {@code local}; defaults depend on the adapter type. */
    private ProviderKind kind;
    private String baseUrl;
    private String apiKey;
    /** OpenAI organisation / project headers (optional). */
    private String organization;
    private String project;
    private String defaultModel;
    /** Optional allow-list of models; when empty, models are discovered via {@code GET /models}. */
    private List<String> models = new ArrayList<>();
    private Double temperature;
    private Integer maxTokens;
    private Duration connectTimeout = Duration.ofSeconds(5);
    /** Overall timeout for non-streamed calls and the maximum idle time between streamed chunks. */
    private Duration timeout = Duration.ofSeconds(120);
    private Duration healthTimeout = Duration.ofSeconds(5);
    private Retry retry = new Retry();
    private CircuitBreaker circuitBreaker = new CircuitBreaker();
    /** Ask OpenAI-compatible servers for token usage in streams ({@code stream_options.include_usage}). */
    private boolean streamUsage = true;
    /** Name of the output token limit parameter ({@code max_tokens} or {@code max_completion_tokens}). */
    private String maxTokensParameter = "max_tokens";
    /** Additional JSON fields merged into every request body (vendor specific options). */
    private Map<String, Object> extraBody = new LinkedHashMap<>();
    /** Request fields used to disable reasoning; overrides family/engine defaults when set. */
    private Map<String, Object> reasoningDisabledParams = new LinkedHashMap<>();
    /** Additional HTTP headers sent with every request. */
    private Map<String, String> headers = new LinkedHashMap<>();
    /** OpenAI: whether responses may be stored by OpenAI ({@code store}). */
    private boolean store = false;
    /** OpenAI: model prefixes treated as reasoning models (no temperature, reasoning effort supported). */
    private List<String> reasoningModelPrefixes = new ArrayList<>(List.of("o1", "o3", "o4", "gpt-5", "gpt-6"));
    /** OpenAI: reasoning effort for normal requests (empty = model default). */
    private String reasoningEffort;
    /** OpenAI: reasoning effort when the application asks for a fast answer (titles, summaries). */
    private String reasoningDisabledEffort = "low";
    /** OpenAI: reasoning summary mode ({@code auto}, {@code concise}, {@code detailed}); empty = none. */
    private String reasoningSummary;
    /** Prices per million tokens, matched by longest model prefix. */
    private List<ModelPricing> pricing = new ArrayList<>();
    /** Fallback price when no {@link #pricing} entry matches. */
    private ModelPricing defaultPricing;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getFamily() {
        return family;
    }

    public void setFamily(String family) {
        this.family = family;
    }

    public String getEngine() {
        return engine;
    }

    public void setEngine(String engine) {
        this.engine = engine;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public ProviderKind getKind() {
        return kind;
    }

    public void setKind(ProviderKind kind) {
        this.kind = kind;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getOrganization() {
        return organization;
    }

    public void setOrganization(String organization) {
        this.organization = organization;
    }

    public String getProject() {
        return project;
    }

    public void setProject(String project) {
        this.project = project;
    }

    public String getDefaultModel() {
        return defaultModel;
    }

    public void setDefaultModel(String defaultModel) {
        this.defaultModel = defaultModel;
    }

    public List<String> getModels() {
        return models;
    }

    public void setModels(List<String> models) {
        this.models = models == null ? new ArrayList<>() : models;
    }

    public Double getTemperature() {
        return temperature;
    }

    public void setTemperature(Double temperature) {
        this.temperature = temperature;
    }

    public Integer getMaxTokens() {
        return maxTokens;
    }

    public void setMaxTokens(Integer maxTokens) {
        this.maxTokens = maxTokens;
    }

    public Duration getConnectTimeout() {
        return connectTimeout;
    }

    public void setConnectTimeout(Duration connectTimeout) {
        this.connectTimeout = connectTimeout;
    }

    public Duration getTimeout() {
        return timeout;
    }

    public void setTimeout(Duration timeout) {
        this.timeout = timeout;
    }

    public Duration getHealthTimeout() {
        return healthTimeout;
    }

    public void setHealthTimeout(Duration healthTimeout) {
        this.healthTimeout = healthTimeout;
    }

    public Retry getRetry() {
        return retry;
    }

    public void setRetry(Retry retry) {
        this.retry = retry;
    }

    public CircuitBreaker getCircuitBreaker() {
        return circuitBreaker;
    }

    public void setCircuitBreaker(CircuitBreaker circuitBreaker) {
        this.circuitBreaker = circuitBreaker;
    }

    public boolean isStreamUsage() {
        return streamUsage;
    }

    public void setStreamUsage(boolean streamUsage) {
        this.streamUsage = streamUsage;
    }

    public String getMaxTokensParameter() {
        return maxTokensParameter;
    }

    public void setMaxTokensParameter(String maxTokensParameter) {
        this.maxTokensParameter = maxTokensParameter;
    }

    public Map<String, Object> getExtraBody() {
        return extraBody;
    }

    public void setExtraBody(Map<String, Object> extraBody) {
        this.extraBody = extraBody == null ? new LinkedHashMap<>() : extraBody;
    }

    public Map<String, Object> getReasoningDisabledParams() {
        return reasoningDisabledParams;
    }

    public void setReasoningDisabledParams(Map<String, Object> reasoningDisabledParams) {
        this.reasoningDisabledParams = reasoningDisabledParams == null ? new LinkedHashMap<>() : reasoningDisabledParams;
    }

    public Map<String, String> getHeaders() {
        return headers;
    }

    public void setHeaders(Map<String, String> headers) {
        this.headers = headers == null ? new LinkedHashMap<>() : headers;
    }

    public boolean isStore() {
        return store;
    }

    public void setStore(boolean store) {
        this.store = store;
    }

    public List<String> getReasoningModelPrefixes() {
        return reasoningModelPrefixes;
    }

    public void setReasoningModelPrefixes(List<String> reasoningModelPrefixes) {
        this.reasoningModelPrefixes = reasoningModelPrefixes == null ? new ArrayList<>() : reasoningModelPrefixes;
    }

    public String getReasoningEffort() {
        return reasoningEffort;
    }

    public void setReasoningEffort(String reasoningEffort) {
        this.reasoningEffort = reasoningEffort;
    }

    public String getReasoningDisabledEffort() {
        return reasoningDisabledEffort;
    }

    public void setReasoningDisabledEffort(String reasoningDisabledEffort) {
        this.reasoningDisabledEffort = reasoningDisabledEffort;
    }

    public String getReasoningSummary() {
        return reasoningSummary;
    }

    public void setReasoningSummary(String reasoningSummary) {
        this.reasoningSummary = reasoningSummary;
    }

    public List<ModelPricing> getPricing() {
        return pricing;
    }

    public void setPricing(List<ModelPricing> pricing) {
        this.pricing = pricing == null ? new ArrayList<>() : pricing;
    }

    public ModelPricing getDefaultPricing() {
        return defaultPricing;
    }

    public void setDefaultPricing(ModelPricing defaultPricing) {
        this.defaultPricing = defaultPricing;
    }

    @Override
    public String toString() {
        return "ProviderProperties{type=" + type + ", family=" + family + ", engine=" + engine + ", baseUrl=" + baseUrl
                + ", apiKey=" + Secrets.mask(apiKey) + ", defaultModel=" + defaultModel + ", enabled=" + enabled + "}";
    }

    /** Retry policy. Only failures where the request was not processed by the provider are retried. */
    public static class Retry {
        /** Total attempts including the first call. */
        private int maxAttempts = 2;
        private Duration initialBackoff = Duration.ofMillis(500);
        private double backoffMultiplier = 2.0;
        /** Rate-limited calls are only retried if the provider asks to wait at most this long. */
        private Duration maxRateLimitWait = Duration.ofSeconds(5);

        public int getMaxAttempts() {
            return maxAttempts;
        }

        public void setMaxAttempts(int maxAttempts) {
            this.maxAttempts = maxAttempts;
        }

        public Duration getInitialBackoff() {
            return initialBackoff;
        }

        public void setInitialBackoff(Duration initialBackoff) {
            this.initialBackoff = initialBackoff;
        }

        public double getBackoffMultiplier() {
            return backoffMultiplier;
        }

        public void setBackoffMultiplier(double backoffMultiplier) {
            this.backoffMultiplier = backoffMultiplier;
        }

        public Duration getMaxRateLimitWait() {
            return maxRateLimitWait;
        }

        public void setMaxRateLimitWait(Duration maxRateLimitWait) {
            this.maxRateLimitWait = maxRateLimitWait;
        }
    }

    /** Circuit breaker opened by connection failures, timeouts and 5xx responses. */
    public static class CircuitBreaker {
        private float failureRateThreshold = 50f;
        private int slidingWindowSize = 20;
        private int minimumNumberOfCalls = 5;
        private Duration waitDurationInOpenState = Duration.ofSeconds(30);
        private int permittedCallsInHalfOpenState = 2;

        public float getFailureRateThreshold() {
            return failureRateThreshold;
        }

        public void setFailureRateThreshold(float failureRateThreshold) {
            this.failureRateThreshold = failureRateThreshold;
        }

        public int getSlidingWindowSize() {
            return slidingWindowSize;
        }

        public void setSlidingWindowSize(int slidingWindowSize) {
            this.slidingWindowSize = slidingWindowSize;
        }

        public int getMinimumNumberOfCalls() {
            return minimumNumberOfCalls;
        }

        public void setMinimumNumberOfCalls(int minimumNumberOfCalls) {
            this.minimumNumberOfCalls = minimumNumberOfCalls;
        }

        public Duration getWaitDurationInOpenState() {
            return waitDurationInOpenState;
        }

        public void setWaitDurationInOpenState(Duration waitDurationInOpenState) {
            this.waitDurationInOpenState = waitDurationInOpenState;
        }

        public int getPermittedCallsInHalfOpenState() {
            return permittedCallsInHalfOpenState;
        }

        public void setPermittedCallsInHalfOpenState(int permittedCallsInHalfOpenState) {
            this.permittedCallsInHalfOpenState = permittedCallsInHalfOpenState;
        }
    }

    /** Price per million tokens in USD. */
    public static class ModelPricing {
        /** Model id or prefix (longest prefix wins). */
        private String model;
        private BigDecimal inputPerMillion;
        private BigDecimal outputPerMillion;

        public ModelPricing() {
        }

        public ModelPricing(String model, BigDecimal inputPerMillion, BigDecimal outputPerMillion) {
            this.model = model;
            this.inputPerMillion = inputPerMillion;
            this.outputPerMillion = outputPerMillion;
        }

        public String getModel() {
            return model;
        }

        public void setModel(String model) {
            this.model = model;
        }

        public BigDecimal getInputPerMillion() {
            return inputPerMillion;
        }

        public void setInputPerMillion(BigDecimal inputPerMillion) {
            this.inputPerMillion = inputPerMillion;
        }

        public BigDecimal getOutputPerMillion() {
            return outputPerMillion;
        }

        public void setOutputPerMillion(BigDecimal outputPerMillion) {
            this.outputPerMillion = outputPerMillion;
        }
    }
}
