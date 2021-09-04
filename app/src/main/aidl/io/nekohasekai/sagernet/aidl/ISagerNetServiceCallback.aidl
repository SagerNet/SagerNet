package io.nekohasekai.sagernet.aidl;

import io.nekohasekai.sagernet.aidl.TrafficStats;
import io.nekohasekai.sagernet.aidl.AppStatsList;

oneway interface ISagerNetServiceCallback {
  void stateChanged(int state, String profileName, String msg);
  void trafficUpdated(long profileId, in TrafficStats stats, boolean isCurrent);
  void statsUpdated(in AppStatsList statsList);
  void observatoryResultsUpdated(long groupId);
  // Traffic data has persisted to database, listener should refetch their data from database
  void profilePersisted(long profileId);
  void missingPlugin(String profileName, String pluginName);
  void routeAlert(int type, String routeName);
}
