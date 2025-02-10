package energy;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.cloudsimplus.allocationpolicies.VmAllocationPolicyFirstFit;
import org.cloudsimplus.brokers.DatacenterBrokerSimple;
import org.cloudsimplus.builders.tables.CloudletsTableBuilder;
import org.cloudsimplus.cloudlets.Cloudlet;
import org.cloudsimplus.cloudlets.CloudletSimple;
import org.cloudsimplus.core.CloudSimPlus;
import org.cloudsimplus.datacenters.Datacenter;
import org.cloudsimplus.datacenters.DatacenterSimple;
import org.cloudsimplus.hosts.Host;
import org.cloudsimplus.hosts.HostSimple;
import org.cloudsimplus.power.models.PowerModelHost;
import org.cloudsimplus.power.models.PowerModelHostSimple;
import org.cloudsimplus.resources.Pe;
import org.cloudsimplus.resources.PeSimple;
import org.cloudsimplus.utilizationmodels.UtilizationModelDynamic;
import org.cloudsimplus.vms.Vm;
import org.cloudsimplus.vms.VmSimple;

import java.util.ArrayList;
import java.util.List;

public class Classical {
    private static Config config;
    private CloudSimPlus simulation;
    private DatacenterBrokerSimple broker;
    private List<Datacenter> datacenters;
    private List<Vm> vmList;

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
        broker = new DatacenterBrokerSimple(simulation);
    
        // ✅ First, create VMs to avoid null pointer exception
        vmList = createVms();
    
        // ✅ Then, create datacenters
        datacenters = createDatacenters();
    
        brokerSubmit();
        simulation.start();
    
        printResults();
        printDatacenterEnergyConsumption();
    }
    

    private List<Datacenter> createDatacenters() {
        List<Datacenter> datacenterList = new ArrayList<>();
        JsonArray datacentersConfig = config.getArray("DATACENTERS");
    
        for (JsonElement element : datacentersConfig) {
            JsonObject dcConfig = element.getAsJsonObject();
            String dcName = dcConfig.get("name").getAsString();
            Datacenter datacenter = createDatacenter(dcConfig, dcName);
            datacenterList.add(datacenter);
            
            // ✅ Create cloudlets specifically for this datacenter
            List<Cloudlet> cloudletsForDatacenter = createCloudlets(dcConfig);
            broker.submitCloudletList(cloudletsForDatacenter);
    
            // ✅ Distribute VMs equally across datacenters
            int numVmsPerDatacenter = vmList.size() / datacentersConfig.size();
            for (int i = 0; i < numVmsPerDatacenter; i++) {
                Vm vm = vmList.get(i);
                datacenter.getHostList().get(i % datacenter.getHostList().size()).createVm(vm);
            }
        }
        return datacenterList;
    }
    

    private Datacenter createDatacenter(JsonObject dcConfig, String name) {
    int numHosts = dcConfig.get("hosts").getAsInt();
    double MAX_POWER = dcConfig.get("MAX_POWER").getAsDouble();
    double STATIC_POWER = dcConfig.get("STATIC_POWER").getAsDouble();
    double HOST_START_UP_POWER = dcConfig.get("HOST_START_UP_POWER").getAsDouble();
    double HOST_SHUT_DOWN_POWER = dcConfig.get("HOST_SHUT_DOWN_POWER").getAsDouble();

    List<Host> hostList = new ArrayList<>(numHosts);
    for (int i = 0; i < numHosts; i++) {
        Host host = createHost(dcConfig);
        
        // ✅ FIX: Use PowerModelHost instead of PowerModelHostSimple
        PowerModelHost powerModel = new PowerModelHostSimple(MAX_POWER, STATIC_POWER)
            .setStartupPower(HOST_START_UP_POWER)
            .setShutDownPower(HOST_SHUT_DOWN_POWER);
        
        host.setPowerModel(powerModel);
        host.enableUtilizationStats();
        hostList.add(host);
    }

    DatacenterSimple datacenter = new DatacenterSimple(simulation, hostList, new VmAllocationPolicyFirstFit());
    datacenter.setName(name);
    return datacenter;
}


    private Host createHost(JsonObject dcConfig) {
        int hostPes = dcConfig.get("HOST_PES").getAsInt();
        int hostMips = dcConfig.get("HOST_MIPS").getAsInt();
        int hostRam = dcConfig.get("HOST_RAM").getAsInt();
        int hostBw = dcConfig.get("HOST_BW").getAsInt();
        long hostStorage = dcConfig.get("HOST_STORAGE").getAsLong();

        List<Pe> peList = new ArrayList<>();
        for (int i = 0; i < hostPes; i++) {
            peList.add(new PeSimple(hostMips));
        }

        return new HostSimple(hostRam, hostBw, hostStorage, peList);
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
            vm.enableUtilizationStats();
            vmList.add(vm);
        }
        return vmList;
    }

    private List<Cloudlet> createCloudlets(JsonObject dcConfig) {
        int numCloudlets = dcConfig.get("cloudlets").getAsInt();
        int cloudletPes = config.getInt("CLOUDLET_PES");
        int cloudletLength = config.getInt("CLOUDLET_LENGTH");

        List<Cloudlet> cloudletList = new ArrayList<>();
        for (int i = 0; i < numCloudlets; i++) {
            Cloudlet cloudlet = new CloudletSimple(cloudletLength, cloudletPes, new UtilizationModelDynamic(0.7));
            cloudlet.setSizes(1024);
            cloudletList.add(cloudlet);
        }
        return cloudletList;
    }

    private void brokerSubmit() {
        broker.submitVmList(vmList);
    }

    private void printResults() {
        System.out.println("\n------------------------------- Simulation Results -------------------------------");
        new CloudletsTableBuilder(broker.getCloudletFinishedList()).build();
    }

    private void printDatacenterEnergyConsumption() {
        System.out.println("\n------------------------------- Datacenter Energy Consumption -------------------------------");

        for (Datacenter datacenter : datacenters) {
            double totalEnergyConsumptionWatts = 0;
            double totalUtilization = 0;
            int totalHosts = datacenter.getHostList().size();

            for (Host host : datacenter.getHostList()) {
                double hostUtilization = host.getVmCreatedList()
                        .stream()
                        .mapToDouble(vm -> vm.getCpuUtilizationStats().getMean())
                        .sum();

                double utilizationPercentMean = hostUtilization / Math.max(1, host.getVmCreatedList().size());
                double wattsMean = host.getPowerModel().getPower(utilizationPercentMean);
                double hostAliveTime = simulation.clock() - host.getFirstStartTime();
                double powerConsumptionWatts = wattsMean * hostAliveTime;

                totalEnergyConsumptionWatts += powerConsumptionWatts;
                totalUtilization += utilizationPercentMean;
            }

            double avgUtilization = (totalHosts == 0) ? 0 : (totalUtilization / totalHosts) * 100;
            double totalPowerConsumptionKWh = totalEnergyConsumptionWatts / (1000 * 3600);

            System.out.printf("Datacenter: %-15s | Hosts: %4d | Avg CPU Usage: %6.1f%% | Total Energy: %.6f kWh%n",
                    datacenter.getName(), totalHosts, avgUtilization, totalPowerConsumptionKWh);
        }
    }
}
