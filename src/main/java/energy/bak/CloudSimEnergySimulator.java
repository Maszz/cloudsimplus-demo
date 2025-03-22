package energy.bak;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import org.cloudsimplus.cloudlets.Cloudlet;
import org.cloudsimplus.cloudlets.CloudletSimple;
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
import org.cloudsimplus.schedulers.cloudlet.CloudletSchedulerTimeShared;
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

public class CloudSimEnergySimulator {
    public static void main(String[] args) {
        CloudSimPlus simulation = new CloudSimPlus();

        Datacenter datacenter1 = createDatacenter(simulation);
        Datacenter datacenter2 = createDatacenter(simulation);

        DatacenterBrokerSimple broker = new DatacenterBrokerSimple(simulation);

        List<Vm> vmList = createVms(2);
        broker.submitVmList(vmList);

        List<Cloudlet> cloudletList = createCloudlets(5);
        broker.submitCloudletList(cloudletList);

        simulation.start();

        printResults(datacenter1, datacenter2);
    }

    private static Datacenter createDatacenter(CloudSimPlus simulation) {
        List<Host> hostList = new ArrayList<>();
        int hostPes = 4;
        long ram = 16384; // 16 GB
        long storage = 1_000_000; // 1 TB
        long bw = 10_000; // 10 Gbps

        List<Pe> peList = new ArrayList<>();
        for (int i = 0; i < hostPes; i++) {
            peList.add(new PeSimple(1000)); // Each PE has 1000 MIPS
        }

        Host host = new HostSimple(ram, bw, storage, peList);
        host.setVmScheduler(new VmSchedulerTimeShared());
        host.setPowerModel(new PowerModelHostSimple(200, 10)); // Power model with max 200W, idle 10W
        hostList.add(host);

        return new DatacenterSimple(simulation, hostList);
    }

    private static List<Vm> createVms(int numberOfVms) {
        List<Vm> vmList = new ArrayList<>();
        long ram = 4096; // 4 GB
        long bw = 1000;
        long size = 10_000; // 10 GB
        int pesNumber = 2;
        double mips = 500;

        for (int i = 0; i < numberOfVms; i++) {
            Vm vm = new VmSimple(mips, pesNumber);
            vm.setRam(ram).setBw(bw).setSize(size);
            vm.setCloudletScheduler(new CloudletSchedulerTimeShared());
            vmList.add(vm);
        }
        return vmList;
    }

    private static List<Cloudlet> createCloudlets(int numberOfCloudlets) {
        List<Cloudlet> cloudletList = new ArrayList<>();
        int pesNumber = 1;
        long length = 10000; // Represents TPS workload
        long fileSize = 300;
        long outputSize = 300;

        for (int i = 0; i < numberOfCloudlets; i++) {
            Cloudlet cloudlet = new CloudletSimple(length, pesNumber);
            cloudlet.setFileSize(fileSize).setOutputSize(outputSize);
            cloudlet.setUtilizationModel(new UtilizationModelDynamic(0.5)); // 50% CPU utilization
            cloudletList.add(cloudlet);
        }
        return cloudletList;
    }

    private static void printResults(Datacenter dc1, Datacenter dc2) {
        System.out.println("\nRESULTS");
        System.out.println("========================");
        printDatacenterEnergy(dc1, "Datacenter 1");
        printDatacenterEnergy(dc2, "Datacenter 2");
    }

    private static void printDatacenterEnergy(Datacenter datacenter, String name) {
        double totalEnergy = 0;
        System.out.println("\n" + name + " Host Energy Consumption:");
        for (Host host : datacenter.getHostList()) {
            double energy = host.getPowerModel().getPower(host.getCpuPercentUtilization());
            totalEnergy += energy;
            System.out.printf("Host %d CPU Utilization: %.2f%%, Energy: %.2fW\n",
                    host.getId(), host.getCpuPercentUtilization() * 100, energy);
        }
        System.out.printf("Total Energy Consumption in %s: %.2fW\n", name, totalEnergy);
    }
}