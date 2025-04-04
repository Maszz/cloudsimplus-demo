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
import org.cloudsimplus.power.models.PowerModelHostSpec;
import org.cloudsimplus.resources.Pe;
import org.cloudsimplus.resources.PeSimple;
import org.cloudsimplus.schedulers.vm.VmSchedulerTimeShared;
import org.cloudsimplus.utilizationmodels.UtilizationModel;
import org.cloudsimplus.utilizationmodels.UtilizationModelFull;
import org.cloudsimplus.vms.Vm;
import org.cloudsimplus.vms.VmSimple;
import org.cloudsimplus.allocationpolicies.VmAllocationPolicyBestFit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.cloudsimplus.util.Log;

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

    private final List<Double> powerPerSecond = new ArrayList<>();
    private final List<Double> energyPerSecond = new ArrayList<>();
    private final List<Integer> successPerSecond = new ArrayList<>();
    private final List<Integer> pendingPerSecond = new ArrayList<>();
    private double cumulativeEnergykWs = 0.0;

    private String monthLabel = "Month";
    private static final boolean DEBUG = true;

    public static void main(String[] args) {
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
            new Possion().run(datacenters, label);
            if (DEBUG) break;
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

        simulation.addOnClockTickListener(eventInfo -> {
            if (DEBUG) logger.debug("Tick: {}", simulation.clock());
            submitPoissonCloudlets(datacentersConfig, lambda);
        });

        setupEnergyTracking();
        simulation.start();

        brokers.forEach(Possion::createCloudletsResultTable);
        printDatacenterEnergyConsumption();
    }

    private void submitPoissonCloudlets(JsonArray datacentersConfig, int lambda) {
        for (int i = 0; i < datacenters.size(); i++) {
            JsonObject dcConfig = datacentersConfig.get(i).getAsJsonObject();
            DatacenterBrokerSimple broker = brokers.get(i);
            int lastCloudlets = dcConfig.get("cloudlets").getAsInt();
            int currentCloudlets = totalCloudletsGenerated.get(i);
            int remaining = lastCloudlets - currentCloudlets;
            if (remaining <= 0) continue;

            PoissonDistribution poisson = new PoissonDistribution(lambda);
            int poissonArrivals = Math.min(poisson.sample(), remaining);
            if (DEBUG) logger.debug("[Tick {}] {}: +{} cloudlets (total so far: {}/{})", simulation.clock(), broker.getName(), poissonArrivals, currentCloudlets + poissonArrivals, lastCloudlets);

            int unfinished = broker.getCloudletSubmittedList().size() - broker.getCloudletFinishedList().size();
            if (unfinished > 64) continue;

            List<Cloudlet> newCloudlets = createDynamicCloudlets(currentCloudlets, poissonArrivals);
            broker.submitCloudletList(newCloudlets);
            currentCloudlets += poissonArrivals;
            totalCloudletsGenerated.set(i, currentCloudlets);
        }

        boolean allFinished = true;
        for (int i = 0; i < datacenters.size(); i++) {
            int totalExpected = datacentersConfig.get(i).getAsJsonObject().get("cloudlets").getAsInt();
            DatacenterBrokerSimple broker = brokers.get(i);
            if (broker.getCloudletSubmittedList().size() < totalExpected) {
                allFinished = false;
                break;
            }
        }

        if (allFinished) {
            if (DEBUG) logger.info("All cloudlets submitted, terminating simulation...");
            simulation.terminate();
        }
    }

    private void setupEnergyTracking() {
        simulation.addOnClockTickListener(eventInfo -> {
            int success = 0;
            int pending = 0;
            for (DatacenterBrokerSimple broker : brokers) {
                success += broker.getCloudletFinishedList().size();
                pending += broker.getCloudletSubmittedList().size() - success;
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
        List<Pe> peList = new ArrayList<>();
        JsonObject hostSpec = control.getAsJsonObject("host_spec");
        int pes = hostSpec.get("HOST_PES").getAsInt();
        int mips = hostSpec.get("HOST_MIPS").getAsInt();
        for (int i = 0; i < pes; i++) {
            peList.add(new PeSimple(mips));
        }
        Host host = new HostSimple(
                hostSpec.get("HOST_RAM").getAsInt(),
                hostSpec.get("HOST_BW").getAsInt(),
                hostSpec.get("HOST_STORAGE").getAsLong(),
                peList
        );
        host.setVmScheduler(new VmSchedulerTimeShared());
        host.setPowerModel(PowerModelHostSpec.getInstance(control.get("power_spec_path").getAsString()));
        host.enableUtilizationStats();
        Datacenter datacenter = new DatacenterSimple(simulation, List.of(host), new VmAllocationPolicyBestFit());
        datacenter.setName(name);
        datacenter.setSchedulingInterval(control.get("SCHEDULING_INTERVAL").getAsInt());
        return datacenter;
    }

    private void createBrokersVms(JsonArray datacentersConfig) {
        int vmGlobalIndex = 0;
        for (int index = 0; index < datacenters.size(); index++) {
            Datacenter dc = datacenters.get(index);
            JsonObject dcConfig = datacentersConfig.get(index).getAsJsonObject();
            DatacenterBrokerSimple broker = new DatacenterBrokerSimple(simulation);
            broker.setName(dcConfig.get("name").getAsString());
            broker.setLastSelectedDc(dc);
            final var vmList = createVms(vmGlobalIndex);
            broker.submitVmList(vmList);
            broker.setVmDestructionDelay(Double.MAX_VALUE);
            brokers.add(broker);
            vmGlobalIndex += control.get("vm").getAsInt();
        }
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
            vm.enableUtilizationStats();
            vmList.add(vm);
        }
        return vmList;
    }

    private List<Cloudlet> createDynamicCloudlets(int startId, int count) {
        List<Cloudlet> cloudletList = new ArrayList<>();
        JsonObject spec = control.getAsJsonObject("cloudlet_spec");
        long length = spec.get("CLOUDLET_LENGTH").getAsLong();
        int pes = spec.get("CLOUDLET_PES").getAsInt();
        UtilizationModel utilization = new UtilizationModelFull();

        for (int i = 0; i < count; i++) {
            Cloudlet c = new CloudletSimple(startId + i, length, pes)
                    .setUtilizationModelCpu(utilization)
                    .setUtilizationModelRam(utilization)
                    .setUtilizationModelBw(utilization);
            cloudletList.add(c);
        }
        return cloudletList;
    }

    private static void createCloudletsResultTable(final DatacenterBrokerSimple broker) {
        new CloudletsTableBuilder(broker.getCloudletCreatedList()).build();
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
            logger.info("Datacenter: {} | Cloudlets: {} | Energy: {:.4f} kWh", dc.getName(), totalCloudlets, totalEnergy);
            System.out.printf("Datacenter: %s | Cloudlets: %d | Energy: %.4f kWh\n", dc.getName(), totalCloudlets, totalEnergy);
        }
    }
}