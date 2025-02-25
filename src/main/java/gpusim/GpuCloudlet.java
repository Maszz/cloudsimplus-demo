package gpusim;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.cloudsimplus.cloudlets.CloudletSimple;
import org.cloudsimplus.cloudlets.network.CloudletExecutionTask;
import org.cloudsimplus.cloudlets.network.CloudletTask;
import org.cloudsimplus.vms.Vm;

public class GpuCloudlet extends CloudletSimple {
    private int currentTaskNum;
    private final List<CloudletTask> tasks;

    public GpuCloudlet(final int pesNumber) {
        this(-1, pesNumber);
    }

    public GpuCloudlet(final int id, final int pesNumber) {
        super(id, -1, pesNumber);
        this.currentTaskNum = -1;
        this.tasks = new ArrayList<>();
    }

    public double getNumberOfTasks() {
        return tasks.size();
    }

    public List<CloudletTask> getTasks() {
        return Collections.unmodifiableList(tasks);
    }

    public boolean isTasksStarted() {
        return currentTaskNum > -1;
    }

    public boolean startNextTaskIfCurrentIsFinished(final double nextTaskStartTime) {
        return getNextTaskIfCurrentIfFinished()
                .map(task -> startTask(task, nextTaskStartTime))
                .isPresent();
    }

    private static CloudletTask startTask(final CloudletTask task, double time) {
        task.setStartTime(time);
        return task;
    }

    public Optional<CloudletTask> getCurrentTask() {
        if (currentTaskNum < 0 || currentTaskNum >= tasks.size()) {
            return Optional.empty();
        }

        return Optional.of(tasks.get(currentTaskNum));
    }

    private Optional<CloudletTask> getNextTaskIfCurrentIfFinished() {
        if (getCurrentTask().filter(CloudletTask::isActive).isPresent()) {
            return Optional.empty();
        }

        if (this.currentTaskNum <= tasks.size() - 1) {
            this.currentTaskNum++;
        }

        return getCurrentTask();
    }

    @Override
    public boolean isFinished() {
        final boolean allTasksFinished = tasks == null || tasks.stream().allMatch(CloudletTask::isFinished);
        return super.isFinished() && allTasksFinished;
    }

    @Override
    public long getLength() {
        return getTasks().stream()
                .filter(CloudletTask::isExecutionTask)
                .map(task -> (CloudletExecutionTask) task)
                .mapToLong(CloudletExecutionTask::getLength)
                .sum();
    }

    public GpuVm getVm() {
        return (GpuVm) super.getVm();
    }

    @Override
    public GpuCloudlet setVm(final Vm vm) {
        if (vm == Vm.NULL) {
            setVm(GpuVm.NULL);
            return this;
        }

        if (vm instanceof GpuVm) {
            super.setVm(vm);
            return this;
        }

        throw new IllegalArgumentException("GpuCloudlet can just be executed by a GpuVm");
    }

}
