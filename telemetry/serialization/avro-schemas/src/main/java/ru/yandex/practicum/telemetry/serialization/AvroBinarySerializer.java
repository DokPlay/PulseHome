package ru.yandex.practicum.telemetry.serialization;

import org.apache.avro.io.BinaryEncoder;
import org.apache.avro.io.DatumWriter;
import org.apache.avro.io.EncoderFactory;
import org.apache.avro.specific.SpecificDatumWriter;
import org.apache.avro.specific.SpecificRecord;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;

public final class AvroBinarySerializer {

    private AvroBinarySerializer() {
    }

    public static <T extends SpecificRecord> byte[] serialize(T record) {
        try {
            return AvroEncodingSupport.encode(record);
        } catch (IOException exception) {
            throw new UncheckedIOException("Failed to serialize Avro record", exception);
        }
    }

    /**
     * Legacy raw-binary encoding kept only for backwards-compatible tests and old payload readers.
     * Prefer {@link #serialize(SpecificRecord)} for all new writes.
     */
    @Deprecated(forRemoval = false)
    public static <T extends SpecificRecord> byte[] serializeLegacy(T record) {
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
