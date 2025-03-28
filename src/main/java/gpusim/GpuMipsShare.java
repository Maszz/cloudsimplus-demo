package gpusim;

import java.util.Objects;

public class GpuMipsShare {
    public static final GpuMipsShare NULL = new GpuMipsShare();

    private long cores; // Represents GPU cores instead of PEs
    private double mips; // MIPS capacity for each GPU core

    /**
     * Creates an empty GPU MIPS share, with no cores.
     */
    public GpuMipsShare() {
        this(0, 0);
    }

    /**
     * Creates a GPU MIPS share with 1 core having a given MIPS capacity.
     * 
     * @param mips the allocated or requested MIPS capacity for every GPU core.
     */
    public GpuMipsShare(final double mips) {
        this(1, mips);
    }

    public long getCores() {
        return cores;
    }

    /**
     * Creates a GPU MIPS share according to a given {@link Gpu} capacity.
     * 
     * @param gpu the GPU to get its capacity to create the GpuMipsShare.
     */
    public GpuMipsShare(final Gpu gpu) {
        Objects.requireNonNull(gpu, "GPU cannot be null");
        this.cores = Math.max(0, gpu.getCores().size());
        this.mips = Math.max(0, gpu.getCores().get(0).getCapacity()); // Assume uniform MIPS per core
    }

    /**
     * Creates a GPU MIPS share with a defined number of GPU cores and MIPS capacity
     * per core.
     * 
     * @param cores the number of GPU cores shared.
     * @param mips  the allocated or requested MIPS capacity for each GPU core.
     */
    public GpuMipsShare(final long cores, final double mips) {
        this.cores = Math.max(0, cores);
        this.mips = Math.max(0, mips);
    }

    /**
     * Clone constructor.
     * 
     * @param share the GPU MIPS share to clone.
     */
    public GpuMipsShare(final GpuMipsShare share) {
        Objects.requireNonNull(share, "GpuMipsShare cannot be null");
        this.cores = share.cores;
        this.mips = share.mips;
    }

    /** Returns the MIPS capacity per GPU core. */
    public double mips() {
        return mips;
    }

    public final void setMips(final double mips) {
        this.mips = Math.max(0, mips);
    }

    /** Returns the number of allocated/requested GPU cores. */
    public long cores() {
        return cores;
    }

    /** Returns `true` if there is no MIPS capacity allocated. */
    public boolean isEmpty() {
        return cores == 0 || mips == 0;
    }

    /** Returns the total MIPS capacity (sum across all GPU cores). */
    public double totalMips() {
        return cores * mips;
    }

    /**
     * Removes a given number of GPU cores from the MIPS share.
     * It won't remove more cores than are available.
     * 
     * @param count number of GPU cores to remove.
     * @return the number of actual removed GPU cores.
     */
    public long remove(final long count) {
        final long removedCores = Math.min(Math.max(0, count), this.cores);
        this.cores -= removedCores;
        return removedCores;
    }

    @Override
    public String toString() {
        return "GpuMipsShare{" +
                "cores=" + cores +
                ", mips=" + mips +
                '}';
    }
}