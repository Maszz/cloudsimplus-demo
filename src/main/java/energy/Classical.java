package energy;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.cloudsimplus.allocationpolicies.VmAllocationPolicyRoundRobin;
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

import java.util.ArrayList;
import java.util.List;

public class Classical {
    private static Config config;
    private CloudSimPlus simulation;
    private List<DatacenterBrokerSimple> brokers;
    private List<Datacenter> datacenters;

    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java Classical <config.json>");
            System.exit(1);
        }
        config = new Config(args[0]);
        new Classical().run();
    }

    public void run() {
        simulation = new CloudSimPlus();
        brokers = new ArrayList<>();
        datacenters = new ArrayList<>();

        JsonArray datacentersConfig = config.getArray("DATACENTERS");
        createBrokers(datacentersConfig);

        simulation.start();
        printResults();
        printDatacenterEnergyConsumption();
    }

    private void createBrokers(JsonArray datacentersConfig) {
        for (JsonElement element : datacentersConfig) {
            JsonObject dcConfig = element.getAsJsonObject();
            String dcName = dcConfig.get("name").getAsString();

            Datacenter datacenter = createDatacenter(dcConfig, dcName);
            DatacenterBrokerSimple broker = createBroker();
            broker.setName(dcName);

            brokers.add(broker);
            datacenters.add(datacenter);

            List<Vm> vmsForDatacenter = createVms(dcConfig);
            List<Cloudlet> cloudletsForDatacenter = createCloudlets(dcConfig);

            broker.submitVmList(vmsForDatacenter);
            broker.submitCloudletList(cloudletsForDatacenter);
        }
    }

    private DatacenterBrokerSimple createBroker() {
        return new DatacenterBrokerSimple(simulation);
    }

    private Datacenter createDatacenter(JsonObject dcConfig, String name) {
        int numHosts = dcConfig.get("hosts").getAsInt();
        JsonObject hostSpec = dcConfig.getAsJsonObject("host_spec");
        JsonObject powerSpec = dcConfig.getAsJsonObject("power_spec");
    
        List<Host> hostList = new ArrayList<>();
        for (int i = 0; i < numHosts; i++) {
            Host host = createHost(hostSpec);
            PowerModelHostSimple powerModel = new PowerModelHostSimple(
                powerSpec.get("MAX_POWER").getAsDouble(),
                powerSpec.get("STATIC_POWER").getAsDouble()
            );
            host.setPowerModel(powerModel);
            host.enableUtilizationStats();
            hostList.add(host);
        }
    
        Datacenter datacenter = new DatacenterSimple(simulation, hostList, new VmAllocationPolicyRoundRobin());
        datacenter.setName(name); // ✅ Explicitly setting the datacenter name
        return datacenter;
    }

    private Host createHost(JsonObject hostSpec) {
        int hostPes = hostSpec.get("HOST_PES").getAsInt();
        int hostMips = hostSpec.get("HOST_MIPS").getAsInt();
        int hostRam = hostSpec.get("HOST_RAM").getAsInt();
        int hostBw = hostSpec.get("HOST_BW").getAsInt();
        long hostStorage = hostSpec.get("HOST_STORAGE").getAsLong();

        List<Pe> peList = new ArrayList<>();
        for (int i = 0; i < hostPes; i++) {
            peList.add(new PeSimple(hostMips));  // ✅ Corrected MIPS retrieval
        }

        return new HostSimple(hostRam, hostBw, hostStorage, peList);
    }

    private List<Vm> createVms(JsonObject dcConfig) {
        JsonObject vmSpec = dcConfig.getAsJsonObject("vm_spec");
        JsonObject hostSpec = dcConfig.getAsJsonObject("host_spec");

        int hostMips = hostSpec.get("HOST_MIPS").getAsInt();
        int hostPes = hostSpec.get("HOST_PES").getAsInt();

        int vmPes = vmSpec.get("VM_PES").getAsInt();
        int vmRam = vmSpec.get("VM_RAM").getAsInt();
        int vmBw = vmSpec.get("VM_BW").getAsInt();
        int vmStorage = vmSpec.get("VM_STORAGE").getAsInt();

        List<Vm> vmList = new ArrayList<>();
        int vmsPerDatacenter = dcConfig.get("vm").getAsInt();
        for (int i = 0; i < vmsPerDatacenter; i++) {
            Vm vm = new VmSimple(hostMips / hostPes, vmPes)
                    .setRam(vmRam)
                    .setBw(vmBw)
                    .setSize(vmStorage);
            vm.enableUtilizationStats();
            vmList.add(vm);
        }
        return vmList;
    }

    private List<Cloudlet> createCloudlets(JsonObject dcConfig) {
        JsonObject cloudletSpec = dcConfig.getAsJsonObject("cloudlet_spec");

        int cloudletLength = cloudletSpec.get("CLOUDLET_LENGTH").getAsInt();
        int cloudletPes = cloudletSpec.get("CLOUDLET_PES").getAsInt();

        List<Cloudlet> cloudletList = new ArrayList<>();
        int cloudletsPerDatacenter = dcConfig.get("cloudlets").getAsInt();
        for (int i = 0; i < cloudletsPerDatacenter; i++) {
            Cloudlet cloudlet = new CloudletSimple(cloudletLength, cloudletPes);
            cloudlet.setSizes(1024);
            cloudletList.add(cloudlet);
        }
        return cloudletList;
    }

    private void printResults() {
        System.out.println("\n---------------- Simulation Results ----------------");
        for (DatacenterBrokerSimple broker : brokers) {
            new CloudletsTableBuilder(broker.getCloudletFinishedList()).build();
        }
    }

    private void printDatacenterEnergyConsumption() {
        System.out.println("\n---------------- Detailed Datacenter Report ----------------");
        for (int i = 0; i < datacenters.size(); i++) {
            Datacenter dc = datacenters.get(i);
            DatacenterBrokerSimple broker = brokers.get(i);
    
            double totalEnergy = 0.0;
            double totalUtilization = 0.0;
            int utilizedHosts = 0;
    
            for (Host host : dc.getHostList()) {
                double utilizationMean = host.getCpuUtilizationStats().getMean();
                if (!Double.isNaN(utilizationMean)) {
                    totalEnergy += host.getPowerModel().getPower(utilizationMean);
                    totalUtilization += utilizationMean;
                    utilizedHosts++;
                }
            }
    
            double avgUtilization = utilizedHosts > 0 ? totalUtilization / utilizedHosts : 0.0;
    
            int totalVMs = broker.getVmCreatedList().size();
            int totalCloudlets = broker.getCloudletSubmittedList().size();
    
            Host firstHost = dc.getHostList().get(0);
            int hostPes = firstHost.getPeList().size();
            long hostRam = firstHost.getRam().getCapacity();
            long hostBw = firstHost.getBw().getCapacity();
            long hostStorage = firstHost.getStorage().getCapacity();
    
            System.out.println("\nDatacenter Report:");
            System.out.printf("Datacenter Name      : %s%n", dc.getName());
            System.out.printf("Datacenter Broker    : %s%n", broker.getName());
            System.out.printf("Total Hosts          : %d%n", dc.getHostList().size());
            System.out.printf("Host Spec            : %d PEs | %d RAM | %d BW | %d Storage%n",
                hostPes, hostRam, hostBw, hostStorage);
            System.out.printf("Total VMs            : %d%n", totalVMs);
            System.out.printf("Total Cloudlets      : %d%n", totalCloudlets);
            System.out.printf("CPU Utilization      : %.2f%%%n", avgUtilization * 100);
            System.out.printf("Total Energy Used    : %.6f kWh%n", totalEnergy / 1000);
        }
    }    
}
