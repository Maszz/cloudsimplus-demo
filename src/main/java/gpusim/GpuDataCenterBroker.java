package gpusim;

import static org.cloudsimplus.brokers.DatacenterBroker.LOGGER;

import java.util.Comparator;

import org.cloudsimplus.brokers.DatacenterBrokerSimple;
import org.cloudsimplus.cloudlets.Cloudlet;
import org.cloudsimplus.core.CloudSimPlus;
import org.cloudsimplus.vms.Vm;

public class GpuDataCenterBroker extends DatacenterBrokerSimple {
    /**
     * Creates a DatacenterBroker object.
     *
     * @param simulation The CloudSimPlus instance that represents the simulation
     *                   the Entity is related to
     */
    public GpuDataCenterBroker(final CloudSimPlus simulation) {
        /**
         * Stll not need it.
         * 
         */
        super(simulation);
    }

    /**
     * Selects the VM with the lowest number of PEs that is able to run a given
     * Cloudlet.
     * 
     * @param cloudlet the Cloudlet to find a VM to run it
     * @return the VM selected for the Cloudlet or {@link Vm#NULL} if no suitable VM
     *         was found
     */

}
