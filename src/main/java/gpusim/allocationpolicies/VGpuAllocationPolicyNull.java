package gpusim.allocationpolicies;

import org.cloudsimplus.autoscaling.VerticalVmScaling;

import gpusim.resources.Gpu;
import gpusim.resources.GpuSuitability;
import gpusim.vgpu.VGpu;
import gpusim.videocards.Videocard;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiFunction;

public final class VGpuAllocationPolicyNull implements VGpuAllocationPolicy {

    @Override
    public Videocard getVideocard() {
        return Videocard.NULL;
    }

    @Override
    public void setVideocard(Videocard videocard) {
        /**/ }

    @Override
    public GpuSuitability allocateGpuForVGpu(VGpu vgpu) {
        return GpuSuitability.NULL;
    }

    @Override
    public GpuSuitability allocateGpuForVGpu(VGpu vgpu, Gpu gpu) {
        return GpuSuitability.NULL;
    }

    @Override
    public <T extends VGpu> List<T> allocateGpuForVGpu(Collection<T> vgpuCollection) {
        return Collections.emptyList();
    }

    @Override
    public boolean scaleVmVertically(VerticalVmScaling scaling) {
        return false;
    }

    // @Override public boolean scaleVmVertically (VerticalVmScaling scaling) {
    // return false; }
    @Override
    public void deallocateGpuForVGpu(VGpu vgpu) {
        /**/ }

    @Override
    public void setFindGpuForVGpuFunction(
            BiFunction<VGpuAllocationPolicy, VGpu, Optional<Gpu>> findGpuForVGpuFunction) {
        /**/ }

    @Override
    public List<Gpu> getGpuList() {
        return Collections.emptyList();
    }

    @Override
    public Map<VGpu, Gpu> getOptimizedAllocationMap(
            List<? extends VGpu> vgpuList) {
        return Collections.emptyMap();
    }

    @Override
    public Optional<Gpu> findGpuForVGpu(VGpu vgpu) {
        return Optional.empty();
    }

    @Override
    public boolean isVGpuMigrationSupported() {
        return false;
    }

    @Override
    public int getGpuCountForParallelSearch() {
        return 0;
    }

    @Override
    public void setGpuCountForParallelSearch(int gpuCountForParallelSearch) {
        /**/ }

}