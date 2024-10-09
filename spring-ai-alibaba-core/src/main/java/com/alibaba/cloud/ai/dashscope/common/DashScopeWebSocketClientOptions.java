package com.alibaba.cloud.ai.dashscope.common;

public class DashScopeWebSocketClientOptions {

	private String baseUrl;

	private String apiKey;

	private String workSpaceId;

	private boolean stream;

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

	public String getWorkSpaceId() {
		return workSpaceId;
	}

	public void setWorkSpaceId(String workSpaceId) {
		this.workSpaceId = workSpaceId;
	}

	public boolean isStream() {
		return stream;
	}

	public void setStream(boolean stream) {
		this.stream = stream;
	}

	public static Builder builder() {
		return new Builder();
	}

	public static class Builder {

		protected DashScopeWebSocketClientOptions options;

		public Builder() {
			this.options = new DashScopeWebSocketClientOptions();
		}

		public Builder(DashScopeWebSocketClientOptions options) {
			this.options = options;
		}

		public Builder withBaseUrl(String baseUrl) {
			options.setBaseUrl(baseUrl);
			return this;
		}

		public Builder withApiKey(String apiKey) {
			options.setApiKey(apiKey);
			return this;
		}

		public Builder withWorkSpaceId(String workSpaceId) {
			options.setWorkSpaceId(workSpaceId);
			return this;
		}

		public Builder withStream(boolean stream) {
			options.setStream(stream);
			return this;
		}

		public DashScopeWebSocketClientOptions build() {
			return options;
		}

	}

}
