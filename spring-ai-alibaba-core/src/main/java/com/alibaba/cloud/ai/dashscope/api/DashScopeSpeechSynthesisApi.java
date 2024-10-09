package com.alibaba.cloud.ai.dashscope.api;

import com.alibaba.dashscope.audio.tts.SpeechSynthesizer;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.reactive.socket.client.ReactorNettyWebSocketClient;
import org.springframework.web.reactive.socket.client.WebSocketClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.nio.ByteBuffer;
import org.springframework.web.reactive.socket.WebSocketMessage;
import reactor.netty.http.client.HttpClient;

import static com.alibaba.cloud.ai.dashscope.common.DashScopeApiConstants.*;

public class DashScopeSpeechSynthesisApi {

	private static final Logger logger = LoggerFactory.getLogger(DashScopeSpeechSynthesisApi.class);

	private final WebSocketClient webSocketClient;

	public DashScopeSpeechSynthesisApi(String apiKey) {
		this(apiKey, null);
	}

	public DashScopeSpeechSynthesisApi(String apiKey, String workSpaceId) {
		this(apiKey, workSpaceId, DEFAULT_WEBSOCKET_URL);
	}

	public DashScopeSpeechSynthesisApi(String apiKey, String workSpaceId, String websocketUrl) {
		HttpClient httpClient = HttpClient.create()
			.headers(ApiUtils.getWebsocketJsonContentHeaders(apiKey, workSpaceId))
			.secure();

		this.webSocketClient = new ReactorNettyWebSocketClient(httpClient);
	}

	// @formatter:off
	// TODO
	@JsonInclude(JsonInclude.Include.NON_NULL)
	public record Request(
			@JsonProperty("header") RequestHeader header,
			@JsonProperty("payload") RequestPayload payload) {
		public record RequestHeader(
			@JsonProperty("action") String action,
			@JsonProperty("task_id") String taskId,
			@JsonProperty("streaming") String streaming
		) {}
		public record RequestPayload(
			@JsonProperty("model") String model,
			@JsonProperty("task_group") String taskGroup,
			@JsonProperty("task") String task,
			@JsonProperty("function") String function,
			@JsonProperty("input") RequestPayloadInput input,
			@JsonProperty("parameters") RequestPayloadParameters parameters) {
			public record RequestPayloadInput(
				@JsonProperty("text") String text
			) {}
			public record RequestPayloadParameters(
				@JsonProperty("volume") Integer volume,
				@JsonProperty("text_type") String textType,
				@JsonProperty("voice") String voice,
				@JsonProperty("sample_rate") Integer sampleRate,
				@JsonProperty("rate") Double rate,
				@JsonProperty("format") String format,
				@JsonProperty("pitch") Double pitch,
				@JsonProperty("phoneme_timestamp_enabled") Boolean phonemeTimestampEnabled,
				@JsonProperty("word_timestamp_enabled") Boolean wordTimestampEnabled
			) {}
		}
	}

	// @formatter:off
    public static class Response {
        ByteBuffer audio;

        public ByteBuffer getAudio() {
            return audio;
        }
    }
    // @formatter:on

	public Flux<WebSocketMessage> execute(Request request) {
		return Flux.create(sink -> {
			this.webSocketClient.execute(URI.create(DEFAULT_WEBSOCKET_URL), session -> {
				String message = null;
				try {
					message = (new ObjectMapper()).writeValueAsString(request);
				}
				catch (JsonProcessingException e) {
					throw new RuntimeException(e);
				}

				Mono<Void> send = session.send(Mono.just(session.textMessage(message)));

				Flux<WebSocketMessage> receive = session.receive()
					.doOnNext(sink::next)
					.doOnComplete(sink::complete)
					.doOnError(sink::error);

				return send.thenMany(receive).then();
			}).block();
		});
	}

	public Flux<ByteBuffer> streamOut(Request request) {
		return execute(request).map(message -> {
			if (message.getType() == WebSocketMessage.Type.BINARY) {
				logger.debug("receive stream ...");
				return message.getPayload();
			}
			else {
				logger.debug("receive text: {}", message.getPayloadAsText());
				return message.getPayload().factory().allocateBuffer(0);
			}
		}).map(dataBuffer -> {
			byte[] bytes = new byte[dataBuffer.readableByteCount()];
			dataBuffer.read(bytes);
			return ByteBuffer.wrap(bytes);
		});
	}

	public enum RequestTextType {

		// @formatter:off
		@JsonProperty("plain_text") PLAIN_TEXT("PlainText"),
		@JsonProperty("ssml") SSML("SSML");
		// @formatter:on

		private final String value;

		private RequestTextType(String value) {
			this.value = value;
		}

		public String getValue() {
			return value;
		}

	}

	public enum ResponseFormat {

		// @formatter:off
        @JsonProperty("pcm") PCM("pcm"),
        @JsonProperty("wav") WAV("wav"),
        @JsonProperty("mp3") MP3("mp3");
        // @formatter:on

		public final String value;

		ResponseFormat(String value) {
			this.value = value;
		}

		public String getValue() {
			return this.value;
		}

	}

}
