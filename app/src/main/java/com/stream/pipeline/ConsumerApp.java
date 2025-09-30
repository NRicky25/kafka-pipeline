package com.stream.pipeline;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;

import java.io.InputStream;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.Duration;
import java.util.List;
import java.util.Properties;

public class ConsumerApp {
    // Event shape produced by ProducerApp
    record Txn(String id, String ts, String customer_id, int amount_cents, String category) {}

    public static void main(String[] args) throws Exception {
        Properties cfg = loadProps();

        String bootstrap = cfg.getProperty("kafka.bootstrap");
        String topic = cfg.getProperty("kafka.topic");

        // DB pool
        HikariDataSource ds = makeDataSource(cfg);

        // Kafka consumer config
        Properties c = new Properties();
        c.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrap);
        // use a fresh group id to re-read from earliest if you ran a previous version
        c.put(ConsumerConfig.GROUP_ID_CONFIG, "txn-consumer-group-v2");
        c.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        c.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        c.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        c.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "true");

        ObjectMapper mapper = new ObjectMapper();

        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(c)) {
            consumer.subscribe(List.of(topic));
            System.out.println("Consuming from topic: " + topic);

            while (true) {
                ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(500));
                if (records.isEmpty()) continue;

                try (Connection cn = ds.getConnection()) {
                    cn.setAutoCommit(false);

                    for (ConsumerRecord<String, String> rec : records) {
                        Txn t = mapper.readValue(rec.value(), Txn.class);

                        // 1) raw insert (cast String -> uuid and timestamptz in SQL)
                        try (PreparedStatement ps = cn.prepareStatement(
                                "INSERT INTO transactions_raw (id, ts, customer_id, amount_cents, category) " +
                                "VALUES (?::uuid, ?::timestamptz, ?, ?, ?) " +
                                "ON CONFLICT (id) DO NOTHING")) {
                            ps.setString(1, t.id());
                            ps.setString(2, t.ts());
                            ps.setString(3, t.customer_id());
                            ps.setInt(4, t.amount_cents());
                            ps.setString(5, t.category());
                            ps.executeUpdate();
                        }

                        // 2) aggregate upsert
                        try (PreparedStatement ps = cn.prepareStatement(
                                "INSERT INTO customer_agg (customer_id, total_cents, txn_count) VALUES (?, ?, 1) " +
                                "ON CONFLICT (customer_id) DO UPDATE SET " +
                                "total_cents = customer_agg.total_cents + EXCLUDED.total_cents, " +
                                "txn_count  = customer_agg.txn_count  + 1")) {
                            ps.setString(1, t.customer_id());
                            ps.setInt(2, t.amount_cents());
                            ps.executeUpdate();
                        }
                    }
                    cn.commit();
                } catch (SQLException e) {
                    e.printStackTrace();
                }
            }
        } finally {
            ds.close();
        }
    }

    private static HikariDataSource makeDataSource(Properties cfg) {
        HikariConfig hc = new HikariConfig();
        hc.setJdbcUrl(cfg.getProperty("db.url"));
        hc.setUsername(cfg.getProperty("db.user"));
        hc.setPassword(cfg.getProperty("db.pass"));
        hc.setMaximumPoolSize(5);
        return new HikariDataSource(hc);
    }

    private static Properties loadProps() throws Exception {
        try (InputStream is = ConsumerApp.class.getClassLoader().getResourceAsStream("app.properties")) {
            Properties p = new Properties();
            p.load(is);
            return p;
        }
    }
}
