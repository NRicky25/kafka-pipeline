package com.stream.pipeline;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.producer.*;
import org.apache.kafka.common.serialization.StringSerializer;

import java.io.InputStream;
import java.time.Instant;
import java.util.Properties;
import java.util.Random;
import java.util.UUID;

public class ProducerApp {

    // id is a UUID now (not String)
    record Txn(UUID id, String ts, String customer_id, int amount_cents, String category) {}

    public static void main(String[] args) throws Exception {
        Properties cfg = loadProps();
        String bootstrap = cfg.getProperty("kafka.bootstrap");
        String topic = cfg.getProperty("kafka.topic");

        Properties p = new Properties();
        p.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrap);
        p.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        p.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        p.put(ProducerConfig.ACKS_CONFIG, "all");

        ObjectMapper mapper = new ObjectMapper();
        Random r = new Random();

        try (Producer<String, String> producer = new KafkaProducer<>(p)) {
            System.out.println("Producing to topic: " + topic);
            String[] cats = {"grocery", "electronics", "fitness", "cafe", "books"};
            String[] customers = {"c001","c002","c003","c004","c005"};

            for (int i = 0; i < 200; i++) {
                Txn evt = new Txn(
                        UUID.randomUUID(),                // keep as UUID
                        Instant.now().toString(),        // ISO-8601 string
                        customers[r.nextInt(customers.length)],
                        500 + r.nextInt(5000),
                        cats[r.nextInt(cats.length)]
                );

                String payload = mapper.writeValueAsString(evt);
                ProducerRecord<String, String> rec =
                        new ProducerRecord<>(topic, evt.customer_id(), payload);
                producer.send(rec, (md, ex) -> { if (ex != null) ex.printStackTrace(); });
                Thread.sleep(50);
            }
            producer.flush();
        }
        System.out.println("Producer done.");
    }

    private static Properties loadProps() throws Exception {
        try (InputStream is = ProducerApp.class.getClassLoader().getResourceAsStream("app.properties")) {
            Properties p = new Properties();
            p.load(is);
            return p;
        }
    }
}
