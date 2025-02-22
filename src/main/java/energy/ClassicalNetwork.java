package energy;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import org.cloudsimplus.vms.VmResourceStats;
import org.cloudsimplus.builders.tables.CloudletsTableBuilder;
import org.cloudsimplus.core.CloudSimPlus;
import org.cloudsimplus.brokers.DatacenterBroker;
import org.cloudsimplus.brokers.DatacenterBrokerSimple;
import org.cloudsimplus.datacenters.Datacenter;
import org.cloudsimplus.datacenters.DatacenterSimple;
import org.cloudsimplus.cloudlets.network.CloudletSendTask;
import org.cloudsimplus.cloudlets.network.NetworkCloudlet;
import org.cloudsimplus.cloudlets.Cloudlet;
import org.cloudsimplus.cloudlets.CloudletSimple;
import org.cloudsimplus.hosts.Host;
import org.cloudsimplus.hosts.HostSimple;
import org.cloudsimplus.listeners.EventInfo;
import org.cloudsimplus.network.topologies.BriteNetworkTopology;
import org.cloudsimplus.resources.Pe;
import org.cloudsimplus.resources.PeSimple;
import org.cloudsimplus.vms.HostResourceStats;
import org.cloudsimplus.vms.Vm;
import org.cloudsimplus.power.models.PowerModelHostSimple;
import org.cloudsimplus.power.models.PowerModelHostSpec;
import org.cloudsimplus.power.models.PowerModelDatacenterSimple;
import org.cloudsimplus.power.models.PowerModelHost;
import org.cloudsimplus.allocationpolicies.*;
import org.cloudsimplus.vms.VmSimple;
import org.cloudsimplus.schedulers.vm.*;
import org.cloudsimplus.utilizationmodels.*;

import static org.cloudsimplus.util.TimeUtil.currentTimeSecs;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

public class ClassicalNetwork {
    private static Config config;
    private CloudSimPlus simulation;
    private List<DatacenterBrokerSimple> brokers;
    private List<Datacenter> datacenters;
    int totalCloudletsGenerated = 0;
    
    // private static final int SIMULATION_DURATION = 10; // Total simulation time in seconds

    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java Classical <config.json>");
            System.exit(1);
        }
        config = new Config(args[0]);
        new ClassicalNetwork().run();
    }

    public void run() {
        JsonArray datacentersConfig = config.getArray("DATACENTERS");
        simulation = new CloudSimPlus();

        brokers = new ArrayList<>();
        datacenters = createDatacenters(datacentersConfig);
        configureNetwork();

        createBrokersVms(datacentersConfig);

        simulation.addOnClockTickListener(eventInfo -> {
            generateCloudletsPerSecond(eventInfo);
        });

        // Ensure simulation waits until all Cloudlets are finished before stopping
        simulation.addOnSimulationPauseListener(eventInfo -> {
            if (allCloudletsCompleted()) {
                System.out.println(
                        "All cloudlets completed. Stopping simulation at " + eventInfo.getTime() + " seconds.");
                simulation.terminate();
            }
        });

        simulation.start();

        brokers.forEach(ClassicalNetwork::createCloudletsResultTable);
        printDatacenterEnergyConsumption();
        printDatacenterEnergyConsumptionCSV();
    }


    private void configureNetwork() {
        // Step 1: Load BRITE Network Topology
        String topologyFile = "src/main/resources/brite/topology.brite";
        BriteNetworkTopology networkTopology = new BriteNetworkTopology(topologyFile);

        // Step 2: Add Network Topology to CloudSim Simulation
        simulation.setNetworkTopology(networkTopology);

        // Step 3: Map Datacenters in a Loop
        for (int i = 0; i < datacenters.size(); i++) {
            networkTopology.mapNode(datacenters.get(i), i); // Map Datacenter i to Node i
            System.out.println("✅ Mapped " + datacenters.get(i).getName() + " to BRITE Node " + i);
        }

        // Step 4: Map Brokers in a Loop
        for (int i = 0; i < brokers.size(); i++) {
            int nodeId = datacenters.size() + i; // Broker nodes start after Datacenter nodes
            networkTopology.mapNode(brokers.get(i), nodeId);
            System.out.println("✅ Mapped Broker " + (i + 1) + " to BRITE Node " + nodeId);
        }

        System.out.println("🎯 Network topology successfully applied!");
    }

    private boolean allCloudletsCompleted() {
        for (DatacenterBrokerSimple broker : brokers) {
            if (!broker.getCloudletFinishedList().containsAll(broker.getCloudletSubmittedList())) {
                return false; // Some Cloudlets are still running
            }
        }
        return true; // All Cloudlets are completed
    }

    private List<Datacenter> createDatacenters(JsonArray datacentersConfig) {
        List<Datacenter> datacenters = new ArrayList<>();
        for (JsonElement element : datacentersConfig) {
            JsonObject dcConfig = element.getAsJsonObject();
            Datacenter datacenter = createDatacenter(dcConfig);
            datacenters.add(datacenter);
        }
        return datacenters;
    }

    private Datacenter createDatacenter(JsonObject dcConfig) {
        int numHosts = dcConfig.get("hosts").getAsInt();
        List<Host> hostList = new ArrayList<>();
        for (int i = 0; i < numHosts; i++) {
            Host host = createHost(i, dcConfig);
            hostList.add(host);
        }
        VmAllocationPolicy policy = getVmAllocationPolicy(dcConfig);

        Datacenter datacenter = new DatacenterSimple(simulation, hostList, policy);
        datacenter.setName(dcConfig.get("name").getAsString());
        datacenter.setSchedulingInterval(dcConfig.get("SCHEDULING_INTERVAL").getAsInt());
        return datacenter;
    }

    public VmAllocationPolicy getVmAllocationPolicy(JsonObject dcConfig) {
        String key = "VmAllocationPolicy";
        String type = dcConfig.has(key) ? dcConfig.get(key).getAsString() : "";

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

    private Host createHost(final int id, JsonObject dcConfig) {
        JsonObject hostSpec = dcConfig.getAsJsonObject("host_spec");
        int hostPes = hostSpec.get("HOST_PES").getAsInt();
        int hostMips = hostSpec.get("HOST_MIPS").getAsInt();
        int hostRam = hostSpec.get("HOST_RAM").getAsInt();
        int hostBw = hostSpec.get("HOST_BW").getAsInt();
        long hostStorage = hostSpec.get("HOST_STORAGE").getAsLong();
        double HOST_START_UP_DELAY = hostSpec.get("HOST_START_UP_DELAY").getAsDouble();
        double HOST_SHUT_DOWN_DELAY = hostSpec.get("HOST_SHUT_DOWN_DELAY").getAsDouble();
        double HOST_START_UP_POWER = hostSpec.get("HOST_START_UP_POWER").getAsDouble();
        double HOST_SHUT_DOWN_POWER = hostSpec.get("HOST_SHUT_DOWN_POWER").getAsDouble();
        VmScheduler vmScheduler = getVmScheduler(dcConfig);

        List<Pe> peList = new ArrayList<>();
        for (int i = 0; i < hostPes; i++) {
            peList.add(new PeSimple(hostMips));
        }

        final var host = new HostSimple(hostRam, hostBw, hostStorage, peList);
        host.setStartupDelay(HOST_START_UP_DELAY)
                .setShutDownDelay(HOST_SHUT_DOWN_DELAY);

        final var powerModel = getPowerModel(dcConfig);
        powerModel
                .setStartupPower(HOST_START_UP_POWER)
                .setShutDownPower(HOST_SHUT_DOWN_POWER);

        host.setId(id)
                .setVmScheduler(vmScheduler)
                .setPowerModel(powerModel);
        host.enableUtilizationStats();

        return host;
    }

    public PowerModelHost getPowerModel(JsonObject dcConfig) {
        String key = "power_spec_path";
        String type = dcConfig.has(key) ? dcConfig.get(key).getAsString() : "Manual";

        if (type == "Manual") {
            JsonObject powerSpec = dcConfig.getAsJsonObject("power_spec");
            double MAX_POWER = powerSpec.get("MAX_POWER").getAsDouble();
            double STATIC_POWER = powerSpec.get("STATIC_POWER").getAsDouble();
            return new PowerModelHostSimple(MAX_POWER, STATIC_POWER);
        } else {
            PowerModelHostSpec DEF_POWER_MODEL = PowerModelHostSpec.getInstance(type);
            return new PowerModelHostSpec(DEF_POWER_MODEL.getPowerSpecs());
        }
    }

    public VmScheduler getVmScheduler(JsonObject dcConfig) {
        String key = "VmScheduler";
        String type = dcConfig.has(key) ? dcConfig.get(key).getAsString() : "";

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

    private void generateCloudletsPerSecond(EventInfo eventInfo) {
        JsonArray datacentersConfig = config.getArray("DATACENTERS");
        int index = 0;
        for (DatacenterBrokerSimple broker : brokers) {
            JsonObject dcConfig = datacentersConfig.get(index).getAsJsonObject();
            int lastCloudlets = dcConfig.get("lastCloudlets").getAsInt();
            if (totalCloudletsGenerated > lastCloudlets -1) {
                System.out.println("Stopping cloudlet generation at " + eventInfo.getTime() + " seconds.");
                simulation.removeOnClockTickListener(this::generateCloudletsPerSecond);
                return; // Prevent further execution
            }
            int tps = dcConfig.get("cloudlets").getAsInt();
            List<Cloudlet> cloudlets = createDynamicCloudlets(totalCloudletsGenerated, tps, dcConfig);
            broker.submitCloudletList(cloudlets);
            totalCloudletsGenerated += tps;
        }
    }

    private List<Cloudlet> createDynamicCloudlets(int startId, int TRANSACTIONS_PER_SECOND, JsonObject dcConfig) {
        JsonObject cloudletSpec = dcConfig.getAsJsonObject("cloudlet_spec");
        long cloudletLength = cloudletSpec.get("CLOUDLET_LENGTH").getAsLong();
        int cloudletPes = cloudletSpec.get("CLOUDLET_PES").getAsInt();
        long fileSize = cloudletSpec.get("FILE_SIZE").getAsLong();
        long outputSize = cloudletSpec.get("OUTPUT_SIZE").getAsLong();
    
        List<Cloudlet> cloudletList = new ArrayList<>();
        UtilizationModel utilization = getUtilizationModel(dcConfig);
    
        for (int i = 0; i < TRANSACTIONS_PER_SECOND; i++) {
            NetworkCloudlet senderCloudlet = new NetworkCloudlet(cloudletPes);
            NetworkCloudlet receiverCloudlet = new NetworkCloudlet(cloudletPes);
    
            senderCloudlet.setFileSize(fileSize)
                          .setOutputSize(outputSize)
                          .setLength(cloudletLength)
                          .setUtilizationModelCpu(utilization)
                          .setUtilizationModelRam(utilization)
                          .setUtilizationModelBw(utilization);
    
            receiverCloudlet.setFileSize(fileSize)
                            .setOutputSize(outputSize)
                            .setLength(cloudletLength)
                            .setUtilizationModelCpu(utilization)
                            .setUtilizationModelRam(utilization)
                            .setUtilizationModelBw(utilization);
    
            CloudletSendTask sendTask = new CloudletSendTask(startId + i);
            senderCloudlet.addTask(sendTask);
    
            // Ensure that both sender and receiver cloudlets have assigned VMs before sending packets
            if (senderCloudlet.isBoundToVm() && receiverCloudlet.isBoundToVm()) {
                sendTask.addPacket(receiverCloudlet, outputSize);
            } else {
                System.err.println("⚠️ Warning: Cloudlets must be assigned to VMs before sending packets.");
            }
    
            cloudletList.add(senderCloudlet);
            cloudletList.add(receiverCloudlet);
        }
        
        return cloudletList;
    }
    
    

    private void createBrokersVms(JsonArray datacentersConfig) {
        int vmGlobalIndex = 0;
        for (int index = 0; index < datacenters.size(); index++) {
            Datacenter dc = datacenters.get(index);
            JsonObject dcConfig = datacentersConfig.get(index).getAsJsonObject();
            DatacenterBrokerSimple broker = createBroker(dc, dcConfig);
            final var vmList = createVms(vmGlobalIndex, dcConfig);

            broker.submitVmList(vmList);
            broker.setVmDestructionDelay(Double.MAX_VALUE);
            brokers.add(broker);

            vmGlobalIndex += dcConfig.get("vm").getAsInt();
        }
    }

    private DatacenterBrokerSimple createBroker(final Datacenter dc, JsonObject dcConfig) {
        DatacenterBrokerSimple broker = new DatacenterBrokerSimple(simulation);
        broker.setName(dcConfig.get("name").getAsString());
        broker.setLastSelectedDc(dc);
        return broker;
    }

    private List<Vm> createVms(int startId, JsonObject dcConfig) {
        JsonObject hostSpec = dcConfig.getAsJsonObject("host_spec");
        long hostMips = hostSpec.get("HOST_MIPS").getAsLong();
        int hostPes = hostSpec.get("HOST_PES").getAsInt();
        int hostRam = hostSpec.get("HOST_RAM").getAsInt();
        int hostBw = hostSpec.get("HOST_BW").getAsInt();
        long hostStorage = hostSpec.get("HOST_STORAGE").getAsLong();

        int vms = dcConfig.get("vm").getAsInt();
        int vmPes = hostPes / vms;
        long vmRam = hostRam / vms;
        long vmBw = hostBw / vms;
        long vmStorage = hostStorage / vms;

        List<Vm> vmList = new ArrayList<>();
        for (int i = 0; i < vms; i++) {
            Vm vm = new VmSimple(startId + i, hostMips / hostPes, vmPes);
            vm.setRam(vmRam).setBw(vmBw).setSize(vmStorage);
            vm.enableUtilizationStats();
            vmList.add(vm);
        }
        return vmList;
    }

    // private List<Cloudlet> createCloudlets(int startId, JsonObject dcConfig) {
    //     JsonObject cloudletSpec = dcConfig.getAsJsonObject("cloudlet_spec");
    //     long cloudletLength = cloudletSpec.get("CLOUDLET_LENGTH").getAsLong();
    //     int cloudletPes = cloudletSpec.get("CLOUDLET_PES").getAsInt();
    //     long fileSize = cloudletSpec.get("FILE_SIZE").getAsLong();
    //     long outputSize = cloudletSpec.get("OUTPUT_SIZE").getAsLong();
    //     long cloudletCount = dcConfig.get("cloudlets").getAsLong();

    //     List<Cloudlet> cloudletList = new ArrayList<>();
    //     UtilizationModel Utilization = getUtilizationModel(dcConfig);

    //     for (int i = 0; i < cloudletCount; i++) {
    //         Cloudlet cloudlet = new CloudletSimple(startId + i, cloudletLength, cloudletPes)
    //         // Cloudlet cloudlet = new NetworkCloudlet(cloudletPes)
    //                 .setFileSize(fileSize)
    //                 .setOutputSize(outputSize)
    //                 .setUtilizationModelCpu(Utilization)
    //                 .setUtilizationModelRam(Utilization)
    //                 .setUtilizationModelBw(Utilization);
    //         cloudletList.add(cloudlet);
    //     }
    //     return cloudletList;
    // }

    public UtilizationModel getUtilizationModel(JsonObject dcConfig) {
        String key = "UtilizationModel";
        String type = dcConfig.has(key) ? dcConfig.get(key).getAsString() : "";

        switch (type) {
            case "F":
                return new UtilizationModelFull();
            case "D":
                return new UtilizationModelDynamic(0.5);
            default:
                System.err.println("Warning: Unknown UtilizationModel '" + type + "', using default (F)");
                return new UtilizationModelFull(); // Default
        }
    }

    private static void createCloudletsResultTable(final DatacenterBroker broker) {
        new CloudletsTableBuilder(broker.getCloudletFinishedList())
                .setTitle(broker.getName())
                .build();
    }

    private void printDatacenterEnergyConsumption() {
        System.out.println("\n---------------- Detailed Datacenter Report ----------------");

        for (int i = 0; i < datacenters.size(); i++) {
            Datacenter dc = datacenters.get(i);
            DatacenterBrokerSimple broker = brokers.get(i);
            int totalCloudlets = broker.getCloudletSubmittedList().size();

            double totalEnergy = 0.0;
            double totalUtilization = 0.0;
            int utilizedHosts = 0;
            int totalHosts = dc.getHostList().size();

            System.out.printf("\nDatacenter Name      : %s%n", dc.getName());
            System.out.printf("Total Hosts          : %d%n", totalHosts);

            // Display Host Specifications and Utilization
            boolean flag = true;
            for (Host host : dc.getHostList()) {
                // Mean CPU utilization and power consumption
                final HostResourceStats cpuStats = host.getCpuUtilizationStats();
                final double utilizationPercentMean = cpuStats.getMean(); // Mean CPU utilization
                final double wattsMean = host.getPowerModel().getPower(utilizationPercentMean); // Mean power
                                                                                                // consumption
                // Calculate host alive time (in seconds)
                final double hostAliveTime = host.getSimulation().clock() - host.getFirstStartTime();
                // Calculate total power consumption in watt-seconds (Joules)
                final double hostPowerConsumptionWatts = wattsMean * hostAliveTime;
                // Convert total power consumption to kilowatt-hours (kWh)
                final double hostPowerConsumptionKWh = hostPowerConsumptionWatts / (1000 * 3600);
                totalEnergy += hostPowerConsumptionKWh;
                totalUtilization += utilizationPercentMean;
                utilizedHosts++;
                if (flag) {
                    // Host Specifications
                    System.out.printf("  Host ID                : %d%n", host.getId());
                    System.out.printf("  RAM                    : %d MB%n", host.getRam().getCapacity());
                    System.out.printf("  Bandwidth              : %d MBps%n", host.getBw().getCapacity());
                    System.out.printf("  Storage                : %d MB%n", host.getStorage().getCapacity());
                    System.out.printf("  Number of PEs          : %d%n", host.getPeList().size());
                    System.out.printf("  CPU Usage mean         : %6.1f%%\n", utilizationPercentMean * 100);
                    System.out.printf("  Power Consumption mean : %8.0f W\n", wattsMean);
                    System.out.printf("  Host Power Consumption : %.1f W-s (%.6f kWh)\n", hostPowerConsumptionWatts,
                            hostPowerConsumptionKWh);
                    System.out.printf("  Host Alive Time        : %.1f s%n", hostAliveTime);
                    flag = false;
                }
            }

            // If no cloudlets, use only static power instead of NaN
            if (totalCloudlets == 0) {
                System.out.println(
                        "Warning: No cloudlets found in " + dc.getName() + ". Using static power consumption.");
                totalEnergy = dc.getHostList().stream()
                        .mapToDouble(h -> h.getPowerModel().getPower(0))
                        .sum() * dc.getSimulation().clock() / (1000 * 3600); // Convert to kWh
            }

            double avgUtilization = utilizedHosts > 0 ? totalUtilization / utilizedHosts : -1;

            System.out.printf("Total VMs            : %d%n", broker.getVmCreatedList().size());
            System.out.printf("Total Cloudlets      : %d%n", broker.getCloudletSubmittedList().size());
            System.out.printf("Average CPU Utilization: %.2f%%%n", avgUtilization * 100);
            System.out.printf("Total Energy Used    : %.6f kWh%n", totalEnergy);
            System.out.println("------------------------------");
        }
    }

    private void printDatacenterEnergyConsumptionCSV() {
        String fileName = "output/csv/" + config.get_filename() + ".csv";
        try (PrintWriter writer = new PrintWriter(new FileWriter(fileName))) {
            // Write CSV header
            writer.println("Datacenter Name,Total Energy Used (kWh), cloudlets");

            for (int i = 0; i < datacenters.size(); i++) {
                Datacenter dc = datacenters.get(i);
                DatacenterBrokerSimple broker = brokers.get(i);
                int totalCloudlets = broker.getCloudletSubmittedList().size();

                double totalEnergy = 0.0;
                // double totalUtilization = 0.0;
                // int utilizedHosts = 0;
                // int totalHosts = dc.getHostList().size();

                for (Host host : dc.getHostList()) {
                    // Mean CPU utilization and power consumption
                    final HostResourceStats cpuStats = host.getCpuUtilizationStats();
                    final double utilizationPercentMean = cpuStats.getMean(); // Mean CPU utilization
                    final double wattsMean = host.getPowerModel().getPower(utilizationPercentMean); // Mean power
                                                                                                    // consumption
                    // Calculate host alive time (in seconds)
                    final double hostAliveTime = host.getSimulation().clock() - host.getFirstStartTime();
                    // Calculate total power consumption in watt-seconds (Joules)
                    final double hostPowerConsumptionWatts = wattsMean * hostAliveTime;
                    // Convert total power consumption to kilowatt-hours (kWh)
                    final double hostPowerConsumptionKWh = hostPowerConsumptionWatts / (1000 * 3600);
                    totalEnergy += hostPowerConsumptionKWh;
                    // totalUtilization += utilizationPercentMean;
                    // utilizedHosts++;

                    // // Write host details to CSV
                    // writer.printf("%s,%d,%d,%d,%d,%d,%d,%.1f,%.0f,%.1f,%.6f,%.1f%n",
                    // dc.getName(),
                    // totalHosts,
                    // host.getId(),
                    // host.getRam().getCapacity(),
                    // host.getBw().getCapacity(),
                    // host.getStorage().getCapacity(),
                    // host.getPeList().size(),
                    // utilizationPercentMean * 100,
                    // wattsMean,
                    // hostPowerConsumptionWatts,
                    // hostPowerConsumptionKWh,
                    // hostAliveTime);
                }

                // If no cloudlets, use only static power instead of NaN
                if (totalCloudlets == 0) {
                    System.out.println(
                            "Warning: No cloudlets found in " + dc.getName() + ". Using static power consumption.");
                    totalEnergy = dc.getHostList().stream()
                            .mapToDouble(h -> h.getPowerModel().getPower(0))
                            .sum() * dc.getSimulation().clock() / (1000 * 3600); // Convert to kWh
                }

                // double avgUtilization = utilizedHosts > 0 ? totalUtilization / utilizedHosts
                // : -1;

                // Write summary details to CSV
                writer.printf("%s,%.6f,%d%n",
                        dc.getName(),
                        totalEnergy,
                        totalCloudlets);
            }
            System.out.println("Datacenter energy consumption report has been written to " + fileName);
        } catch (IOException e) {
            System.err.println("Error writing to CSV file: " + e.getMessage());
        }
    }

}
