package energy;

import com.google.gson.*;
import java.io.FileReader;
import java.io.IOException;

import org.cloudsimplus.allocationpolicies.VmAllocationPolicy;
import org.cloudsimplus.allocationpolicies.VmAllocationPolicyBestFit;
import org.cloudsimplus.allocationpolicies.VmAllocationPolicyFirstFit;
import org.cloudsimplus.allocationpolicies.VmAllocationPolicyRandom;
import org.cloudsimplus.allocationpolicies.VmAllocationPolicyRoundRobin;
import org.cloudsimplus.allocationpolicies.VmAllocationPolicySimple;
import org.cloudsimplus.power.models.PowerModelHost;
import org.cloudsimplus.power.models.PowerModelHostSimple;
import org.cloudsimplus.power.models.PowerModelHostSpec;
import org.cloudsimplus.schedulers.cloudlet.CloudletScheduler;
import org.cloudsimplus.schedulers.cloudlet.CloudletSchedulerSpaceShared;
import org.cloudsimplus.schedulers.cloudlet.CloudletSchedulerTimeShared;
import org.cloudsimplus.schedulers.vm.VmScheduler;
import org.cloudsimplus.schedulers.vm.VmSchedulerSpaceShared;
import org.cloudsimplus.schedulers.vm.VmSchedulerTimeShared;

public class Config {
    private String fileName;
    private JsonObject jsonObject;

    public Config(String fileName) {
        try (FileReader reader = new FileReader(fileName)) {
            this.jsonObject = JsonParser.parseReader(reader).getAsJsonObject();
            // Get the last part of the path (filename itself)
            String[] pathParts = fileName.split("/");
            String lastPart = pathParts[pathParts.length - 1]; // Last element after splitting by "/"

            // Remove the file extension
            String[] nameParts = lastPart.split("\\.");
            this.fileName = nameParts[0]; // First element before the dot

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

    public String get_filename() {
        return fileName;
    }

    public JsonObject getObject(String key) {
        return jsonObject.has(key) ? jsonObject.getAsJsonObject(key) : new JsonObject();
    }

    public JsonObject getRoot() {
        return jsonObject;
    }

    public VmScheduler getVMScheduler() {
        String key = "VmScheduler";
        String type = this.getRoot().has(key) ? this.getRoot().get(key).getAsString() : "";

        switch (type) {
            case "TS":
                return new VmSchedulerTimeShared();
            case "SS":
                return new VmSchedulerSpaceShared();
            default:
                System.err.println("Warning: Unknown VM Scheduler '" + type + "', using default (SS)");
                return new VmSchedulerSpaceShared(); // Default policy
        }
    }

    public CloudletScheduler getCloudletScheduler() {
        String key = "cloudletScheduler";
        String type = this.getRoot().has(key) ? this.getRoot().get(key).getAsString() : "";

        switch (type) {
            case "TS":
                return new CloudletSchedulerTimeShared();
            case "SS":
                return new CloudletSchedulerSpaceShared();
            default:
                System.err.println("Warning: Unknown VM Scheduler '" + type + "', using default (SS)");
                return new CloudletSchedulerTimeShared(); // Default policy
        }
    }

    public VmAllocationPolicy getVmAllocationPolicy() {
        String key = "VmAllocationPolicy";
        String type = this.getRoot().has(key) ? this.getRoot().get(key).getAsString() : "";

        switch (type) {
            case "SP":
                return new VmAllocationPolicySimple();
            case "FF":
                return new VmAllocationPolicyFirstFit();
            case "BF":
                return new VmAllocationPolicyBestFit();
            case "RR":
                return new VmAllocationPolicyRoundRobin();
            case "RD":
                return new VmAllocationPolicyRandom(null);
            default:
                System.err.println("Warning: Unknown VM Allocation Policy '" + type + "', using default (SP)");
                return new VmAllocationPolicySimple(); // Default policy
        }
    }

    public PowerModelHost getPowerModel() {
        String key = "power_spec_path";
        String type = this.getRoot().has(key) ? this.getRoot().get(key).getAsString() : "Manual";

        if (type == "Manual") {
            JsonObject powerSpec = this.getRoot().getAsJsonObject("power_spec");
            double MAX_POWER = powerSpec.get("MAX_POWER").getAsDouble();
            double STATIC_POWER = powerSpec.get("STATIC_POWER").getAsDouble();
            return new PowerModelHostSimple(MAX_POWER, STATIC_POWER);
        } else {
            PowerModelHostSpec DEF_POWER_MODEL = PowerModelHostSpec.getInstance(type);
            return new PowerModelHostSpec(DEF_POWER_MODEL.getPowerSpecs());
        }
    }
}
