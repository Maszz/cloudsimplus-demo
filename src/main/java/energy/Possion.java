package energy;

import com.google.gson.*;
import org.apache.commons.math3.distribution.PoissonDistribution;
import org.cloudsimplus.builders.tables.CloudletsTableBuilder;
import org.cloudsimplus.brokers.DatacenterBrokerSimple;
import org.cloudsimplus.cloudlets.Cloudlet;
import org.cloudsimplus.cloudlets.CloudletSimple;
import org.cloudsimplus.core.CloudSimPlus;
import org.cloudsimplus.datacenters.Datacenter;
import org.cloudsimplus.datacenters.DatacenterSimple;
import org.cloudsimplus.hosts.Host;
import org.cloudsimplus.hosts.HostSimple;
import org.cloudsimplus.power.models.PowerModelHost;
import org.cloudsimplus.power.models.PowerModelHostSimple;
import org.cloudsimplus.power.models.PowerModelHostSpec;
import org.cloudsimplus.resources.Pe;
import org.cloudsimplus.resources.PeSimple;
import org.cloudsimplus.schedulers.cloudlet.CloudletScheduler;
import org.cloudsimplus.schedulers.cloudlet.CloudletSchedulerSpaceShared;
import org.cloudsimplus.schedulers.cloudlet.CloudletSchedulerTimeShared;
import org.cloudsimplus.schedulers.vm.VmScheduler;
import org.cloudsimplus.schedulers.vm.VmSchedulerSpaceShared;
import org.cloudsimplus.schedulers.vm.VmSchedulerTimeShared;
import org.cloudsimplus.utilizationmodels.UtilizationModel;
import org.cloudsimplus.utilizationmodels.UtilizationModelFull;
import org.cloudsimplus.vms.Vm;
import org.cloudsimplus.vms.VmSimple;
import org.cloudsimplus.allocationpolicies.VmAllocationPolicy;
import org.cloudsimplus.allocationpolicies.VmAllocationPolicyBestFit;
import org.cloudsimplus.allocationpolicies.VmAllocationPolicyFirstFit;
import org.cloudsimplus.allocationpolicies.VmAllocationPolicyRandom;
import org.cloudsimplus.allocationpolicies.VmAllocationPolicyRoundRobin;
import org.cloudsimplus.allocationpolicies.VmAllocationPolicySimple;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.cloudsimplus.util.Log;

import static org.cloudsimplus.util.TimeUtil.daysToSeconds;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.*;

public class Possion {
    private static final Logger logger = LoggerFactory.getLogger(Possion.class);

    private static JsonObject control;
    private static JsonArray months;
    private CloudSimPlus simulation;
    private List<DatacenterBrokerSimple> brokers;
    private List<Datacenter> datacenters;
    private List<Integer> totalCloudletsGenerated;
    private Set<Integer> finishedDatacenterIndexes = new HashSet<>();

    private final List<Double> energyPerHour = new ArrayList<>();
    private final List<List<Double>> energyPerTickPerNode = new ArrayList<>();
    private double cumulativeEnergykWs = 0.0;

    private static double interval;
    private int month_num;
    private static final boolean DEBUG = true;
    private double lastTick = -1;
    private static final int scaleFactor = 1000;

    public static void main(String[] args) {
        Log.setLevel(ch.qos.logback.classic.Level.ERROR);

        if (args.length < 1) {
            logger.error("Usage: java Conference <config.json>");
            System.exit(1);
        }

        control = new Config(args[0]).getRoot();
        months = control.getAsJsonArray("MONTHS");
        interval = control.get("SCHEDULING_INTERVAL").getAsInt();
        for (int i = 0; i < months.size(); i++) {
            JsonObject month = months.get(i).getAsJsonObject();
            JsonArray datacenters = month.getAsJsonArray("DATACENTERS");
            int month_num = month.get("MONTH").getAsInt();
            if (DEBUG) System.out.println("Starting Month" + month_num);
            
            for (int j = 0; j < datacenters.size(); j++) {
                JsonArray singleDatacenterArray = new JsonArray();
                singleDatacenterArray.add(datacenters.get(j));
                String dcName = datacenters.get(j).getAsJsonObject().get("name").getAsString();
                System.out.printf("▶️ Starting simulation for Month %d - Datacenter %s\n", month_num, dcName);
                Possion instance = new Possion();
                instance.run(new CloudSimPlus(), singleDatacenterArray, month_num);
            }
            if (DEBUG)
                break;
        }
    }

    public void run(CloudSimPlus simulation, JsonArray datacentersConfig, int month_num) {
        this.simulation = simulation;
        this.month_num = month_num;
        int lambda = control.has("lambda") ? control.get("lambda").getAsInt() : 30;

        
        brokers = new ArrayList<>();
        totalCloudletsGenerated = new ArrayList<>();
        datacenters = createDatacenters(datacentersConfig);
        createBrokersVms(datacentersConfig);
        for (int i = 0; i < datacenters.size(); i++) {
            totalCloudletsGenerated.add(0);
        }

        // On tick
        simulation.addOnClockTickListener(eventInfo -> {
            double now = simulation.clock();
            if (now - lastTick < 1.0 || now < 1.0)
                return;
            lastTick = now;
            submitPoissonCloudlets(datacentersConfig, lambda);
            energyTracking();
            terminator(datacentersConfig);
        });

        simulation.terminateAt(daysToSeconds(1));

        // Start simulation
        simulation.start();
        if (DEBUG)
            brokers.forEach(Possion::createCloudletsResultTable);

        // Print Energy
        printDatacenterEnergyConsumption();

        // Write CSV
        writeTickEnergy();
    }

    private int getDaysInMonth(int monthNumber) {
        return switch (monthNumber) {
            case 2 -> 28;
            case 4, 6, 9, 11 -> 30;
            default -> 31;
        };
    }

    private void terminator(JsonArray datacentersConfig) {
        boolean allDone = true;

        for (int i = 0; i < datacenters.size(); i++) {
            if (finishedDatacenterIndexes.contains(i))
                continue;

            JsonObject dcConfig = datacentersConfig.get(i).getAsJsonObject();
            int expected = dcConfig.get("cloudlets").getAsInt() / getDaysInMonth(month_num) / scaleFactor;
            DatacenterBrokerSimple broker = brokers.get(i);

            int submitted = broker.getCloudletSubmittedList().size();
            int finished = broker.getCloudletFinishedList().size();
            boolean allCloudletsFinished = broker.getCloudletCreatedList().stream().allMatch(Cloudlet::isFinished);

            if (submitted >= expected && finished >= expected && allCloudletsFinished) {
                // ✅ All cloudlets done for this datacenter
                System.out.printf("✅ [%.2f] %s finished all %d cloudlets.\n",
                        simulation.clock(), broker.getName(), expected);
                finishedDatacenterIndexes.add(i);
            } else {
                allDone = false;
            }
        }

        if (allDone) {
            System.out.printf("✅✅ [%.2f] All datacenters finished. Terminating simulation.\n", simulation.clock());
            simulation.terminate();
        }
    }

    public VmScheduler getVMScheduler() {
        String key = "VmScheduler";
        String type = control.has(key) ? control.get(key).getAsString() : "";

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
        String type = control.has(key) ? control.get(key).getAsString() : "";

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
        String type = control.has(key) ? control.get(key).getAsString() : "";

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
        String type = control.has(key) ? control.get(key).getAsString() : "Manual";

        if (type == "Manual") {
            JsonObject powerSpec = control.getAsJsonObject("power_spec");
            double MAX_POWER = powerSpec.get("MAX_POWER").getAsDouble();
            double STATIC_POWER = powerSpec.get("STATIC_POWER").getAsDouble();
            return new PowerModelHostSimple(MAX_POWER, STATIC_POWER);
        } else {
            PowerModelHostSpec DEF_POWER_MODEL = PowerModelHostSpec.getInstance(type);
            return new PowerModelHostSpec(DEF_POWER_MODEL.getPowerSpecs());
        }
    }

    private List<Cloudlet> createDynamicCloudlets(int currentCloudlets, int allowedArrivals) {
        List<Cloudlet> cloudletList = new ArrayList<>();
        JsonObject spec = control.getAsJsonObject("cloudlet_spec");
        int length = spec.get("CLOUDLET_LENGTH").getAsInt();
        int pes = spec.get("CLOUDLET_PES").getAsInt();
        UtilizationModel utilization = new UtilizationModelFull();

        for (int i = 0; i < allowedArrivals; i++) {
            Cloudlet c = new CloudletSimple(currentCloudlets + i, length, pes)
                    .setUtilizationModelCpu(utilization)
                    .setUtilizationModelRam(utilization)
                    .setUtilizationModelBw(utilization);
            cloudletList.add(c);
        }
        return cloudletList;
    }

    private void submitPoissonCloudlets(JsonArray datacentersConfig, int lambda) {
        for (int i = 0; i < datacenters.size(); i++) {
            JsonObject dcConfig = datacentersConfig.get(i).getAsJsonObject();
            DatacenterBrokerSimple broker = brokers.get(i);
            int lastCloudlets = dcConfig.get("cloudlets").getAsInt() / getDaysInMonth(month_num) / scaleFactor;
            int currentCloudlets = totalCloudletsGenerated.get(i);
            int remaining = lastCloudlets - currentCloudlets;
            if (remaining <= 0)
                continue;

            if (broker.getVmCreatedList().isEmpty()) {
                System.err.printf("❌ [Tick %.2f] %s: No VMs available! Skipping cloudlet submission.\n",
                        simulation.clock(), broker.getName());
                continue;
            }

            Vm vm = broker.getVmCreatedList().get(0);
            if (vm.isIdle() == false && vm.getCloudletScheduler().getCloudletExecList().isEmpty()) {
                // VM not yet allocated or active, skip
                continue;
            }
            int vmPes = (int) vm.getPesNumber();
            int running = vm.getCloudletScheduler().getCloudletExecList().size();
            int waiting = vm.getCloudletScheduler().getCloudletWaitingList().size();
            int inflight = running + waiting;

            int queueCapacity = vmPes - 1; // limit inflight to number of PEs
            int availableSlots = queueCapacity - inflight;

            if (availableSlots <= 0) {
                if (DEBUG)
                    System.out.printf("[Tick %.2f] %s: Queue full → inflight=%d, skipping submission\n",
                            simulation.clock(), broker.getName(), inflight);
                continue;
            }

            PoissonDistribution poisson = new PoissonDistribution(lambda);
            double poissonArrivals = Math.min((poisson.sample()), remaining);
            // int allowedArrivals = (int) poissonArrivals;
            int allowedArrivals = (int) Math.min(poissonArrivals,
                    (vmPes * broker.getVmCreatedList().size()) - inflight);
            if (allowedArrivals <= 0) {
                if (DEBUG) {
                    int submitted = broker.getCloudletSubmittedList().size();
                    int done = broker.getCloudletFinishedList().size();
                    System.out.printf("[Tick %.2f] %s: +%d cloudlets (done/submitted/total): %d/%d/%d%n",
                            simulation.clock(), broker.getName(), allowedArrivals, done, submitted, lastCloudlets);
                }
                continue;
            }

            List<Cloudlet> newCloudlets = createDynamicCloudlets(currentCloudlets, allowedArrivals);
            broker.submitCloudletList(newCloudlets);
            totalCloudletsGenerated.set(i, currentCloudlets + allowedArrivals);

            if (DEBUG) {
                int submitted = broker.getCloudletSubmittedList().size();
                int done = broker.getCloudletFinishedList().size();
                System.out.printf("[Tick %.2f] %s: +%d cloudlets (done/submitted/total): %d/%d/%d%n",
                        simulation.clock(), broker.getName(), allowedArrivals, done, submitted, lastCloudlets);
            }
        }

    }

    private void energyTracking() {
        if (finishedDatacenterIndexes.size() >= datacenters.size()) {
            if (DEBUG)
                System.out.println("🔚 All datacenters finished — stop tracking energy.");
            return;
        }

        List<Double> currentTickEnergy = new ArrayList<>();

        for (int i = 0; i < datacenters.size(); i++) {
            if (finishedDatacenterIndexes.contains(i)) {
                currentTickEnergy.add(0.0);
                continue;
            }

            Datacenter dc = datacenters.get(i);
            double totalPowerkW = 0.0;

            for (Host host : dc.getHostList()) {
                double utilization = host.getCpuUtilizationStats().getMean();
                double power = host.getPowerModel().getPower(utilization); // in watts
                totalPowerkW += power / 1000.0; // convert to kW
            }

            double tickDuration = interval; // from config
            double energyKWs = totalPowerkW * tickDuration; // in seconds

            currentTickEnergy.add(energyKWs);
        }

        energyPerTickPerNode.add(currentTickEnergy);
    }

    private void writeTickEnergy() {
        String filename = "output/csv/day/month_" + month_num + "_energy.csv";
        try (PrintWriter writer = new PrintWriter(new FileWriter(filename))) {
            // Header: Tick,Node1,Node2,...
            writer.print("Tick");
            for (int i = 0; i < datacenters.size(); i++) {
                writer.printf(",Node%d", i + 1);
            }
            writer.println();

            for (int tick = 0; tick < energyPerTickPerNode.size(); tick++) {
                double tickTime = tick * interval;
                writer.printf("%.0f", tickTime);
                List<Double> tickEnergies = energyPerTickPerNode.get(tick);
                for (double energy : tickEnergies) {
                    writer.printf(",%.6f", energy);
                }
                writer.println();
            }

            if (DEBUG)
                System.out.printf("✅ Wrote tick energy file: %s\n", filename);
        } catch (IOException e) {
            System.err.printf("❌ Error writing tick energy CSV: %s\n", e.getMessage());
        }
    }

    private List<Datacenter> createDatacenters(JsonArray datacentersConfig) {
        List<Datacenter> datacenters = new ArrayList<>();
        for (int i = 0; i < datacentersConfig.size(); i++) {
            JsonObject dc = datacentersConfig.get(i).getAsJsonObject();
            String name = dc.has("name") ? dc.get("name").getAsString() : "Datacenter" + i;
            datacenters.add(createDatacenter(name));
        }
        return datacenters;
    }

    private Datacenter createDatacenter(String name) {
        int numHosts = control.get("hosts").getAsInt();
        List<Host> hostList = new ArrayList<>();
        for (int i = 0; i < numHosts; i++) {
            Host host = createHost(i);
            hostList.add(host);
        }
        VmAllocationPolicy policy = getVmAllocationPolicy();

        Datacenter datacenter = new DatacenterSimple(simulation, hostList, policy);
        datacenter.setName(name);
        datacenter.setSchedulingInterval(interval);
        return datacenter;
    }

    private Host createHost(final int id) {
        JsonObject hostSpec = control.getAsJsonObject("host_spec");
        int hostPes = hostSpec.get("HOST_PES").getAsInt();
        int hostMips = hostSpec.get("HOST_MIPS").getAsInt();
        int hostRam = hostSpec.get("HOST_RAM").getAsInt();
        int hostBw = hostSpec.get("HOST_BW").getAsInt();
        long hostStorage = hostSpec.get("HOST_STORAGE").getAsLong();
        VmScheduler vmScheduler = getVMScheduler();

        List<Pe> peList = new ArrayList<>();
        for (int i = 0; i < hostPes; i++) {
            peList.add(new PeSimple(hostMips));
        }

        final var host = new HostSimple(hostRam, hostBw, hostStorage, peList);
        final var powerModel = getPowerModel();
        host.setId(id)
                .setVmScheduler(vmScheduler)
                .setPowerModel(powerModel);
        host.enableUtilizationStats();

        return host;
    }

    private void createBrokersVms(JsonArray datacentersConfig) {
        int vmGlobalIndex = 0;
        for (int index = 0; index < datacenters.size(); index++) {
            Datacenter dc = datacenters.get(index);
            JsonObject dcConfig = datacentersConfig.get(index).getAsJsonObject();
            DatacenterBrokerSimple broker = createBroker(dcConfig);
            broker.setLastSelectedDc(dc);
            final var vmList = createVms(vmGlobalIndex);
            broker.submitVmList(vmList);
            // broker.setVmDestructionDelay(Double.MAX_VALUE);

            brokers.add(broker);
            vmGlobalIndex += control.get("vm").getAsInt();
        }
    }

    private DatacenterBrokerSimple createBroker(JsonObject dcConfig) {
        DatacenterBrokerSimple broker = new DatacenterBrokerSimple(simulation);
        String broker_name = dcConfig.get("name").getAsString();
        broker.setName(broker_name);
        if (DEBUG)
            logger.info("Create broker: {}", broker_name);
        if (DEBUG)
            System.out.printf("Create broker: %s\n", broker_name);
        return broker;
    }

    private List<Vm> createVms(int startId) {
        JsonObject spec = control.getAsJsonObject("host_spec");
        int vms = control.get("vm").getAsInt();
        int hostPes = spec.get("HOST_PES").getAsInt();
        long hostMips = spec.get("HOST_MIPS").getAsLong();
        int vmPes = hostPes / vms;
        long vmMips = hostMips / vms;
        long vmRam = spec.get("HOST_RAM").getAsInt() / vms;
        long vmBw = spec.get("HOST_BW").getAsInt() / vms;
        long vmStorage = spec.get("HOST_STORAGE").getAsLong() / vms;

        List<Vm> vmList = new ArrayList<>();
        for (int i = 0; i < vms; i++) {
            Vm vm = new VmSimple(startId + i, vmMips, vmPes);
            vm.setRam(vmRam).setBw(vmBw).setSize(vmStorage);
            vm.setCloudletScheduler(getCloudletScheduler());
            vm.enableUtilizationStats();
            vmList.add(vm);
        }
        // if (DEBUG)
        //     logger.info("Create VM: {} - ", startId, startId + vms--);
        // if (DEBUG)
        //     System.out.printf("Create VM: %d-%d\n", startId, startId + vms--);
        return vmList;
    }

    private void printDatacenterEnergyConsumption() {
        logger.info("\n--- Energy Report for month{} ---", month_num);
        System.out.printf("--- Energy Report for month%s ---\n", month_num);
        for (int i = 0; i < datacenters.size(); i++) {
            Datacenter dc = datacenters.get(i);
            DatacenterBrokerSimple broker = brokers.get(i);
            JsonObject dcConfig = control.getAsJsonArray("MONTHS")
                    .get(month_num - 1).getAsJsonObject()
                    .getAsJsonArray("DATACENTERS")
                    .get(i).getAsJsonObject();
            int expectedTotal = dcConfig.get("cloudlets").getAsInt() / getDaysInMonth(month_num) / scaleFactor;
            int totalSubmittedCloudlets = broker.getCloudletSubmittedList().size();
            int totalFinishCloudlets = broker.getCloudletFinishedList().size();
            double totalEnergy = 0.0;
            for (Host host : dc.getHostList()) {
                final double utilization = host.getCpuUtilizationStats().getMean();
                final double watts = host.getPowerModel().getPower(utilization);
                double hostFirstStart = host.getFirstStartTime();
                double hostLastFinish = 0.0;

                for (Vm vm : host.getVmList()) {
                    for (Cloudlet c : brokers.get(i).getCloudletFinishedList()) {
                        if (c.getVm() == vm) {
                            hostLastFinish = Math.max(hostLastFinish, c.getFinishTime());
                        }
                    }
                }

                double activeTime = Math.max(0, hostLastFinish - hostFirstStart);
                final double energyKWh = (watts * activeTime) / (1000 * 3600);
                totalEnergy += energyKWh;
            }
            logger.info("Datacenter: {:<2} | Cloudlets(P/S/E): {:>4}|{:>4}|{:>4} | Energy: {:.4f} kWh",
                    dc.getName(), totalSubmittedCloudlets - totalFinishCloudlets, totalSubmittedCloudlets,
                    expectedTotal, totalEnergy);
            System.out.printf("Datacenter: %-6s | Cloudlets(P/S/E): %4d|%4d|%4d | Energy: %.4f kWh\n",
                    dc.getName(), totalSubmittedCloudlets - totalFinishCloudlets, totalSubmittedCloudlets,
                    expectedTotal, totalEnergy);
        }
    }

    private static void createCloudletsResultTable(final DatacenterBrokerSimple broker) {
        new CloudletsTableBuilder(broker.getCloudletCreatedList()).build();
    }
}