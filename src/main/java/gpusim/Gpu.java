package gpusim;

import org.cloudsimplus.resources.PeSimple;
import org.cloudsimplus.resources.Pe;
import java.util.ArrayList;
import java.util.List;

public class Gpu {
    public static final Gpu NULL = new Gpu(); // Singleton Null GPU

    private int id;
    private List<Pe> cores;
    private long memory;

    // Constructor for standard GPUs
    public Gpu(int id, int cores, long memory, double mips) {
        this.id = id;
        this.cores = new ArrayList<>(); // Initialize list
        for (int i = 0; i < cores; i++) {
            this.cores.add(new PeSimple(mips));
        }
        this.memory = memory;
    }

    // Private constructor for Gpu.NULL
    private Gpu() {
        this.id = -1; // Special ID for NULL GPU
        this.cores = List.of(); // Immutable empty list
        this.memory = 0;
    }

    public List<Pe> getCores() {
        return cores;
    }

    public long getMemory() {
        return memory;
    }

    public boolean isNull() {
        return this == NULL;
    }
}
