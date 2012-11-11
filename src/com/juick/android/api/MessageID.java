package com.juick.android.api;

import com.google.gson.*;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import com.juick.android.MainActivity;
import com.juick.android.MicroBlog;

import java.io.IOException;
import java.io.Serializable;
import java.lang.reflect.Type;

/**
 * Created with IntelliJ IDEA.
 * User: san
 * Date: 11/9/12
 * Time: 4:03 AM
 * To change this template use File | Settings | File Templates.
 */
public abstract class MessageID implements Serializable {
    public abstract String toString();

    public abstract String toDisplayString();

    public abstract MicroBlog getMicroBlog();


    public static class MessageIDAdapter implements JsonSerializer<MessageID>, JsonDeserializer<MessageID> {

        public MessageIDAdapter() {
        }

        @Override
        public JsonElement serialize(MessageID src, Type typeOfSrc,
                                     JsonSerializationContext context) {
            JsonElement e = new JsonPrimitive(src.toString());
            return e;
        }

        @Override
        public MessageID deserialize(JsonElement jsonElement, Type type, JsonDeserializationContext jsonDeserializationContext) throws JsonParseException {
            if (jsonElement instanceof JsonPrimitive) {
                JsonPrimitive p = (JsonPrimitive)jsonElement;
                String keyString = p.getAsString();
                for (MicroBlog microBlog : MainActivity.microBlogs.values()) {
                    MessageID key = microBlog.createKey(keyString);
                    if (key != null) return key;
                }
            }
            return null;
        }

    }

}
