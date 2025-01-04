package GTNHNightlyUpdater;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

public class JsonParser {
    public static <T> T parse(String json, Class<T> clazz) {
        Gson gson = new GsonBuilder().create();
        return gson.fromJson(json, clazz);
    }
}
