package com.fc.springcloud.provider.Impl.hcloud;

import com.fc.springcloud.pojo.dto.GatewayEvent;
import com.fc.springcloud.pojo.dto.MetricsEvent;
import com.fc.springcloud.pojo.dto.ScheduleAction;
import com.fc.springcloud.pojo.dto.ScheduleEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import jointfaas.mesh.model.Model.Application;
import jointfaas.mesh.model.Model.Step;
import lombok.SneakyThrows;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;

// Scheduler will analyze data from upstream and create event to scale up/down containers
public class Scheduler {

  final double concurrence = 10.0;
  private static final Log logger = LogFactory.getLog(Scheduler.class);

  private HCloudProvider provider;
  private BlockingQueue<MetricsEvent> metricsEvents;
  private BlockingQueue<GatewayEvent> gatewayEvents;
  private BlockingQueue<ScheduleEvent> scheduleEvents;
  private ExecutorService backend;

  public Scheduler(BlockingQueue<MetricsEvent> upstream, BlockingQueue<ScheduleEvent> downstream,
      BlockingQueue<GatewayEvent> gatewayUpstream, HCloudProvider provider) {
    this.metricsEvents = upstream;
    this.scheduleEvents = downstream;
    this.gatewayEvents = gatewayUpstream;
    this.provider = provider;
    this.backend = Executors.newFixedThreadPool(2);
  }

  public void Start() {
    // Finally if Scheduler makes a decision, it will create a sheduleEvent and put it into queues.
    this.backend.execute(new Runnable() {
      @SneakyThrows
      @Override
      public void run() {
        do {
          handleMetrics(metricsEvents.take());
        } while (true);
      }
    });

    this.backend.execute(new Runnable() {
      @SneakyThrows
      @Override
      public void run() {
        do {
          handleMosn(gatewayEvents.take());
        } while (true);
      }
    });
  }

  private boolean shouldScaleUp(MetricsEvent event) {
    List<String> instances = this.provider.getWorkerMaintainer()
        .GetInstanceByFunctionName(event.getFunctionName());
    int size = instances.size();
    return event.getQps() / size > concurrence;
  }

  private Integer predictTarget(MetricsEvent event) {
    List<String> instances = this.provider.getWorkerMaintainer()
        .GetInstanceByFunctionName(event.getFunctionName());
    int size = instances.size();
    return new Double(Math.ceil(event.getQps() / concurrence)).intValue();
  }

  private List<ScheduleEvent> CreateContainerScheduleEvent(MetricsEvent event) {
    Integer target = predictTarget(event);
    String functionName = event.getFunctionName();
    List<ScheduleEvent> events = new ArrayList<>();
    ScheduleEvent scheduleEvent = new ScheduleEvent();
    scheduleEvent.setFunctionName(functionName);
    scheduleEvent.setAction(ScheduleAction.create);
    scheduleEvent.setTarget(target);
    events.add(scheduleEvent);
    return events;
  }

  private List<ScheduleEvent> DeleteOrStopContainerScheduleEvent(MetricsEvent event) {
    Integer target = predictTarget(event);
    List<ScheduleEvent> events = new ArrayList<>();
    ScheduleEvent scheduleEvent = new ScheduleEvent();
    scheduleEvent.setFunctionName(event.getFunctionName());
    scheduleEvent.setAction(ScheduleAction.delete);
    scheduleEvent.setTarget(target);
    events.add(scheduleEvent);
    return events;
  }

  private void handleMosn(GatewayEvent event) {
    logger.info("handle mosn");
    logger.info(event);
    try {
      if (this.provider.getConfig().batchScale) {
        Application application = this.provider.getMeshInjector()
            .getApplication(event.getApplicationName());
        List<ScheduleEvent> events = new ArrayList<>();
        for (Step step : application.getStepsMap().values()) {
          if (step.getFunction() != null) {
            ScheduleEvent scheduleEvent = new ScheduleEvent();
            scheduleEvent.setAction(ScheduleAction.create);
            scheduleEvent.setFunctionName(step.getFunction().getFunctionName());
            scheduleEvent.setTarget(1);
            events.add(scheduleEvent);
          }
        }
        this.scheduleEvents.addAll(events);
      } else {
        ScheduleEvent scheduleEvent = new ScheduleEvent();
        scheduleEvent.setAction(ScheduleAction.create);
        scheduleEvent.setFunctionName(event.getFunctionName());
        scheduleEvent.setTarget(1);
        this.scheduleEvents.add(scheduleEvent);
      }
    } catch (RuntimeException e) {
      logger.error(e);
      // handle error about application is not existed
      ScheduleEvent scheduleEvent = new ScheduleEvent();
      scheduleEvent.setAction(ScheduleAction.create);
      scheduleEvent.setFunctionName(event.getFunctionName());
      scheduleEvent.setTarget(1);
      this.scheduleEvents.add(scheduleEvent);
    }
  }

  private void handleMetrics(MetricsEvent event) {
    logger.info(event);
    List<ScheduleEvent> schedules = null;
    if (shouldScaleUp(event)) {
      logger.debug("should scale up");
      schedules = CreateContainerScheduleEvent(event);
    } else if (shouldScaleDown(event)) {
      logger.debug("should scale down");
      schedules = DeleteOrStopContainerScheduleEvent(event);
    }
    if (schedules != null) {
      this.scheduleEvents.addAll(schedules);
    }
  }

  private boolean shouldScaleDown(MetricsEvent event) {
    // todo add some timestamp for optimize
    List<String> instances = this.provider.getWorkerMaintainer()
        .GetInstanceByFunctionName(event.getFunctionName());
    int size = instances.size();
    return event.getQps() / size < concurrence;
  }

}
