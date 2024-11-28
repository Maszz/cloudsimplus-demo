/*
 * CloudSim Plus: A modern, highly-extensible and easier-to-use Framework for
 * Modeling and Simulation of Cloud Computing Infrastructures and Services.
 * http://cloudsimplus.org
 *
 *     Copyright (C) 2015-2021 Universidade da Beira Interior (UBI, Portugal) and
 *     the Instituto Federal de Educação Ciência e Tecnologia do Tocantins (IFTO, Brazil).
 *
 *     This file is part of CloudSim Plus.
 *
 *     CloudSim Plus is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     CloudSim Plus is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with CloudSim Plus. If not, see <http://www.gnu.org/licenses/>.
 */
package com.example.cloudsim.demo;

import ch.qos.logback.classic.Level;
import org.cloudsimplus.allocationpolicies.VmAllocationPolicyFirstFit;
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
import org.cloudsimplus.util.Log;
import org.cloudsimplus.utilizationmodels.UtilizationModelDynamic;
import org.cloudsimplus.vms.HostResourceStats;
import org.cloudsimplus.vms.Vm;
import org.cloudsimplus.vms.VmResourceStats;
import org.cloudsimplus.vms.VmSimple;
import static java.util.Comparator.comparingLong;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.cloudsimplus.util.TimeUtil.elapsedSeconds;
import static org.cloudsimplus.util.TimeUtil.secondsToStr;

/**
 * An example creating a huge number of Hosts, VMs and Cloudlets
 * to simulate a large-scale cloud infrastructure.
 *
 * <p>
 * The example may run out of memory.
 * Try to increase heap memory space passing, for instance,
 * -Xmx6g to the java command line, where 6g means 6GB of maximum heap size.
 * </p>
 *
 * <p>
 * Your computer may not even have enough memory capacity to run this example
 * and it may just crashes with OutOfMemoryException.
 * </p>
 *
 * <p>
 * Some factors that drastically impact simulation performance and memory
 * consumption
 * is the {@link #CLOUDLETS} number and {@link #SCHEDULING_INTERVAL}.
 * </p>
 *
 * @author Manoel Campos da Silva Filho
 * @since ClodSimPlus 7.3.1
 */
public class Main {
    private static final int RAM_PER_PE = 2048;
    private static final int HOSTS = 1000;
    private static final int HOST_PES = 16;
    private static final int HOST_MIPS = 38400; // in MIPS
    private static final int HOST_RAM = HOST_PES * RAM_PER_PE; // in Megabytes
    private static final long HOST_BW = 1024; // in Megabits/s
    private static final long HOST_STORAGE = 1_000_000; // in Megabytes

    private static final int VMS = 4000;
    private static final int VM_PES = 4;
    private static final int VM_RAM = VM_PES * RAM_PER_PE; // in Megabytes
    private static final int VM_BW = (int) (HOST_BW / VM_PES); // in Megabits/s

    private static final int CLOUDLETS = VMS;
    private static final int CLOUDLET_PES = VM_PES;
    private static final int CLOUDLET_LENGTH = 34_500_000;

    /** Indicates the time (in seconds) the Host takes to start up. */
    private static final double HOST_START_UP_DELAY = 0;
    /** Indicates the time (in seconds) the Host takes to shut down. */
    private static final double HOST_SHUT_DOWN_DELAY = 0;

    /** Indicates Host power consumption (in Watts) during startup. */
    private static final double HOST_START_UP_POWER = 5;

    /** Indicates Host power consumption (in Watts) during shutdown. */
    private static final double HOST_SHUT_DOWN_POWER = 3;

    /**
     * Defines the power a Host uses, even if it's idle (in Watts).
     */
    private static final double STATIC_POWER = 35;

    /**
     * The max power a Host uses (in Watts).
     */
    private static final int MAX_POWER = 50;

    /**
     * Defines a time interval to process cloudlets execution
     * and possibly collect data. Setting a value greater than 0
     * enables that interval, which cause huge performance penaults for
     * lage scale simulations.
     *
     * @see Datacenter#setSchedulingInterval(double)
     */
    private static final double SCHEDULING_INTERVAL = -1;

    private final CloudSimPlus simulation;
    private final DatacenterBroker broker0;
    private List<Host> hostList;

    private final List<Vm> vmList;

    private final List<Cloudlet> cloudletList;
    private final Datacenter datacenter0;
    private final double startSecs;
    private final Map<Double, Double> cpuUtilizationHistory = new HashMap<>();

    public static void main(String[] args) {
        new Main();
    }

    private Main() {
        // Disable logging for performance improvements.
        // Log.setLevel(Level.OFF);
        Log.setLevel(ch.qos.logback.classic.Level.INFO);

        this.startSecs = System.currentTimeMillis() / 1000.0;
        System.out.println("Creating simulation scenario at " + LocalDateTime.now());
        System.out.printf("Creating 1 Datacenter -> Hosts: %,d VMs: %,d Cloudlets: %,d%n", HOSTS, VMS, CLOUDLETS);

        simulation = new CloudSimPlus();
        datacenter0 = createDatacenter();

        // Creates a broker that is a software acting on behalf of a cloud customer to
        // manage his/her VMs and Cloudlets
        broker0 = new DatacenterBrokerSimple(simulation);

        vmList = createVms();
        cloudletList = createCloudlets();
        brokerSubmit();

        System.out.println("Starting simulation after " + actualElapsedTime());
        simulation.start();

        final long submittedCloudlets = broker0.getCloudletSubmittedList().size();
        final List<Cloudlet> cloudletFinishedList = broker0.getCloudletFinishedList();
        System.out.printf("Submitted Cloudlets: %d Finished Cloudlets: %d%n", submittedCloudlets,
                cloudletFinishedList.size());

        System.out.printf(
                "Simulated time: %s Actual Execution Time: %s%n", simulatedTime(), actualElapsedTime());
        System.out.println("------------------------------- SIMULATION FOR SCHEDULING INTERVAL = " + SCHEDULING_INTERVAL
                + " -------------------------------");
        final Comparator<Cloudlet> hostComparator = comparingLong(cl -> cl.getVm().getHost().getId());
        cloudletFinishedList.sort(hostComparator.thenComparing(cl -> cl.getVm().getId()));

        new CloudletsTableBuilder(cloudletFinishedList).build();
        printHostsCpuUtilizationAndPowerConsumption();
        printVmsCpuUtilizationAndPowerConsumption();
    }

    private String simulatedTime() {
        return secondsToStr(simulation.clock());
    }

    private String actualElapsedTime() {
        return secondsToStr(elapsedSeconds(startSecs));
    }

    private void brokerSubmit() {
        System.out.printf("Submitting %,d VMs%n", VMS);
        broker0.submitVmList(vmList);

        System.out.printf("Submitting %,d Cloudlets%n", CLOUDLETS);
        broker0.submitCloudletList(cloudletList);
    }

    /**
     * Creates a Datacenter and its Hosts.
     */
    private Datacenter createDatacenter() {
        hostList = new ArrayList<Host>(HOSTS);
        System.out.printf("Creating %,d Hosts%n", HOSTS);
        for (int i = 0; i < HOSTS; i++) {
            final var host = createHost();
            final var powerModel = new PowerModelHostSimple(MAX_POWER, STATIC_POWER);
            powerModel
                    .setStartupPower(HOST_START_UP_POWER)
                    .setShutDownPower(HOST_SHUT_DOWN_POWER);
            host.setStartupDelay(HOST_START_UP_DELAY).setShutDownDelay(HOST_SHUT_DOWN_DELAY);
            host.setId(i);
            host.setPowerModel(powerModel);
            host.enableUtilizationStats();

            hostList.add(host);
        }

        var dc = new DatacenterSimple(simulation, hostList, new VmAllocationPolicyFirstFit());
        dc.setSchedulingInterval(-1);
        return dc;
    }

    private Host createHost() {
        final var peList = new ArrayList<Pe>(HOST_PES);
        // List of Host's CPUs (Processing Elements, PEs)
        for (int i = 0; i < HOST_PES; i++) {
            // Uses a PeProvisionerSimple by default to provision PEs for VMs
            peList.add(new PeSimple(HOST_MIPS));
        }

        /*
         * Uses ResourceProvisionerSimple by default for RAM and BW provisioning
         * and VmSchedulerSpaceShared for VM scheduling.
         */
        return new HostSimple(HOST_RAM, HOST_BW, HOST_STORAGE, peList);
    }

    /**
     * Creates a list of VMs.
     */
    private List<Vm> createVms() {
        final var vmList = new ArrayList<Vm>(VMS);
        System.out.printf("Creating %,d VMs%n", VMS);
        for (int i = 0; i < VMS; i++) {
            // Uses a CloudletSchedulerTimeShared by default to schedule Cloudlets
            final var vm = new VmSimple(HOST_MIPS, VM_PES);
            vm.setRam(VM_RAM).setBw(VM_BW).setSize(10_000);
            vm.enableUtilizationStats();
            vmList.add(vm);
        }

        return vmList;
    }

    /**
     * Creates a list of Cloudlets.
     */
    private List<Cloudlet> createCloudlets() {
        final var cloudletList = new ArrayList<Cloudlet>(CLOUDLETS);

        // UtilizationModel defining the Cloudlets use only 50% of any resource all the
        // time

        for (int i = 0; i < CLOUDLETS; i++) {
            final var cloudlet = new CloudletSimple(CLOUDLET_LENGTH, CLOUDLET_PES,
                    new UtilizationModelDynamic(0.3).setUtilizationUpdateFunction(
                            time -> {
                                double mean = 0.5;
                                double stdDev = 0.1;
                                double utilization = mean + new Random().nextGaussian() * stdDev;
                                return Math.max(0, Math.min(1, utilization));
                            }));
            cloudlet.setSizes(1024);
            cloudletList.add(cloudlet);
        }

        return cloudletList;
    }

    private void printVmsCpuUtilizationAndPowerConsumption() {
        vmList.sort(comparingLong(vm -> vm.getHost().getId()));
        for (Vm vm : vmList) {
            final var powerModel = vm.getHost().getPowerModel();
            final double hostStaticPower = powerModel instanceof PowerModelHostSimple powerModelHost
                    ? powerModelHost.getStaticPower()
                    : 0;
            final double hostStaticPowerByVm = hostStaticPower / vm.getHost().getVmCreatedList().size();

            // VM CPU utilization relative to the host capacity
            final double vmRelativeCpuUtilization = vm.getCpuUtilizationStats().getMean()
                    / vm.getHost().getVmCreatedList().size();
            final double vmPower = powerModel.getPower(vmRelativeCpuUtilization) - hostStaticPower
                    + hostStaticPowerByVm; // W
            final VmResourceStats cpuStats = vm.getCpuUtilizationStats();
            System.out.printf(
                    "Vm   %2d CPU Usage Mean: %6.1f%% | Power Consumption Mean: %8.0f W%n",
                    vm.getId(), cpuStats.getMean() * 100, vmPower);
        }
    }

    /**
     * The Host CPU Utilization History is only computed
     * if VMs utilization history is enabled by calling
     * {@code vm.getUtilizationHistory().enable()}.
     */
    private void printHostsCpuUtilizationAndPowerConsumption() {
        System.out.println();
        for (final Host host : hostList) {
            printHostCpuUtilizationAndPowerConsumption(host);
        }
        System.out.println();
    }

    private void printHostCpuUtilizationAndPowerConsumption(final Host host) {
        // Mean CPU utilization and power consumption
        final HostResourceStats cpuStats = host.getCpuUtilizationStats();
        final double utilizationPercentMean = cpuStats.getMean(); // Mean CPU utilization
        final double wattsMean = host.getPowerModel().getPower(utilizationPercentMean); // Mean power consumption

        // Calculate host alive time (in seconds)
        final double hostAliveTime = host.getSimulation().clock() - host.getFirstStartTime();

        // Calculate total power consumption in watt-seconds (Joules)
        final double totalPowerConsumptionWatts = wattsMean * hostAliveTime;

        // Convert total power consumption to kilowatt-hours (kWh)
        final double totalPowerConsumptionKWh = totalPowerConsumptionWatts / (1000 * 3600);

        // Print results
        System.out.printf(
                "Host %2d CPU Usage mean: %6.1f%% | Power Consumption mean: %8.0f W | Total Power Consumption: %.1f W-s (%.6f kWh) | Host Alive Time: %.1f s%n",
                host.getId(), utilizationPercentMean * 100, wattsMean, totalPowerConsumptionWatts,
                totalPowerConsumptionKWh, hostAliveTime);

    }
}