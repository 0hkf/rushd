package com.rushd.images;
import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import java.io.IOException;

public class RevisionStringDeserializer extends StdDeserializer<String> {
    public RevisionStringDeserializer() { super(String.class); }
    @Override public String deserialize(JsonParser parser, DeserializationContext context) throws IOException {
        if (!parser.hasToken(JsonToken.VALUE_STRING))
            return (String) context.handleUnexpectedToken(String.class, parser);
        return parser.getText();
    }
}
