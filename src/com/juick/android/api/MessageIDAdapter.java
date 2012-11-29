package com.juick.android.api;

import com.google.gson.*;
import com.juick.android.MainActivity;
import com.juick.android.MicroBlog;
import com.juickadvanced.data.MessageID;

import java.lang.reflect.Type;

/**
* Created with IntelliJ IDEA.
* User: san
* Date: 11/29/12
* Time: 2:45 PM
* To change this template use File | Settings | File Templates.
*/
public class MessageIDAdapter implements JsonSerializer<MessageID>, JsonDeserializer<MessageID> {

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
