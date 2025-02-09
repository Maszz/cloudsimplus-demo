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
        return jsonObject.has(key) ? jsonObject.get(key).getAsInt() : 0;
    }

    public long getLong(String key) {
        return jsonObject.has(key) ? jsonObject.get(key).getAsLong() : 0L;
    }

    public double getDouble(String key) {
        return jsonObject.has(key) ? jsonObject.get(key).getAsDouble() : 0.0;
    }

    public String getString(String key) {
        return jsonObject.has(key) ? jsonObject.get(key).getAsString() : "";
    }

    public JsonArray getArray(String key) {
        return jsonObject.has(key) ? jsonObject.getAsJsonArray(key) : new JsonArray();
    }
}
