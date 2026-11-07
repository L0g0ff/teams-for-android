package nl.wapnet.teamswrapper;

import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import androidx.annotation.RequiresApi;

/**
 * Earpiece/speaker switch for Teams calls. WebView cannot list audio outputs, so the Teams
 * speaker picker stays empty and Chromium routes call audio to the loudspeaker.
 * Measured on Fairphone / /e/OS during a call: mode NORMAL, communication device = speaker.
 * The earpiece only carries audio in MODE_IN_COMMUNICATION, so switching to it sets that mode.
 * ponytail: API 31+ only (setCommunicationDevice); older Android keeps the loudspeaker.
 */
@RequiresApi(31)
final class CallAudio {
    private final AudioManager audio;
    private boolean changedMode;

    CallAudio(AudioManager audio) {
        this.audio = audio;
    }

    boolean hasEarpiece() {
        return find(AudioDeviceInfo.TYPE_BUILTIN_EARPIECE) != null;
    }

    boolean onEarpiece() {
        AudioDeviceInfo device = audio.getCommunicationDevice();
        return device != null && device.getType() == AudioDeviceInfo.TYPE_BUILTIN_EARPIECE;
    }

    /** Returns true when the new route was accepted by Android. */
    boolean toggle() {
        if (onEarpiece()) {
            restore();
            AudioDeviceInfo speaker = find(AudioDeviceInfo.TYPE_BUILTIN_SPEAKER);
            return speaker != null && audio.setCommunicationDevice(speaker);
        }
        AudioDeviceInfo earpiece = find(AudioDeviceInfo.TYPE_BUILTIN_EARPIECE);
        if (earpiece == null) {
            return false;
        }
        if (audio.getMode() != AudioManager.MODE_IN_COMMUNICATION) {
            audio.setMode(AudioManager.MODE_IN_COMMUNICATION);
            changedMode = true;
        }
        return audio.setCommunicationDevice(earpiece);
    }

    /** Undo our changes; called when switching back and when the Activity goes away. */
    void restore() {
        if (changedMode) {
            audio.clearCommunicationDevice();
            audio.setMode(AudioManager.MODE_NORMAL);
            changedMode = false;
        }
    }

    private AudioDeviceInfo find(int type) {
        for (AudioDeviceInfo device : audio.getAvailableCommunicationDevices()) {
            if (device.getType() == type) {
                return device;
            }
        }
        return null;
    }
}
