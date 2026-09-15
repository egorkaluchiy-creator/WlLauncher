package net.legacylauncher.instances;

public interface InstanceManagerListener {
    void onActiveInstanceChanged(String oldInstance, String newInstance);
    void onInstancesListChanged();
}
