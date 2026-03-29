package ru.yandex.practicum.telemetry.serialization;

import org.apache.avro.Schema;
import org.apache.avro.data.TimeConversions;
import org.apache.avro.message.BinaryMessageDecoder;
import org.apache.avro.message.BinaryMessageEncoder;
import org.apache.avro.specific.SpecificData;
import org.apache.avro.specific.SpecificRecord;
import org.apache.avro.specific.SpecificRecordBase;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

final class AvroEncodingSupport {

    private static final byte SINGLE_OBJECT_MAGIC_0 = (byte) 0xC3;
    private static final byte SINGLE_OBJECT_MAGIC_1 = 0x01;
    private static final int SINGLE_OBJECT_MIN_LENGTH = 10;
    private static final SpecificData MODEL = new SpecificData();
    private static final Map<Schema, BinaryMessageEncoder<?>> ENCODERS = new ConcurrentHashMap<>();
    private static final Map<Schema, BinaryMessageDecoder<?>> DECODERS = new ConcurrentHashMap<>();

    static {
        MODEL.addLogicalTypeConversion(new TimeConversions.TimestampMillisConversion());
    }

    private AvroEncodingSupport() {
    }

    static boolean isSingleObjectEncoded(byte[] payload) {
        return payload != null
                && payload.length >= SINGLE_OBJECT_MIN_LENGTH
                && payload[0] == SINGLE_OBJECT_MAGIC_0
                && payload[1] == SINGLE_OBJECT_MAGIC_1;
    }

    static <T extends SpecificRecord> byte[] encode(T record) throws IOException {
        ByteBuffer byteBuffer = encoderFor(record.getSchema()).encode(record);
        byte[] bytes = new byte[byteBuffer.remaining()];
        byteBuffer.get(bytes);
        return bytes;
    }

    static <T extends SpecificRecordBase> T decodeSingleObject(Schema schema, byte[] payload) throws IOException {
        return castDecodedRecord(decoderFor(schema).decode(ByteBuffer.wrap(payload)));
    }

    @SuppressWarnings("unchecked")
    private static <T extends SpecificRecord> BinaryMessageEncoder<T> encoderFor(Schema schema) {
        return (BinaryMessageEncoder<T>) ENCODERS.computeIfAbsent(schema, key -> new BinaryMessageEncoder<>(MODEL, key));
    }

    @SuppressWarnings("unchecked")
    private static <T extends SpecificRecordBase> BinaryMessageDecoder<T> decoderFor(Schema schema) {
        return (BinaryMessageDecoder<T>) DECODERS.computeIfAbsent(schema, key -> new BinaryMessageDecoder<SpecificRecordBase>(MODEL, key));
    }

    @SuppressWarnings("unchecked")
    private static <T extends SpecificRecordBase> T castDecodedRecord(SpecificRecordBase record) {
        return (T) record;
    }
}
