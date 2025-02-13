package energy;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.cloudsimplus.allocationpolicies.VmAllocationPolicyRoundRobin;
import org.cloudsimplus.brokers.DatacenterBroker;
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
import org.cloudsimplus.schedulers.vm.VmSchedulerTimeShared;
import org.cloudsimplus.utilizationmodels.UtilizationModelDynamic;
import org.cloudsimplus.utilizationmodels.UtilizationModelFull;
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
        JsonArray datacentersConfig = config.getArray("DATACENTERS");
        simulation = new CloudSimPlus();
        brokers = new ArrayList<>();
        
        datacenters = createDatacenters(datacentersConfig);
        createBrokersVmsAndCloudlets(datacentersConfig);

        simulation.start();

        // printResults();
        brokers.forEach(Classical::createCloudletsResultTable);
        printDatacenterEnergyConsumption();
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
        Datacenter datacenter = new DatacenterSimple(simulation, hostList, new VmAllocationPolicyRoundRobin());
        datacenter.setName(dcConfig.get("name").getAsString());
        datacenter.setSchedulingInterval(config.getDouble("SCHEDULING_INTERVAL"));
        return datacenter;
    }
    
    private Host createHost(final int id, JsonObject dcConfig) {
        JsonObject hostSpec = dcConfig.getAsJsonObject("host_spec");
        JsonObject powerSpec = dcConfig.getAsJsonObject("power_spec");
        int hostPes = hostSpec.get("HOST_PES").getAsInt();
        int hostMips = hostSpec.get("HOST_MIPS").getAsInt();
        int hostRam = hostSpec.get("HOST_RAM").getAsInt();
        int hostBw = hostSpec.get("HOST_BW").getAsInt();
        long hostStorage = hostSpec.get("HOST_STORAGE").getAsLong();
        double HOST_START_UP_DELAY = powerSpec.get("HOST_START_UP_DELAY").getAsDouble();
        double HOST_SHUT_DOWN_DELAY = powerSpec.get("HOST_SHUT_DOWN_DELAY").getAsDouble();
        double MAX_POWER = powerSpec.get("MAX_POWER").getAsDouble();
        double STATIC_POWER = powerSpec.get("STATIC_POWER").getAsDouble();
        double HOST_START_UP_POWER = powerSpec.get("HOST_START_UP_POWER").getAsDouble();
        double HOST_SHUT_DOWN_POWER = powerSpec.get("HOST_SHUT_DOWN_POWER").getAsDouble();
        
        final var vmScheduler = new VmSchedulerTimeShared();
        

        List<Pe> peList = new ArrayList<>();
        for (int i = 0; i < hostPes; i++) {
            peList.add(new PeSimple(hostMips));
        }

        final var host = new HostSimple(hostRam, hostBw, hostStorage, peList);
        host.setStartupDelay(HOST_START_UP_DELAY)
            .setShutDownDelay(HOST_SHUT_DOWN_DELAY);

        final var powerModel = new PowerModelHostSimple(MAX_POWER, STATIC_POWER);
        powerModel
                  .setStartupPower(HOST_START_UP_POWER)
                  .setShutDownPower(HOST_SHUT_DOWN_POWER);

        host.setId(id)
            .setVmScheduler(vmScheduler)
            .setPowerModel(powerModel);
        host.enableUtilizationStats();

        return host;
    }

    private void createBrokersVmsAndCloudlets(JsonArray datacentersConfig) {
        for (int index = 0; index < datacenters.size(); index++) {
            int vmsNumber = 0;
            int cloudletsNumber = 0;
            if (index != 0) {
                vmsNumber += datacentersConfig.get(index-1).getAsJsonObject().get("vm").getAsInt();
                cloudletsNumber += datacentersConfig.get(index-1).getAsJsonObject().get("cloudlets").getAsInt();
            }
            Datacenter dc = datacenters.get(index);
            JsonObject dcConfig = datacentersConfig.get(index).getAsJsonObject();
    
            DatacenterBrokerSimple broker = createBroker(dc, dcConfig);

            final var vmList = createVms(vmsNumber, dcConfig);
            final var cloudletList = createCloudlets(cloudletsNumber, dcConfig);
    
            broker.submitVmList(vmList);
            broker.submitCloudletList(cloudletList);
            brokers.add(broker);
        }
    }

    
    
    private DatacenterBrokerSimple createBroker(final Datacenter dc, JsonObject dcConfig) {
        DatacenterBrokerSimple broker = new DatacenterBrokerSimple(simulation);
        broker.setName(dcConfig.get("name").getAsString());
        broker.setLastSelectedDc(dc);
        return broker;
    }

    private List<Vm> createVms(int vmsNumber ,JsonObject dcConfig) {
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
            Vm vm = new VmSimple(vmsNumber+i, hostMips / hostPes, vmPes);
            vm.setRam(vmRam).setBw(vmBw).setSize(vmStorage).enableUtilizationStats();
            vmList.add(vm);
        }
        return vmList;
    }

    private List<Cloudlet> createCloudlets(int cloudletsNumber ,JsonObject dcConfig) {
        JsonObject cloudletSpec = dcConfig.getAsJsonObject("cloudlet_spec");

        int cloudletLength = cloudletSpec.get("CLOUDLET_LENGTH").getAsInt();
        int cloudletPes = cloudletSpec.get("CLOUDLET_PES").getAsInt();
        int fileSize = cloudletSpec.get("FILE_SIZE").getAsInt();
        int outputSize = cloudletSpec.get("OUTPUT_SIZE").getAsInt();
        int cloudletCount = dcConfig.get("cloudlets").getAsInt();

        List<Cloudlet> cloudletList = new ArrayList<>();
        final var fullUtilization = new UtilizationModelFull();

        for (int i = 0; i < cloudletCount; i++) {
            Cloudlet cloudlet = new CloudletSimple(cloudletsNumber+i, cloudletLength, cloudletPes)
                    .setFileSize(fileSize)
                    .setOutputSize(outputSize)
                    .setUtilizationModelCpu(fullUtilization)
                    .setUtilizationModelRam(fullUtilization)
                    .setUtilizationModelBw(fullUtilization);
            cloudletList.add(cloudlet);
        }
        return cloudletList;
    }

    private static void createCloudletsResultTable(final DatacenterBroker broker) {
        new CloudletsTableBuilder(broker.getCloudletFinishedList())
                .setTitle(broker.getName())
                .build();
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
            int totalHosts = dc.getHostList().size();
    
            for (Host host : dc.getHostList()) {
                double utilizationMean = host.getCpuUtilizationStats().getMean();
                if (!Double.isNaN(utilizationMean)) {
                    double powerUsage = host.getPowerModel().getPower(utilizationMean);
                    totalEnergy += powerUsage;
                    totalUtilization += utilizationMean;
                    utilizedHosts++;
                }
            }
    
            double avgUtilization = utilizedHosts > 0 ? totalUtilization / utilizedHosts : 0.0;
    
            System.out.println("\nDatacenter Report:");
            System.out.printf("Datacenter Name      : %s%n", dc.getName());
            System.out.printf("Total Hosts          : %d%n", totalHosts);
            System.out.printf("Total VMs            : %d%n", broker.getVmCreatedList().size());
            System.out.printf("Total Cloudlets      : %d%n", broker.getCloudletSubmittedList().size());
            System.out.printf("CPU Utilization      : %.2f%%%n", avgUtilization * 100);
            System.out.printf("Total Energy Used    : %.6f kWh%n", totalEnergy / 1000);
        }
    }
    
    
}
