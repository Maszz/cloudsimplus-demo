package energy.bak;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import energy.Config;

import org.cloudsimplus.cloudlets.Cloudlet;
import org.cloudsimplus.cloudlets.network.CloudletReceiveTask;
import org.cloudsimplus.cloudlets.network.CloudletSendTask;
import org.cloudsimplus.cloudlets.network.NetworkCloudlet;
import org.cloudsimplus.brokers.DatacenterBrokerSimple;
import org.cloudsimplus.builders.tables.CloudletsTableBuilder;
import org.cloudsimplus.datacenters.Datacenter;
import org.cloudsimplus.datacenters.DatacenterSimple;
import org.cloudsimplus.hosts.Host;
import org.cloudsimplus.hosts.HostSimple;
import org.cloudsimplus.listeners.EventInfo;
import org.cloudsimplus.resources.Pe;
import org.cloudsimplus.resources.PeSimple;
import org.cloudsimplus.vms.Vm;
import org.cloudsimplus.vms.VmSimple;
import org.slf4j.LoggerFactory;
import org.cloudsimplus.utilizationmodels.UtilizationModel;
import org.cloudsimplus.utilizationmodels.UtilizationModelDynamic;
import org.cloudsimplus.utilizationmodels.UtilizationModelFull;
import org.cloudsimplus.schedulers.vm.VmScheduler;
import org.cloudsimplus.schedulers.vm.VmSchedulerSpaceShared;
import org.cloudsimplus.schedulers.vm.VmSchedulerTimeShared;
import org.cloudsimplus.allocationpolicies.VmAllocationPolicy;
import org.cloudsimplus.allocationpolicies.VmAllocationPolicyBestFit;
import org.cloudsimplus.allocationpolicies.VmAllocationPolicyFirstFit;
import org.cloudsimplus.allocationpolicies.VmAllocationPolicyRandom;
import org.cloudsimplus.allocationpolicies.VmAllocationPolicyRoundRobin;
import org.cloudsimplus.allocationpolicies.VmAllocationPolicySimple;
import org.cloudsimplus.network.topologies.BriteNetworkTopology;
import org.cloudsimplus.power.models.PowerModelHost;
import org.cloudsimplus.power.models.PowerModelHostSimple;
import org.cloudsimplus.power.models.PowerModelHostSpec;
import org.cloudsimplus.core.CloudSimPlus;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;

public class ClassicalNetwork {
    private static Config config;
    private CloudSimPlus simulation;
    private DatacenterBrokerSimple broker1;
    private DatacenterBrokerSimple broker2;
    private Datacenter datacenter1;
    private Datacenter datacenter2;
    private int totalCloudletsGenerated = 0;
    private static final Logger logger = LoggerFactory.getLogger(ClassicalNetwork.class);

    public static void main(String[] args) {
        // Log.setLevel(ch.qos.logback.classic.Level.INFO);
        logger.info("\n===== Stage 0: Initialization =====");
        if (args.length < 1) {
            logger.error("Usage: java ClassicalNetwork <config.json>");
            System.exit(1);
        }
        config = new Config(args[0]);
        new ClassicalNetwork().run();
    }

    public void run() {
        JsonArray dcConfig = config.getArray("DATACENTERS");
        simulation = new CloudSimPlus();
        logger.info("\n===== Stage 1: Datacenter and Host Setup =====");
        // datacenters = createDatacenters(datacentersConfig);
        datacenter1 = createDatacenter((JsonObject) dcConfig.get(0));
        datacenter2 = createDatacenter((JsonObject) dcConfig.get(1));
        logger.info("\n===== Stage 2: Broker and VM Creation =====");
        // brokers = createBrokersVms(datacentersConfig);
        broker1 = createBrokersVms(datacenter1, (JsonObject) dcConfig.get(0));
        logger.info("\n===== Stage 3: Simulation Execution =====");
        simulation.addOnSimulationStartListener(eventInfo -> {
            logger.info("✅ Simulation started! Ensuring all VMs are created before cloudlets.");
            if (broker1.getVmCreatedList().isEmpty()) {
                logger.error("🚨 * No VMs created for broker '{}'. Cloudlets may fail.", broker1.getName());
            }
        });
        simulation.addOnClockTickListener(this::generateCloudletsPerSecond);
        // Ensure simulation waits until all Cloudlets are finished before stopping
        simulation.addOnSimulationPauseListener(eventInfo -> {
            if (allCloudletsCompleted(broker1) & allCloudletsCompleted(broker1)) {
                logger.info(
                        "All cloudlets completed. Stopping simulation at " + eventInfo.getTime() + " seconds.");
                simulation.terminate();
            }
        });
        simulation.start();
        logger.info("\n===== Stage 4: Results and Energy Consumption Calculation =====");
        // brokers.forEach(ClassicalNetwork::createCloudletsResultTable);
        createCloudletsResultTable(broker1);
        createCloudletsResultTable(broker2);
        printDatacenterEnergyConsumption();
    }

    private boolean allCloudletsCompleted(DatacenterBrokerSimple broker) {
        if (!broker.getCloudletFinishedList().containsAll(broker.getCloudletSubmittedList())) {
            return false;
        }
        return true;
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
        logger.info("✅ Datacenter '{}' created with {} hosts.", datacenter.getName(), datacenter.getHostList().size());

        for (Host host : datacenter.getHostList()) {
            logger.info("Host {} - RAM: {} MB | PEs: {} | Available PEs: {}",
                    host.getId(), host.getRam().getCapacity(), host.getPesNumber(), host.getFreePesNumber());
        }
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
                logger.warn("Warning: Unknown VM Allocation Policy '" + type + "', using default (SP)");
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
        // ✅ Enhanced Logging for Debugging
        logger.info("✅ Host {} created with resources:", host.getId());
        logger.info(" - CPU PEs : {}", hostPes);
        logger.info(" - CPU MIPS per PE: {}", hostMips);
        logger.info(" - RAM : {} MB", hostRam);
        logger.info(" - Bandwidth : {} Mbps", hostBw);
        logger.info(" - Storage : {} GB", hostStorage / 1e9);
        logger.info(" - VM Scheduler : {}", vmScheduler.getClass().getSimpleName());
        logger.info(" - Power Model : {}", powerModel.getClass().getSimpleName());
        logger.info(" - Startup Delay : {} sec | Shutdown Delay: {} sec",
                HOST_START_UP_DELAY, HOST_SHUT_DOWN_DELAY);
        logger.info(" - Power: Startup {} W | Shutdown {} W", HOST_START_UP_POWER,
                HOST_SHUT_DOWN_POWER);
        return host;
    }

    public PowerModelHost getPowerModel(JsonObject dcConfig) {
        String key = "power_spec_path";
        String type = dcConfig.has(key) ? dcConfig.get(key).getAsString() : "Manual";
        if (type.equals("Manual")) {
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
                return new VmSchedulerTimeShared();
        }
    }

    // private List<DatacenterBrokerSimple> createBrokersVms(JsonArray
    // datacentersConfig) {
    // List<DatacenterBrokerSimple> brokers = new ArrayList<>();

    // int vmGlobalIndex = 0;
    // for (int index = 0; index < datacenters.size(); index++) {
    // Datacenter dc = datacenters.get(index);
    // JsonObject dcConfig = datacentersConfig.get(index).getAsJsonObject();

    // DatacenterBrokerSimple broker = createBroker(dc, dcConfig);
    // final var vmList = createVms(vmGlobalIndex, dcConfig);
    // broker.setLastSelectedDc(dc);
    // broker.submitVmList(vmList);
    // broker.setVmDestructionDelay(Double.MAX_VALUE); // Never destroy VMs until
    // simulation ends
    // brokers.add(broker);

    // vmGlobalIndex += dcConfig.get("vm").getAsInt();

    // logger.info("✅ {} VMs submitted to broker '{}'", vmList.size(),
    // broker.getName());

    // for (Vm vm : vmList) {
    // if (vm.getHost() == null) {
    // logger.error("❌ VM {} was created but NOT assigned to any host!",
    // vm.getId());
    // } else {
    // logger.info("✅ VM {} assigned to Host {} in Datacenter '{}'",
    // vm.getId(), vm.getHost().getId(), vm.getHost().getDatacenter().getName());
    // }
    // }
    // }
    // return brokers;
    // }

    private DatacenterBrokerSimple createBrokersVms(Datacenter dc, JsonObject dcConfig) {
        int vmGlobalIndex = 0;
        DatacenterBrokerSimple broker = createBroker(dc, dcConfig);
        final var vmList = createVms(vmGlobalIndex, dcConfig);
        broker.submitVmList(vmList);
        broker.setVmDestructionDelay(Double.MAX_VALUE); // Never destroy VMs until simulation ends
        vmGlobalIndex += dcConfig.get("vm").getAsInt();
        logger.info("✅ {} VMs submitted to broker '{}'", vmList.size(), broker.getName());
        for (Vm vm : vmList) {
            if (vm.getHost() == null) {
                logger.error("❌ VM {} was created but NOT assigned to any host!", vm.getId());
            } else {
                logger.info("✅ VM {} assigned to Host {} in Datacenter '{}'",
                        vm.getId(), vm.getHost().getId(), vm.getHost().getDatacenter().getName());
            }
        }
        return broker;
    }

    private DatacenterBrokerSimple createBroker(final Datacenter dc, JsonObject dcConfig) {
        DatacenterBrokerSimple broker = new DatacenterBrokerSimple(simulation);
        broker.setName(dcConfig.get("name").getAsString());
        if (broker.getName().equals(dc.getName())) {
            broker.setLastSelectedDc(dc);
        }
        logger.info("Broker " + broker.getName() + " created.");
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
        int vmPes = (hostPes / vms) - 8;
        long vmRam = (hostRam / vms) - 120000;
        long vmBw = (hostBw / vms) - 1000000;
        long vmStorage = (hostStorage / vms);
        long vmMips = (hostMips / vms) - 400000;

        List<Vm> vmList = new ArrayList<>();
        for (int i = 0; i < vms; i++) {
            Vm vm = new VmSimple(startId + i, vmMips, vmPes);
            vm.setRam(vmRam).setBw(vmBw).setSize(vmStorage);
            vm.enableUtilizationStats();
            logger.info("🆕 Creating VM {}: PEs: {}, RAM: {} MB, BW: {} Mbps, Storage: {}GB",
                    vm.getId(), vmPes, vmRam, vmBw, vmStorage / 1e9);
            vmList.add(vm);
        }
        return vmList;
    }

    // // ✅ Method to check if a host can allocate a VM based on the VmScheduler
    // policy
    // private boolean canHostVm(Host host, Vm vm) {
    // VmScheduler vmScheduler = host.getVmScheduler();

    // if (vmScheduler instanceof VmSchedulerSpaceShared) {
    // // ✅ SpaceShared: Only allocate if enough **free PEs**
    // return host.getFreePesNumber() >= vm.getPesNumber();
    // }
    // else if (vmScheduler instanceof VmSchedulerTimeShared) {
    // // ✅ TimeShared: Allocate if enough **MIPS available**
    // return vmScheduler.getTotalAvailableMips() >= vm.getMips();
    // }
    // return false;
    // }

    private void generateCloudletsPerSecond(EventInfo eventInfo) {
        JsonArray datacentersConfig = config.getArray("DATACENTERS");
        JsonObject dcConfig = datacentersConfig.get(0).getAsJsonObject();
        int lastCloudlets = dcConfig.get("lastCloudlets").getAsInt();
        if (totalCloudletsGenerated > lastCloudlets - 1) {
            logger.info("Stopping cloudlet generation at " + eventInfo.getTime() + " seconds.");
            simulation.removeOnClockTickListener(this::generateCloudletsPerSecond);
            return;
        }
        List<Vm> vmList = broker1.getVmCreatedList();
        for (Vm vm : vmList) {
            if (vm.getHost() == null) {
                logger.error("❌ VM {} was created but NOT assigned to a host!", vm.getId());
            } else {
                logger.info("✅ VM {} assigned to Host {} in Datacenter '{}'",
                        vm.getId(), vm.getHost().getId(), vm.getHost().getDatacenter().getName());
            }
        }
        int tps = dcConfig.get("cloudlets").getAsInt();
        List<Cloudlet> cloudlets = createNetworkCloudlets(totalCloudletsGenerated, tps, dcConfig);
        broker1.submitCloudletList(cloudlets);
        totalCloudletsGenerated += tps;
        logger.info("✅ Submitting {} cloudlets to broker '{}'", tps, broker1.getName());
    }

    private List<Cloudlet> createNetworkCloudlets(int startId, int transactionsPerSecond, JsonObject dcConfig) {
        List<Cloudlet> cloudletList = new ArrayList<>();
        UtilizationModel utilization = getUtilizationModel(dcConfig);
        JsonObject cloudletSpec = dcConfig.getAsJsonObject("cloudlet_spec");

        Datacenter senderDatacenter = datacenter1;
        Datacenter receiverDatacenter = datacenter2;

        List<Vm> senderVms = senderDatacenter.getHostList().stream()
                .flatMap(host -> host.getVmList().stream())
                .toList();
        List<Vm> receiverVms = receiverDatacenter.getHostList().stream()
                .flatMap(host -> host.getVmList().stream())
                .toList();

        if (senderVms.isEmpty() || receiverVms.isEmpty()) {
            logger.error("❌ No available VMs in sender or receiver datacenter!");
            return cloudletList;
        }

        for (int i = 0; i < transactionsPerSecond; i++) {
            NetworkCloudlet sender = new NetworkCloudlet(startId + i, cloudletSpec.get("CLOUDLET_PES").getAsInt());
            NetworkCloudlet receiver = new NetworkCloudlet(startId + i + transactionsPerSecond,
                    cloudletSpec.get("CLOUDLET_PES").getAsInt());

            sender.setUtilizationModelCpu(utilization)
                    .setUtilizationModelRam(utilization)
                    .setUtilizationModelBw(utilization)
                    .setVm(senderVms.get(i % senderVms.size()));

            receiver.setUtilizationModelCpu(utilization)
                    .setUtilizationModelRam(utilization)
                    .setUtilizationModelBw(utilization)
                    .setVm(receiverVms.get(i % receiverVms.size()));

            CloudletSendTask sendTask = new CloudletSendTask(startId + i);
            sender.addTask(sendTask);

            CloudletReceiveTask receiveTask = new CloudletReceiveTask(startId + i + transactionsPerSecond,
                    receiverVms.get(i));
            receiver.addTask(receiveTask);
            sendTask.addPacket(receiver, cloudletSpec.get("OUTPUT_SIZE").getAsLong());

            cloudletList.add(sender);
            cloudletList.add(receiver);
        }
        return cloudletList;
    }

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

    private static void createCloudletsResultTable(final DatacenterBrokerSimple broker) {
        List<Cloudlet> finishedCloudlets = broker.getCloudletFinishedList();
        if (!finishedCloudlets.isEmpty()) {
            new CloudletsTableBuilder(finishedCloudlets).build();
        } else {
            logger.warn("⚠ No cloudlets finished for broker '{}'", broker.getName());
        }

    }

    private void printDatacenterEnergyConsumption() {
            double totalEnergy1 = 0.0;
            double totalEnergy2 = 0.0;
            for (Host host : datacenter1.getHostList()) {
                totalEnergy1 += host.getPowerModel().getPower(host.getCpuUtilizationStats().getMean())
                        * simulation.clock() / 3600;
            }
            for (Host host : datacenter2.getHostList()) {
                totalEnergy2 += host.getPowerModel().getPower(host.getCpuUtilizationStats().getMean())
                        * simulation.clock() / 3600;
            }
            logger.info("🔋 Datacenter '{}' used {} kWh of energy.", datacenter1.getName(), String.format("%.6f", totalEnergy1));
            logger.info("🔋 Datacenter '{}' used {} kWh of energy.", datacenter2.getName(), String.format("%.6f", totalEnergy2));
        }
}
