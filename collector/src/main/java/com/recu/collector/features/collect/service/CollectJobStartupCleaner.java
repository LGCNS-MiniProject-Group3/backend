package com.recu.collector.features.collect.service;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/** collector가 뜬 뒤, DB에 남은 RUNNING 수집을 FAILED로 정리한다. */
@Component
public class CollectJobStartupCleaner {

  private final CollectJobService collectJobService;

  public CollectJobStartupCleaner(CollectJobService collectJobService) {
    this.collectJobService = collectJobService;
  }

  @EventListener(ApplicationReadyEvent.class)
  public void onReady() {
    collectJobService.failStaleRunningJobs();
  }
}
