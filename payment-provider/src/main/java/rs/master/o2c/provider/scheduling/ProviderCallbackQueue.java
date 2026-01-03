package rs.master.o2c.provider.scheduling;

import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

@Component
public class ProviderCallbackQueue {

    private final Sinks.Many<ProviderCallbackTask> sink = Sinks.many().unicast().onBackpressureBuffer();

    public void enqueue(ProviderCallbackTask task) {
        sink.emitNext(task, Sinks.EmitFailureHandler.FAIL_FAST);
    }

    public Flux<ProviderCallbackTask> flux() {
        return sink.asFlux();
    }
}
