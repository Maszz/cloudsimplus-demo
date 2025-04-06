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

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.*;

public class Node {
    private static final Logger logger = LoggerFactory.getLogger(Possion.class);

    private static JsonObject control;
    private static JsonArray months;
    private CloudSimPlus simulation;
    private List<DatacenterBrokerSimple> brokers;
    private List<Datacenter> datacenters;
    private List<Integer> totalCloudletsGenerated;
    private double lastSubmissionTime = 0.0;

    private final List<Double> powerPerSecond = new ArrayList<>();
    private final List<Double> energyPerSecond = new ArrayList<>();
    private final List<Integer> successPerSecond = new ArrayList<>();
    private final List<Integer> pendingPerSecond = new ArrayList<>();
    private double cumulativeEnergykWs = 0.0;

    private String monthLabel = "Month";
    private static final boolean DEBUG = true;

    public static void main(String[] args) {
        System.out.println("Starting simulation...");
        if (args.length < 1) {
            logger.error("Usage: java Conference <config.json>");
            System.exit(1);
        }
        control = new Config(args[0]).getRoot();
        months = control.getAsJsonArray("MONTHS");
        Log.setLevel(ch.qos.logback.classic.Level.ERROR);
        for (int i = 0; i < months.size(); i++) {
            JsonObject month = months.get(i).getAsJsonObject();
            JsonArray datacenters = month.getAsJsonArray("DATACENTERS");
            String label = "Month" + month.get("MONTH").getAsInt();
            System.out.println("Starting " + label);
            new Node().run(datacenters, label);
            if (DEBUG)
                break;
            // break;
        }
    }

    public void run(JsonArray datacentersConfig, String label) {
        this.monthLabel = label;
        int lambda = control.has("lambda") ? control.get("lambda").getAsInt() : 30;

        simulation = new CloudSimPlus();
        brokers = new ArrayList<>();
        datacenters = createDatacenters(datacentersConfig);
        totalCloudletsGenerated = new ArrayList<>();
        for (int i = 0; i < datacenters.size(); i++) {
            totalCloudletsGenerated.add(0);
        }
        createBrokersVms(datacentersConfig);

        setupEnergyTracking();

        simulation.addOnClockTickListener(eventInfo -> {
            for (DatacenterBrokerSimple broker : brokers) {
                for (Vm vm : broker.getVmCreatedList()) {
                    int exec = vm.getCloudletScheduler().getCloudletExecList().size();
                    if (DEBUG) {
                        logger.debug("Tick %.2f: VM #%d running %d cloudlets\n", simulation.clock(), vm.getId(), exec);
                        System.out.printf("Tick %.2f: VM #%d running %d cloudlets\n", simulation.clock(), vm.getId(),
                                exec);
                    }
                }
            }
            submitPoissonCloudlets(datacentersConfig, lambda);
            terminator(datacentersConfig);
        });
        // simulation.terminateAt(1);
        // simulation.terminateAt(5000000);
        simulation.terminateAt(2592000); // Ensures clock ticks at least up to x
        // seconds

        simulation.start();
        // for (DatacenterBrokerSimple broker : brokers) {
        // while (broker.getVmCreatedList().isEmpty()) {
        // simulation.runFor(1);
        // }
        // }

        writeMonthlyCSV();
        brokers.forEach(Node::createCloudletsResultTable);
        printDatacenterEnergyConsumption();
    }

    private void terminator(JsonArray datacentersConfig) {
        if (lastSubmissionTime == 0.0)
            return; // 🛡 Prevent early termination

        boolean allSubmitted = true;
        boolean allFinished = true;

        for (int i = 0; i < datacenters.size(); i++) {
            JsonObject dcConfig = datacentersConfig.get(i).getAsJsonObject();
            int expected = dcConfig.get("cloudlets").getAsInt();
            DatacenterBrokerSimple broker = brokers.get(i);
            int submitted = broker.getCloudletSubmittedList().size();
            int finished = broker.getCloudletFinishedList().size();

            if (submitted < expected) {
                allSubmitted = false;
            }
            if (finished < expected) {
                allFinished = false;
            }
        }

        double now = simulation.clock();

        if (allSubmitted && allFinished) {
            System.out.printf("✅ [%.2f] All cloudlets submitted and finished. Terminating simulation.%n", now);
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
        long length = spec.get("CLOUDLET_LENGTH").getAsLong();
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
        // 🔒 Ensure all VMs are ready before submitting cloudlets
        boolean allVMsReady = brokers.stream().allMatch(b -> !b.getVmCreatedList().isEmpty());
        if (!allVMsReady)
            return;
        for (int i = 0; i < datacenters.size(); i++) {
            JsonObject dcConfig = datacentersConfig.get(i).getAsJsonObject();
            DatacenterBrokerSimple broker = brokers.get(i);
            int lastCloudlets = dcConfig.get("cloudlets").getAsInt();
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
            // long maxCloudletsPerVm = vm.getPesNumber();
            int maxCloudletsPerVm = 32;
            int inflight = vm.getCloudletScheduler().getCloudletExecList().size()
                    + vm.getCloudletScheduler().getCloudletWaitingList().size();

            if (inflight >= maxCloudletsPerVm) {
                if (DEBUG)
                    System.out.printf("[Tick %.2f] %s: Skipping submission, inflight = %d\n",
                            simulation.clock(), broker.getName(), inflight);
                continue;
            }

            PoissonDistribution poisson = new PoissonDistribution(lambda);
            int poissonArrivals = Math.min(poisson.sample(), remaining);
            int allowedArrivals = Math.min(poissonArrivals, maxCloudletsPerVm -
            inflight);
            // int allowedArrivals = poissonArrivals;
            if (allowedArrivals <= 0)
                continue;

            List<Cloudlet> newCloudlets = createDynamicCloudlets(currentCloudlets, allowedArrivals);
            for (Cloudlet cl : newCloudlets)
                broker.bindCloudletToVm(cl, vm);
            broker.submitCloudletList(newCloudlets);
            lastSubmissionTime = simulation.clock();
            totalCloudletsGenerated.set(i, currentCloudlets + allowedArrivals);

            if (DEBUG) {
                int submitted = broker.getCloudletSubmittedList().size();
                int done = broker.getCloudletFinishedList().size();
                System.out.printf("[Tick %.2f] %s: +%d cloudlets (done/submitted/total): %d/%d/%d%n",
                        simulation.clock(), broker.getName(), allowedArrivals, done, submitted, lastCloudlets);
            }
        }

    }

    private void setupEnergyTracking() {
        simulation.addOnClockTickListener(eventInfo -> {
            int success = 0;
            int pending = 0;
            for (DatacenterBrokerSimple broker : brokers) {
                for (Cloudlet cl : broker.getCloudletSubmittedList()) {
                    if (cl.isFinished()) {
                        success++;
                    } else {
                        pending++;
                    }
                }
            }

            double totalPowerkW = 0.0;
            for (Datacenter dc : datacenters) {
                for (Host host : dc.getHostList()) {
                    double utilization = host.getCpuUtilizationStats().getMean();
                    double power = host.getPowerModel().getPower(utilization);
                    totalPowerkW += power / 1000;
                }
            }

            cumulativeEnergykWs += totalPowerkW;
            powerPerSecond.add(totalPowerkW);
            energyPerSecond.add(cumulativeEnergykWs);
            successPerSecond.add(success);
            pendingPerSecond.add(pending);
        });
    }

    private void writeMonthlyCSV() {
        String filename = "output/csv/month/" + monthLabel + ".csv";
        try (PrintWriter writer = new PrintWriter(new FileWriter(filename))) {
            writer.println("Tick,Success,Queue,EnergyAtTick(kW)");
            for (int i = 0; i < powerPerSecond.size(); i++) {
                writer.printf("%d,%d,%d,%.6f%n",
                        i + 1,
                        successPerSecond.get(i),
                        pendingPerSecond.get(i),
                        powerPerSecond.get(i));
            }
            System.out.printf("Written energy data to %s\n", filename);
        } catch (IOException e) {
            System.err.printf("Error writing %s: %s\n", filename, e.getMessage());
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
        int interval = control.get("SCHEDULING_INTERVAL").getAsInt();
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
            broker.setVmDestructionDelay(Double.MAX_VALUE);

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
        long vmMips = hostMips / hostPes;
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
        if (DEBUG)
            logger.info("Create VM: {} - ", startId, startId + vms--);
        if (DEBUG)
            System.out.printf("Create VM: %d-%d\n", startId, startId + vms--);
        return vmList;
    }

    private void printDatacenterEnergyConsumption() {
        logger.info("\n--- Energy Report for {} ---", monthLabel);
        System.out.printf("--- Energy Report for %s ---\n", monthLabel);
        for (int i = 0; i < datacenters.size(); i++) {
            Datacenter dc = datacenters.get(i);
            DatacenterBrokerSimple broker = brokers.get(i);
            int totalCloudlets = broker.getCloudletSubmittedList().size();
            double totalEnergy = 0.0;
            for (Host host : dc.getHostList()) {
                final double utilization = host.getCpuUtilizationStats().getMean();
                final double watts = host.getPowerModel().getPower(utilization);
                final double aliveTime = simulation.clock() - host.getFirstStartTime();
                final double energyKWh = (watts * aliveTime) / (1000 * 3600);
                totalEnergy += energyKWh;
            }
            logger.info("Datacenter: {} | Cloudlets: {} | Energy: {:.4f} kWh", dc.getName(), totalCloudlets,
                    totalEnergy);
            System.out.printf("Datacenter: %s | Cloudlets: %d | Energy: %.4f kWh\n", dc.getName(), totalCloudlets,
                    totalEnergy);
        }
    }

    private static void createCloudletsResultTable(final DatacenterBrokerSimple broker) {
        new CloudletsTableBuilder(broker.getCloudletCreatedList()).build();
    }
}