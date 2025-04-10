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
import org.cloudsimplus.provisioners.ResourceProvisionerSimple;
import org.cloudsimplus.resources.Pe;
import org.cloudsimplus.resources.PeSimple;
import org.cloudsimplus.schedulers.vm.VmScheduler;
import org.cloudsimplus.utilizationmodels.UtilizationModel;
import org.cloudsimplus.vms.Vm;
import org.cloudsimplus.vms.VmSimple;
import org.cloudsimplus.allocationpolicies.VmAllocationPolicy;
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

    private static Config config;
    private CloudSimPlus simulation;
    private List<DatacenterBrokerSimple> brokers;
    private List<Datacenter> datacenters;
    private List<Integer> totalCloudletsGenerated;
    private Set<Integer> finishedDatacenterIndexes = new HashSet<>();

    private final List<List<Double>> energyPerTickPerNode = new ArrayList<>();

    private int month_num;
    private static final boolean DEBUG = false;
    private double lastTick = -1;
    private static final int scaleFactor = 24 * 2;

    public static void main(String[] args) {
        Log.setLevel(ch.qos.logback.classic.Level.ERROR);

        if (args.length < 1) {
            logger.error("Usage: java Conference <config.json>");
            System.exit(1);
        }

        config = new Config(args[0]);
        for (JsonElement month : config.getMonths()) {
            JsonArray datacenters = month.getAsJsonObject().getAsJsonArray("DATACENTERS");
            int month_num = month.getAsJsonObject().get("MONTH").getAsInt();
            Possion instance = new Possion();
            if (DEBUG)
                System.out.println("Starting Month" + month_num);
            instance.run(new CloudSimPlus(), datacenters, month_num);
            if (DEBUG)
                break;
        }
    }

    public void run(CloudSimPlus simulation, JsonArray datacentersConfig, int month_num) {
        this.simulation = simulation;
        this.month_num = month_num;

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

            submitPoissonCloudlets(datacentersConfig);
            updateProcessing();
            energyTracking();
            terminator(datacentersConfig);

            // ✅ บังคับให้ simulation เดินไปข้างหน้าเรื่อย ๆ ถ้ามีงานที่ยังไม่เสร็จ
            boolean stillRunning = brokers.stream()
                    .anyMatch(b -> b.getCloudletSubmittedList().stream().anyMatch(c -> !c.isFinished()));

            if (stillRunning) {
                simulation.getCis().schedule(simulation.getEntityList().get(0), 1.0, 9999);
            }

        });

        simulation.addOnEventProcessingListener(info -> {
            // Inject dummy future event to keep simulation clock ticking
        });

        // simulation.start();
        simulation.startSync();
        while (simulation.isRunning()) {
            simulation.runFor(1.0);
            updateProcessing();
        }

        if (DEBUG)
            brokers.forEach(Possion::createCloudletsResultTable);

        // Print Energy
        printDatacenterEnergyConsumption();

        // Write CSV
        writeTickEnergy();
    }

    private void updateProcessing() {
        for (Datacenter dc : datacenters) {
            for (Host host : dc.getHostList()) {
                double time = simulation.clock();
                host.updateProcessing(time);
            }
        }
        simulation.getCis().schedule(simulation.getEntityList().get(0), 1.0, 9999);

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
            JsonObject dcConfig = datacentersConfig.get(i).getAsJsonObject();
            int expected = dcConfig.get("cloudlets").getAsInt() / getDaysInMonth(month_num) / scaleFactor;
            DatacenterBrokerSimple broker = brokers.get(i);
            int submitted = broker.getCloudletSubmittedList().size();
            int done = broker.getCloudletFinishedList().size();
            boolean noRemaining = (submitted >= expected);
            boolean allFinished = (done >= expected);
            boolean allSuccess = broker.getCloudletFinishedList().stream()
                    .allMatch(c -> c.isFinished() && c.getStatus() == Cloudlet.Status.SUCCESS);

            // progress bar
            printProgressBar(simulation.clock(), broker.getName(), done, submitted, expected);

            if (finishedDatacenterIndexes.contains(i))
                continue;
            if (noRemaining && allFinished && allSuccess) {
                System.err.println("✅ " + broker.getName() + " Finish");
                finishedDatacenterIndexes.add(i);
            } else {
                allDone = false;
            }
        }

        if (finishedDatacenterIndexes.size() == datacenters.size() && allDone) {
            if (DEBUG) {
                System.err.printf("✅✅ [%.2f] All datacenters finished. Terminating simulation.\n", simulation.clock());
            }
            simulation.terminate();
        }
    }

    private List<Cloudlet> createDynamicCloudlets(int currentCloudlets, int allowedArrivals) {
        List<Cloudlet> cloudletList = new ArrayList<>();
        JsonObject spec = config.getRoot().getAsJsonObject("cloudlet_spec");
        int length = spec.get("CLOUDLET_LENGTH").getAsInt();
        int pes = spec.get("CLOUDLET_PES").getAsInt();
        int file_size = spec.get("CLOUDLET_FILESIZE").getAsInt();
        int output_size = spec.get("CLOUDLET_OUTPUTSIZE").getAsInt();
        UtilizationModel cpu_utilization = config.getCloudletCPU();
        UtilizationModel ram_utilization = config.getCloudletRAM();
        UtilizationModel bw_utilization = config.getCloudletBW();
        for (int i = 0; i < allowedArrivals; i++) {
            Cloudlet c = new CloudletSimple(currentCloudlets + i, length, pes)
                    .setUtilizationModelCpu(cpu_utilization)
                    .setUtilizationModelRam(ram_utilization)
                    .setUtilizationModelBw(bw_utilization);
            c.setFileSize(file_size);
            c.setOutputSize(output_size);
            cloudletList.add(c);
        }
        return cloudletList;
    }

    private void submitPoissonCloudlets(JsonArray datacentersConfig) {
        for (int i = 0; i < datacenters.size(); i++) {
            JsonObject dcConfig = datacentersConfig.get(i).getAsJsonObject();
            DatacenterBrokerSimple broker = brokers.get(i);
            if (broker.getVmCreatedList().isEmpty()) {
                System.err.printf("❌ [Tick %.2f] %s: No VMs available! Skipping cloudlet submission.\n",
                        simulation.clock(), broker.getName());
                continue;
            }

            int lastCloudlets = dcConfig.get("cloudlets").getAsInt() / getDaysInMonth(month_num) / scaleFactor;
            int currentCloudlets = totalCloudletsGenerated.get(i);
            int remaining = lastCloudlets - currentCloudlets;
            if (remaining <= 0) {
                updateProcessing();
                continue;
            }

            Vm vm = broker.getVmCreatedList().get(0);
            int vmPes = (int) vm.getPesNumber();
            int running = vm.getCloudletScheduler().getCloudletExecList().size();
            int waiting = vm.getCloudletScheduler().getCloudletWaitingList().size();
            if (waiting > 0)
                System.err.println("❌ There are waiting: " + waiting);
            int inflight = running + waiting;

            // limit inflight to number of PEs
            // int queueCapacity = vmPes - 1;
            int queueCapacity = vmPes - 1;
            int availableSlots = queueCapacity - inflight;
            if (availableSlots <= 0) {
                updateProcessing();
                continue;
            }

            int lambda = (int) (dcConfig.get("cloudlets").getAsInt() / getDaysInMonth(month_num) / daysToSeconds(1));
            PoissonDistribution poisson = new PoissonDistribution(lambda);
            double poissonArrivals = Math.min((poisson.sample()), remaining);
            // int allowedArrivals = (int) poissonArrivals;
            int allowedArrivals = (int) Math.min(poissonArrivals, availableSlots);
            if (allowedArrivals <= 0) {
                if (DEBUG) {
                    int submitted = broker.getCloudletSubmittedList().size();
                    int done = broker.getCloudletFinishedList().size();
                    System.out.printf(
                            "[Tick %.2f] %s: %d allowedArrivals (done/submitted/total): %d/%d/%d ;util: %.4f%% %n",
                            simulation.clock(), broker.getName(), allowedArrivals, done, submitted, lastCloudlets,
                            vm.getCpuPercentUtilization(simulation.clock()));
                }
                updateProcessing();
                continue;
            }

            List<Cloudlet> newCloudlets = createDynamicCloudlets(currentCloudlets, allowedArrivals);
            
            broker.submitCloudletList(newCloudlets);
            updateProcessing();
            if (DEBUG) {
                for (Cloudlet c : broker.getCloudletSubmittedList()) {
                    System.out.printf("Cloudlet %d: Status=%s, Start=%.2f, Finish=%.2f\n",
                            c.getId(), c.getStatus(), c.getLifeTime(), c.getFinishTime());
                }
            }
            totalCloudletsGenerated.set(i, currentCloudlets + allowedArrivals);
            if (DEBUG) {
                int submitted = broker.getCloudletSubmittedList().size();
                int done = broker.getCloudletFinishedList().size();
                System.out.printf("[Tick %.2f] %s: +%d cloudlets (done/submitted/total): %d/%d/%d ;util: %.4f%%%n",
                        simulation.clock(), broker.getName(), allowedArrivals, done, submitted, lastCloudlets,
                        vm.getCpuPercentUtilization());
            }
        }
    }

    private void printProgressBar(double tick, String nodeName, int finished, int submitted, int expected) {
        int barLength = 30;
        double progressRatio = (double) finished / expected;
        double submitRatio = (double) submitted / expected;
        int finishBars = (int) (progressRatio * barLength);
        int submitBars = (int) (submitRatio * barLength);
        StringBuilder bar = new StringBuilder("|");
        for (int j = 0; j < barLength; j++) {
            if (j < finishBars)
                bar.append('█');
            else if (j < submitBars)
                bar.append('▒');
            else
                bar.append(' ');
        }
        bar.append("|");

        System.err.printf("\r[Tick %.0f] %-10s C(F/S/E): %6d|%6d|%6d %s", tick,
                nodeName, finished, submitted, expected,
                bar.toString());
        System.err.flush();
        System.err.println();
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
                host.updateProcessing(simulation.clock());
                double host_utilization = host.getCpuUtilizationStats().getMean();
                double power = host.getPowerModel().getPower(host_utilization); // in watts
                totalPowerkW += power / 1000.0; // convert to kW
            }
            double energyKWs = totalPowerkW * config.getInterval(); // in seconds
            currentTickEnergy.add(energyKWs);
        }

        energyPerTickPerNode.add(currentTickEnergy);
    }

    private void writeTickEnergy() {
        String filename = "output/csv/day/" + config.get_filename() + "_month" + month_num + "_energy.csv";
        try (PrintWriter writer = new PrintWriter(new FileWriter(filename))) {
            writer.print("Tick");
            for (int i = 0; i < datacenters.size(); i++) {
                writer.printf(",Node%d", i + 1);
            }
            writer.println();
            for (int tick = 0; tick < energyPerTickPerNode.size(); tick++) {
                double tickTime = tick * config.getInterval();
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
        int numHosts = config.getRoot().get("hosts").getAsInt();
        List<Host> hostList = new ArrayList<>();
        for (int i = 0; i < numHosts; i++) {
            Host host = createHost(i);
            hostList.add(host);
        }
        VmAllocationPolicy policy = config.getVmAllocationPolicy();
        Datacenter datacenter = new DatacenterSimple(simulation, hostList, policy);
        datacenter.setName(name);
        datacenter.setSchedulingInterval(config.getInterval());
        return datacenter;
    }

    private Host createHost(final int id) {
        JsonObject hostSpec = config.getRoot().getAsJsonObject("host_spec");
        int hostPes = hostSpec.get("HOST_PES").getAsInt();
        int hostMips = hostSpec.get("HOST_MIPS").getAsInt();
        int hostRam = hostSpec.get("HOST_RAM").getAsInt();
        int hostBw = hostSpec.get("HOST_BW").getAsInt();
        long hostStorage = hostSpec.get("HOST_STORAGE").getAsLong();
        VmScheduler vmScheduler = config.getVMScheduler();
        List<Pe> peList = new ArrayList<>();
        for (int i = 0; i < hostPes; i++) {
            peList.add(new PeSimple(hostMips));
        }
        final var host = new HostSimple(hostRam, hostBw, hostStorage, peList);
        final var powerModel = config.getPowerModel();
        host.setId(id)
                .setVmScheduler(vmScheduler)
                .setPowerModel(powerModel);
        host.setRamProvisioner(new ResourceProvisionerSimple())
                .setBwProvisioner(new ResourceProvisionerSimple());
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
            vmGlobalIndex += config.getRoot().get("vm").getAsInt();
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
        JsonObject spec = config.getRoot().getAsJsonObject("host_spec");
        int vms = config.getRoot().get("vm").getAsInt();
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
            vm.setCloudletScheduler(config.getCloudletScheduler());
            vm.enableUtilizationStats();
            vmList.add(vm);
        }
        return vmList;
    }

    private void printDatacenterEnergyConsumption() {
        logger.info("\n--- Energy Report for month{} ---", month_num);
        System.out.printf("--- Energy Report for month%s ---\n", month_num);
        for (int i = 0; i < datacenters.size(); i++) {
            Datacenter dc = datacenters.get(i);
            DatacenterBrokerSimple broker = brokers.get(i);
            JsonObject dcConfig = config.getRoot().getAsJsonArray("MONTHS")
                    .get(month_num - 1).getAsJsonObject()
                    .getAsJsonArray("DATACENTERS")
                    .get(i).getAsJsonObject();
            int expectedTotal = dcConfig.get("cloudlets").getAsInt() / getDaysInMonth(month_num) / scaleFactor;
            int totalSubmittedCloudlets = broker.getCloudletSubmittedList().size();
            int totalFinishCloudlets = broker.getCloudletFinishedList().size();
            double totalEnergy = 0.0;
            for (Host host : dc.getHostList()) {
                final double utilization = host.getCpuUtilizationStats().getMean();
                System.err.println(utilization);
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

                logger.info("{:<2}: {:>6.2f} | C(P/S/E): {:>4}|{:>4}|{:>4} | Energy: {:.4f} kWh",
                        dc.getName(), activeTime, totalSubmittedCloudlets - totalFinishCloudlets,
                        totalSubmittedCloudlets,
                        expectedTotal, totalEnergy);
                System.out.printf("%-6s: %6.2f | C(P/S/E): %4d|%4d|%4d | Energy: %.4f kWh\n",
                        dc.getName(), activeTime, totalSubmittedCloudlets - totalFinishCloudlets,
                        totalSubmittedCloudlets,
                        expectedTotal, totalEnergy);
            }
        }
    }

    private static void createCloudletsResultTable(final DatacenterBrokerSimple broker) {
        new CloudletsTableBuilder(broker.getCloudletCreatedList()).build();
    }
}