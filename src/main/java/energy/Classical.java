package energy;

import org.cloudsimplus.allocationpolicies.VmAllocationPolicyBestFit;
import org.cloudsimplus.brokers.DatacenterBrokerSimple;
import org.cloudsimplus.builders.tables.CloudletsTableBuilder;
import org.cloudsimplus.cloudlets.Cloudlet;
import org.cloudsimplus.cloudlets.CloudletSimple;
import org.cloudsimplus.core.CloudSimPlus;
import org.cloudsimplus.datacenters.Datacenter;
import org.cloudsimplus.datacenters.DatacenterSimple;
import org.cloudsimplus.hosts.Host;
import org.cloudsimplus.hosts.HostSimple;
import org.cloudsimplus.power.models.PowerModelHostSimple;
import org.cloudsimplus.resources.Pe;
import org.cloudsimplus.resources.PeSimple;
import org.cloudsimplus.vms.Vm;
import org.cloudsimplus.vms.VmSimple;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;

public class Classical {
    private static Config config;
    private CloudSimPlus simulation;
    private DatacenterBrokerSimple broker;
    private List<Datacenter> datacenters;
    private List<Vm> vmList;
    private List<Cloudlet> cloudletList;

    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java Classical <config.json>");
            System.exit(1);
        }
        config = new Config(args[0]); // ✅ Uses Config.java to read JSON
        new Classical().run();
    }

    public void run() {
        simulation = new CloudSimPlus();
        broker = new DatacenterBrokerSimple(simulation);

        datacenters = createDatacenters();
        vmList = createVms();
        cloudletList = createCloudlets();

        broker.submitVmList(vmList);
        broker.submitCloudletList(cloudletList);

        System.out.println("Starting simulation...");
        simulation.start();

        new CloudletsTableBuilder(broker.getCloudletFinishedList()).build();
    }

    private List<Datacenter> createDatacenters() {
        List<Datacenter> datacenterList = new ArrayList<>();
        JsonArray datacentersConfig = config.getArray("DATACENTERS");

        for (JsonElement element : datacentersConfig) {
            JsonObject dcConfig = element.getAsJsonObject(); // ✅ Convert JsonElement to JsonObject
            datacenterList.add(createDatacenter(dcConfig));
        }

        return datacenterList;
    }

    private Datacenter createDatacenter(JsonObject dcConfig) {
        String name = dcConfig.get("name").getAsString();
        int numHosts = dcConfig.get("hosts").getAsInt();
        int hostPes = dcConfig.get("HOST_PES").getAsInt();
        int hostMips = dcConfig.get("HOST_MIPS").getAsInt();
        int hostRam = dcConfig.get("HOST_RAM").getAsInt();
        int hostBw = dcConfig.get("HOST_BW").getAsInt();
        long hostStorage = dcConfig.get("HOST_STORAGE").getAsLong();

        List<Host> hostList = new ArrayList<>();
        for (int i = 0; i < numHosts; i++) {
            List<Pe> peList = new ArrayList<>();
            for (int j = 0; j < hostPes; j++) {
                peList.add(new PeSimple(hostMips / hostPes));
            }
            Host host = new HostSimple(hostRam, hostBw, hostStorage, peList);
            host.setPowerModel(new PowerModelHostSimple(800, 80)); // Power consumption model
            hostList.add(host);
        }
        System.out.printf("✅ Created Datacenter: %s with %d hosts%n", name, numHosts);
        return new DatacenterSimple(simulation, hostList, new VmAllocationPolicyBestFit());
    }

    private List<Vm> createVms() {
        int numVms = config.getInt("VMS");
        int vmPes = config.getInt("VM_PES");
        int vmRam = config.getInt("VM_RAM");
        int vmBw = config.getInt("VM_BW");
        int vmStorage = config.getInt("VM_STORAGE");

        List<Vm> vmList = new ArrayList<>();
        for (int i = 0; i < numVms; i++) {
            Vm vm = new VmSimple(vmPes * 1000, vmPes)
                    .setRam(vmRam)
                    .setBw(vmBw)
                    .setSize(vmStorage);
            vmList.add(vm);
        }
        System.out.printf("✅ Created %d VMs%n", numVms);
        return vmList;
    }

    private List<Cloudlet> createCloudlets() {
        int numCloudlets = config.getInt("CLOUDLETS");
        int cloudletPes = config.getInt("CLOUDLET_PES");
        int cloudletLength = config.getInt("CLOUDLET_LENGTH");

        List<Cloudlet> cloudletList = new ArrayList<>();
        for (int i = 0; i < numCloudlets; i++) {
            Cloudlet cloudlet = new CloudletSimple(cloudletLength, cloudletPes);
            cloudlet.setSizes(1024);
            cloudletList.add(cloudlet);
        }
        System.out.printf("✅ Created %d Cloudlets%n", numCloudlets);
        return cloudletList;
    }
}
