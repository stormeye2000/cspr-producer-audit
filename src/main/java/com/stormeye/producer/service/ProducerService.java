package com.stormeye.producer.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.kafka.core.reactive.ReactiveKafkaProducerTemplate;
import org.springframework.retry.RetryContext;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.stereotype.Service;
import com.stormeye.producer.config.ServiceProperties;

import reactor.kafka.sender.SenderOptions;

/**
 * Service to start the kafka producer
 * Each emitter will be test connected via the retry template
 * Each emitter from the properties file then runs in its own thread
 */
@Service
public class ProducerService {

    private static final Logger log = LoggerFactory.getLogger(ProducerService.class.getName());

    private final ServiceProperties properties;
    private final EmitterService emitterService;
    private final TopicsService topicsService;
    private final RetryTemplate retryTemplate;

    public ProducerService(@Qualifier("ServiceProperties") final ServiceProperties properties, final EmitterService emitterService, final TopicsService topicsService, final RetryTemplate retryTemplate) {
        this.properties = properties;
        this.emitterService = emitterService;
        this.topicsService = topicsService;
        this.retryTemplate = retryTemplate;
    }

    public void startEventConsumers() {

        final ReactiveKafkaProducerTemplate<Integer, String> producerTemplate =
                new ReactiveKafkaProducerTemplate<>(SenderOptions.create(new KafkaProducerService(properties).getProperties()));

        properties.getEmitters().forEach(
                emitter -> {

                    RetryContext context = null;
                    try {
                        context = retryTemplate.execute(ctx -> {
                            emitterService.connect(emitter);
                            return ctx;
                        });
                    } catch (Exception e) {
                        log.error("Failed to connect to emitter [{}] after retries, {}", emitter, e.getMessage());
                    }

                    if (context != null && !context.isExhaustedOnly()){

                        log.info("Successfully connected to emitter: [{}]", emitter);
                        log.info("Starting kafka producer for casper event emitter: [{}]", emitter);

                        new ProducerThread(producerTemplate, emitterService, topicsService, emitter).start();
                    }


                }

        );

    }



}
