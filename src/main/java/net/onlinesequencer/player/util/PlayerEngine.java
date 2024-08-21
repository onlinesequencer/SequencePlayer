package net.onlinesequencer.player.util;

import net.onlinesequencer.player.protos.SequenceProto;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import javax.annotation.Nullable;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.ProtocolException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;

import static net.onlinesequencer.player.SequencePlayer.plugin;
import static org.bukkit.Bukkit.getServer;

class SoundType {
    float pitch;  // set
    float volume;  // mult
    Sound sound;
    SoundType(float volume, float pitch, Sound sound) {
        this.pitch = pitch;
        this.volume = volume;
        this.sound = sound;
    }
}

class NormalizedSoundParams {
    Sound inst;
    float volume;
    float pitch;
    public NormalizedSoundParams(Sound inst, float volume, float pitch) {
        this.inst = inst;
        this.volume = volume;
        this.pitch = pitch;
    }
}

enum DrumSound {
    KICK,
    SNARE,
    HAT,
    OPEN_HAT,
    HIGHEST_TOM,
    HIGH_TOM,
    HIGH_MID_TOM,
    MID_TOM,
    MID_LOW_TOM,
    LOW_TOM,
    STICKS,
    CRASH,
    RIDE,
    SHAKER;

    SoundType getSound() {
        switch (this) {
            case KICK:
                return new SoundType(1.0f, 4.0f, Sound.BLOCK_NOTE_BLOCK_BASEDRUM);
            case SNARE:
                return new SoundType(1.0f, 7.0f, Sound.BLOCK_NOTE_BLOCK_SNARE);
            case HIGHEST_TOM:
                return new SoundType(1.0f, 24.0f, Sound.BLOCK_NOTE_BLOCK_BASEDRUM);
            case HIGH_TOM:
                return new SoundType(1.0f, 20.0f, Sound.BLOCK_NOTE_BLOCK_BASEDRUM);
            case HIGH_MID_TOM:
                return new SoundType(1.0f, 18.0f, Sound.BLOCK_NOTE_BLOCK_BASEDRUM);
            case MID_TOM:
                return new SoundType(1.0f, 15.0f, Sound.BLOCK_NOTE_BLOCK_BASEDRUM);
            case MID_LOW_TOM:
                return new SoundType(1.0f, 12.0f, Sound.BLOCK_NOTE_BLOCK_BASEDRUM);
            case LOW_TOM:
                return new SoundType(1.0f, 10.0f, Sound.BLOCK_NOTE_BLOCK_BASEDRUM);
            case HAT:
            case OPEN_HAT:  // idk
                return new SoundType(1.0f, 12.0f, Sound.BLOCK_NOTE_BLOCK_HAT);
            case STICKS:
                return new SoundType(1.0f, 3.0f, Sound.BLOCK_NOTE_BLOCK_HAT);
            case CRASH:
            case RIDE:
                return new SoundType(0.3f, 16.0f, Sound.BLOCK_FIRE_EXTINGUISH);
            case SHAKER:
                return new SoundType(1.0f, 10.0f, Sound.BLOCK_SUSPICIOUS_SAND_PLACE);
            default:
                return null;
        }
    }
}

class MarkerTracker {
    List<SequenceProto.Marker> markers;
    public MarkerTracker(List<SequenceProto.Marker> markers) {
        this.markers = markers;
    }

    public int findLastMarkerIndexBeforeTimeForTypeAndInstrument(float time, int type, int instrument) {
        int i = 0;
        for (SequenceProto.Marker m : this.markers) {
            float mt = m.getTime();
            if (m.getSetting() != type || m.getInstrument() != instrument) {
                i++;
                continue;
            }
            try {
                SequenceProto.Marker next = this.markers.get(i + 1);
                float nextTime = next.getTime();
                if (time < nextTime && time >= mt) {
                    return i;
                }
            } catch (IndexOutOfBoundsException ie) {
                return i;
            }
            i++;
        }
        return -1;
    }

    public static float lerp(float v0, float v1, float t) {
        return (1 - t) * v0 + t * v1;
    }

    public Float getValueAtTimeForTypeWithInstrument(float time, int type, int instrument) {
        int firstIndex = this.findLastMarkerIndexBeforeTimeForTypeAndInstrument(time, type, instrument);
        if (firstIndex == -1) return null;
        SequenceProto.Marker startMarker = this.markers.get(firstIndex);
        SequenceProto.Marker endMarker;
        try {
            endMarker = this.markers.get(firstIndex + 1);
        } catch (IndexOutOfBoundsException ie) {
            return startMarker.getValue();
        }
        if (endMarker == null || !endMarker.getBlend()) return startMarker.getValue();

        return lerp(startMarker.getValue(), endMarker.getValue(), (time - startMarker.getTime()) / (endMarker.getTime() - startMarker.getTime()));
    }
}

public class PlayerEngine {
    boolean playing;
    
    static final double[] volumeWeight = {0.25, 0.5, 0.4, 0.8, 0.8, 0.8, 0.65, 0.8, 0.3, 0.2, 0.2, 0.55, 0.1, 0.1, 0.1, 0.1, 0.1, 0.3, 0.3, 0.3, 1.3, 1.3, 1.5, 1, 1, 1.5, 1.5, 1.0, 1.0, 1.0, 1.0, 0.8, 1.0, 1.3, 1.0, 1.0, 1.0, 0.5, 1.0, 1.0, 1.0, 0.4, 2.8, 0.18, 0.3, 1, 0.55, 1, 0.6, 0.4, 0.5, 0.5, 0.8, 0.8, 0.8, 0.1, 0.3, 0.3, 0.3, 0.0, 1.0};

    public Thread getThread(Object executor, int sequenceId, Callable<Boolean> onNote, Callable<Boolean> onEnded) {
        Player player;
        Location location;
        if (executor instanceof Player) {
            location = null;
            player = (Player) executor;
        } else {
            player = null;
            if (executor instanceof Location) {
                location = (Location) executor;
            } else {
                throw new RuntimeException("'executor' must be either Player or Location");
            }
        }
        Runnable sequenceProcessor = () -> {
            String urlStr = String.format("https://onlinesequencer.net/app/api/get_proto.php?id=%d", sequenceId);
            URL url;
            try {
                url = new URL(urlStr);
            } catch (MalformedURLException e) {
                if (player != null) player.sendMessage(ChatColor.RED + "Could not format URL");
                return;
            }
            HttpURLConnection conn = null;
            try {
                conn = (HttpURLConnection) url.openConnection();
            } catch (IOException e) {
                if (player != null) player.sendMessage(ChatColor.RED + "Could not connect to server");
            }
            try {
                assert conn != null;
                conn.setRequestMethod("GET");
            } catch (ProtocolException e) {
                // shouldn't happen
                return;
            }
            try {
                int respCode = conn.getResponseCode();
                if (respCode != 200) {
                    if (player != null) player.sendMessage(ChatColor.RED + "Invalid sequence! (" + respCode + ")");
                    return;
                }
            } catch (IOException e) {
                if (player != null) player.sendMessage(ChatColor.RED + "Could not retrieve status");
            }
            try (InputStream stream = conn.getInputStream()) {
                SequenceProto.Sequence seq = SequenceProto.Sequence.parseFrom(stream);
                List<SequenceProto.Note> notes = new ArrayList<>(seq.getNotesList());
                notes.sort((SequenceProto.Note a, SequenceProto.Note b) -> (int)((a.getTime() - b.getTime()) * 1000));
                AtomicReference<Float> lastTime = new AtomicReference<>((float) 0);
                AtomicReference<Float> bpm = new AtomicReference<>((float) seq.getSettings().getBpm());
                AtomicReference<Float> sleepTime = new AtomicReference<>(15000f / bpm.get());
                if (player != null) player.sendMessage(ChatColor.AQUA + "Playing sequence " + sequenceId);
                this.playing = true;
                MarkerTracker tracker = new MarkerTracker(seq.getMarkersList());
                notes.forEach((SequenceProto.Note note) -> {
                    if (!this.playing) return;
                    float time = note.getTime();
                    // track BPM if necessary
                    Float markerBPM = tracker.getValueAtTimeForTypeWithInstrument(note.getTime(), 0, 0);
                    if (markerBPM != null) {
                        // recalculate tempo
                        sleepTime.set(15000f / markerBPM);
                        bpm.set(markerBPM);
                    }
                    if (time > lastTime.get()) {
                        long diffTime = (long) (sleepTime.get() * (time - lastTime.get()));
                        try {
                            TimeUnit.MILLISECONDS.sleep(diffTime);
                        } catch (InterruptedException e) {
                            if (player != null) {
                                player.sendMessage(ChatColor.RED + "Sleep failed");
                            } else {
                                Logger.getLogger("SequencePlayer").log(Level.WARNING, String.format("Failed to sleep %d milliseconds", diffTime));
                            }
                        }
                    }
                    SequenceProto.InstrumentSettings settings = seq.getSettings().getInstrumentsMap().get(note.getInstrument());
                    if (player != null) {
                        playNote(player, note, settings, tracker);
                    } else {
                        playNote(location, note, settings, tracker);
                    }
                    if (settings.getDelay()) {
                        // Schedule delay
                        long delayTicks = (long)((60f / bpm.get()) * 10f);
                        // 3 responses
                        for (int i = 0; i < 4; i++) {
                            if (player != null) {
                                playNoteDelayed(player, note, settings, tracker, delayTicks * i, (4f - (float)i) * 0.25f);
                            } else {
                                playNoteDelayed(location, note, settings, tracker, delayTicks * i, (4f - (float)i) * 0.25f);
                            }
                        }
                    }
                    lastTime.set(time);
                    try {
                        onNote.call();
                    } catch (Exception e) {
                        Logger.getLogger("SequencePlayer").log(Level.WARNING, "Failed to execute callback onNote for SequencePlayer");
                    }
                });
                try {
                    onEnded.call();
                } catch (Exception e) {
                    Logger.getLogger("SequencePlayer").log(Level.WARNING, "Failed to execute callback onEnded for SequencePlayer");
                }
            } catch (IOException e) {
                if (player != null) player.sendMessage(ChatColor.RED + "Could not read");
            }
        };
        return new Thread(sequenceProcessor);
    }

    public void stop() {
        this.playing = false;
    }

    private @Nullable DrumSound translateDrumKitOrElectricDrumKit(int note) {
        switch (note) {
            case 31:
            case 33:
            case 37:
                return DrumSound.STICKS;
            case 35:
            case 36:
                return DrumSound.KICK;
            case 38:
            case 39:
            case 40:
                return DrumSound.SNARE;
            case 41:
                return DrumSound.LOW_TOM;
            case 43:
                return DrumSound.MID_LOW_TOM;
            case 45:
                return DrumSound.MID_TOM;
            case 47:
                return DrumSound.HIGH_MID_TOM;
            case 48:
                return DrumSound.HIGH_TOM;
            case 50:
                return DrumSound.HIGHEST_TOM;
            case 42:
            case 44:
                return DrumSound.HAT;
            case 46:
                return DrumSound.OPEN_HAT;
            case 49:
            case 57:
                return DrumSound.CRASH;
            case 51:
            case 59:
                return DrumSound.RIDE;
            case 82:
                return DrumSound.SHAKER;
            default:
                return null;
        }
    }

    private @Nullable DrumSound translate808DrumKit(int note) {
        switch (note) {
            case 27:
            case 28:
            case 29:
                return DrumSound.KICK;
            case 30:
            case 31:
            case 32:
            case 33:
                return DrumSound.SNARE;
            case 38:
                return DrumSound.MID_TOM;
            case 39:
                return DrumSound.HIGH_MID_TOM;
            case 40:
                return DrumSound.HIGH_TOM;
            case 41:
                return DrumSound.HIGHEST_TOM;
            case 34:
            case 44:
                return DrumSound.STICKS;
            case 35:
                return DrumSound.HAT;
            case 36:
                return DrumSound.OPEN_HAT;
            case 37:
                return DrumSound.CRASH;
            default:
                return null;
        }
    }

    private @Nullable DrumSound translate8BitDrumKit(int note) {
        switch (note) {
            case 31:
                return DrumSound.KICK;
            case 32:
            case 34:
            case 35:
            case 36:
                return DrumSound.SNARE;
            case 33:
                return DrumSound.HAT;
            default:
                return null;
        }
    }

    private @Nullable DrumSound translate2013DrumKit(int note) {
        switch (note) {
            case 31:
            case 32:
                return DrumSound.KICK;
            case 33:
            case 34:
            case 35:
            case 36:
            case 56:
            case 58:
                return DrumSound.SNARE;
            // this drum kit has kind of wonky tom pitches, but this should do
            case 37:
                return DrumSound.LOW_TOM;
            case 39:
                return DrumSound.MID_TOM;
            case 41:
                return DrumSound.MID_LOW_TOM;
            case 43:
                return DrumSound.HIGH_MID_TOM;
            case 44:
                return DrumSound.HIGH_TOM;
            case 46:
                return DrumSound.HIGHEST_TOM;
            case 38:
                return DrumSound.HAT;
            case 40:
                return DrumSound.OPEN_HAT;
            case 42:
                return DrumSound.CRASH;  // close
            case 45:
            case 48:
            case 53:
            case 57:
                return DrumSound.CRASH;
            case 47:
            case 54:
                return DrumSound.RIDE;
            default:
                return null;
        }
    }

    private @Nullable DrumSound translate909DrumKit(int note) {
        switch (note) {
            case 27:
            case 28:
            case 29:
                return DrumSound.KICK;
            case 30:
            case 31:
            case 32:
            case 40:
                return DrumSound.SNARE;
            case 33:
                return DrumSound.LOW_TOM;
            case 35:
                return DrumSound.MID_LOW_TOM;
            case 34:
                return DrumSound.MID_TOM;
            case 37:
                return DrumSound.HIGH_MID_TOM;
            case 36:
                return DrumSound.HIGH_TOM;
            case 38:
                return DrumSound.HIGHEST_TOM;
            case 41:
            case 42:
                return DrumSound.HAT;
            case 43:
            case 44:
                return DrumSound.OPEN_HAT;
            case 45:
                return DrumSound.CRASH;
            case 46:
                return DrumSound.RIDE;
            case 39:
                return DrumSound.STICKS;
            default:
                return null;
        }
    }

    private @Nullable DrumSound translate2023DrumKit(int note) {
        switch (note) {
            case 24:
            case 30:
                return DrumSound.STICKS;
            case 25:
            case 26:
            case 27:
            case 28:
            case 29:
                return DrumSound.KICK;
            case 31:
            case 32:
            case 33:
                return DrumSound.SNARE;
            case 36:
                return DrumSound.LOW_TOM;
            case 37:
                return DrumSound.MID_LOW_TOM;
            case 38:
            case 40:
                return DrumSound.MID_TOM;
            case 39:
                return DrumSound.HIGH_MID_TOM;
            case 41:
                return DrumSound.HIGH_TOM;
            case 42:
            case 44:
                return DrumSound.HAT;
            case 43:
                return DrumSound.OPEN_HAT;
            case 46:
            case 47:
            case 48:
                return DrumSound.CRASH;
            case 49:
            case 50:
                return DrumSound.RIDE;
            default:
                return null;
        }
    }

    private @Nullable Sound translateInstrument(int instrument) {
        int baseInst = instrument % 10000;
        switch (baseInst) {
            case 1:
            case 4:
            case 22:
            case 32:
            case 33:
            case 35:
            case 38:
            case 44:
            case 49:
                return Sound.BLOCK_NOTE_BLOCK_GUITAR;
            case 3:
            case 20:
            case 28:
            case 45:
            case 46:
            case 47:
                return Sound.BLOCK_NOTE_BLOCK_CHIME;
            case 5:
            case 29:
            case 37:
            case 48:
            case 54:
                return Sound.BLOCK_NOTE_BLOCK_BASS;
            case 6:
            case 7:
            case 11:
            case 12:
            case 52:
                return Sound.BLOCK_NOTE_BLOCK_PLING;
            case 9:
            case 10:
            case 23:
            case 24:
            case 50:
            case 51:
                return Sound.BLOCK_NOTE_BLOCK_FLUTE;
            case 13:
            case 14:
            case 15:
            case 16:
            case 27:
            case 30:
            case 55:
            case 56:
            case 57:
                return Sound.BLOCK_NOTE_BLOCK_BIT;
            case 19:
                return Sound.BLOCK_NOTE_BLOCK_XYLOPHONE;
            case 21:
            case 34:
                return Sound.BLOCK_NOTE_BLOCK_IRON_XYLOPHONE;
            case 2:
            case 31:
            case 39:
            case 36:
            case 40:
            case 42:
            case 53:
                return null;
            default:
                return Sound.BLOCK_NOTE_BLOCK_HARP;
        }
    }

    private NormalizedSoundParams getParams(SequenceProto.Note note, SequenceProto.InstrumentSettings instSettings, MarkerTracker tracker) {
        Sound inst = translateInstrument(note.getInstrument());
        float volume = note.getVolume() * (float) volumeWeight[note.getInstrument() % 10000];
        float pitch = (float)note.getTypeValue();
        float addPitch = 0f;
        float multVolume = 1f;
        if (instSettings != null) {
            addPitch = instSettings.getDetune() / 100;
            multVolume = instSettings.getVolume();
        }

        // Marker tracking
        Float markerVolume = tracker.getValueAtTimeForTypeWithInstrument(note.getTime(), 1, note.getInstrument());
        if (markerVolume != null) {
            volume = markerVolume;
        }

        Float markerPitch = tracker.getValueAtTimeForTypeWithInstrument(note.getTime(), 11, note.getInstrument());
        if (markerPitch != null) {
            addPitch = markerPitch / 100;
        }

        Float markerMasterVolume = tracker.getValueAtTimeForTypeWithInstrument(note.getTime(), 8, 0);
        if (markerMasterVolume != null) {
            volume *= markerMasterVolume;
        }
        volume *= multVolume;

        // Clamping
        if (volume < 0.0001) {
            return null;
        }
        pitch += addPitch + 6f;
        if (pitch < 0) {
            pitch = 0f;
        }
        if (pitch > 24) {
            pitch = 12 + (pitch % 12);
        }

        if (inst == null) {
            DrumSound drumInst = null;
            switch (note.getInstrument()) {
                case 2:
                case 31:
                    drumInst = translateDrumKitOrElectricDrumKit(note.getTypeValue());
                    break;
                case 39:
                    drumInst = translate8BitDrumKit(note.getTypeValue());
                    break;
                case 36:
                    drumInst = translate808DrumKit(note.getTypeValue());
                    break;
                case 40:
                    drumInst = translate2013DrumKit(note.getTypeValue());
                    break;
                case 42:
                    drumInst = translate909DrumKit(note.getTypeValue());
                    break;
                case 53:
                    drumInst = translate2023DrumKit(note.getTypeValue());
                    break;
            }
            if (drumInst == null || drumInst.getSound() == null) {
                return null;
            }
            SoundType sound = drumInst.getSound();
            inst = sound.sound;
            volume *= sound.volume;
            pitch = sound.pitch;
        }

        return new NormalizedSoundParams(inst, volume, (float) Math.pow(2.0, ((double) pitch - 12.0) / 12.0));
    }

    private void playNote(Player player, SequenceProto.Note note, SequenceProto.InstrumentSettings instSettings, MarkerTracker tracker) {
        NormalizedSoundParams params = this.getParams(note, instSettings, tracker);
        if (params == null) return;
        getServer().getScheduler().runTask(plugin, () ->
                player.playSound(player.getLocation(), params.inst, params.volume, params.pitch));
    }

    private void playNote(Location location, SequenceProto.Note note, SequenceProto.InstrumentSettings instSettings, MarkerTracker tracker) {
        NormalizedSoundParams params = this.getParams(note, instSettings, tracker);
        if (params == null) return;
        getServer().getScheduler().runTask(plugin, () ->
                Objects.requireNonNull(location.getWorld()).playSound(location, params.inst, params.volume * 2, params.pitch));
    }

    private void playNoteDelayed(Player player, SequenceProto.Note note, SequenceProto.InstrumentSettings instSettings, MarkerTracker tracker, long ticks, float volMod) {
        NormalizedSoundParams params = this.getParams(note, instSettings, tracker);
        if (params == null) return;
        getServer().getScheduler().runTaskLater(plugin, () ->
                player.playSound(player.getLocation(), params.inst, params.volume * volMod, params.pitch), ticks);
    }

    private void playNoteDelayed(Location location, SequenceProto.Note note, SequenceProto.InstrumentSettings instSettings, MarkerTracker tracker, long ticks, float volMod) {
        NormalizedSoundParams params = this.getParams(note, instSettings, tracker);
        if (params == null) return;
        getServer().getScheduler().runTaskLater(plugin, () ->
                Objects.requireNonNull(location.getWorld()).playSound(location, params.inst, params.volume * 2 * volMod, params.pitch), ticks);
    }
}
