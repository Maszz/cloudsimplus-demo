package energy;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.cloudsimplus.allocationpolicies.VmAllocationPolicyFirstFit;
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
import org.cloudsimplus.vms.Vm;
import org.cloudsimplus.vms.VmSimple;

import java.util.ArrayList;
import java.util.List;

public class Classical {
    private int ramPerPe;
    private int hosts;
    private int hostPes;
    private int hostMips;
    private long hostBw;
    private long hostStorage;
    private int vms;
    private int vmPes;
    private int cloudletLength;
    private double schedulingInterval;

    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java Classical <config-file>");
            System.exit(1);
        }

        Config config = new Config(args[0]);
        new Classical(config).run();
    }

    public Classical(Config config) {
        this.ramPerPe = config.getInt("RAM_PER_PE");
        this.hosts = config.getInt("HOSTS");
        this.hostPes = config.getInt("HOST_PES");
        this.hostMips = config.getInt("HOST_MIPS");
        this.hostBw = config.getLong("HOST_BW");
        this.hostStorage = config.getLong("HOST_STORAGE");
        this.vms = config.getInt("VMS");
        this.vmPes = config.getInt("VM_PES");
        this.cloudletLength = config.getInt("CLOUDLET_LENGTH");
        this.schedulingInterval = config.getDouble("SCHEDULING_INTERVAL");
    }

    public void run() {
        CloudSimPlus simulation = new CloudSimPlus();

        List<Host> hostList = createHosts();
        Datacenter datacenter = new DatacenterSimple(simulation, hostList, new VmAllocationPolicyFirstFit());
        datacenter.setSchedulingInterval(schedulingInterval);

        List<Vm> vmList = createVms();
        List<Cloudlet> cloudletList = createCloudlets();

        var broker = new DatacenterBrokerSimple(simulation);
        broker.submitVmList(vmList);
        broker.submitCloudletList(cloudletList);

        simulation.start();

        new CloudletsTableBuilder(broker.getCloudletFinishedList()).build();
    }

    private List<Host> createHosts() {
        List<Host> hostList = new ArrayList<>(hosts);
        for (int i = 0; i < hosts; i++) {
            List<Pe> peList = new ArrayList<>(hostPes);
            for (int j = 0; j < hostPes; j++) {
                peList.add(new PeSimple(hostMips));
            }
            Host host = new HostSimple(ramPerPe * hostPes, hostBw, hostStorage, peList);
            host.setPowerModel(new PowerModelHostSimple(800, 80));
            hostList.add(host);
        }
        return hostList;
    }

    private List<Vm> createVms() {
        List<Vm> vmList = new ArrayList<>(vms);
        for (int i = 0; i < vms; i++) {
            Vm vm = new VmSimple(hostMips, vmPes);
            vm.setRam(vmPes * ramPerPe).setBw(hostBw / vmPes).setSize(10000);
            vmList.add(vm);
        }
        return vmList;
    }

    private List<Cloudlet> createCloudlets() {
        List<Cloudlet> cloudletList = new ArrayList<>(vms);
        for (int i = 0; i < vms; i++) {
            Cloudlet cloudlet = new CloudletSimple(cloudletLength, vmPes);
            cloudlet.setSizes(1024);
            cloudletList.add(cloudlet);
        }
        return cloudletList;
    }
}
