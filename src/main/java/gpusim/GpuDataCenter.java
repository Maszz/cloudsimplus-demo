package gpusim;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.cloudsimplus.allocationpolicies.VmAllocationPolicy;
import org.cloudsimplus.allocationpolicies.VmAllocationPolicySimple;
import org.cloudsimplus.core.Simulation;
import org.cloudsimplus.datacenters.DatacenterSimple;
import org.cloudsimplus.hosts.Host;
import org.cloudsimplus.hosts.network.NetworkHost;
import org.cloudsimplus.network.switches.EdgeSwitch;
import org.cloudsimplus.network.switches.Switch;

public class GpuDataCenter extends DatacenterSimple {
    public GpuDataCenter(Simulation simulation, List<? extends Host> hostList, VmAllocationPolicy vmAllocationPolicy) {
        super(simulation, hostList, vmAllocationPolicy);
    }

    public GpuDataCenter(Simulation simulation, List<? extends Host> hostList) {
        super(simulation, hostList);
    }

    @Override
    public List<GpuHost> getHostList() {
        return super.getHostList();
    }

}
