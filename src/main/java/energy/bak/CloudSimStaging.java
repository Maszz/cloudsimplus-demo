package energy.bak;

import org.cloudsimplus.brokers.DatacenterBrokerSimple;
import org.cloudsimplus.cloudlets.Cloudlet;
import org.cloudsimplus.cloudlets.network.CloudletReceiveTask;
import org.cloudsimplus.cloudlets.network.CloudletSendTask;
import org.cloudsimplus.cloudlets.network.NetworkCloudlet;
import org.cloudsimplus.core.CloudSimPlus;
import org.cloudsimplus.datacenters.Datacenter;
import org.cloudsimplus.datacenters.DatacenterSimple;
import org.cloudsimplus.hosts.Host;
import org.cloudsimplus.hosts.HostSimple;
import org.cloudsimplus.network.topologies.BriteNetworkTopology;
import org.cloudsimplus.resources.Pe;
import org.cloudsimplus.resources.PeSimple;
import org.cloudsimplus.utilizationmodels.UtilizationModelFull;
import org.cloudsimplus.vms.Vm;
import org.cloudsimplus.vms.VmSimple;
import org.cloudsimplus.listeners.EventInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class CloudSimStaging {
    private static DatacenterBrokerSimple broker;
    private static CloudSimPlus simulation;
    private static Datacenter datacenter1, datacenter2;
    private static int cloudletCounter = 0;

    public static void main(String[] args) {
        // 1. Initialize CloudSim
        simulation = new CloudSimPlus();
        
        // 2. Create Datacenters
        datacenter1 = createDatacenter(simulation, "Datacenter1");
        datacenter2 = createDatacenter(simulation, "Datacenter2");
        
        // 3. Create Broker
        broker = new DatacenterBrokerSimple(simulation);
        
        // 4. Create VM List
        List<Vm> vmList = new ArrayList<>();
        Vm vm1 = new VmSimple(1000, 1);
        vm1.setRam(2048).setBw(10000).setSize(1000000);
        Vm vm2 = new VmSimple(1000, 1);
        vm2.setRam(2048).setBw(10000).setSize(1000000);
        vmList.add(vm1);
        vmList.add(vm2);
        broker.submitVmList(vmList);
        
        // 5. Load Network Topology
        BriteNetworkTopology networkTopology = new BriteNetworkTopology();
        simulation.setNetworkTopology(networkTopology);
        
        // 6. Start Dynamic Cloudlet Creation Every Second
        simulation.addOnClockTickListener(CloudSimStaging::generateNetworkCloudletsPerSecond);
        
        // 7. Start Simulation
        simulation.start();
        
        // 8. Print Results
        printCloudletList(broker);
    }

    private static Datacenter createDatacenter(CloudSimPlus simulation, String name) {
        List<Host> hostList = new ArrayList<>();
        List<Pe> peList = new ArrayList<>();
        peList.add(new PeSimple(1000)); // Add one Processing Element (PE)
        
        Host host = new HostSimple(2048, 10000, 1000000, peList);
        hostList.add(host);
        
        Datacenter datacenter = new DatacenterSimple(simulation, hostList);
        datacenter.setName(name);
        return datacenter;
    }

    private static void generateNetworkCloudletsPerSecond(EventInfo eventInfo) {
        if (eventInfo.getTime() > 10) { // Stop generating after 10 seconds
            simulation.terminate();
            return;
        }

        List<NetworkCloudlet> cloudletList = createNetworkCloudlets(2); // Generate 2 cloudlets per second
        broker.submitCloudletList(cloudletList);
        System.out.printf("Generated %d new network cloudlets at time %.2f\n", cloudletList.size(), eventInfo.getTime());
    }

    private static List<NetworkCloudlet> createNetworkCloudlets(int numCloudlets) {
        List<NetworkCloudlet> cloudletList = new ArrayList<>();
        Random rand = new Random();

        for (int i = 0; i < numCloudlets; i++) {
            int length = 5000 + rand.nextInt(5000); // Random length between 5000-10000
            int pes = 1 + rand.nextInt(2); // Random PEs (1 or 2)
            
            NetworkCloudlet sender = new NetworkCloudlet(cloudletCounter++, pes);
            NetworkCloudlet receiver = new NetworkCloudlet(cloudletCounter++, pes);
            
            sender.setUtilizationModel(new UtilizationModelFull());
            receiver.setUtilizationModel(new UtilizationModelFull());
            
            CloudletSendTask sendTask = new CloudletSendTask(cloudletCounter);
            CloudletReceiveTask receiveTask = new CloudletReceiveTask(cloudletCounter, broker.getVmCreatedList().get(i));
            
            sendTask.addPacket(receiver, 500);
            sender.addTask(sendTask);
            receiver.addTask(receiveTask);
            
            cloudletList.add(sender);
            cloudletList.add(receiver);
        }
        return cloudletList;
    }

    private static void printCloudletList(DatacenterBrokerSimple broker) {
        for (Cloudlet cloudlet : broker.getCloudletFinishedList()) {
            System.out.printf("Cloudlet %d finished. Execution time: %.2f seconds\n", cloudlet.getId(), cloudlet.getTotalExecutionTime());
        }
    }
}
