package gpusim;

import static org.cloudsimplus.brokers.DatacenterBroker.LOGGER;

import java.util.Comparator;

import org.cloudsimplus.brokers.DatacenterBrokerSimple;
import org.cloudsimplus.cloudlets.Cloudlet;
import org.cloudsimplus.core.CloudSimPlus;
import org.cloudsimplus.vms.Vm;

/**
 * Broker not need to do anything.
 */
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

}
