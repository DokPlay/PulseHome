package ru.yandex.practicum.telemetry.serialization;

import org.apache.avro.Schema;
import org.apache.avro.data.TimeConversions;
import org.apache.avro.io.BinaryDecoder;
import org.apache.avro.io.DecoderFactory;
import org.apache.avro.specific.SpecificData;
import org.apache.avro.specific.SpecificDatumReader;
import org.apache.avro.specific.SpecificRecordBase;
import org.apache.kafka.common.serialization.Deserializer;

import java.io.IOException;
import java.io.UncheckedIOException;

public class BaseAvroDeserializer<T extends SpecificRecordBase> implements Deserializer<T> {

    private static final SpecificData MODEL = createSpecificDataModel();

    private final DecoderFactory decoderFactory;
    private final SpecificDatumReader<T> datumReader;
    private final Schema schema;

    public BaseAvroDeserializer(Schema schema) {
        this(DecoderFactory.get(), schema);
    }

    public BaseAvroDeserializer(DecoderFactory decoderFactory, Schema schema) {
        this.decoderFactory = decoderFactory;
        this.schema = schema;
        this.datumReader = new SpecificDatumReader<>(schema, schema, MODEL);
    }

    @Override
    public T deserialize(String topic, byte[] data) {
        if (data == null) {
            return null;
        }

        try {
            if (AvroEncodingSupport.isSingleObjectEncoded(data)) {
                return AvroEncodingSupport.decodeSingleObject(schema, data);
            }
            BinaryDecoder decoder = decoderFactory.binaryDecoder(data, null);
            return datumReader.read(null, decoder);
        } catch (IOException exception) {
            throw new UncheckedIOException("Failed to deserialize Avro message from topic " + topic, exception);
        }
    }

    private static SpecificData createSpecificDataModel() {
        SpecificData model = new SpecificData();
        model.addLogicalTypeConversion(new TimeConversions.TimestampMillisConversion());
        return model;
    }
}
