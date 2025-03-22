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
import org.cloudsimplus.hosts.network.NetworkHost;
import org.cloudsimplus.listeners.EventInfo;
import org.cloudsimplus.resources.Pe;
import org.cloudsimplus.resources.PeSimple;
import org.cloudsimplus.vms.Vm;
import org.cloudsimplus.vms.VmSimple;
import org.cloudsimplus.vms.network.NetworkVm;
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
import java.util.Optional;

import org.slf4j.Logger;

public class MainSimulation {
    private static Config config;
    private static final Logger logger = LoggerFactory.getLogger(MainSimulation.class);
    private CloudSimPlus simulation;
    private DatacenterBrokerSimple broker1, broker2;
    private Datacenter datacenter1, datacenter2;
    private int totalCloudletsGenerated = 0;
    private int totalVMsGenerated = 0;

    public static void main(String[] args) {
        // Log.setLevel(ch.qos.logback.classic.Level.INFO);
        if (args.length < 1) {
            logger.error("Usage: java ClassicalNetwork <config.json>");
            System.exit(1);
        }
        config = new Config(args[0]);
        new MainSimulation().run();
    }

    public void run() {
        simulation = new CloudSimPlus();
        logger.info("\n[Stage 1] Initializing Datacenters and Hosts");
        JsonArray datacentersConfig = config.getArray("DATACENTERS");
        JsonObject dcConfig1 = datacentersConfig.get(0).getAsJsonObject();
        JsonObject dcConfig2 = datacentersConfig.get(1).getAsJsonObject();
        datacenter1 = createDatacenter(dcConfig1);
        datacenter2 = createDatacenter(dcConfig2);

        logger.info("\n[Stage 2] Creating Brokers and VMs");
        broker1 = new DatacenterBrokerSimple(simulation);
        broker2 = new DatacenterBrokerSimple(simulation);
        broker1.setName(dcConfig1.get("name").getAsString());
        broker2.setName(dcConfig2.get("name").getAsString());
        // broker1 = createAndSubmitVMs(broker1, datacenter1, dcConfig1);
        // broker2 = createAndSubmitVMs(broker2, datacenter2, dcConfig2);
        
        logger.info("\n[Stage 3] Loading Network Topology");
        BriteNetworkTopology networkTopology = new BriteNetworkTopology(config.getString("network_spec_path"));
        simulation.setNetworkTopology(networkTopology);
        networkTopology.mapNode(broker1, 0);
        networkTopology.mapNode(broker2, 1);

        logger.info("\n[Stage 4] Starting Cloudlet Generation");
        simulation.addOnClockTickListener(event -> generateCloudletsPerSecond(event.getTime()));
        simulation.start();
        
        logger.info("\n[Stage 5] Simulation Complete. Calculating Results");
        createCloudletsResultTable(broker1);
        createCloudletsResultTable(broker2);
        logEnergyConsumption();
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
        long hostMips = hostSpec.get("HOST_MIPS").getAsLong();
        int hostRam = hostSpec.get("HOST_RAM").getAsInt();
        int hostBw = hostSpec.get("HOST_BW").getAsInt();
        long hostStorage = hostSpec.get("HOST_STORAGE").getAsLong();
        double HOST_START_UP_DELAY = hostSpec.get("HOST_START_UP_DELAY").getAsDouble();
        double HOST_SHUT_DOWN_DELAY = hostSpec.get("HOST_SHUT_DOWN_DELAY").getAsDouble();
        double HOST_START_UP_POWER = hostSpec.get("HOST_START_UP_POWER").getAsDouble();
        double HOST_SHUT_DOWN_POWER = hostSpec.get("HOST_SHUT_DOWN_POWER").getAsDouble();
        VmScheduler vmScheduler = getVmScheduler(dcConfig);
        long peMips = hostMips/hostPes;
        List<Pe> peList = new ArrayList<>();
        for (int i = 0; i < hostPes; i++) {
            peList.add(new PeSimple(peMips));
        }
        final var host = new NetworkHost(hostRam, hostBw, hostStorage, peList);
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

        List<Vm> vmList = createVms(totalVMsGenerated, dcConfig);
        for (Vm vm : vmList) {
            host.createVm(vm);
        }
        try {
            Thread.sleep(5000);
        } catch (InterruptedException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        // // ✅ Enhanced Logging for Debugging
        // logger.info("✅ Host {} created with resources:", host.getId());
        // logger.info(" - CPU PEs : {}", hostPes);
        // logger.info(" - CPU MIPS per PE: {}", hostMips);
        // logger.info(" - RAM : {} MB", hostRam);
        // logger.info(" - Bandwidth : {} Mbps", hostBw);
        // logger.info(" - Storage : {} GB", hostStorage / 1e9);
        // logger.info(" - VM Scheduler : {}", vmScheduler.getClass().getSimpleName());
        // logger.info(" - Power Model : {}", powerModel.getClass().getSimpleName());
        // logger.info(" - Startup Delay : {} sec | Shutdown Delay: {} sec",
        //         HOST_START_UP_DELAY, HOST_SHUT_DOWN_DELAY);
        // logger.info(" - Power: Startup {} W | Shutdown {} W", HOST_START_UP_POWER,
        //         HOST_SHUT_DOWN_POWER);
        return host;
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

    private DatacenterBrokerSimple createAndSubmitVMs(DatacenterBrokerSimple broker, Datacenter dc, JsonObject dcConfig) {
        List<Vm> vmList = createVms(totalVMsGenerated, dcConfig);
        broker.submitVmList(vmList);
        return broker;
    }

    private List<Vm> createVms(int startId, JsonObject dcConfig) {
        JsonObject vmSpec = dcConfig.getAsJsonObject("vm_spec");
        int vms = dcConfig.get("vm").getAsInt();
        int vmPes = vmSpec.get("VM_PES").getAsInt();
        long vmRam = vmSpec.get("VM_RAM").getAsLong();
        long vmBw = vmSpec.get("VM_BW").getAsLong();
        long vmStorage = vmSpec.get("VM_STORAGE").getAsLong();
        long vmMips = vmSpec.get("VM_MIPS").getAsLong();

        List<Vm> vmList = new ArrayList<>();
        for (int i = 0; i < vms; i++) {
            Vm vm = new NetworkVm(startId + i, vmMips, vmPes);
            vm.setRam(vmRam).setBw(vmBw).setSize(vmStorage);
            vm.enableUtilizationStats();
            logger.info("🆕 Creating VM {}: PEs: {}, RAM: {} MB, BW: {} Mbps, Storage: {}GB",
                    vm.getId(), vmPes, vmRam, vmBw, vmStorage / 1e9);
            vmList.add(vm);
        }
        return vmList;
    }

    private void generateCloudletsPerSecond(double time) {
        JsonObject dcConfig1 = config.getArray("DATACENTERS").get(0).getAsJsonObject();
        int cloudletsPerSecond = dcConfig1.get("cloudlets").getAsInt();
        int limitCloudlet = dcConfig1.get("lastCloudlets").getAsInt();
        JsonObject cloudlet_spec = dcConfig1.getAsJsonObject("cloudlet_spec");
        int cloudletPes = cloudlet_spec.get("CLOUDLET_PES").getAsInt();
        if (totalCloudletsGenerated >= limitCloudlet) {
            logger.info("Stopping cloudlet generation at {} seconds.", time);
            return;
        }

        if (broker1.getVmCreatedList().isEmpty()) {
            logger.error("No VM createdddde");
        }
    
        List<NetworkCloudlet> cloudletList = new ArrayList<>();
        for (int i = 0; i < cloudletsPerSecond; i++) {
            NetworkCloudlet sender = new NetworkCloudlet(totalCloudletsGenerated + i, cloudletPes);
            NetworkCloudlet receiver = new NetworkCloudlet(totalCloudletsGenerated + i + limitCloudlet, cloudletPes);
    
            sender.addTask(new CloudletSendTask(i));
            receiver.addTask(new CloudletReceiveTask(i, broker1.getVmCreatedList().get(i)));
    
            cloudletList.add(sender);
            cloudletList.add(receiver);
        }
        broker1.submitCloudletList(cloudletList);
        totalCloudletsGenerated += cloudletsPerSecond;
        logger.info("Submitted {} Cloudlets at {}s", cloudletsPerSecond, time);
    }    

    private static void createCloudletsResultTable(final DatacenterBrokerSimple broker) {
        List<Cloudlet> finishedCloudlets = broker.getCloudletSubmittedList();
        if (!finishedCloudlets.isEmpty()) {
            new CloudletsTableBuilder(finishedCloudlets).build();
        } else {
            logger.warn("⚠ No cloudlets finished for broker '{}'", broker.getName());
        }
    }

    private void logEnergyConsumption() {
        double energy = datacenter1.getHostList().stream()
                .mapToDouble(host -> host.getPowerModel().getPower(host.getCpuUtilizationStats().getMean()))
                .sum();
        logger.info("Total Energy Consumption: {} kWh", energy);
    }
}
