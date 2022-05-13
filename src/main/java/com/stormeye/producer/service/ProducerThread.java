package com.stormeye.producer.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.http.HttpResponse;
import java.util.Date;
import java.util.List;
import reactor.core.publisher.Flux;
import reactor.kafka.sender.KafkaSender;
import reactor.kafka.sender.SenderRecord;

/**
 * Starts a kafka producer in its own thread
 * Events are read from the emitter and their event type is mapped to a kafka topic
 * Each event type is then written to its own corresponding topic
 */
class ProducerThread extends Thread{

    private static final Logger log = LoggerFactory.getLogger(ProducerThread.class.getName());

    private static final Integer MAX_RANGE = 1;

    private final HttpService httpService;
    private final List<String> topics;
    private final KafkaSender<Integer, String> sender;

    public ProducerThread(final HttpService httpService, final List<String> topics, final KafkaSender<Integer, String> sender){
        this.httpService = httpService;
        this.topics = topics;
        this.sender = sender;
    }

    private boolean hasTopic(final String event) {
        return topics.stream().anyMatch(event::contains);
    }

    private String getTopic(final String event) {
        return topics.stream().filter(event::contains).findAny().orElse(null);
    }

    public void run() {

        try {

            httpService.getClient().send(httpService.getRequest(), HttpResponse.BodyHandlers.ofLines()).body().forEach(

                    event -> {

                        if (hasTopic(event)){

                            final String topic = getTopic(event);

                            if (topic != null) {

                                log.debug("Topic: [{}] - Event : {}", topic, event);

                                final Flux<SenderRecord<Integer, String, Integer>> outboundFlux = Flux.range(0, MAX_RANGE)
                                        .map(i -> SenderRecord.create(getTopic(event), 0, new Date().getTime(), i, event, i));

                                sender.send(outboundFlux)
                                        .doOnError(e-> {
                                            log.error("Send failed for event: {}", event);
                                            log.error("Error - {}", e.getMessage());
                                        })
                                        .subscribe();
                            } else {
                                log.error("Unknown topic for event - {}", event);
                            }

                        }
                    }
            );

        } catch (IOException | InterruptedException e) {
            log.error(e.getMessage());
        }

    }
}
