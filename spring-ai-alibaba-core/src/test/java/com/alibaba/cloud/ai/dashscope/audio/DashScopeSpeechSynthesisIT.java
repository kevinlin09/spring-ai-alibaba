package com.alibaba.cloud.ai.dashscope.audio;

import com.alibaba.cloud.ai.autoconfigure.dashscope.DashScopeAutoConfiguration;
import com.alibaba.cloud.ai.dashscope.api.DashScopeSpeechSynthesisApi;
import com.alibaba.cloud.ai.dashscope.audio.synthesis.DashScopeSpeechSynthesisModel;
import com.alibaba.cloud.ai.dashscope.audio.synthesis.DashScopeSpeechSynthesisOptions;
import com.alibaba.cloud.ai.dashscope.audio.synthesis.SpeechSynthesisPrompt;
import com.alibaba.cloud.ai.dashscope.audio.synthesis.SpeechSynthesisResponse;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import reactor.core.publisher.Flux;
import java.io.*;

@TestPropertySource("classpath:application.yml")
@SpringBootTest(classes = DashScopeAutoConfiguration.class)
public class DashScopeSpeechSynthesisIT {

	private static final Logger logger = LoggerFactory.getLogger(DashScopeSpeechSynthesisIT.class);

	@Autowired
	private DashScopeSpeechSynthesisModel model;

	@Test
	void call() {
		SpeechSynthesisResponse response = model.call(new SpeechSynthesisPrompt("白日依山尽，黄河入海流。"));

		// play pcm: ffplay -f s16le -ar 48000 -ch_layout mono output-stream.pcm
		File file = new File("/Users/zhiyi/Downloads/output-call.mp3");
		try (FileOutputStream fos = new FileOutputStream(file)) {
			try {
				logger.info("write call audio to file ...");
				fos.write(response.getResult().getOutput().getAudio().array());
			}
			catch (IOException e) {
				throw new RuntimeException(e);
			}
		}
		catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	@Test
	void stream() {
		Flux<SpeechSynthesisResponse> response = model.stream(new SpeechSynthesisPrompt("白日依山尽，黄河入海流。"));

		// play pcm: ffplay -f s16le -ar 48000 -ch_layout mono output-stream.pcm
		File file = new File("/Users/zhiyi/Downloads/output-stream.mp3");
		try (FileOutputStream fos = new FileOutputStream(file)) {
			response.subscribe(synthesisResponse -> {
				try {
					logger.info("write stream audio to file ...");
					fos.write(synthesisResponse.getResult().getOutput().getAudio().array());
				}
				catch (IOException e) {
					throw new RuntimeException(e);
				}
			});
		}
		catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

}
