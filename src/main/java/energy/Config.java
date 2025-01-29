package energy;

import com.google.gson.*;
import java.io.FileReader;
import java.io.IOException;

public class Config {
    private JsonObject jsonObject;

    public Config(String fileName) {
        try (FileReader reader = new FileReader(fileName)) {
            jsonObject = JsonParser.parseReader(reader).getAsJsonObject();
        } catch (IOException e) {
            System.err.println("Error reading configuration file: " + e.getMessage());
            System.exit(1);
        }
    }

    public int getInt(String key) {
        return jsonObject.get(key).getAsInt();
    }

    public long getLong(String key) {
        return jsonObject.get(key).getAsLong();
    }

    public double getDouble(String key) {
        return jsonObject.get(key).getAsDouble();
    }

    public String getString(String key) {
        return jsonObject.get(key).getAsString();
    }

    public JsonArray getArray(String key) {
        return jsonObject.getAsJsonArray(key);
    }

    public JsonObject getObject(String key) {
        return jsonObject.getAsJsonObject(key);
    }
}
