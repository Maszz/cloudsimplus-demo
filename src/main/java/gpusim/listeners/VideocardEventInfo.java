package gpusim.listeners;

import org.cloudsimplus.listeners.EventInfo;
import gpusim.videocards.Videocard;

public interface VideocardEventInfo extends EventInfo {

    Videocard getVideocard();
}