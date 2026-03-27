package ru.yandex.practicum.telemetry.collector.service;

import org.apache.avro.io.BinaryEncoder;
import org.apache.avro.io.DatumWriter;
import org.apache.avro.io.EncoderFactory;
import org.apache.avro.specific.SpecificDatumWriter;
import org.apache.avro.specific.SpecificRecord;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;

@Component
public class AvroBinarySerializer {

    public <T extends SpecificRecord> byte[] serialize(T record) {
        try {
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            DatumWriter<T> writer = new SpecificDatumWriter<>(record.getSchema());
            BinaryEncoder encoder = EncoderFactory.get().binaryEncoder(outputStream, null);
            writer.write(record, encoder);
            encoder.flush();
            return outputStream.toByteArray();
        } catch (IOException exception) {
            throw new UncheckedIOException("Failed to serialize Avro record", exception);
        }
    }
}
