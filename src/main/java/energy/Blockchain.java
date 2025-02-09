package energy;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.cloudsimplus.brokers.DatacenterBrokerSimple;
import org.cloudsimplus.cloudlets.Cloudlet;
import org.cloudsimplus.cloudlets.CloudletSimple;
import org.cloudsimplus.core.CloudSimPlus;
import org.cloudsimplus.datacenters.DatacenterSimple;
import org.cloudsimplus.hosts.Host;
import org.cloudsimplus.hosts.HostSimple;
import org.cloudsimplus.resources.Pe;
import org.cloudsimplus.resources.PeSimple;
import org.cloudsimplus.vms.Vm;
import org.cloudsimplus.vms.VmSimple;
import org.cloudsimplus.allocationpolicies.VmAllocationPolicyBestFit;
import org.cloudsimplus.builders.tables.CloudletsTableBuilder;

import java.util.ArrayList;
import java.util.List;

public class Blockchain {
    private static Config config;

    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("❌ ERROR: No config file specified! Usage: java Blockchain <config-file>");
            System.exit(1);
        }

        config = new Config(args[0]);
        CloudSimPlus simulation = new CloudSimPlus();

        JsonArray datacentersConfig = config.getArray("DATACENTERS");
        List<DatacenterSimple> datacenters = new ArrayList<>();

        for (JsonElement element : datacentersConfig) {
            JsonObject dc = element.getAsJsonObject();
            int numHosts = dc.get("hosts").getAsInt();
            datacenters.add(createDatacenter(simulation, numHosts));
        }

        DatacenterBrokerSimple broker = new DatacenterBrokerSimple(simulation);


        int totalVMs = config.getInt("VMS");
        int numDatacenters = datacentersConfig.size();
        int remainingVMs = totalVMs;

        for (int i = 0; i < numDatacenters; i++) {
            int vmsForThisDatacenter = (i == numDatacenters - 1) ? remainingVMs : totalVMs / numDatacenters;
            remainingVMs -= vmsForThisDatacenter;

            List<Vm> vmList = new ArrayList<>();
            for (int j = 0; j < vmsForThisDatacenter; j++) {
                Vm vm = new VmSimple(config.getInt("HOST_MIPS"), config.getInt("VM_PES"))
                        .setRam(config.getInt("VM_RAM"))
                        .setBw(config.getInt("VM_BW"))
                        .setSize(config.getInt("VM_STORAGE"));
                vmList.add(vm);
            }
            broker.submitVmList(vmList);
        }

        simulation.start();
        new CloudletsTableBuilder(broker.getCloudletFinishedList()).build();
    }

    private static DatacenterSimple createDatacenter(CloudSimPlus simulation, int numberOfHosts) {
        List<Host> hostList = new ArrayList<>();
        for (int i = 0; i < numberOfHosts; i++) {
            List<Pe> peList = new ArrayList<>();
            for (int j = 0; j < config.getInt("HOST_PES"); j++) {
                peList.add(new PeSimple(config.getInt("HOST_MIPS") / config.getInt("HOST_PES")));
            }
            Host host = new HostSimple(
                    config.getInt("RAM_PER_PE") * config.getInt("HOST_PES"),
                    config.getLong("HOST_BW"),
                    config.getLong("HOST_STORAGE"),
                    peList
            );
            hostList.add(host);
        }
        return new DatacenterSimple(simulation, hostList, new VmAllocationPolicyBestFit()); // ✅ Updated to Best Fit policy
    }
}
