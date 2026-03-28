package ru.yandex.practicum.telemetry.serialization;

import org.apache.avro.specific.SpecificRecord;
import org.apache.kafka.common.serialization.Serializer;

import java.io.IOException;
import java.io.UncheckedIOException;

public class AvroMessageSerializer<T extends SpecificRecord> implements Serializer<T> {

    @Override
    public byte[] serialize(String topic, T data) {
        if (data == null) {
            return null;
        }

        try {
            return AvroEncodingSupport.encode(data);
        } catch (IOException exception) {
            throw new UncheckedIOException("Failed to serialize Avro message for topic " + topic, exception);
        }
    }
}
