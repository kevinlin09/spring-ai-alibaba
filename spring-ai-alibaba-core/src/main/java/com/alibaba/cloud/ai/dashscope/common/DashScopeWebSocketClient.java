package com.alibaba.cloud.ai.dashscope.common;

import com.alibaba.cloud.ai.dashscope.api.ApiUtils;
import org.jetbrains.annotations.NotNull;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.web.reactive.socket.WebSocketHandler;
import org.springframework.web.reactive.socket.WebSocketMessage;
import org.springframework.web.reactive.socket.WebSocketSession;
import org.springframework.web.reactive.socket.client.ReactorNettyWebSocketClient;
import org.springframework.web.reactive.socket.client.WebSocketClient;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.util.retry.Retry;
import reactor.netty.http.client.HttpClient;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicBoolean;
import java.time.Duration;

import static com.alibaba.cloud.ai.dashscope.common.DashScopeApiConstants.HEADER_OPENAPI_SOURCE;
import static com.alibaba.cloud.ai.dashscope.common.DashScopeApiConstants.SOURCE_FLAG;

public class DashScopeWebSocketClient implements WebSocketHandler {

	private final Logger logger = LoggerFactory.getLogger(DashScopeWebSocketClient.class);

	private final DashScopeWebSocketClientOptions options;

	private final WebSocketClient webSocketClient;

	private final AtomicBoolean isConnected;

	private WebSocketSession session;

	private final Sinks.Many<ByteBuffer> sink;

	private final Flux<ByteBuffer> flux;

	public DashScopeWebSocketClient(DashScopeWebSocketClientOptions options) {
		this.options = options;
		this.isConnected = new AtomicBoolean(false);

		this.sink = Sinks.many().multicast().onBackpressureBuffer();
		this.flux = this.sink.asFlux();

		HttpClient httpClient = HttpClient.create()
			// .headers(headers -> {
			// headers.set("Authorization", "bearer " + this.options.getApiKey());
			// headers.set("user-agent", ApiUtils.userAgent());
			// })
			// .baseUrl("https://dashscope.aliyuncs.com/api-ws/v1/inference/")
			.secure();
		this.webSocketClient = new ReactorNettyWebSocketClient(httpClient);
		// this.webSocketClient = new ReactorNettyWebSocketClient();
	}

	private Mono<Void> establish() {
		HttpHeaders headers = null;

		return this.webSocketClient.execute(URI.create(this.options.getBaseUrl()), headers, this)
			// .retryWhen(Retry.backoff(3, Duration.ofSeconds(1))) // 连接时的重试，3次，每次1秒
			.doOnError(error -> {
				logger.error("Failed to connect to WebSocket server after retries: {}", error.getMessage());
				this.sink.tryEmitError(error);
			})
			.doOnSuccess(v -> logger.info("Connected to WebSocket server successfully."))
			.then();
	}

	public Flux<ByteBuffer> streamCall(String request) {
		return Mono.defer(() -> {
			if (!isConnected.get()) {
				logger.info("Not connected. Establishing WebSocket connection...");
				return establish() // 请替换为实际的URL和API-KEY
					.then(Mono.just(session));
			}
			logger.info("Already connected. Reusing existing WebSocket session.");
			return Mono.just(session);
		}).flatMapMany(sess -> {
			if (sess == null || !sess.isOpen()) {
				return Flux.error(new IllegalStateException("WebSocket session is not open."));
			}
			logger.info("Sending request over WebSocket.");

			// 创建WebSocketMessage
			WebSocketMessage message = sess.textMessage(request);

			// 发送消息
			return sess.send(Mono.just(message))
				.thenMany(sess.receive()
					.filter(webSocketMessage -> webSocketMessage.getType() == WebSocketMessage.Type.BINARY)
					.map(WebSocketMessage::getPayload)
					.map(DataBuffer::asByteBuffer));
		})
			.retryWhen(Retry.backoff(3, Duration.ofSeconds(1))) // 发送时的重试，3次，每次1秒
			.doOnError(e -> logger.error("send ws message failed: {}", e.getMessage()));
	}

	public Flux<ByteBuffer> streamCall2(String request) {
		// 检查连接是否已建立
		if (this.session == null || this.session.isOpen()) {
			logger.info("connect and send...");
			// 如果未连接，建立连接并发送请求
			return establish() // 替换为实际的URL和API-KEY
				.then(sendRequest(request))
				.thenMany(flux);
		}
		else {
			// 已连接，直接发送请求
			logger.info("send...");
			return sendRequest(request).thenMany(flux);
		}
	}

	public void streamCall3(String request) throws URISyntaxException {
		WebSocketClient client = new ReactorNettyWebSocketClient();

		HttpHeaders headers = new HttpHeaders();
		headers.setBearerAuth(System.getenv("DASHSCOPE_API_KEY"));
		headers.set(HEADER_OPENAPI_SOURCE, SOURCE_FLAG);

		URI url = new URI("https://dashscope.aliyuncs.com/api-ws/v1/inference/");

		File file = new File("/Users/zhiyi/Downloads/output4.wav");
		// Flux<Integer> flux = Flux.range(1, 3).delayElements(Duration.ofMillis(10));
		client.execute(url, headers, session -> session.send(Mono.just(session.textMessage(request)))
			.and(session.receive().doOnNext(data -> {
				// System.out.printf("data: %s\n", data.getPayloadAsText());
				try (FileOutputStream fos = new FileOutputStream(file)) {
					fos.write(data.getPayload().asByteBuffer().array());
					System.out.println("synthesis done!");
				}
				catch (IOException e) {
					throw new RuntimeException(e);
				}
			}).then())).block();
	}

	private Mono<Void> sendRequest(String request) {
		if (this.session != null && this.session.isOpen()) {
			WebSocketMessage message = this.session.textMessage(request);
			try {
				return session.send(Mono.just(message))
					.doOnError(error -> logger.error("Failed to send text message: {}", error.getMessage()));
			}
			finally {
				logger.info("send finished");
			}
		}
		else {
			return Mono.error(new IllegalStateException("WebSocket session is not open"));
		}
	}

	@NotNull
	@Override
	public Mono<Void> handle(WebSocketSession session) {
		logger.info("WebSocket connection established");

		this.session = session;
		this.isConnected.set(true);

		return session.receive().doOnNext(message -> {
			logger.info("Received message type: {}", message.getType());
			if (message.getType() == WebSocketMessage.Type.BINARY) {
				ByteBuffer byteBuffer = message.getPayload().asByteBuffer();
				Sinks.EmitResult result = this.sink.tryEmitNext(byteBuffer);
				if (result.isFailure()) {
					logger.error("Failed to emit audio data: {}", result);
				}
			}
			else if (message.getType() == WebSocketMessage.Type.TEXT) {
				String text = message.getPayloadAsText();
				logger.info("Received text message: {}", text);
			}
		}).doOnError(error -> {
			logger.error("WebSocket receive error: {}", error.getMessage());
			this.sink.tryEmitError(error);
			isConnected.set(false);
		}).doOnComplete(() -> {
			logger.info("WebSocket connection closed.");
			this.sink.tryEmitComplete();
			isConnected.set(false);
		}).then();
	}

	public static class Request {

	}

	public static class Response {

	}

}
