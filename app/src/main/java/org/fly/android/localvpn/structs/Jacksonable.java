package org.fly.android.localvpn.structs;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import org.apache.commons.codec.binary.StringUtils;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;

public class Jacksonable {

    public static class Builder{
        public static ObjectMapper makeAdapter()
        {
            ObjectMapper objectMapper = new ObjectMapper();
            objectMapper.enable(JsonParser.Feature.IGNORE_UNDEFINED);
            objectMapper.enable(JsonParser.Feature.ALLOW_COMMENTS);
            objectMapper.enable(JsonParser.Feature.ALLOW_SINGLE_QUOTES);
            objectMapper.enable(JsonParser.Feature.ALLOW_TRAILING_COMMA);
            objectMapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
            objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
            return objectMapper;
        }

        public static String toJson(Object o)
        {
            return toJson(makeAdapter(), o);
        }

        public static String toJson(ObjectMapper objectMapper, Object o)
        {
            try {
                return objectMapper.writeValueAsString(o);
            } catch (JsonProcessingException e)
            {
                e.printStackTrace();
            }

            return null;
        }

        public static void toJson(File file, Object o) throws IOException
        {
            toJson(makeAdapter(), file, o);
        }

        public static void toJson(ObjectMapper objectMapper, File file, Object o) throws IOException
        {
            objectMapper.writeValue(file, o);
        }

        public static <T> Map<String, T> jsonToMap(ObjectMapper objectMapper, String json)
        {
            if (json != null && !json.isEmpty()) {

                try {
                    return objectMapper.readValue(json, new TypeReference<Map<String, T>>(){});
                } catch (IOException e)
                {
                    e.printStackTrace();
                    return null;
                }
            }

            return null;
        }

        public static <T> Map<String, T> jsonToMap(String json)
        {
            return jsonToMap(makeAdapter(), json);
        }

        public static <T> List<Map<String, T>> jsonToRecords(String json)
        {
            return jsonToRecords(makeAdapter(), json);
        }

        public static <T> List<Map<String, T>> jsonToRecords(ObjectMapper objectMapper, String json)
        {
            if (json != null && !json.isEmpty()) {

                try {
                    return objectMapper.readValue(json, new TypeReference<List<Map<String, T>>>(){});
                } catch (IOException e)
                {
                    e.printStackTrace();
                    return null;
                }
            }

            return null;
        }

        public static <T> List<T> jsonToList(String json){
            return jsonToList(makeAdapter(), json);
        }

        public static <T> List<T> jsonToList(ObjectMapper objectMapper, String json)
        {
            if (json != null && !json.isEmpty()) {
                try {
                    return objectMapper.readValue(json, new TypeReference<List<T>>(){});
                } catch (IOException e)
                {
                    e.printStackTrace();
                    return null;
                }
            }

            return null;
        }

    }

    public static <T> T fromJson(ObjectMapper objectMapper, final Class<T> clazz, String json) throws IOException
    {
        return objectMapper.readValue(json, clazz);
    }

    public static <T> T fromJson(final Class<T> clazz, String json) throws IOException
    {
        return fromJson(Builder.makeAdapter(), clazz, json);
    }

    public static <T> T fromJson(ObjectMapper objectMapper, final Class<T> clazz, byte[] json) throws IOException
    {
        return fromJson(objectMapper, clazz, StringUtils.newStringUtf8(json));
    }

    public static <T> T fromJson(final Class<T> clazz, byte[] json) throws IOException
    {
        return fromJson(clazz, StringUtils.newStringUtf8(json));
    }

    public static <T> T fromJson(ObjectMapper objectMapper, final Class<T> clazz, File file) throws IOException
    {
        return objectMapper.readValue(file, clazz);
    }

    public static <T> T fromJson(final Class<T> clazz, File file) throws IOException
    {
        return fromJson(Builder.makeAdapter(), clazz, file);
    }

    public String toJson(ObjectMapper objectMapper)
    {

        return Builder.toJson(objectMapper, this);
    }

    public String toJson()
    {
        return toJson(Builder.makeAdapter());
    }

    public void toJson(ObjectMapper objectMapper, File file) throws Exception
    {
        Builder.toJson(objectMapper, file, this);
    }

    public void toJson(File file) throws Exception
    {
        toJson(Builder.makeAdapter(), file);
    }
}
