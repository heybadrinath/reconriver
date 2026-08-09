package io.github.heybadrinath.reconriver.audit;

import io.github.heybadrinath.reconriver.shared.events.LifecycleEvent;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

@Component
class LifecycleEventMetrics {
    private final MeterRegistry meters;

    LifecycleEventMetrics(MeterRegistry meters) {
        this.meters = meters;
    }

    @ApplicationModuleListener
    void record(LifecycleEvent event) {
        meters.counter("reconriver.lifecycle.events", "type", event.type()).increment();
    }
}
