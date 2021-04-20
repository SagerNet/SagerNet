package io.nekohasekai.sagernet.aidl;

import io.nekohasekai.sagernet.aidl.TrafficStats;

oneway interface IShadowsocksServiceCallback {
  void stateChanged(int state, String profileName, String msg);
  void trafficUpdated(long profileId, in TrafficStats stats);
  // Traffic data has persisted to database, listener should refetch their data from database
  void trafficPersisted(long profileId);
}
