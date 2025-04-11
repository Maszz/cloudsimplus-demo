package gpusim.allocationpolicies;

import org.cloudsimplus.allocationpolicies.VmAllocationPolicy;
import org.cloudsimplus.autoscaling.VerticalVmScaling;
import org.cloudsimplus.datacenters.Datacenter;
import org.cloudsimplus.hosts.Host;
import org.cloudsimplus.hosts.HostSuitability;
import org.cloudsimplus.schedulers.MipsShare;
import org.cloudsimplus.vms.Vm;
import lombok.NonNull;

import gpusim.hosts.GpuHost;
import gpusim.vms.GpuVm;
import gpusim.datacenters.GpuDatacenter;

import javax.annotation.processing.Processor;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

public abstract class GpuVmAllocationPolicyAbstract implements GpuVmAllocationPolicy {

    private BiFunction<GpuVmAllocationPolicy, GpuVm, Optional<GpuHost>> findGpuHostForGpuVmFunction;

    private Datacenter datacenter;

    private int gpuHostCountForParallelSearch;

    public GpuVmAllocationPolicyAbstract() {
        this(null);
    }

    public GpuVmAllocationPolicyAbstract(
            final BiFunction<VmAllocationPolicy, Vm, Optional<Host>> findGpuHostForGpuVmFunction) {
        setDatacenter(Datacenter.NULL);
        setFindHostForVmFunction(findGpuHostForGpuVmFunction);
        this.gpuHostCountForParallelSearch = DEF_GPUHOST_COUNT_PARALLEL_SEARCH;
    }

    @Override
    public final <T extends Host> List<T> getHostList() {
        return datacenter.getHostList();
    }

    @Override
    public GpuDatacenter getDatacenter() {
        return datacenter == Datacenter.NULL ? null : (GpuDatacenter) datacenter;
    }

    @Override
    public GpuVmAllocationPolicy setDatacenter(@NonNull final Datacenter datacenter) {
        // Hacky method to allow CloudSim Plus to use our fake datacenters, while
        // preserving GpuDatacenter normally
        if (datacenter == null || (!(datacenter instanceof GpuDatacenter) && datacenter != Datacenter.NULL)) {
            throw new IllegalArgumentException("Datacenter must be instance of GpuDatacenter, or Datacenter.NULL.");
        }

        this.datacenter = datacenter;
        return this;
    }

    @Override
    public boolean scaleVmVertically(final VerticalVmScaling scaling) {
        if (scaling.isVmUnderloaded()) {
            return downScaleVmVertically(scaling);
        }

        if (scaling.isVmOverloaded()) {
            return upScaleVmVertically(scaling);
        }

        return false;
    }

    private boolean upScaleVmVertically(final VerticalVmScaling scaling) {
        return isRequestingCpuScaling(scaling) ? scaleVmPesUpOrDown(scaling) : upScaleVmNonCpuResource(scaling);
    }

    private boolean downScaleVmVertically(final VerticalVmScaling scaling) {
        return isRequestingCpuScaling(scaling) ? scaleVmPesUpOrDown(scaling) : downScaleVmNonCpuResource(scaling);
    }

    private boolean scaleVmPesUpOrDown(final VerticalVmScaling scaling) {
        final double pesNumberForScaling = scaling.getResourceAmountToScale();
        if (pesNumberForScaling == 0) {
            return false;
        }

        final boolean isVmUnderloaded = scaling.isVmUnderloaded();
        // Avoids trying to downscale the number of vPEs to zero
        if (isVmUnderloaded && scaling.getVm().getPesNumber() == pesNumberForScaling) {
            scaling.logDownscaleToZeroNotAllowed();
            return false;
        }

        if (scaling.isVmOverloaded() && isNotHostPesSuitableToUpScaleVm(scaling)) {
            scaling.logResourceUnavailable();
            return false;
        }

        final Vm vm = scaling.getVm();
        vm.getHost().getVmScheduler().deallocatePesFromVm(vm);
        final int signal = isVmUnderloaded ? -1 : 1;
        // Removes or adds some capacity from/to the resource, respectively if the VM is
        // under or overloaded
        vm.getProcessor().sumCapacity((long) pesNumberForScaling * signal);

        vm.getHost().getVmScheduler().allocatePesForVm(vm);
        return true;
    }

    private boolean isNotHostPesSuitableToUpScaleVm(final VerticalVmScaling scaling) {
        final Vm vm = scaling.getVm();
        final long pesCountForScaling = (long) scaling.getResourceAmountToScale();
        final MipsShare additionalVmMips = new MipsShare(pesCountForScaling, vm.getMips());
        return !vm.getHost().getVmScheduler().isSuitableForVm(vm, additionalVmMips);
    }

    private boolean isRequestingCpuScaling(final VerticalVmScaling scaling) {
        return Processor.class.equals(scaling.getResourceClass());
    }

    private boolean upScaleVmNonCpuResource(final VerticalVmScaling scaling) {
        return scaling.allocateResourceForVm();
    }

    private boolean downScaleVmNonCpuResource(final VerticalVmScaling scaling) {
        final var resourceManageableClass = scaling.getResourceClass();
        final var vmResource = scaling.getVm().getResource(resourceManageableClass);
        final double amountToDeallocate = scaling.getResourceAmountToScale();
        final var resourceProvisioner = scaling.getVm().getHost().getProvisioner(resourceManageableClass);
        final double newTotalVmResource = vmResource.getCapacity() - amountToDeallocate;
        if (resourceProvisioner.allocateResourceForVm(scaling.getVm(), newTotalVmResource)) {
            LOGGER.info(
                    "{}: {}: {} {} deallocated from {}: new capacity is {}. Current resource usage is {}%",
                    scaling.getVm().getSimulation().clockStr(),
                    scaling.getClass().getSimpleName(),
                    (long) amountToDeallocate, resourceManageableClass.getSimpleName(),
                    scaling.getVm(), vmResource.getCapacity(),
                    vmResource.getPercentUtilization() * 100);
            return true;
        }

        LOGGER.error(
                "{}: {}: {} requested to reduce {} capacity by {} but an unexpected error occurred and the resource was not resized",
                scaling.getVm().getSimulation().clockStr(),
                scaling.getClass().getSimpleName(),
                scaling.getVm(),
                resourceManageableClass.getSimpleName(), (long) amountToDeallocate);
        return false;

    }

    @Override
    public HostSuitability allocateHostForVm(final Vm vm) {
        if (getHostList().isEmpty()) {
            LOGGER.error(
                    "{}: {}: {} could not be allocated because there isn't any GpuHost for "
                            + "GpuDatacenter {}",
                    vm.getSimulation().clockStr(), getClass().getSimpleName(), vm,
                    getDatacenter().getId());
            // return new HostSuitability("GpuDatacenter has no Gpuhost.");
            return new HostSuitability(vm, "GpuDatacenter has no Gpuhost.");

        }

        if (vm.isCreated()) {
            // return new HostSuitability("GpuVM is already created");
            return new HostSuitability(vm, "GpuVM is already created");
        }

        final var optionalGpuHost = findHostForVm(vm);
        if (optionalGpuHost.filter(Host::isActive).isPresent()) {
            return allocateHostForVm(vm, optionalGpuHost.get());
        }

        LOGGER.warn("{}: {}: No suitable Gpuhost found for {} in {}", vm.getSimulation().clockStr(),
                getClass().getSimpleName(), vm, datacenter);
        // return new HostSuitability("No suitable Gpuhost found");
        return new HostSuitability(vm, "No suitable Gpuhost found");
    }

    @Override
    public final Set<HostSuitability> allocateHostForVm(@NonNull final List<Vm> vmList) {
        if (vmList.isEmpty()) {
            LOGGER.warn("{}: {}: No GpuVMs to allocate a Gpuhost to", getClass().getSimpleName(), vmList);
            return Collections.emptySet();
        }
        if (vmList.stream().anyMatch(vm -> !(vm instanceof GpuVm))) {
            throw new IllegalArgumentException(
                    "The list of VMs to allocate a Gpuhost to must contain only GpuVMs");
        }
        // requireNonNull(vmList, "The list of GpuVMs to allocate a Gpuhost to cannot be
        // null");
        return vmList.stream()
                .map(this::allocateHostForVm)
                .filter(hostSuitability -> !hostSuitability.fully())
                .collect(Collectors.toSet());
    }

    @Override
    public HostSuitability allocateHostForVm(final Vm vm, final Host host) {
        /*
         * if(vm instanceof VmGroup vmGroup){
         * return createVmsFromGroup(vmGroup, host);
         * }
         */

        return createVm((GpuVm) vm, (GpuHost) host);
    }

    /*
     * private HostSuitability createGpuVmsFromGroup (final VmGroup vmGroup, final
     * Host host) {
     * int createdVms = 0;
     * final var hostSuitabilityForVmGroup = new HostSuitability();
     * for (final Vm vm : vmGroup.getVmList()) {
     * final var hostSuitability = createVm(vm, host);
     * hostSuitabilityForVmGroup.setSuitability(hostSuitability);
     * createdVms += Conversion.boolToInt(hostSuitability.fully());
     * }
     * 
     * vmGroup.setCreated(createdVms > 0);
     * if(vmGroup.isCreated()) {
     * vmGroup.setHost(host);
     * }
     * 
     * return hostSuitabilityForVmGroup;
     * }
     */

    private HostSuitability createVm(final GpuVm vm, final GpuHost host) {
        final var suitability = host.createVm(vm);
        if (suitability.fully()) {
            LOGGER.info(
                    "{}: {}: {} has been allocated to {}",
                    vm.getSimulation().clockStr(), getClass().getSimpleName(), vm, host);
        } else {
            LOGGER.error(
                    "{}: {} Creation of {} on {} failed due to {}.",
                    vm.getSimulation().clockStr(), getClass().getSimpleName(), vm, host, suitability);
        }

        return suitability;
    }

    // @Override
    // public void deallocateHostForVm(final Vm vm) {
    // vm.getHost().destroyVm(vm);
    // }

    @Override
    public final GpuVmAllocationPolicy setFindHostForVmFunction(
            final BiFunction<VmAllocationPolicy, Vm, Optional<Host>> findGpuHostForGpuVmFunction) {
        this.findGpuHostForGpuVmFunction = (BiFunction) findGpuHostForGpuVmFunction;
        return this;
    }

    @Override
    public final Optional<Host> findHostForVm(final Vm vm) {
        final var optionalHost = findGpuHostForGpuVmFunction == null ? defaultFindGpuHostForGpuVm((GpuVm) vm)
                : findGpuHostForGpuVmFunction.apply(this, (GpuVm) vm);
        // optionalHost = Optional.of((GpuHost)optionalHost.get().setActive(true));
        return optionalHost.map(gpuHost -> gpuHost.setActive(true));
        // return optionalHost;
    }

    protected abstract Optional<Host> defaultFindGpuHostForGpuVm(GpuVm vm);

    @Override
    public Map<Vm, Host> getOptimizedAllocationMap(final List<? extends Vm> gpuvmList) {
        /*
         * This method implementation doesn't perform any
         * VM placement optimization and, in fact, has no effect.
         * Classes implementing the {@link VmAllocationPolicyMigration}
         * provide actual implementations for this method that can be overridden
         * by subclasses.
         */
        return Collections.emptyMap();
    }

    @Override
    public int getHostCountForParallelSearch() {
        return gpuHostCountForParallelSearch;
    }

    @Override
    public GpuVmAllocationPolicy setHostCountForParallelSearch(final int hostCountForParallelSearch) {
        this.gpuHostCountForParallelSearch = hostCountForParallelSearch;
        return this;
    }

    @Override
    public boolean isVmMigrationSupported() {
        return false;
    }
}