package gpusim;

import java.util.ArrayList;
import java.util.List;

import org.cloudsimplus.hosts.HostSimple;
import org.cloudsimplus.hosts.HostSuitability;
import org.cloudsimplus.resources.Pe;
import org.cloudsimplus.vms.Vm;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GpuHost extends HostSimple {
    public static final GpuHost NULL = new GpuHost();
    private static final Logger LOGGER = LoggerFactory.getLogger(GpuHost.class.getSimpleName());

    private final List<Gpu> gpuList = new ArrayList<>();

    public GpuHost(final long ram, final long bw, final long storage, final List<Pe> peList, final List<Gpu> gpuList) {
        super(ram, bw, storage, peList);
        this.gpuList.addAll(gpuList);

    }

    private GpuHost() {
        this(0, 0, 0, List.of(Pe.NULL), List.of(Gpu.NULL));
        setId(-1);
    }

    public List<Gpu> getGpuList() {
        return gpuList;
    }

    public GpuHost addGpu(final Gpu gpu) {
        gpuList.add(gpu);
        return this;
    }

    public GpuHost removeGpu(final Gpu gpu) {
        gpuList.remove(gpu);
        return this;
    }

    public GpuHost removeGpu(final int index) {
        gpuList.remove(index);
        return this;
    }

    public GpuHost clearGpuList() {
        gpuList.clear();
        return this;
    }

    @Override
    public HostSuitability createVm(final Vm vm) {
        final HostSuitability suitability = super.createVm(vm);

        // need Gpu scheduling

        return suitability;
    }

    @Override
    public double updateProcessing(final double currentTime) {
        final double nextFinishingCloudletTime = super.updateProcessing(currentTime);

        // need Gpu logic

        return nextFinishingCloudletTime;
    }

}
