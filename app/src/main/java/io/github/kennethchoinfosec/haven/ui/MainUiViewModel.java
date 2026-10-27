package io.github.kennethchoinfosec.haven.ui;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

/** Activity-scoped state shared by the main screen and its profile list fragments. */
public final class MainUiViewModel extends ViewModel {
    public enum EventType {
        REFRESH_APPS,
        CONTEXT_MENU_CLOSED
    }

    private final MutableLiveData<String> mSearchQuery = new MutableLiveData<>("");
    private final MutableLiveData<UiEvent> mEvents = new MutableLiveData<>();

    public LiveData<String> searchQuery() {
        return mSearchQuery;
    }

    public LiveData<UiEvent> events() {
        return mEvents;
    }

    public void setSearchQuery(String query) {
        mSearchQuery.setValue(query);
    }

    public void refreshApps(boolean remoteProfile) {
        postEvent(EventType.REFRESH_APPS, remoteProfile);
    }

    public void contextMenuClosed(boolean remoteProfile) {
        postEvent(EventType.CONTEXT_MENU_CLOSED, remoteProfile);
    }

    private void postEvent(EventType type, boolean remoteProfile) {
        mEvents.setValue(new UiEvent(type, remoteProfile));
    }

    public static final class UiEvent {
        private final EventType mType;
        private final boolean mRemoteProfile;
        private boolean mHandled;

        private UiEvent(EventType type, boolean remoteProfile) {
            mType = type;
            mRemoteProfile = remoteProfile;
        }

        public EventType type() {
            return mType;
        }

        public boolean isRemoteProfile() {
            return mRemoteProfile;
        }

        public synchronized boolean markHandled() {
            if (mHandled) {
                return false;
            }
            mHandled = true;
            return true;
        }
    }
}
