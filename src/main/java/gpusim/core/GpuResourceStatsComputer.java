package gpusim.core;

public interface GpuResourceStatsComputer<T extends GResourceStats<?>> {

    T getGpuUtilizationStats();

    void enableUtilizationStats();
}