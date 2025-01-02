package GTNHNightlyUpdater;

import GTNHNightlyUpdater.Models.Assets;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import lombok.val;

import java.io.IOException;
import java.nio.file.Files;

public class JsonParser {
    public static <T> T parse(String json, Class<T> clazz) {
        Gson gson = new GsonBuilder().create();
        return gson.fromJson(json, clazz);
    }

    public static void saveAssets(Assets.Asset asset) throws IOException {
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        val output = gson.toJson(asset);
        Files.write(Main.Config.localAssetFile, output.getBytes());
    }
}
