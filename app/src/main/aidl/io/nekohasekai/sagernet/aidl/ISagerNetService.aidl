package io.nekohasekai.sagernet.aidl;

import io.nekohasekai.sagernet.aidl.ISagerNetServiceCallback;

interface ISagerNetService {
  int getState();
  String getProfileName();

  void registerCallback(in ISagerNetServiceCallback cb);
  void startListeningForBandwidth(in ISagerNetServiceCallback cb, long timeout);
  oneway void stopListeningForBandwidth(in ISagerNetServiceCallback cb);
  void startListeningForStats(in ISagerNetServiceCallback cb, long timeout);
  oneway void stopListeningForStats(in ISagerNetServiceCallback cb);
  oneway void unregisterCallback(in ISagerNetServiceCallback cb);
  oneway void protect(int fd);
  int urlTest();
  oneway void resetTrafficStats();
  boolean getTrafficStatsEnabled();

}
