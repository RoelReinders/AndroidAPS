package info.nightscout.androidaps.plugins.pump.omnipod.history;

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;

public enum OmnicoreCommandHistoryStatus {
    NEW(R.string.omnicore_command_status_new),
    PENDING(R.string.omnicore_command_status_pending),
    SUCCESS(R.string.omnicore_command_status_success),
    FAILED(R.string.omnicore_command_status_failed),
    CANCELLED(R.string.omnicore_command_status_cancelled),
    EXECUTED(R.string.omnicore_command_status_executed),
    UNKNOWN(R.string.omnicore_command_status_unknown);

    private int resourceId;

    OmnicoreCommandHistoryStatus(int resourceId) {

        this.resourceId = resourceId;
    }

    public String getDescription() {
        return MainApp.gs(resourceId);
    }
}
